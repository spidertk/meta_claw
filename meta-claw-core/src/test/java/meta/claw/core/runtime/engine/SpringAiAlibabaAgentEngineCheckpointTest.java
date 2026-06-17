package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAlibabaAgentEngineCheckpointTest {

    @Test
    void executePassesRunnableConfigWithSessionIdAndVesselId() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(any())).thenReturn(agent);
        doReturn(new AssistantMessage("hello")).when(agent).call(anyList(), any(RunnableConfig.class));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext("v1", "s1");
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        engine.execute(ctx, request);

        org.mockito.ArgumentCaptor<RunnableConfig> configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
        verify(agent).call(anyList(), configCaptor.capture());
        RunnableConfig config = configCaptor.getValue();
        assertTrue(config.threadId().isPresent());
        assertEquals("s1", config.threadId().get());
        assertEquals("v1", config.metadata("vesselId").orElse("missing"));
        assertEquals(100, config.metadata("maxCheckpointsPerThread").orElse(-1));
    }

    @Test
    void executeFallsBackToTaskIdWhenSessionIdMissing() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(any())).thenReturn(agent);
        doReturn(new AssistantMessage("hello")).when(agent).call(anyList(), any(RunnableConfig.class));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext("v1", null);
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        engine.execute(ctx, request);

        org.mockito.ArgumentCaptor<RunnableConfig> configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
        verify(agent).call(anyList(), configCaptor.capture());
        RunnableConfig config = configCaptor.getValue();
        assertTrue(config.threadId().isPresent());
        assertEquals("t1", config.threadId().get());
    }

    @Test
    void executeStreamPassesRunnableConfig() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(any())).thenReturn(agent);
        when(agent.streamMessages(anyList(), any(RunnableConfig.class))).thenReturn(Flux.empty());
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext("v1", "s2");
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s2")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        engine.executeStream(ctx, request, mock(meta.claw.core.llm.SpiStreamingCallback.class));

        org.mockito.ArgumentCaptor<RunnableConfig> configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
        verify(agent).streamMessages(anyList(), configCaptor.capture());
        RunnableConfig config = configCaptor.getValue();
        assertEquals("s2", config.threadId().orElse("missing"));
        assertEquals("v1", config.metadata("vesselId").orElse("missing"));
    }

    @Test
    void resumeWithoutCheckpointResumeDoesNotSetResumeFlag() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(any())).thenReturn(agent);
        doReturn(new AssistantMessage("final")).when(agent).call(anyList(), any(RunnableConfig.class));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext("v1", "s1");
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        engine.resume(ctx, request, mock(meta.claw.core.runtime.hitl.ApprovalTicket.class),
                mock(meta.claw.core.runtime.hitl.ApprovalResolution.class));

        org.mockito.ArgumentCaptor<RunnableConfig> configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
        verify(agent).call(anyList(), configCaptor.capture());
        RunnableConfig config = configCaptor.getValue();
        assertEquals("s1", config.threadId().orElse("missing"));
        assertFalse(config.metadata("checkpointResume").isPresent());
    }

    @Test
    void resumeWithCheckpointResumeSetsResumeFlag() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(any())).thenReturn(agent);
        doReturn(new AssistantMessage("final")).when(agent).call(anyList(), any(RunnableConfig.class));
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContextWithCheckpointResume("v1", "s1");
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        engine.resume(ctx, request, mock(meta.claw.core.runtime.hitl.ApprovalTicket.class),
                mock(meta.claw.core.runtime.hitl.ApprovalResolution.class));

        org.mockito.ArgumentCaptor<RunnableConfig> configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
        verify(agent).call(anyList(), configCaptor.capture());
        RunnableConfig config = configCaptor.getValue();
        assertEquals("s1", config.threadId().orElse("missing"));
    }

    private TaskContext dummyContext(String vesselId, String sessionId) {
        return dummyContext(vesselId, sessionId, false);
    }

    private TaskContext dummyContextWithCheckpointResume(String vesselId, String sessionId) {
        return dummyContext(vesselId, sessionId, true);
    }

    private TaskContext dummyContext(String vesselId, String sessionId, boolean checkpointResume) {
        VesselTask task = VesselTask.builder()
                .taskId("t1")
                .vesselId(vesselId)
                .sessionId(sessionId)
                .userMessage("hi")
                .build();
        VesselProfile profile = mock(VesselProfile.class);
        meta.claw.core.config.RuntimeConfig runtimeConfig = new meta.claw.core.config.RuntimeConfig();
        runtimeConfig.setProviderConfig(new ProviderConfig());

        VesselConfig config = new VesselConfig();
        config.setAgentEngine("alibaba");
        VesselConfig.Identity identity = new VesselConfig.Identity();
        identity.setId(vesselId);
        config.setIdentity(identity);
        if (checkpointResume) {
            VesselConfig.AlibabaAgentConfig alibabaConfig = new VesselConfig.AlibabaAgentConfig();
            alibabaConfig.setCheckpointResume(true);
            config.setAlibabaAgent(alibabaConfig);
        }
        runtimeConfig.setVesselConfig(config);

        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .vesselConfig(config)
                .runtimeConfig(runtimeConfig)
                .build();
        when(profile.getBundle()).thenReturn(bundle);
        return new TaskContext(task, profile, new meta.claw.core.runtime.subsystem.SubSystemRegistry());
    }
}
