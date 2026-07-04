package meta.claw.core.llm.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.tool.SpiToolCall;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步 LLM 调用的响应提取与指标记录 Advisor。
 * <p>
 * 位于 Advisor 链最内侧，紧挨实际模型调用，负责：
 * <ul>
 *   <li>测量本次 LLM 调用 latency；</li>
 *   <li>从 {@link ChatResponse} 提取 content、reasoningContent、usage、toolCalls；</li>
 *   <li>将提取结果写入共享 {@link MetaClawCallContext}；</li>
 *   <li>调用 {@link MetricsRecorder} 记录 latency 与 token usage。</li>
 * </ul>
 */
@Slf4j
public class MetaClawResponseCallAdvisor implements CallAdvisor {

    private final MetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;

    public MetaClawResponseCallAdvisor(MetricsRecorder metricsRecorder, ObjectMapper objectMapper) {
        this.metricsRecorder = metricsRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        MetaClawCallContext ctx = (MetaClawCallContext) request.context().get("metaClawCallContext");
        long startTime = System.currentTimeMillis();

        ChatClientResponse response = chain.nextCall(request);

        long latency = System.currentTimeMillis() - startTime;
        ChatResponse chatResponse = response != null ? response.chatResponse() : null;

        if (ctx != null && chatResponse != null) {
            Generation gen = chatResponse.getResult();
            AssistantMessage am = gen != null && gen.getOutput() instanceof AssistantMessage
                    ? (AssistantMessage) gen.getOutput() : null;

            ctx.setContent(am != null ? am.getText() : "");
            ctx.setReasoningContent(extractReasoningContent(gen));
            ctx.setUsage(extractUsage(chatResponse));
            ctx.setToolCalls(extractToolCalls(am));

            log.debug("[MetaClawResponseCallAdvisor] vessel={}, latency={}ms, contentLen={}, toolCalls={}",
                    ctx.getVesselId(), latency,
                    ctx.getContent() != null ? ctx.getContent().length() : 0,
                    ctx.getToolCalls() != null ? ctx.getToolCalls().size() : 0);
        }

        if (ctx != null) {
            recordMetrics(ctx.getVesselId(), latency, ctx.getUsage());
        }

        return response;
    }

    @Override
    public String getName() {
        return "MetaClawResponseCallAdvisor";
    }

    @Override
    public int getOrder() {
        // 最内侧，紧挨模型调用
        return 100;
    }

    private static String extractReasoningContent(Generation gen) {
        if (gen == null) {
            return null;
        }
        String reasoningContent = null;
        if (gen.getOutput() instanceof AssistantMessage am && am.getMetadata() != null) {
            Object rc = am.getMetadata().get("reasoningContent");
            if (rc instanceof String s && !s.isEmpty()) {
                reasoningContent = s;
            }
        }
        if (reasoningContent == null && gen.getMetadata() != null) {
            Object rc = gen.getMetadata().get("reasoningContent");
            if (rc instanceof String s && !s.isEmpty()) {
                reasoningContent = s;
            }
        }
        return reasoningContent;
    }

    private static SpiUsage extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        org.springframework.ai.chat.metadata.Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        return SpiUsage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    private List<SpiToolCall> extractToolCalls(AssistantMessage am) {
        List<SpiToolCall> result = new ArrayList<>();
        if (am == null || !am.hasToolCalls()) {
            return result;
        }
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            try {
                Map<String, Object> args = objectMapper.readValue(tc.arguments(), new TypeReference<>() {});
                result.add(SpiToolCall.builder().id(tc.id()).name(tc.name()).arguments(args).build());
            } catch (Exception e) {
                log.warn("Failed to parse tool call arguments: {}", tc.arguments(), e);
            }
        }
        return result;
    }

    private void recordMetrics(String vesselId, long latencyMs, SpiUsage usage) {
        if (metricsRecorder == null) {
            return;
        }
        metricsRecorder.recordLlmLatency(vesselId, latencyMs);
        metricsRecorder.recordTokenUsage(vesselId, usage);
    }
}
