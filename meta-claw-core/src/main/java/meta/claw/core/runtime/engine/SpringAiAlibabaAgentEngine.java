package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Spring AI Alibaba {@link ReactAgent} 的执行引擎实现。
 *
 * <p>Phase 2 先实现同步 call；Phase 3 接入 streamMessages；Phase 4 接入 HITL Hook。</p>
 */
@Component
public class SpringAiAlibabaAgentEngine implements AgentEngine {

    @Autowired
    private ReactAgentFactory reactAgentFactory;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ReactAgent agent = reactAgentFactory.get(ctx);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
        try {
            AssistantMessage result = agent.call(messages);
            return new Reply(ReplyType.TEXT, result.getText());
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            throw new RuntimeException("Alibaba agent execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        // Phase 3：接入 SAA streamMessages
        throw new UnsupportedOperationException(
                "SpringAiAlibabaAgentEngine streaming is planned for Phase 3");
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        // Phase 4：把 ApprovalResolution 结果重新注入 messages 再 call
        ReactAgent agent = reactAgentFactory.get(ctx);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
        try {
            AssistantMessage result = agent.call(messages);
            return new Reply(ReplyType.TEXT, result.getText());
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            throw new RuntimeException("Alibaba agent resume failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "alibaba";
    }
}
