package meta.claw.tool.registry;

import meta.claw.core.tool.annotation.ToolService;
import meta.claw.core.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @ToolService
    static class TestTool {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    @ToolService
    static class AnotherTool {
        public int add(int a, int b) {
            return a + b;
        }
    }

    @Test
    void register_shouldCollectInstances() {
        ToolRegistry registry = new ToolRegistry(null);
        TestTool tool1 = new TestTool();
        AnotherTool tool2 = new AnotherTool();

        registry.register(tool1);
        registry.register(tool2);

        assertEquals(2, registry.toolCount());
        assertTrue(registry.getToolInstances().contains(tool1));
        assertTrue(registry.getToolInstances().contains(tool2));
    }

    @Test
    void register_shouldIgnoreNull() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(null);
        assertEquals(0, registry.toolCount());
    }

    @Test
    void unregister_shouldRemoveInstance() {
        ToolRegistry registry = new ToolRegistry(null);
        TestTool tool = new TestTool();
        registry.register(tool);

        assertTrue(registry.unregister(tool));
        assertEquals(0, registry.toolCount());
    }

    @Test
    void unregister_shouldReturnFalseForUnknown() {
        ToolRegistry registry = new ToolRegistry(null);
        assertFalse(registry.unregister(new TestTool()));
    }

    @Test
    void getToolInstances_shouldBeUnmodifiable() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(new TestTool());

        var instances = registry.getToolInstances();
        assertThrows(UnsupportedOperationException.class, instances::clear);
    }
}
