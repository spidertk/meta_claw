package meta.claw.tool.registry;

import meta.claw.core.tool.SpiToolDefinition;
import meta.claw.core.tool.annotation.Tool;
import meta.claw.core.tool.annotation.ToolParam;
import meta.claw.core.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    static class TestTool {
        @Tool(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", description = "Name") String name) {
            return "Hello " + name;
        }

        @Tool(name = "add", description = "Add two numbers")
        public int add(@ToolParam(name = "a", description = "A") int a, @ToolParam(name = "b", description = "B") int b) {
            return a + b;
        }
    }

    static class DuplicateTool {
        @Tool(name = "greet", description = "Another greet")
        public String greet(@ToolParam(name = "name", description = "Name") String name) {
            return "Hi " + name;
        }
    }

    @Test
    void register_shouldScanAnnotatedMethods() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(new TestTool());

        List<SpiToolDefinition> defs = registry.getToolDefinitions();
        assertEquals(2, defs.size());
        assertTrue(registry.hasTool("greet"));
        assertTrue(registry.hasTool("add"));
    }

    @Test
    void register_shouldSkipDuplicateNames() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(new TestTool());
        registry.register(new DuplicateTool());

        List<SpiToolDefinition> defs = registry.getToolDefinitions();
        assertEquals(2, defs.size());

        ToolRegistry.ToolMethod method = registry.findMethod("greet");
        assertNotNull(method);
        assertEquals(TestTool.class, method.target().getClass());
    }

    @Test
    void findMethod_shouldReturnNullForUnknownTool() {
        ToolRegistry registry = new ToolRegistry(null);
        assertNull(registry.findMethod("unknown"));
    }

    @Test
    void register_shouldIgnoreNull() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(null);
        assertTrue(registry.getToolDefinitions().isEmpty());
    }

    @Test
    void getToolDefinitions_shouldBeUnmodifiable() {
        ToolRegistry registry = new ToolRegistry(null);
        registry.register(new TestTool());

        List<SpiToolDefinition> defs = registry.getToolDefinitions();
        assertThrows(UnsupportedOperationException.class, defs::clear);
    }
}
