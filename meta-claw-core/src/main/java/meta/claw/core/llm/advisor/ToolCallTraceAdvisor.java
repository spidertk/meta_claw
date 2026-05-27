package meta.claw.core.llm.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.tool.registry.ToolRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用追踪 Advisor。
 * <p>
 * 包装在 {@link org.springframework.ai.chat.client.advisor.ToolCallAdvisor} 内侧（order=0），
 * 记录每次实际发给 ChatModel 的请求和响应，包括 tool_calls 和 tool_responses。
 * </p>
 * <p>
 * 由于 {@code ToolCallAdvisor} 默认 order = Integer.MIN_VALUE + 500（最外层），
 * 本 Advisor 在 chain 中排在 ToolCallAdvisor 之后，因此能记录到 ToolCallAdvisor
 * 内部循环调用 ChatModel 的每一次完整交互。
 * </p>
 */
@Slf4j
@Component
public class ToolCallTraceAdvisor implements CallAdvisor, StreamAdvisor {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final ToolRegistry toolRegistry;

    public ToolCallTraceAdvisor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.info("[TRACE-CALL-REQUEST]\n{}", formatRequest(request));
        ChatClientResponse response = chain.nextCall(request);
        log.info("[TRACE-CALL-RESPONSE]\n{}", formatResponse(response));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        log.info("[TRACE-STREAM-REQUEST]\n{}", formatRequest(request));
        StringBuilder contentBuilder = new StringBuilder();
        return chain.nextStream(request)
                .doOnNext(r -> {
                    ChatResponse cr = r.chatResponse();
                    if (cr != null && cr.getResult() != null) {
                        AssistantMessage am = cr.getResult().getOutput();
                        if (am.getText() != null) {
                            contentBuilder.append(am.getText());
                        }
                    }
                })
                .doOnComplete(() -> {
                    String content = contentBuilder.toString();
                    if (!content.isEmpty()) {
                        log.info("[TRACE-STREAM-RESPONSE] content={}", content);
                    } else {
                        log.info("[TRACE-STREAM-RESPONSE] (empty, tool call intercepted by ToolCallAdvisor)");
                    }
                    log.info("[TRACE-STREAM] Completed");
                });
    }

    @Override
    public String getName() {
        return "ToolCallTraceAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private String formatRequest(ChatClientRequest req) {
        try {
            StringBuilder sb = new StringBuilder();
            // 检查是否包含 tool call 相关的消息
            boolean hasToolCalls = req.prompt().getInstructions().stream()
                    .anyMatch(m -> m instanceof AssistantMessage am && am.hasToolCalls());
            boolean hasToolResponses = req.prompt().getInstructions().stream()
                    .anyMatch(m -> m instanceof ToolResponseMessage);
            if (hasToolCalls || hasToolResponses) {
                sb.append("[!!! TOOL CALL ROUND !!!]\n");
            }
            // 打印可用工具
            sb.append("[tools: ").append(toolRegistry.toolCount()).append("] ");
            List<String> toolNames = toolRegistry.getToolInstances().stream()
                    .map(Object::getClass)
                    .map(Class::getSimpleName)
                    .toList();
            if (!toolNames.isEmpty()) {
                sb.append(toolNames).append("\n");
            } else {
                sb.append("[]\n");
            }
            // 打印消息
            List<Map<String, Object>> msgList = new ArrayList<>();
            for (Message m : req.prompt().getInstructions()) {
                msgList.add(messageToMap(m));
            }
            sb.append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(msgList));
            return sb.toString();
        } catch (Exception e) {
            return "messages count=" + req.prompt().getInstructions().size() + " (format error: " + e.getMessage() + ")";
        }
    }

    private String formatResponse(ChatClientResponse resp) {
        try {
            ChatResponse cr = resp.chatResponse();
            if (cr == null) {
                return "null";
            }
            Generation gen = cr.getResult();
            if (gen == null) {
                return "no generation";
            }
            AssistantMessage am = gen.getOutput();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", "assistant");
            map.put("content", am.getText());
            if (am.hasToolCalls()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (var tc : am.getToolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("name", tc.name());
                    tcMap.put("arguments", tc.arguments());
                    tcs.add(tcMap);
                }
                map.put("tool_calls", tcs);
            }
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private Map<String, Object> messageToMap(Message m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.getMessageType().getValue());
        if (m instanceof AssistantMessage am) {
            map.put("content", am.getText());
            if (am.hasToolCalls()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (var tc : am.getToolCalls()) {
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("name", tc.name());
                    tcMap.put("arguments", tc.arguments());
                    tcs.add(tcMap);
                }
                map.put("tool_calls", tcs);
            }
        } else if (m instanceof ToolResponseMessage trm) {
            List<Map<String, Object>> responses = new ArrayList<>();
            for (var r : trm.getResponses()) {
                Map<String, Object> rMap = new LinkedHashMap<>();
                rMap.put("id", r.id());
                rMap.put("name", r.name());
                rMap.put("responseData", r.responseData());
                responses.add(rMap);
            }
            map.put("tool_responses", responses);
        } else {
            map.put("content", m.getText());
        }
        return map;
    }
}
