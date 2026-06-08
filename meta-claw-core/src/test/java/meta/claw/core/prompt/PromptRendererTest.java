package meta.claw.core.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptRendererTest {

    PromptRenderer renderer = new PromptRenderer();

    @Test
    void render_emptyVars_returnsEmpty() {
        assertEquals("", renderer.renderSystem(Map.of()));
    }

    @Test
    void render_basicVars() {
        Map<String, String> vars = Map.of(
                "vessel_name", "TestBot",
                "vessel_description", "A test vessel",
                "identity", "Test identity",
                "current_time", "2026-06-06 12:00:00 CST",
                "location", "Asia/Shanghai"
        );
        String result = renderer.renderSystem(vars);
        System.out.println("=== RENDER OUTPUT ===");
        System.out.println(result);
        System.out.println("=== END OUTPUT ===");
        assertTrue(result.contains("# TestBot"));
        assertTrue(result.contains("A test vessel"));
        assertTrue(result.contains("## Identity"));
        assertTrue(result.contains("Test identity"));
    }

    @Test
    void render_skipsEmptySections() {
        Map<String, String> vars = Map.of(
                "vessel_name", "MinimalBot",
                "identity", ""
        );
        String result = renderer.renderSystem(vars);
        assertTrue(result.contains("# MinimalBot"));
        assertFalse(result.contains("## Identity"));
    }
}
