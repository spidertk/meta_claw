package meta.claw.tool;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellToolTest {

    private final ShellTool tool = new ShellTool();

    @Test
    void echoCommandReturnsOutput() {
        String json = tool.execute("echo hello", 5);
        assertTrue(json.contains("\"exitCode\":0"), "expected success exit code but got: " + json);
        assertTrue(json.contains("hello"), "expected stdout to contain 'hello' but got: " + json);
    }

    @Test
    void failingCommandReturnsNonZero() {
        String json = tool.execute("exit 1", 5);
        assertTrue(json.contains("\"exitCode\":1"), "expected non-zero exit code but got: " + json);
    }

    @Test
    void emptyCommandReturnsError() {
        String json = tool.execute("   ", 5);
        assertTrue(json.contains("Error: empty command"), "expected empty command error but got: " + json);
    }

    @Test
    void disabledToolReturnsError() {
        ShellTool disabled = new ShellTool();
        ReflectionTestUtils.setField(disabled, "enabled", false);
        String json = disabled.execute("echo hello", 5);
        assertTrue(json.contains("disabled"), "expected disabled error but got: " + json);
    }
}
