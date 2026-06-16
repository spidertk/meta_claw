package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.AgentExecutor;
import meta.claw.core.runtime.StreamingAgentExecutor;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NativeAgentEngineTest {

    @Test
    void nameIsNative() {
        NativeAgentEngine engine = new NativeAgentEngine();
        assertEquals("native", engine.name());
    }

    @Test
    void executeDelegatesToAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        StreamingAgentExecutor streamingAgentExecutor = mock(StreamingAgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);
        ReflectionTestUtils.setField(engine, "streamingAgentExecutor", streamingAgentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        Reply expected = new Reply(ReplyType.TEXT, "hello");
        when(agentExecutor.execute(ctx, request)).thenReturn(expected);

        Reply actual = engine.execute(ctx, request);

        assertEquals(expected, actual);
        verify(agentExecutor).execute(ctx, request);
        verifyNoInteractions(streamingAgentExecutor);
    }

    @Test
    void executeStreamDelegatesToStreamingAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        StreamingAgentExecutor streamingAgentExecutor = mock(StreamingAgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);
        ReflectionTestUtils.setField(engine, "streamingAgentExecutor", streamingAgentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);
        Reply expected = new Reply(ReplyType.TEXT, "streamed");
        when(streamingAgentExecutor.execute(ctx, request, callback)).thenReturn(expected);

        Reply actual = engine.executeStream(ctx, request, callback);

        assertEquals(expected, actual);
        verify(streamingAgentExecutor).execute(ctx, request, callback);
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void resumeDelegatesToAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        ApprovalTicket ticket = mock(ApprovalTicket.class);
        ApprovalResolution resolution = mock(ApprovalResolution.class);
        Reply expected = new Reply(ReplyType.TEXT, "resumed");
        when(agentExecutor.resume(ctx, request, ticket, resolution)).thenReturn(expected);

        Reply actual = engine.resume(ctx, request, ticket, resolution);

        assertEquals(expected, actual);
        verify(agentExecutor).resume(ctx, request, ticket, resolution);
    }
}
