package meta.claw.core.runtime;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import meta.claw.core.tool.SpiToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务执行上下文。
 * <p>单次 chat()/execute() 调用的唯一工作区。同时承载任务级状态
 * （消息、步骤、累计 token / tool-call）与单次 LLM 调用的临时结果。</p>
 */
@Getter
@Builder
public class TaskContext {

    private final String taskId;
    private final String vesselId;
    private final String sessionId;
    private final String userMessage;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    private final VesselProfile profile;
    private final SubSystemRegistry registry;

    @Builder.Default
    private final MessageThread messages = new MessageThread();

    @Builder.Default
    private final StepLog steps = new StepLog();

    @Builder.Default
    private final AtomicInteger toolCallCount = new AtomicInteger(0);

    @Builder.Default
    private final AtomicReference<SpiUsage> totalTokenUsage = new AtomicReference<>(
            SpiUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build());

    @Builder.Default
    private final long startTime = System.currentTimeMillis();

    // --- 单次 LLM 调用结果（每次 beginCall 时重置） ---
    private String lastContent;
    private String lastReasoningContent;
    private SpiUsage lastUsage;
    private List<SpiToolCall> lastToolCalls;

    /**
     * 便捷构造：VesselRuntime 最常用的场景。
     */
    public static TaskContext create(String vesselId, String sessionId, String userMessage,
                                     VesselProfile profile, SubSystemRegistry registry) {
        return TaskContext.builder()
                .taskId(UUID.randomUUID().toString())
                .vesselId(vesselId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .profile(profile)
                .registry(registry)
                .build();
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    public int getToolCallCount() {
        return toolCallCount.get();
    }

    public SpiUsage getTotalTokenUsage() {
        return totalTokenUsage.get();
    }

    public long getDurationMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 开始一次 LLM 调用，返回供 Advisor 链使用的轻量调用视图。
     */
    public LlmCallContext beginCall(SpiStreamingCallback streamingCallback) {
        this.lastContent = null;
        this.lastReasoningContent = null;
        this.lastUsage = null;
        this.lastToolCalls = null;
        return new LlmCallContext(this, streamingCallback);
    }

    /**
     * 累加单次 LLM 调用的 token 消耗到任务总计。
     */
    public void accumulateTokenUsage(SpiUsage usage) {
        if (usage == null) {
            return;
        }
        totalTokenUsage.updateAndGet(current -> SpiUsage.builder()
                .promptTokens(nullToZero(current.promptTokens()) + nullToZero(usage.promptTokens()))
                .completionTokens(nullToZero(current.completionTokens()) + nullToZero(usage.completionTokens()))
                .totalTokens(nullToZero(current.totalTokens()) + nullToZero(usage.totalTokens()))
                .build());
    }

    /**
     * 累加单次 LLM 调用产生的 tool-call 数量到任务总计。
     */
    public void accumulateToolCalls(List<SpiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        toolCallCount.addAndGet(toolCalls.size());
    }

    void setLastCallResult(String content, String reasoningContent, SpiUsage usage, List<SpiToolCall> toolCalls) {
        this.lastContent = content;
        this.lastReasoningContent = reasoningContent;
        this.lastUsage = usage;
        this.lastToolCalls = toolCalls;
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 单次 LLM 调用的 Advisor 链上下文。
     * <p>由 {@link TaskContext#beginCall(SpiStreamingCallback)} 创建，生命周期仅限一次 LLM 调用。
     * Advisors 通过 {@link org.springframework.ai.chat.client.ChatClientRequest#context()}
     * 读取此对象，并将提取结果写回 {@link TaskContext}。</p>
     */
    @Getter
    public static class LlmCallContext {

        public static final String CONTEXT_KEY = "llmCallContext";
        public static final String EXPLICIT_TOOL_CALLBACKS_KEY = "explicitToolCallbacks";
        public static final String SKIP_TOOL_INJECTION_KEY = "skipToolInjection";

        private final TaskContext taskContext;
        private final SpiStreamingCallback streamingCallback;
        private final long startTime;

        private LlmCallContext(TaskContext taskContext, SpiStreamingCallback streamingCallback) {
            this.taskContext = taskContext;
            this.streamingCallback = streamingCallback;
            this.startTime = System.currentTimeMillis();
        }

        public void setContent(String content) {
            taskContext.lastContent = content;
        }

        public void setReasoningContent(String reasoningContent) {
            taskContext.lastReasoningContent = reasoningContent;
        }

        /**
         * 设置 usage 并自动累加到任务总计。
         */
        public void setUsage(SpiUsage usage) {
            taskContext.lastUsage = usage;
            taskContext.accumulateTokenUsage(usage);
        }

        /**
         * 设置 toolCalls 并自动累加计数到任务总计。
         */
        public void setToolCalls(List<SpiToolCall> toolCalls) {
            taskContext.lastToolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
            taskContext.accumulateToolCalls(taskContext.lastToolCalls);
        }

        public String getContentOrEmpty() {
            return taskContext.lastContent != null ? taskContext.lastContent : "";
        }

        public String getReasoningContent() {
            return taskContext.lastReasoningContent;
        }

        public SpiUsage getUsage() {
            return taskContext.lastUsage;
        }

        public List<SpiToolCall> getToolCallsOrEmpty() {
            return taskContext.lastToolCalls != null ? taskContext.lastToolCalls : new ArrayList<>();
        }

        public String getVesselId() {
            return taskContext.getVesselId();
        }

        public String getSessionId() {
            return taskContext.getSessionId();
        }

        public void recordLlmMetrics(meta.claw.core.runtime.metrics.MetricsRecorder metricsRecorder, long latencyMs) {
            if (metricsRecorder == null) {
                return;
            }
            metricsRecorder.recordLlmLatency(taskContext.getVesselId(), latencyMs);
            metricsRecorder.recordTokenUsage(taskContext.getVesselId(), taskContext.lastUsage);
        }
    }
}
