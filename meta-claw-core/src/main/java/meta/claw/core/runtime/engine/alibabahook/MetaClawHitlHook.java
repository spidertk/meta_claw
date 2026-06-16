package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.runtime.HitlSuspendedException;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.HitlEvaluation;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 将 meta-claw {@link HitlSubSystem} 接入 SAA AFTER_MODEL Hook。
 *
 * <p>当模型返回包含 tool_calls 且命中审批策略时，通过抛 {@link HitlSuspendedException}
 * 中断图执行；外部收集 {@link meta.claw.core.runtime.hitl.ApprovalResolution} 后，
 * 由 {@link meta.claw.core.runtime.engine.SpringAiAlibabaAgentEngine#resume}
 * 把结果重新注入 messages 再调用。</p>
 */
public class MetaClawHitlHook extends ModelHook {

    private static final String MESSAGES_KEY = "messages";

    private final TaskContext ctx;

    public MetaClawHitlHook(TaskContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        AssistantMessage assistant = extractLastAssistantMessage(state);
        if (assistant == null || !assistant.hasToolCalls()) {
            return CompletableFuture.completedFuture(null);
        }

        List<SpiToolCall> spiToolCalls = assistant.getToolCalls().stream()
                .map(tc -> SpiToolCall.builder()
                        .id(tc.id())
                        .name(tc.name())
                        .arguments(parseArguments(tc.arguments()))
                        .build())
                .collect(Collectors.toList());

        HitlSubSystem hitlSubSystem = ctx.getSubSystem("hitl");
        if (hitlSubSystem == null) {
            return CompletableFuture.completedFuture(null);
        }

        HitlEvaluation eval = hitlSubSystem.evaluate(spiToolCalls, ctx);
        if (eval.hasSuspensions()) {
            throw new HitlSuspendedException(eval.getTicket());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of("raw", argumentsJson);
        }
    }

    @Override
    public String getName() {
        return "meta-claw-hitl-hook";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.AFTER_MODEL};
    }
}
