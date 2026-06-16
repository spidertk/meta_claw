package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import meta.claw.core.runtime.HitlSuspendedException;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselTask;
import meta.claw.core.runtime.hitl.ApprovalItem;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.hitl.HitlDecision;
import meta.claw.core.runtime.hitl.HitlEvaluation;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaClawHitlHookTest {

    @Test
    void afterModelWithoutToolCallsReturnsNormally() {
        MetaClawHitlHook hook = createHook(mockHitlSubSystem(false));
        OverAllState state = new OverAllState(Map.of("messages", List.of(new UserMessage("hi"))));

        assertDoesNotThrow(() -> hook.afterModel(state, RunnableConfig.builder().build()).join());
    }

    @Test
    void afterModelWithApprovedToolCallsReturnsNormally() {
        HitlSubSystem hitlSub = mockHitlSubSystem(false);
        MetaClawHitlHook hook = createHook(hitlSub);

        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "safe_tool", "{}")))
                .build();
        OverAllState state = new OverAllState(Map.of("messages", List.of(new UserMessage("hi"), assistant)));

        assertDoesNotThrow(() -> hook.afterModel(state, RunnableConfig.builder().build()).join());
    }

    @Test
    void afterModelWithRequiredApprovalThrowsHitlSuspendedException() {
        HitlSubSystem hitlSub = mockHitlSubSystem(true);
        MetaClawHitlHook hook = createHook(hitlSub);

        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "dangerous", "{}")))
                .build();
        OverAllState state = new OverAllState(Map.of("messages", List.of(new UserMessage("hi"), assistant)));

        HitlSuspendedException ex = assertThrows(HitlSuspendedException.class,
                () -> hook.afterModel(state, RunnableConfig.builder().build()).join());
        assertNotNull(ex.getTicket());
        assertEquals("call_1", ex.getTicket().getItems().get(0).getToolCallId());
    }

    @Test
    void getHookPositionsReturnsAfterModel() {
        MetaClawHitlHook hook = createHook(mockHitlSubSystem(false));
        assertArrayEquals(new HookPosition[]{HookPosition.AFTER_MODEL}, hook.getHookPositions());
    }

    @Test
    void getNameReturnsExpectedValue() {
        MetaClawHitlHook hook = createHook(mockHitlSubSystem(false));
        assertEquals("meta-claw-hitl-hook", hook.getName());
    }

    private MetaClawHitlHook createHook(HitlSubSystem hitlSub) {
        VesselTask task = VesselTask.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .build();
        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(hitlSub);
        TaskContext ctx = new TaskContext(task, null, registry);
        return new MetaClawHitlHook(ctx);
    }

    private HitlSubSystem mockHitlSubSystem(boolean suspended) {
        HitlSubSystem hitlSub = mock(HitlSubSystem.class);
        when(hitlSub.name()).thenReturn("hitl");

        if (suspended) {
            ApprovalTicket ticket = ApprovalTicket.builder()
                    .ticketId("ticket-1")
                    .taskId("t1")
                    .items(List.of(ApprovalItem.builder()
                            .toolCallId("call_1")
                            .toolName("dangerous")
                            .argumentsJson("{}")
                            .build()))
                    .build();
            when(hitlSub.evaluate(anyList(), any(TaskContext.class)))
                    .thenReturn(HitlEvaluation.suspended(ticket, List.of(HitlDecision.REQUIRE_APPROVAL)));
        } else {
            when(hitlSub.evaluate(anyList(), any(TaskContext.class)))
                    .thenReturn(HitlEvaluation.approved(List.of(HitlDecision.APPROVE_AUTO)));
        }
        return hitlSub;
    }
}
