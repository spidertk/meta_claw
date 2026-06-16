package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;

/**
 * Agent 执行引擎 SPI。
 *
 * <p>实现类负责把 {@link SpiChatRequest} 转换为一次 Agent 任务执行，
 * 并返回最终 {@link Reply}。同步、流式、HITL 恢复三种入口必须同时提供。</p>
 *
 * <p>该接口刻意保持最小化：只接收 TaskContext 和 SpiChatRequest，
 * 不暴露任何 Spring AI 或 SAA 专有类型，保证上层 VesselRuntime 与引擎实现解耦。</p>
 */
public interface AgentEngine {

    /** 同步执行一次对话任务。 */
    Reply execute(TaskContext ctx, SpiChatRequest request);

    /** 流式执行一次对话任务。 */
    Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback);

    /** 从 HITL 挂起状态恢复并继续执行。 */
    Reply resume(TaskContext ctx, SpiChatRequest request,
                 ApprovalTicket ticket, ApprovalResolution resolution);

    /** 引擎名称，用于配置选择，如 {@code native} 或 {@code alibaba}。 */
    String name();
}
