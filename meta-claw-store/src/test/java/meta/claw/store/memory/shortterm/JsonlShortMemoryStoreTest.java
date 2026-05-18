package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.SessionMemory;
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
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("Hi")));
        List<MemoryMessage> history = store.getHistory("s1");
        assertEquals(2, history.size());
        assertEquals("Hello", history.get(0).getContent());
        assertEquals("Hi", history.get(1).getContent());
    }

    @Test
    void appendMessage_shouldPersistImmediatelyWithReadableTimestamp() throws IOException {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));

        String persisted = Files.readString(tempDir.resolve("vessel-a/conversations/s1/history.jsonl"));
        assertTrue(persisted.contains("\"content\":\"Hello\""));
        assertTrue(persisted.contains("\"role\":\"user\""));
        assertTrue(persisted.matches("(?s).*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\".*"));
        assertFalse(persisted.contains("\"messageCount\""));
    }

    @Test
    void getHistory_withLimit() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u1")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a1")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u2")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a2")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u3")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a3")));

        List<MemoryMessage> history = store.getHistory("s1", 2);
        assertEquals(4, history.size());
        assertEquals("u2", history.get(0).getContent());
    }

    @Test
    void listSessions_shouldStayVesselScoped() {
        JsonlShortMemoryStore a = createStore();
        JsonlShortMemoryStore b = new JsonlShortMemoryStore(tempDir, "vessel-b");
        a.appendMessage("a1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("A")));
        b.appendMessage("b1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("B")));
        List<SessionMemory> sessions = a.listSessions("vessel-a");
        assertEquals(1, sessions.size());
        assertEquals("a1", sessions.get(0).getSessionId());
    }

    @Test
    void clearHistory_shouldTruncate() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));
        assertTrue(store.clearHistory("s1"));
        assertTrue(store.getHistory("s1").isEmpty());
    }

    @Test
    void appendMessage_shouldStripBase64() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("See data:image/png;base64," + "A".repeat(300))));
        assertTrue(store.getHistory("s1").get(0).getContent().contains("[media:image/png:base64:<stripped>]"));
    }

    @Test
    void getHistory_withLimit_shouldKeepRecentRounds() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u1")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a1")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u2")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a2")));

        List<MemoryMessage> result = store.getHistory("s1", 1);
        assertEquals(2, result.size());
        assertEquals("u2", result.get(0).getContent());
        assertEquals("a2", result.get(1).getContent());
    }

    @Test
    void getHistoryByToken_shouldKeepTail() {
        JsonlShortMemoryStore store = createStore();
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("12345678")));
        store.appendMessage("s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("abcd")));

        List<MemoryMessage> result = store.getHistoryByToken("s1", 3);
        assertEquals(1, result.size());
        assertEquals("abcd", result.get(0).getContent());
    }

    @Test
    void saveSummary_andLoadSummary_roundTrip() {
        JsonlShortMemoryStore store = createStore();
        store.saveSummary("s1", SessionMemory.builder()
                .sessionId("s1")
                .messageCount(2)
                .summary("Greeting exchange")
                .build());

        SessionMemory summary = store.loadSummary("s1");
        assertEquals("s1", summary.getSessionId());
        assertEquals(2, summary.getMessageCount());
        assertEquals("Greeting exchange", summary.getSummary());
    }
}
