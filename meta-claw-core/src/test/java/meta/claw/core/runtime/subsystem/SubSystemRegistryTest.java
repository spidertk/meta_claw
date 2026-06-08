package meta.claw.core.runtime.subsystem;

import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubSystemRegistryTest {

    static class FakeSubSystem implements VesselSubSystem {
        @Override
        public String name() { return "fake"; }
        @Override
        public void configure(SubSystemRegistry registry) {}
    }

    static class HighPrioritySubSystem implements VesselSubSystem {
        @Override
        public String name() { return "high"; }
        @Override
        public void configure(SubSystemRegistry registry) {}
        @Override
        public int priority() { return 1; }
    }

    static class LowPrioritySubSystem implements VesselSubSystem {
        @Override
        public String name() { return "low"; }
        @Override
        public void configure(SubSystemRegistry registry) {}
        @Override
        public int priority() { return 99; }
    }

    @Test
    void registerAndGet() {
        SubSystemRegistry registry = new SubSystemRegistry();
        FakeSubSystem sub = new FakeSubSystem();
        registry.register(sub);
        assertSame(sub, registry.get("fake"));
        assertTrue(registry.has("fake"));
        assertFalse(registry.has("missing"));
    }

    @Test
    void listAll_sortedByPriority() {
        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(new LowPrioritySubSystem());
        registry.register(new HighPrioritySubSystem());
        var list = registry.listAll();
        assertEquals("high", list.get(0).name());
        assertEquals("low", list.get(1).name());
    }

    @Test
    void register_nullName_throws() {
        SubSystemRegistry registry = new SubSystemRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }
}
