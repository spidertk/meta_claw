package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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

    @Test
    void executeStreamThrowsUnsupported() {
        SpringAiAlibabaAgentEngine engine = new SpringAiAlibabaAgentEngine();
        TaskContext ctx = dummyContext();
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();

        assertThrows(UnsupportedOperationException.class,
                () -> engine.executeStream(ctx, request, null));
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
