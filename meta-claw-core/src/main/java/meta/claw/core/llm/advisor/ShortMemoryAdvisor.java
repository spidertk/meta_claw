package meta.claw.core.llm.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.runtime.VesselManager;
import meta.claw.core.runtime.VesselRuntime;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stream Advisor，负责在流式响应结束时将完整的 assistant 消息（含 reasoning、toolCalls）
 * 持久化到 ShortMemory。
 *
 * <p>该 Advisor 位于 {@link org.springframework.ai.chat.client.advisor.ToolCallAdvisor} 与
 * {@link org.springframework.ai.chat.client.advisor.ChatModelStreamAdvisor} 之间，
 * 因此能看到模型原始输出（包含 tool_calls 之前的 reasoning），不受外层 tool call 拦截影响。</p>
 */
@Slf4j
@Component
public class ShortMemoryAdvisor implements StreamAdvisor {

    @Autowired
    private VesselManager vesselManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String vesselName = (String) request.context().get("vesselName");
        String sessionKey = (String) request.context().get("sessionId");
        MemoryConfig  memoryConfig = (MemoryConfig) request.context().get("memoryConfig");
        if (vesselName == null || sessionKey == null) {
            log.debug("[ShortMemoryAdvisor] vesselName or sessionKey not provided, skipping persistence");
            return chain.nextStream(request);
        }

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        return chain.nextStream(request)
                .doOnNext(response -> {
                    ChatResponse cr = response.chatResponse();
                    if (cr == null || cr.getResult() == null) {
                        return;
                    }
                    AssistantMessage am = cr.getResult().getOutput();
                    if (am == null) {
                        return;
                    }
                    if (am.getText() != null) {
                        contentBuilder.append(am.getText());
                    }
                    if (am.getMetadata() != null) {
                        Object rc = am.getMetadata().get("reasoningContent");
                        if (rc instanceof String s && !s.isEmpty()) {
                            reasoningBuilder.append(s);
                        }
                    }
                    if (am.getToolCalls() != null) {
                        toolCalls.addAll(am.getToolCalls());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        List<SpiToolCall> spiToolCalls = toolCalls.stream()
                                .map(tc -> {
                                    try {
                                        Map<String, Object> args = objectMapper.readValue(
                                                tc.arguments(), new TypeReference<Map<String, Object>>() {});
                                        return SpiToolCall.builder()
                                                .id(tc.id())
                                                .name(tc.name())
                                                .arguments(args)
                                                .build();
                                    } catch (Exception e) {
                                        log.warn("Failed to parse tool call arguments: {}", tc.arguments(), e);
                                        return SpiToolCall.builder()
                                                .id(tc.id())
                                                .name(tc.name())
                                                .arguments(Map.of())
                                                .build();
                                    }
                                })
                                .collect(Collectors.toList());

                        MemoryMessage message = MemoryMessage.builder()
                                .timestamp(LocalDateTime.now())
                                .role("assistant")
                                .content(contentBuilder.toString())
                                .reasoningContent(reasoningBuilder.toString())
                                .toolCalls(spiToolCalls.isEmpty() ? null : spiToolCalls)
                                .build();
                        VesselRuntime vesselRuntime =    vesselManager.getRuntime(vesselName);
                        vesselRuntime.getShortMemory().appendMessage(vesselName, sessionKey, message);
                        log.debug("[ShortMemoryAdvisor] Persisted assistant message to memory: vessel={}, session={}, contentLength={}, reasoningLength={}",
                                vesselName, sessionKey, contentBuilder.length(), reasoningBuilder.length());
                    } catch (Exception e) {
                        log.error("[ShortMemoryAdvisor] Failed to persist assistant message", e);
                    }
                });
    }

    @Override
    public String getName() {
        return "ShortMemoryAdvisor";
    }

    @Override
    public int getOrder() {
        // ToolCallAdvisor = -2147483348, ChatModelStreamAdvisor = 2147483647
        // 位于两者之间，能看到原始模型输出
        return 0;
    }
}
