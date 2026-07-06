package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 在 SAA 每次模型调用前后记录 LLM 延迟、Token 消耗与工具调用次数。
 *
 * <p>Token usage 优先从 {@link AssistantMessage#getMetadata()} 中读取；若不存在则仅记录 latency。</p>
 * <p>Tool call 次数在模型返回 tool_calls 时统计，视为该轮模型决策产生的待执行工具调用数。</p>
 */
public class MetaClawModelMetricsHook extends ModelHook {

    private static final String MESSAGES_KEY = "messages";

    private final TaskContext ctx;
    private final MetricsRecorder metricsRecorder;
    private long modelStartNanos;

    public MetaClawModelMetricsHook(TaskContext ctx, MetricsRecorder metricsRecorder) {
        this.ctx = ctx;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        modelStartNanos = System.nanoTime();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        if (metricsRecorder != null) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - modelStartNanos);
            String vesselId = ctx.getVesselId();
            metricsRecorder.recordLlmLatency(vesselId, latencyMs);

            AssistantMessage assistant = extractLastAssistantMessage(state);
            if (assistant != null) {
                recordTokenUsage(assistant, vesselId);
                recordToolCalls(assistant, vesselId);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    private AssistantMessage extractLastAssistantMessage(OverAllState state) {
        if (state == null) {
            return null;
        }
        Object messagesObj = state.data().get(MESSAGES_KEY);
        if (!(messagesObj instanceof List<?> messageList) || messageList.isEmpty()) {
            return null;
        }
        Object last = messageList.get(messageList.size() - 1);
        return last instanceof AssistantMessage am ? am : null;
    }

    private void recordTokenUsage(AssistantMessage assistant, String vesselId) {
        SpiUsage usage = extractUsage(assistant);
        if (usage != null) {
            ctx.accumulateTokenUsage(usage);
            metricsRecorder.recordTokenUsage(vesselId, usage);
        }
    }

    private SpiUsage extractUsage(AssistantMessage assistant) {
        Map<String, Object> metadata = assistant.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object usageObj = metadata.get("usage");
        if (!(usageObj instanceof Usage springUsage)) {
            return null;
        }
        return SpiUsage.builder()
                .promptTokens(springUsage.getPromptTokens())
                .completionTokens(springUsage.getCompletionTokens())
                .totalTokens(springUsage.getTotalTokens())
                .build();
    }

    private void recordToolCalls(AssistantMessage assistant, String vesselId) {
        if (!assistant.hasToolCalls()) {
            return;
        }
        List<meta.claw.core.tool.SpiToolCall> spiToolCalls = new java.util.ArrayList<>();
        for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
            metricsRecorder.recordToolCall(vesselId, tc.name());
            spiToolCalls.add(meta.claw.core.tool.SpiToolCall.builder()
                    .id(tc.id())
                    .name(tc.name())
                    .build());
        }
        ctx.accumulateToolCalls(spiToolCalls);
    }

    @Override
    public String getName() {
        return "meta-claw-model-metrics-hook";
    }
}
