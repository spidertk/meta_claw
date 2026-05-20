package meta.claw.tool.executor;

import meta.claw.core.spi.llm.SpiToolCall;
import meta.claw.core.spi.llm.SpiToolResult;
import meta.claw.tool.annotation.Tool;
import meta.claw.tool.annotation.ToolParam;
import meta.claw.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutorTest {

    static class DemoTool {
        @Tool(name = "echo", description = "Echo")
        public String echo(@ToolParam(name = "msg", description = "Msg") String msg) {
            return "echo:" + msg;
        }

        @Tool(name = "add", description = "Add")
        public int add(@ToolParam(name = "a", description = "A") int a, @ToolParam(name = "b", description = "B") int b) {
            return a + b;
        }

        @Tool(name = "fail", description = "Always fails")
        public String fail() {
            throw new RuntimeException("boom");
        }
    }

    private final ToolExecutor executor = new ToolExecutor();
    private final ToolRegistry registry = new ToolRegistry(null);

    ToolExecutorTest() {
        registry.register(new DemoTool());
    }

    @Test
    void execute_shouldCallMethodAndReturnResult() {
        SpiToolCall call = SpiToolCall.builder()
                .id("call-1")
                .name("echo")
                .arguments(Map.of("msg", "hello"))
                .build();
        ToolRegistry.ToolMethod method = registry.findMethod("echo");

        SpiToolResult result = executor.execute(call, method);

        assertTrue(result.success());
        assertEquals("call-1", result.toolCallId());
        assertEquals("echo:hello", result.content());
    }

    @Test
    void execute_shouldConvertArgumentTypes() {
        SpiToolCall call = SpiToolCall.builder()
                .id("call-2")
                .name("add")
                .arguments(Map.of("a", "3", "b", "4"))
                .build();
        ToolRegistry.ToolMethod method = registry.findMethod("add");

        SpiToolResult result = executor.execute(call, method);

        assertTrue(result.success());
        assertEquals("7", result.content());
    }

    @Test
    void execute_shouldIsolateException() {
        SpiToolCall call = SpiToolCall.builder()
                .id("call-3")
                .name("fail")
                .arguments(Map.of())
                .build();
        ToolRegistry.ToolMethod method = registry.findMethod("fail");

        SpiToolResult result = executor.execute(call, method);

        assertFalse(result.success());
        assertEquals("call-3", result.toolCallId());
        assertNotNull(result.errorMessage());
        assertNotNull(result.errorMessage());
    }

    @Test
    void execute_shouldHandleNullMethod() {
        SpiToolCall call = SpiToolCall.builder()
                .id("call-4")
                .name("x")
                .build();

        SpiToolResult result = executor.execute(call, null);

        assertFalse(result.success());
        assertEquals("call-4", result.toolCallId());
        assertEquals("Missing tool call or method", result.errorMessage());
    }

    @Test
    void execute_shouldHandleNullCall() {
        SpiToolResult result = executor.execute(null, registry.findMethod("echo"));

        assertFalse(result.success());
        assertNull(result.toolCallId());
        assertEquals("Missing tool call or method", result.errorMessage());
    }
}
