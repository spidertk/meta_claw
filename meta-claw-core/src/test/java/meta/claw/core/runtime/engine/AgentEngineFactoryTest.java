package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentEngineFactoryTest {

    @Test
    void collectsEnginesByName() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine nativeEngine = new NamedEngine("native");
        AgentEngine alibabaEngine = new NamedEngine("alibaba");
        factory.setEngines(List.of(nativeEngine, alibabaEngine));

        assertSame(nativeEngine, factory.getEngine("native"));
        assertSame(alibabaEngine, factory.getEngine("alibaba"));
    }

    @Test
    void defaultEngineIsNative() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine nativeEngine = new NamedEngine("native");
        factory.setEngines(List.of(nativeEngine));

        assertSame(nativeEngine, factory.getDefaultEngine());
    }

    @Test
    void throwsOnDuplicateName() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine a = new NamedEngine("native");
        AgentEngine b = new NamedEngine("native");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.setEngines(List.of(a, b)));
        assertTrue(ex.getMessage().contains("Duplicate AgentEngine name"));
    }

    @Test
    void throwsOnUnknownEngine() {
        AgentEngineFactory factory = new AgentEngineFactory();
        factory.setEngines(List.of(new NamedEngine("native")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.getEngine("missing"));
        assertTrue(ex.getMessage().contains("No AgentEngine for name"));
    }

    private static class NamedEngine implements AgentEngine {
        private final String name;

        NamedEngine(String name) {
            this.name = name;
        }

        @Override
        public Reply execute(TaskContext ctx, SpiChatRequest request) {
            return null;
        }

        @Override
        public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
            return null;
        }

        @Override
        public Reply resume(TaskContext ctx, SpiChatRequest request,
                            ApprovalTicket ticket, ApprovalResolution resolution) {
            return null;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
