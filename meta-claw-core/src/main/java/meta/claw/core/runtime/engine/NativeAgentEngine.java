package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.AgentExecutor;
import meta.claw.core.runtime.StreamingAgentExecutor;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 复用现有 {@link AgentExecutor} / {@link StreamingAgentExecutor} 的本地引擎。
 */
@Component
public class NativeAgentEngine implements AgentEngine {

    @Autowired
    private AgentExecutor agentExecutor;
    @Autowired
    private StreamingAgentExecutor streamingAgentExecutor;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        return agentExecutor.execute(ctx, request);
    }

    @Override
    public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        return streamingAgentExecutor.execute(ctx, request, callback);
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        return agentExecutor.resume(ctx, request, ticket, resolution);
    }

    @Override
    public String name() {
        return "native";
    }
}
