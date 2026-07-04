package meta.claw.core.llm.advisor;

import lombok.Getter;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import meta.claw.core.tool.SpiToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 LLM 调用的共享上下文，由 {@link org.springframework.ai.chat.client.ChatClient}
 * 通过 advisor param 传入 Advisor 链，Advisors 在调用过程中把提取结果写回此处。
 * <p>
 * 当与 {@link TaskContext} 绑定时，token usage、tool call 计数会自动同步到任务上下文，
 * 避免 ReAct 循环每次迭代都创建新的上下文对象。
 * </p>
 */
@Getter
public class MetaClawCallContext {

    public static final String CONTEXT_KEY = "metaClawCallContext";
    public static final String EXPLICIT_TOOL_CALLBACKS_KEY = "explicitToolCallbacks";

    private final TaskContext taskContext;
    private SpiStreamingCallback streamingCallback;

    private final String vesselId;
    private final String sessionId;
    private final long startTime;

    private String content;
    private String reasoningContent;
    private SpiUsage usage;
    private List<SpiToolCall> toolCalls = new ArrayList<>();

    /**
     * 绑定到任务上下文的构造方法（推荐）。
     */
    public MetaClawCallContext(TaskContext taskContext) {
        this.taskContext = taskContext;
        this.vesselId = taskContext.getTask().getVesselId();
        this.sessionId = taskContext.getTask().getSessionId();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 无 TaskContext 时的降级构造方法（如 KnowledgeAnalyzer 等非 Agent 链路直接调用）。
     */
    public MetaClawCallContext(String vesselId, String sessionId) {
        this.taskContext = null;
        this.vesselId = vesselId;
        this.sessionId = sessionId;
        this.startTime = System.currentTimeMillis();
    }

    public void setStreamingCallback(SpiStreamingCallback streamingCallback) {
        this.streamingCallback = streamingCallback;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    /**
     * 设置 usage 时，自动同步到 TaskContext。
     */
    public void setUsage(SpiUsage usage) {
        this.usage = usage;
        if (taskContext != null && usage != null) {
            taskContext.addTokenUsage(usage);
        }
    }

    /**
     * 设置 toolCalls 时，自动同步 tool call 计数到 TaskContext。
     */
    public void setToolCalls(List<SpiToolCall> toolCalls) {
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
        if (taskContext != null && !this.toolCalls.isEmpty()) {
            for (SpiToolCall ignored : this.toolCalls) {
                taskContext.incrementToolCallCount();
            }
        }
    }

    /**
     * 记录本次 LLM 调用的 latency 与 token usage。
     */
    public void recordLlmMetrics(MetricsRecorder metricsRecorder, long latencyMs) {
        if (metricsRecorder == null) {
            return;
        }
        metricsRecorder.recordLlmLatency(vesselId, latencyMs);
        metricsRecorder.recordTokenUsage(vesselId, usage);
    }

    /**
     * 获取累积的 content，null 时返回空字符串。
     */
    public String getContentOrEmpty() {
        return content != null ? content : "";
    }

    /**
     * 获取 toolCalls，保证非 null。
     */
    public List<SpiToolCall> getToolCallsOrEmpty() {
        return toolCalls != null ? toolCalls : new ArrayList<>();
    }
}
