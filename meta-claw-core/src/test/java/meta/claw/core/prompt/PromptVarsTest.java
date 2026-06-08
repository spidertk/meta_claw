package meta.claw.core.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptVarsTest {

    @Test
    void empty_returnsEmptyMap() {
        PromptVars vars = PromptVars.empty();
        assertTrue(vars.isEmpty());
        assertTrue(vars.toMap().isEmpty());
    }

    @Test
    void of_createsSingleEntry() {
        PromptVars vars = PromptVars.of("key", "value");
        assertEquals("value", vars.get("key"));
    }

    @Test
    void builder_createsMultipleEntries() {
        PromptVars vars = PromptVars.builder()
                .vars(Map.of("a", "1", "b", "2"))
                .build();
        assertEquals("1", vars.get("a"));
        assertEquals("2", vars.get("b"));
    }

    @Test
    void merge_combinesVars() {
        PromptVars a = PromptVars.of("x", "1");
        PromptVars b = PromptVars.of("y", "2");
        PromptVars merged = a.merge(b);
        assertEquals("1", merged.get("x"));
        assertEquals("2", merged.get("y"));
    }

    @Test
    void merge_laterWins() {
        PromptVars a = PromptVars.of("x", "1");
        PromptVars b = PromptVars.of("x", "2");
        PromptVars merged = a.merge(b);
        assertEquals("2", merged.get("x"));
    }

    @Test
    void merge_withNullReturnsThis() {
        PromptVars a = PromptVars.of("x", "1");
        assertSame(a, a.merge(null));
        assertSame(a, a.merge(PromptVars.empty()));
    }

    @Test
    void toMap_isImmutable() {
        PromptVars vars = PromptVars.of("x", "1");
        assertThrows(UnsupportedOperationException.class,
                () -> vars.toMap().put("y", "2"));
    }
}
