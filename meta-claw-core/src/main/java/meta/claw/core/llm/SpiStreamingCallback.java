package meta.claw.core.llm;

import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.tool.SpiToolCall;

public interface SpiStreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onReasoningChunk(String chunk);
    void onToolCall(SpiToolCall toolCall);

    /**
     * 流式执行过程中触发 HITL 挂起时调用。
     * <p>实现方应展示审批票证并收集用户决议。返回 {@code null} 表示拒绝/取消。</p>
     *
     * @param ticket 审批票证
     * @return 用户决议，null 表示拒绝
     */
    default ApprovalResolution onHitlSuspend(ApprovalTicket ticket) {
        return null;
    }

    void onUsage(SpiUsage usage);
    void onComplete(SpiChatResponse response);
    void onError(Throwable error);
}
