package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import meta.claw.core.runtime.hitl.ApprovalItem;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalStatus;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAlibabaAgentEngineTest {

    @Test
    void nameIsAlibaba() {
        assertEquals("alibaba", new SpringAiAlibabaAgentEngine().name());
    }

    @Test
    void executeDelegatesToReactAgent() throws GraphRunnerException {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(anyTaskContext())).thenReturn(agent);
        doReturn(new AssistantMessage("hello from alibaba")).when(agent).call(anyList());
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        TaskContext ctx = dummyContext();
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .messages(List.of(meta.claw.core.llm.SpiMessage.user("hi")))
                .build();

        Reply reply = engine.execute(ctx, request);

        assertEquals("hello from alibaba", reply.getContent());
    }

    private TaskContext anyTaskContext() {
        return org.mockito.ArgumentMatchers.any();
    }

    @Test
    void resumeExecutesApprovedToolAndContinues() throws Exception {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        ReactAgentFactory factory = mock(ReactAgentFactory.class);
        ReactAgent agent = mock(ReactAgent.class);
        when(factory.get(anyTaskContext())).thenReturn(agent);
        doReturn(new AssistantMessage("final answer")).when(agent).call(anyList());
        ReflectionTestUtils.setField(engine, "reactAgentFactory", factory);

        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("dangerous");
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call(anyString())).thenReturn("done");

        ToolSubSystem toolSub = mock(ToolSubSystem.class);
        when(toolSub.name()).thenReturn("tool");
        when(toolSub.getToolCallbacks()).thenReturn(List.of(callback));

        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(toolSub);

        TaskContext ctx = dummyContext(registry);

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
                        meta.claw.core.llm.SpiMessage.user("do it"),
                        meta.claw.core.llm.SpiMessage.assistant(null, List.of(SpiToolCall.builder()
                                .id("call-1")
                                .name("dangerous")
                                .arguments(Map.of("x", 1))
                                .build()))
                ))
                .build();

        Reply reply = engine.resume(ctx, request, ticket, resolution);
        assertEquals("final answer", reply.getContent());
        verify(callback).call("{\"x\":1}");
    }

    private TaskContext dummyContext() {
        return dummyContext(null);
    }

    private TaskContext dummyContext(SubSystemRegistry registry) {
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
        return new TaskContext(task, profile, registry);
    }
}
