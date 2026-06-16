package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import meta.claw.core.config.AgentFlowConfig;
import meta.claw.core.config.AgentFlowMode;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselAgentConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaaMultiAgentFactoryTest {

    @Test
    void buildsSequentialAgentByDefault() {
        SaaMultiAgentFactory factory = new SaaMultiAgentFactory();
        ReactAgentFactory reactAgentFactory = mock(ReactAgentFactory.class);
        ReactAgent subAgent1 = mock(ReactAgent.class);
        ReactAgent subAgent2 = mock(ReactAgent.class);
        when(reactAgentFactory.buildSubAgent(any(TaskContext.class), any(VesselAgentConfig.class)))
                .thenReturn(subAgent1, subAgent2);
        ReflectionTestUtils.setField(factory, "reactAgentFactory", reactAgentFactory);

        VesselAgentConfig agent1 = new VesselAgentConfig();
        agent1.setName("planner");
        VesselAgentConfig agent2 = new VesselAgentConfig();
        agent2.setName("coder");

        TaskContext ctx = dummyContext(List.of(agent1, agent2), AgentFlowMode.SEQUENTIAL, null);

        Agent agent = factory.get(ctx);

        assertInstanceOf(SequentialAgent.class, agent);
        verify(reactAgentFactory, times(2)).buildSubAgent(eq(ctx), any(VesselAgentConfig.class));
    }

    @Test
    void buildsParallelAgent() {
        SaaMultiAgentFactory factory = new SaaMultiAgentFactory();
        ReactAgentFactory reactAgentFactory = mock(ReactAgentFactory.class);
        ReactAgent subAgent1 = mock(ReactAgent.class);
        ReactAgent subAgent2 = mock(ReactAgent.class);
        when(reactAgentFactory.buildSubAgent(any(TaskContext.class), any(VesselAgentConfig.class)))
                .thenReturn(subAgent1, subAgent2);
        ReflectionTestUtils.setField(factory, "reactAgentFactory", reactAgentFactory);

        VesselAgentConfig agent1 = new VesselAgentConfig();
        agent1.setName("researcher");
        VesselAgentConfig agent2 = new VesselAgentConfig();
        agent2.setName("writer");

        TaskContext ctx = dummyContext(List.of(agent1, agent2), AgentFlowMode.PARALLEL, null);

        Agent agent = factory.get(ctx);

        assertInstanceOf(ParallelAgent.class, agent);
    }

    @Test
    void buildsRoutingAgent() {
        SaaMultiAgentFactory factory = new SaaMultiAgentFactory();
        ReactAgentFactory reactAgentFactory = mock(ReactAgentFactory.class);
        LlmClientProviderManager providerManager = mock(LlmClientProviderManager.class);
        ChatModel routingModel = mock(ChatModel.class);
        when(providerManager.createChatModel(any(ProviderConfig.class))).thenReturn(routingModel);

        ReactAgent subAgent1 = mock(ReactAgent.class);
        when(reactAgentFactory.buildSubAgent(any(TaskContext.class), any(VesselAgentConfig.class)))
                .thenReturn(subAgent1);
        ReflectionTestUtils.setField(factory, "reactAgentFactory", reactAgentFactory);
        ReflectionTestUtils.setField(factory, "llmClientProviderManager", providerManager);

        VesselAgentConfig agent1 = new VesselAgentConfig();
        agent1.setName("handler");

        AgentFlowConfig flow = new AgentFlowConfig();
        flow.setMode("routing");
        flow.setRoutingPrompt("Route to the most appropriate agent.");
        flow.setInstruction("Think step by step.");
        flow.setFallbackAgent("handler");

        TaskContext ctx = dummyContext(List.of(agent1), AgentFlowMode.ROUTING, flow);

        Agent agent = factory.get(ctx);

        assertInstanceOf(LlmRoutingAgent.class, agent);
        LlmRoutingAgent routingAgent = (LlmRoutingAgent) agent;
        verify(providerManager).createChatModel(any(ProviderConfig.class));
    }

    @Test
    void throwsWhenNoSubAgentsConfigured() {
        SaaMultiAgentFactory factory = new SaaMultiAgentFactory();
        TaskContext ctx = dummyContext(List.of(), AgentFlowMode.SEQUENTIAL, null);

        assertThrows(IllegalStateException.class, () -> factory.get(ctx));
    }

    private TaskContext dummyContext(List<VesselAgentConfig> agents, AgentFlowMode mode, AgentFlowConfig flowOverride) {
        VesselTask task = VesselTask.builder()
                .taskId("t1")
                .vesselId("multi-agent-vessel")
                .sessionId("s1")
                .userMessage("hi")
                .build();

        VesselConfig vesselConfig = new VesselConfig();
        vesselConfig.setAgentEngine("alibaba");
        vesselConfig.setAgents(agents);
        AgentFlowConfig flow = flowOverride != null ? flowOverride : new AgentFlowConfig();
        if (mode != null) {
            flow.setMode(mode.name().toLowerCase());
        }
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

        return new TaskContext(task, profile, null);
    }
}
