package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaClawModelMetricsHookTest {

    @Test
    void afterModelRecordsLatencyAndToolCalls() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        TaskContext ctx = dummyContext();
        MetaClawModelMetricsHook hook = new MetaClawModelMetricsHook(ctx, recorder);

        hook.beforeModel(new OverAllState(), RunnableConfig.builder().build());

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_1", "function", "calculator", "{\"a\":1}");
        AssistantMessage assistant = AssistantMessage.builder()
                .content("result")
                .toolCalls(List.of(toolCall))
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("hi"));
        messages.add(assistant);
        OverAllState state = new OverAllState(Map.of("messages", messages));

        hook.afterModel(state, RunnableConfig.builder().build());

        assertEquals(1, registry.timer("agent.llm.latency", "vessel", "v1").count());
        assertEquals(1.0, registry.counter("agent.tool.calls", "vessel", "v1", "tool", "calculator").count());
        assertEquals(1, ctx.getToolCallCount());
    }

    @Test
    void afterModelRecordsTokenUsageFromMetadata() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        TaskContext ctx = dummyContext();
        MetaClawModelMetricsHook hook = new MetaClawModelMetricsHook(ctx, recorder);

        hook.beforeModel(new OverAllState(), RunnableConfig.builder().build());

        DefaultUsage usage = new DefaultUsage(10, 5, 15);
        AssistantMessage assistant = AssistantMessage.builder()
                .content("result")
                .properties(Map.of("usage", usage))
                .build();
        OverAllState state = new OverAllState(Map.of("messages", List.of(assistant)));

        hook.afterModel(state, RunnableConfig.builder().build());

        assertEquals(10.0, registry.counter("agent.llm.tokens.prompt", "vessel", "v1").count());
        assertEquals(5.0, registry.counter("agent.llm.tokens.completion", "vessel", "v1").count());
        assertEquals(15.0, registry.counter("agent.llm.tokens.total", "vessel", "v1").count());
    }

    @Test
    void afterModelHandlesEmptyState() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        MetaClawModelMetricsHook hook = new MetaClawModelMetricsHook(dummyContext(), recorder);
        hook.beforeModel(new OverAllState(), RunnableConfig.builder().build());

        hook.afterModel(new OverAllState(), RunnableConfig.builder().build());

        assertEquals(1, registry.timer("agent.llm.latency", "vessel", "v1").count());
    }

    @Test
    void getNameReturnsExpectedValue() {
        MetaClawModelMetricsHook hook = new MetaClawModelMetricsHook(dummyContext(), null);
        assertEquals("meta-claw-model-metrics-hook", hook.getName());
    }

    private TaskContext dummyContext() {
        VesselProfile profile = mock(VesselProfile.class);
        meta.claw.core.config.RuntimeConfig runtimeConfig = new meta.claw.core.config.RuntimeConfig();
        runtimeConfig.setProviderConfig(new ProviderConfig());
        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .runtimeConfig(runtimeConfig)
                .build();
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
