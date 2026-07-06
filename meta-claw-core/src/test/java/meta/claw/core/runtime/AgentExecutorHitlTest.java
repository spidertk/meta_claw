package meta.claw.core.runtime;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.hitl.*;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentExecutorHitlTest {

    @Test
    void executeThrowsHitlSuspendedExceptionWhenApprovalRequired() {
        AgentExecutor executor = new AgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.chatWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class)))
                .thenReturn(SpiChatResponse.builder()
                        .content("")
                        .toolCalls(List.of(SpiToolCall.builder()
                                .id("call-1")
                                .name("dangerous")
                                .arguments(Map.of("x", 1))
                                .build()))
                        .build());
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("dangerous");
        when(callback.getToolDefinition()).thenReturn(definition);

        ToolSubSystem toolSub = mock(ToolSubSystem.class);
        when(toolSub.name()).thenReturn("tool");
        when(toolSub.getToolCallbacks()).thenReturn(List.of(callback));

        HitlSubSystem hitlSub = new HitlSubSystem();
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("dangerous"), Set.of());
        ReflectionTestUtils.setField(hitlSub, "hitlPolicy", policy);
        ReflectionTestUtils.setField(hitlSub, "hitlGate", new InMemoryHitlGate());

        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(toolSub);
        registry.register(hitlSub);

        TaskContext ctx = TaskContext.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("do it").profile(null).registry(registry
        ).build();

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(SpiMessage.user("do it")))
                .build();

        HitlSuspendedException ex = assertThrows(HitlSuspendedException.class,
                () -> executor.execute(ctx, request));
        assertNotNull(ex.getTicket());
        assertEquals("dangerous", ex.getTicket().getItems().get(0).getToolName());
    }

    @Test
    void resumeExecutesApprovedToolAndContinuesLoop() {
        AgentExecutor executor = new AgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("dangerous");
        when(callback.getToolDefinition()).thenReturn(definition);
        try {
            when(callback.call(anyString())).thenReturn("done");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.chatWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class)))
                .thenReturn(SpiChatResponse.builder()
                        .content("final answer")
                        .toolCalls(List.of())
                        .build());
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        ToolSubSystem toolSub = mock(ToolSubSystem.class);
        when(toolSub.name()).thenReturn("tool");
        when(toolSub.getToolCallbacks()).thenReturn(List.of(callback));

        HitlSubSystem hitlSub = new HitlSubSystem();
        ReflectionTestUtils.setField(hitlSub, "hitlPolicy", new ConfigurableHitlPolicy());
        ReflectionTestUtils.setField(hitlSub, "hitlGate", new InMemoryHitlGate());

        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(toolSub);
        registry.register(hitlSub);

        TaskContext ctx = TaskContext.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("do it").profile(null).registry(registry
        ).build();

        ApprovalTicket ticket = ApprovalTicket.builder()
                .ticketId("ticket-1")
                .taskId("t1")
                .items(List.of(ApprovalItem.builder()
                        .toolCallId("call-1")
                        .toolName("dangerous")
                        .argumentsJson("{\"x\":1}")
                        .build()))
                .build();

        ApprovalResolution resolution = ApprovalResolution.builder()
                .ticketId("ticket-1")
                .decisions(Map.of("call-1", ApprovalStatus.APPROVED))
                .operator("test")
                .build();

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(
                        SpiMessage.user("do it"),
                        SpiMessage.assistant(null, List.of(SpiToolCall.builder()
                                .id("call-1")
                                .name("dangerous")
                                .arguments(Map.of("x", 1))
                                .build()))
                ))
                .build();

        Reply reply = executor.resume(ctx, request, ticket, resolution);
        assertEquals("final answer", reply.getContent());

        try {
            verify(callback).call("{\"x\":1}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
