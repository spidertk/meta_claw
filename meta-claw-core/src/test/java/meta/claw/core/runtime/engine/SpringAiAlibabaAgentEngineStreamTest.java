package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAlibabaAgentEngineStreamTest {

    @Test
    void executeStreamEmitsChunksAndReturnsReply() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(anyTaskContext())).thenReturn(agent);
        when(agent.streamMessages(anyList())).thenReturn(Flux.just(
                new AssistantMessage("Hello "),
                new AssistantMessage("world!")
        ));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);

        Reply reply = engine.executeStream(ctx, request, callback);

        assertNotNull(reply);
        assertEquals(ReplyType.TEXT, reply.getType());
        assertEquals("Hello world!", reply.getContent());
        verify(callback).onStart();
        verify(callback).onChunk("Hello ");
        verify(callback).onChunk("world!");
        verify(callback).onComplete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executeStreamEmitsToolCalls() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(anyTaskContext())).thenReturn(agent);

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_1", "function", "calculator", "{\"a\":1,\"b\":2}");
        AssistantMessage assistantWithTool = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        when(agent.streamMessages(anyList())).thenReturn(Flux.just(assistantWithTool));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("calculate")))
                .build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);

        engine.executeStream(ctx, request, callback);

        verify(callback).onToolCall(argThat(
                (SpiToolCall tc) -> "calculator".equals(tc.getName())
                        && tc.getArguments() != null
                        && Integer.valueOf(1).equals(tc.getArguments().get("a"))));
    }

    @Test
    void executeStreamEmitsReasoningChunks() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(anyTaskContext())).thenReturn(agent);

        AssistantMessage reasoningMessage = AssistantMessage.builder()
                .content("2")
                .properties(Map.of("reasoningContent", "1 + 1 = 2"))
                .build();
        when(agent.streamMessages(anyList())).thenReturn(Flux.just(reasoningMessage));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("what is 1+1")))
                .build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);

        engine.executeStream(ctx, request, callback);

        verify(callback).onReasoningChunk("1 + 1 = 2");
        verify(callback).onChunk("2");
    }

    private TaskContext anyTaskContext() {
        return org.mockito.ArgumentMatchers.any();
    }

    private TaskContext dummyContext() {
        VesselTask task = VesselTask.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .build();
        VesselProfile profile = mock(VesselProfile.class);
        meta.claw.core.config.RuntimeConfig runtimeConfig = new meta.claw.core.config.RuntimeConfig();
        runtimeConfig.setProviderConfig(new ProviderConfig());
        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .runtimeConfig(runtimeConfig)
                .build();
        when(profile.getBundle()).thenReturn(bundle);
        return new TaskContext(task, profile, null);
    }
}
