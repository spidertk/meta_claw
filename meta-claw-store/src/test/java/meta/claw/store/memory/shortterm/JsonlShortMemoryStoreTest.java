package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.SessionMemory;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.util.ProjectRootFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonlShortMemoryStoreTest {

    private String vesselId;
    private JsonlShortMemoryStore store;

    @BeforeEach
    void setUp() {
        vesselId = "test-vessel-" + UUID.randomUUID().toString().substring(0, 8);
        store = new JsonlShortMemoryStore();
    }

    @AfterEach
    void tearDown() throws IOException {
        Path vesselDir = ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(vesselId);
        if (Files.exists(vesselDir)) {
            deleteDir(vesselDir);
        }
    }

    private void deleteDir(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ }
                });
        }
    }

    @Test
    void initializeConversation_shouldCreateEmptyHistoryFile() {
        store.initializeConversation(vesselId, "s0");
        Path historyFile = ProjectRootFinder.getMetaClawDir()
                .resolve("vessels").resolve(vesselId).resolve("conversations").resolve("s0").resolve("history.jsonl");
        assertTrue(historyFile.toFile().exists());
        assertTrue(store.getHistory(vesselId, "s0").isEmpty());
    }

    @Test
    void appendMessage_andGetHistory_roundTrip() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("Hi")));
        List<MemoryMessage> history = store.getHistory(vesselId, "s1");
        assertEquals(2, history.size());
        assertEquals("Hello", history.get(0).getContent());
        assertEquals("Hi", history.get(1).getContent());
    }

    @Test
    void appendMessage_shouldPersistImmediatelyWithReadableTimestamp() throws IOException {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));

        Path historyFile = ProjectRootFinder.getMetaClawDir()
                .resolve("vessels").resolve(vesselId).resolve("conversations").resolve("s1").resolve("history.jsonl");
        String persisted = Files.readString(historyFile);
        assertTrue(persisted.contains("\"content\":\"Hello\""));
        assertTrue(persisted.contains("\"role\":\"user\""));
        assertTrue(persisted.matches("(?s).*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\".*"));
        assertFalse(persisted.contains("\"messageCount\""));
    }

    @Test
    void getHistory_withLimit() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u1")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a1")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u2")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a2")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u3")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a3")));

        List<MemoryMessage> history = store.getHistory(vesselId, "s1", 2);
        assertEquals(4, history.size());
        assertEquals("u2", history.get(0).getContent());
    }

    @Test
    void listSessions_shouldStayVesselScoped() {
        String vesselB = "test-vessel-b-" + UUID.randomUUID().toString().substring(0, 8);
        store.appendMessage(vesselId, "a1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("A")));
        store.appendMessage(vesselB, "b1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("B")));
        List<SessionMemory> sessions = store.listSessions(vesselId);
        assertEquals(1, sessions.size());
        assertEquals("a1", sessions.get(0).getSessionId());

        // cleanup vesselB
        try {
            deleteDir(ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(vesselB));
        } catch (IOException e) { /* ignore */ }
    }

    @Test
    void clearHistory_shouldTruncate() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("Hello")));
        assertTrue(store.clearHistory(vesselId, "s1"));
        assertTrue(store.getHistory(vesselId, "s1").isEmpty());
    }

    @Test
    void appendMessage_shouldStripBase64() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(
                SpiMessage.user("See data:image/png;base64," + "A".repeat(300))));
        assertTrue(store.getHistory(vesselId, "s1").get(0).getContent().contains("[media:image/png:base64:<stripped>]"));
    }

    @Test
    void getHistory_withLimit_shouldKeepRecentRounds() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u1")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a1")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("u2")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("a2")));

        List<MemoryMessage> result = store.getHistory(vesselId, "s1", 1);
        assertEquals(2, result.size());
        assertEquals("u2", result.get(0).getContent());
        assertEquals("a2", result.get(1).getContent());
    }

    @Test
    void getHistoryByToken_shouldKeepTail() {
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.user("12345678")));
        store.appendMessage(vesselId, "s1", MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("abcd")));

        List<MemoryMessage> result = store.getHistoryByToken(vesselId, "s1", 3);
        assertEquals(1, result.size());
        assertEquals("abcd", result.get(0).getContent());
    }

    @Test
    void saveSummary_andLoadSummary_roundTrip() {
        store.saveSummary(vesselId, "s1", SessionMemory.builder()
                .sessionId("s1")
                .messageCount(2)
                .summary("Greeting exchange")
                .build());

        SessionMemory summary = store.loadSummary(vesselId, "s1");
        assertEquals("s1", summary.getSessionId());
        assertEquals(2, summary.getMessageCount());
        assertEquals("Greeting exchange", summary.getSummary());
    }
}
