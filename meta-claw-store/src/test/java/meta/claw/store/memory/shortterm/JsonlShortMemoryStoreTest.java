package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.memory.MemoryEntryConverter;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonlShortMemoryStoreTest {
    @TempDir Path tempDir;

    private JsonlShortMemoryStore createStore() {
        return new JsonlShortMemoryStore(tempDir, "vessel-a");
    }

    @Test
    void initializeConversation_shouldCreateEmptyHistoryFile() {
        JsonlShortMemoryStore store = createStore();
        store.initializeConversation("s0");
        assertTrue(tempDir.resolve("vessel-a/conversations/s0/history.jsonl").toFile().exists());
        assertTrue(store.getHistory("s0").isEmpty());
    }

    @Test
    void appendMessage_andGetHistory_roundTrip() {
        JsonlShortMemoryStore store = createStore();
        store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("Hello")));
        store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.assistant("Hi")));
        List<MemoryEntry> history = store.getHistory("s1");
        assertEquals(2, history.size());
        assertEquals("Hello", history.get(0).getContent());
        assertEquals("Hi", history.get(1).getContent());
    }

    @Test
    void appendMessage_shouldPersistMemoryEntryImmediatelyWithReadableTimestamp() throws IOException {
        JsonlShortMemoryStore store = createStore();
        store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("Hello")));

        String persisted = Files.readString(tempDir.resolve("vessel-a/conversations/s1/history.jsonl"));
        assertTrue(persisted.contains("\"category\":\"message\""));
        assertTrue(persisted.contains("\"content\":\"Hello\""));
        assertTrue(persisted.contains("\"sessionId\":\"s1\""));
        assertTrue(persisted.contains("\"role\":\"user\""));
        assertTrue(persisted.matches("(?s).*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\".*"));
    }

    @Test
    void getHistory_withLimit() {
        JsonlShortMemoryStore store = createStore();
        for (int i = 0; i < 5; i++) store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("M" + i)));
        List<MemoryEntry> history = store.getHistory("s1", 2);
        assertEquals(2, history.size());
        assertEquals("M3", history.get(0).getContent());
    }

    @Test
    void listSessions_shouldStayVesselScoped() {
        JsonlShortMemoryStore a = createStore();
        JsonlShortMemoryStore b = new JsonlShortMemoryStore(tempDir, "vessel-b");
        a.appendEntry("a1", MemoryEntryConverter.fromSpiMessage("a1", SpiMessage.user("A")));
        b.appendEntry("b1", MemoryEntryConverter.fromSpiMessage("b1", SpiMessage.user("B")));
        List<MemoryEntry> sessions = a.listSessions("vessel-a");
        assertEquals(1, sessions.size());
        assertEquals("a1", sessions.get(0).getSessionId());
    }

    @Test
    void clearHistory_shouldTruncate() {
        JsonlShortMemoryStore store = createStore();
        store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("Hello")));
        assertTrue(store.clearHistory("s1"));
        assertTrue(store.getHistory("s1").isEmpty());
    }

    @Test
    void appendMessage_shouldStripBase64() {
        JsonlShortMemoryStore store = createStore();
        store.appendEntry("s1", MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("See data:image/png;base64," + "A".repeat(300))));
        assertTrue(store.getHistory("s1").get(0).getContent().contains("[media:image/png:base64:<stripped>]"));
    }

    @Test
    void getHistory_shouldKeepSystemAndRecentRounds() {
        JsonlShortMemoryStore store = createStore();
        List<MemoryEntry> result = store.getHistory(List.of(
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.system("system")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("u1")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.assistant("a1")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("u2")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.assistant("a2"))
        ), 1);
        assertEquals(3, result.size());
        assertEquals("system", result.get(0).getContent());
        assertEquals("u2", result.get(1).getContent());
        assertEquals("a2", result.get(2).getContent());
    }

    @Test
    void getHistoryByToken_shouldKeepSystemAndTail() {
        JsonlShortMemoryStore store = createStore();
        List<MemoryEntry> result = store.getHistoryByToken(List.of(
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.system("system")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("12345678")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.assistant("abcd"))
        ), 3);
        assertEquals(2, result.size());
        assertEquals("system", result.get(0).getContent());
        assertEquals("abcd", result.get(1).getContent());
    }
}
