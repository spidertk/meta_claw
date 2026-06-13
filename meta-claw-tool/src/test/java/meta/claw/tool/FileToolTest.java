package meta.claw.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolTest {

    private final FileTool tool = new FileTool();

    @Test
    void readWriteAndList(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(tool, "configuredBasePath", tempDir.toString());

        String writeResult = tool.writeFile("subdir/hello.txt", "line1\nline2\nline3");
        assertTrue(writeResult.startsWith("OK:"), "expected OK but got: " + writeResult);

        String content = tool.readFile("subdir/hello.txt", null);
        assertEquals("line1\nline2\nline3", content);

        String list = tool.listFiles("subdir");
        assertTrue(list.contains("[F] hello.txt"), "expected file listing but got: " + list);

        assertEquals("true", tool.fileExists("subdir/hello.txt"));
        assertEquals("false", tool.fileExists("subdir/missing.txt"));
    }

    @Test
    void maxLinesLimitsOutput(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(tool, "configuredBasePath", tempDir.toString());
        tool.writeFile("multi.txt", "a\nb\nc\nd");

        String content = tool.readFile("multi.txt", 2);
        assertEquals("a\nb", content);
    }

    @Test
    void rejectsPathEscape(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(tool, "configuredBasePath", tempDir.toString());
        String result = tool.readFile("../outside.txt", null);
        assertTrue(result.startsWith("Error"), "expected error for escaped path but got: " + result);
    }

    @Test
    void disabledToolReturnsError() {
        FileTool disabled = new FileTool();
        ReflectionTestUtils.setField(disabled, "enabled", false);
        assertTrue(disabled.readFile("x.txt", null).startsWith("Error: File tool is disabled"));
    }
}
