package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonlShortMemoryStoreTest {
    @TempDir Path tempDir;

    private JsonlShortMemoryStore createStore() {
        return new JsonlShortMemoryStore(tempDir, "vessel-a");
    }

    @Test
    void appendMessage_andGetHistory_roundTrip() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", SpiMessage.user("Hello"));
        store.appendMessage("s1", SpiMessage.assistant("Hi"));
        List<SpiMessage> history = store.getHistory("s1");
        assertEquals(2, history.size());
        assertEquals("Hello", history.get(0).content());
        assertEquals("Hi", history.get(1).content());
    }

    @Test
    void getHistory_withLimit() {
        JsonlShortMemoryStore store = createStore();
        for (int i = 0; i < 5; i++) store.appendMessage("s1", SpiMessage.user("M" + i));
        List<SpiMessage> history = store.getHistory("s1", 2);
        assertEquals(2, history.size());
        assertEquals("M3", history.get(0).content());
    }

    @Test
    void listSessions_shouldStayVesselScoped() {
        JsonlShortMemoryStore a = createStore();
        JsonlShortMemoryStore b = new JsonlShortMemoryStore(tempDir, "vessel-b");
        a.appendMessage("a1", SpiMessage.user("A"));
        b.appendMessage("b1", SpiMessage.user("B"));
        List<MemoryEntry> sessions = a.listSessions("vessel-a");
        assertEquals(1, sessions.size());
        assertEquals("a1", sessions.get(0).getSessionId());
    }

    @Test
    void clearHistory_shouldTruncate() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", SpiMessage.user("Hello"));
        assertTrue(store.clearHistory("s1"));
        assertTrue(store.getHistory("s1").isEmpty());
    }

    @Test
    void appendMessage_shouldStripBase64() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", SpiMessage.user("See data:image/png;base64," + "A".repeat(300)));
        assertTrue(store.getHistory("s1").get(0).content().contains("[media:image/png:base64:<stripped>]"));
    }

    @Test
    void truncateByRound_shouldKeepSystemAndRecentRounds() {
        JsonlShortMemoryStore store = createStore();
        List<SpiMessage> result = store.truncateByRound(List.of(
                SpiMessage.system("system"),
                SpiMessage.user("u1"),
                SpiMessage.assistant("a1"),
                SpiMessage.user("u2"),
                SpiMessage.assistant("a2")
        ), 1);
        assertEquals(3, result.size());
        assertEquals("system", result.get(0).content());
        assertEquals("u2", result.get(1).content());
        assertEquals("a2", result.get(2).content());
    }

    @Test
    void truncateByToken_shouldKeepSystemAndTail() {
        JsonlShortMemoryStore store = createStore();
        List<SpiMessage> result = store.truncateByToken(List.of(
                SpiMessage.system("system"),
                SpiMessage.user("12345678"),
                SpiMessage.assistant("abcd")
        ), 3);
        assertEquals(2, result.size());
        assertEquals("system", result.get(0).content());
        assertEquals("abcd", result.get(1).content());
    }
}
