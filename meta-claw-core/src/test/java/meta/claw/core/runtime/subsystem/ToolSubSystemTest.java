package meta.claw.core.runtime.subsystem;

import meta.claw.core.prompt.PromptVars;
import meta.claw.core.tool.annotation.ToolService;
import meta.claw.core.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolSubSystemTest {

    @Test
    void aggregatesLocalAndMcpTools() {
        ToolRegistry registry = new ToolRegistry(mock(org.springframework.context.ApplicationContext.class));
        registry.register(new LocalTool());

        ToolCallbackProvider mcpProvider = mock(ToolCallbackProvider.class);
        ToolCallback mcpCallback = mock(ToolCallback.class);
        ToolDefinition mcpDef = mock(ToolDefinition.class);
        when(mcpDef.name()).thenReturn("mcp-search");
        when(mcpDef.description()).thenReturn("MCP search tool");
        when(mcpCallback.getToolDefinition()).thenReturn(mcpDef);
        when(mcpProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{mcpCallback});

        ToolSubSystem toolSub = new ToolSubSystem();
        org.springframework.test.util.ReflectionTestUtils.setField(toolSub, "toolRegistry", registry);
        org.springframework.test.util.ReflectionTestUtils.setField(toolSub, "mcpToolProviders", List.of(mcpProvider));

        List<ToolCallback> callbacks = toolSub.getToolCallbacks();
        assertEquals(2, callbacks.size());
        assertTrue(callbacks.stream().anyMatch(tc -> tc.getToolDefinition().name().equals("local-calc")));
        assertTrue(callbacks.stream().anyMatch(tc -> tc.getToolDefinition().name().equals("mcp-search")));

        PromptVars vars = toolSub.promptVars();
        String text = vars.toMap().get("tools");
        assertNotNull(text);
        assertTrue(text.contains("local-calc"));
        assertTrue(text.contains("mcp-search"));
    }

    @Test
    void returnsEmptyWhenNoTools() {
        ToolRegistry registry = new ToolRegistry(mock(org.springframework.context.ApplicationContext.class));
        ToolSubSystem toolSub = new ToolSubSystem();
        org.springframework.test.util.ReflectionTestUtils.setField(toolSub, "toolRegistry", registry);

        assertTrue(toolSub.getToolCallbacks().isEmpty());
        assertTrue(toolSub.promptVars().toMap().isEmpty());
    }

    @ToolService
    static class LocalTool {
        @Tool(name = "local-calc", description = "local calc")
        public int calc(int a, int b) { return a + b; }
    }
}
