package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import meta.claw.core.config.AgentFlowConfig;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselAgentConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAlibabaAgentEngineMultiAgentTest {

    @Test
    void executeUsesMultiAgentWhenSubAgentsConfigured() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        SaaMultiAgentFactory multiAgentFactory = mock(SaaMultiAgentFactory.class);
        Agent flowAgent = mock(Agent.class);
        when(multiAgentFactory.get(anyTaskContext())).thenReturn(flowAgent);
        ReflectionTestUtils.setField(engine, "multiAgentFactory", multiAgentFactory);

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", List.of(new AssistantMessage("multi agent result")));
        OverAllState state = new OverAllState(stateData);
        when(flowAgent.invoke(anyList(), any(RunnableConfig.class))).thenReturn(Optional.of(state));

        TaskContext ctx = dummyContextWithAgents();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        Reply reply = engine.execute(ctx, request);

        assertNotNull(reply);
        assertEquals(ReplyType.TEXT, reply.getType());
        assertEquals("multi agent result", reply.getContent());
    }

    @Test
    void executeStreamUsesMultiAgentWhenSubAgentsConfigured() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        SaaMultiAgentFactory multiAgentFactory = mock(SaaMultiAgentFactory.class);
        Agent flowAgent = mock(Agent.class);
        when(multiAgentFactory.get(anyTaskContext())).thenReturn(flowAgent);
        ReflectionTestUtils.setField(engine, "multiAgentFactory", multiAgentFactory);

        when(flowAgent.streamMessages(anyList(), any(RunnableConfig.class))).thenReturn(Flux.just(
                new AssistantMessage("multi "),
                new AssistantMessage("stream")
        ));

        TaskContext ctx = dummyContextWithAgents();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);

        Reply reply = engine.executeStream(ctx, request, callback);

        assertNotNull(reply);
        assertEquals(ReplyType.TEXT, reply.getType());
        assertEquals("multi stream", reply.getContent());
        verify(callback).onStart();
        verify(callback).onChunk("multi ");
        verify(callback).onChunk("stream");
        verify(callback).onComplete(org.mockito.ArgumentMatchers.any());
    }

    private TaskContext anyTaskContext() {
        return org.mockito.ArgumentMatchers.any();
    }

    private TaskContext dummyContextWithAgents() {
        
        VesselAgentConfig subAgent = new VesselAgentConfig();
        subAgent.setName("planner");

        VesselConfig vesselConfig = new VesselConfig();
        vesselConfig.setAgentEngine("alibaba");
        vesselConfig.setAgents(List.of(subAgent));
        AgentFlowConfig flow = new AgentFlowConfig();
        flow.setMode("sequential");
        vesselConfig.setFlow(flow);

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setProvider("openai");
        providerConfig.setModel("gpt-4o");

        RuntimeConfig runtimeConfig = new RuntimeConfig();
        runtimeConfig.setProviderConfig(providerConfig);
        runtimeConfig.setVesselConfig(vesselConfig);

        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .runtimeConfig(runtimeConfig)
                .vesselConfig(vesselConfig)
                .build();

        VesselProfile profile = mock(VesselProfile.class);
        when(profile.getBundle()).thenReturn(bundle);

        return TaskContext.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .profile(profile)
                .registry(null)
                .build();
    }
}
