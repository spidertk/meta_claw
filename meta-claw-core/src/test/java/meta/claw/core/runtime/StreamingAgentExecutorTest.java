package meta.claw.core.runtime;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingAgentExecutorTest {

    @Test
    void streamsTextReplyWhenNoToolCalls() {
        StreamingAgentExecutor executor = new StreamingAgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.streamWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class), any(SpiStreamingCallback.class)))
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onStart();
                    cb.onChunk("hello");
                    cb.onComplete(SpiChatResponse.builder().content("hello").build());
                    return SpiChatResponse.builder().content("hello").build();
                });
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        SubSystemRegistry registry = new SubSystemRegistry();
        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("hi").build(),
                null, registry);

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1").sessionId("s1").messages(List.of(SpiMessage.user("hi"))).build();

        List<String> chunks = new ArrayList<>();
        Reply reply = executor.execute(ctx, request, new SpiStreamingCallback() {
            @Override public void onStart() {}
            @Override public void onChunk(String chunk) { chunks.add(chunk); }
            @Override public void onReasoningChunk(String chunk) {}
            @Override public void onToolCall(SpiToolCall toolCall) {}
            @Override public void onUsage(SpiUsage usage) {}
            @Override public void onComplete(SpiChatResponse response) {}
            @Override public void onError(Throwable error) {}
        });

        assertEquals("hello", reply.getContent());
        assertEquals(List.of("hello"), chunks);
    }

    @Test
    void executesToolCallWithoutHitl() {
        StreamingAgentExecutor executor = new StreamingAgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.streamWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class), any(SpiStreamingCallback.class)))
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onToolCall(SpiToolCall.builder().id("c1").name("calc").arguments(Map.of("x", 1)).build());
                    return SpiChatResponse.builder()
                            .content("")
                            .toolCalls(List.of(SpiToolCall.builder().id("c1").name("calc").arguments(Map.of("x", 1)).build()))
                            .build();
                })
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onChunk("result is 2");
                    return SpiChatResponse.builder().content("result is 2").build();
                });
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("calc");
        when(callback.getToolDefinition()).thenReturn(definition);
        try {
            when(callback.call(anyString())).thenReturn("2");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ToolSubSystem toolSub = mock(ToolSubSystem.class);
        when(toolSub.name()).thenReturn("tool");
        when(toolSub.getToolCallbacks()).thenReturn(List.of(callback));

        HitlSubSystem hitlSub = new HitlSubSystem();
        ReflectionTestUtils.setField(hitlSub, "hitlPolicy", new ConfigurableHitlPolicy());
        ReflectionTestUtils.setField(hitlSub, "hitlGate", new InMemoryHitlGate());

        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(toolSub);
        registry.register(hitlSub);

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("calc").build(),
                null, registry);

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1").sessionId("s1").messages(List.of(SpiMessage.user("calc"))).build();

        Reply reply = executor.execute(ctx, request, new NoOpStreamingCallback());
        assertEquals("result is 2", reply.getContent());
    }

    @Test
    void suspendsAndResumesWithHitlApproval() {
        StreamingAgentExecutor executor = new StreamingAgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.streamWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class), any(SpiStreamingCallback.class)))
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onToolCall(SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build());
                    return SpiChatResponse.builder()
                            .content("")
                            .toolCalls(List.of(SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build()))
                            .build();
                })
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onChunk("approved result");
                    return SpiChatResponse.builder().content("approved result").build();
                });
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("dangerous");
        when(callback.getToolDefinition()).thenReturn(definition);
        try {
            when(callback.call(anyString())).thenReturn("done");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("do it").build(),
                null, registry);

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1").sessionId("s1").messages(List.of(SpiMessage.user("do it"))).build();

        AtomicReference<ApprovalTicket> capturedTicket = new AtomicReference<>();
        Reply reply = executor.execute(ctx, request, new NoOpStreamingCallback() {
            @Override
            public ApprovalResolution onHitlSuspend(ApprovalTicket ticket) {
                capturedTicket.set(ticket);
                return ApprovalResolution.builder()
                        .ticketId(ticket.getTicketId())
                        .decisions(Map.of("c1", ApprovalStatus.APPROVED))
                        .operator("test")
                        .build();
            }
        });

        assertNotNull(capturedTicket.get());
        assertEquals("dangerous", capturedTicket.get().getItems().get(0).getToolName());
        assertEquals("approved result", reply.getContent());
    }

    @Test
    void preservesAssistantContentAndReasoningAfterHitlApproval() {
        StreamingAgentExecutor executor = new StreamingAgentExecutor();
        ReflectionTestUtils.setField(executor, "maxSteps", 10);

        AtomicReference<List<SpiMessage>> secondCallMessages = new AtomicReference<>();
        LlmClientManager llmClient = mock(LlmClientManager.class);
        when(llmClient.streamWithTools(any(SpiChatRequest.class), any(TaskContext.class), any(ToolCallback[].class), any(SpiStreamingCallback.class)))
                .thenAnswer(invocation -> {
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onReasoningChunk("let me think");
                    cb.onToolCall(SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build());
                    return SpiChatResponse.builder()
                            .content("assistant content")
                            .reasoningContent("let me think")
                            .toolCalls(List.of(SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build()))
                            .build();
                })
                .thenAnswer(invocation -> {
                    secondCallMessages.set(new ArrayList<>(invocation.getArgument(0, SpiChatRequest.class).getMessages()));
                    SpiStreamingCallback cb = invocation.getArgument(3);
                    cb.onChunk("approved result");
                    return SpiChatResponse.builder().content("approved result").build();
                });
        ReflectionTestUtils.setField(executor, "llmClient", llmClient);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("dangerous");
        when(callback.getToolDefinition()).thenReturn(definition);
        try {
            when(callback.call(anyString())).thenReturn("done");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("do it").build(),
                null, registry);

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1").sessionId("s1").messages(List.of(SpiMessage.user("do it"))).build();

        Reply reply = executor.execute(ctx, request, new NoOpStreamingCallback() {
            @Override
            public ApprovalResolution onHitlSuspend(ApprovalTicket ticket) {
                return ApprovalResolution.builder()
                        .ticketId(ticket.getTicketId())
                        .decisions(Map.of("c1", ApprovalStatus.APPROVED))
                        .operator("test")
                        .build();
            }
        });

        assertEquals("approved result", reply.getContent());
        assertNotNull(secondCallMessages.get());
        SpiMessage assistantMsg = secondCallMessages.get().stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .findFirst()
                .orElse(null);
        assertNotNull(assistantMsg);
        assertEquals("assistant content", assistantMsg.getContent());
        assertEquals("let me think", assistantMsg.getReasoningContent());
        assertNotNull(assistantMsg.getToolCalls());
        assertEquals(1, assistantMsg.getToolCalls().size());
    }

    static class NoOpStreamingCallback implements SpiStreamingCallback {
        @Override public void onStart() {}
        @Override public void onChunk(String chunk) {}
        @Override public void onReasoningChunk(String chunk) {}
        @Override public void onToolCall(SpiToolCall toolCall) {}
        @Override public void onUsage(SpiUsage usage) {}
        @Override public void onComplete(SpiChatResponse response) {}
        @Override public void onError(Throwable error) {}
    }
}
