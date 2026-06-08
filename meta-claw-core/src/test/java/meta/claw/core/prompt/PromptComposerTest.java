package meta.claw.core.prompt;

import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptComposerTest {

    static class LowSub implements VesselSubSystem {
        @Override public String name() { return "low"; }
        @Override public void configure(SubSystemRegistry r) {}
        @Override public PromptVars promptVars() { return PromptVars.of("a", "1"); }
        @Override public int priority() { return 20; }
    }

    static class HighSub implements VesselSubSystem {
        @Override public String name() { return "high"; }
        @Override public void configure(SubSystemRegistry r) {}
        @Override public PromptVars promptVars() { return PromptVars.of("b", "2"); }
        @Override public int priority() { return 10; }
    }

    @Test
    void compose_mergesAllSubsystems() {
        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(new LowSub());
        registry.register(new HighSub());

        PromptComposer composer = new PromptComposer();
        PromptVars vars = composer.compose(registry);

        assertEquals("1", vars.get("a"));
        assertEquals("2", vars.get("b"));
    }

    @Test
    void compose_emptyRegistryReturnsEmpty() {
        PromptComposer composer = new PromptComposer();
        PromptVars vars = composer.compose(new SubSystemRegistry());
        assertTrue(vars.isEmpty());
    }
}
