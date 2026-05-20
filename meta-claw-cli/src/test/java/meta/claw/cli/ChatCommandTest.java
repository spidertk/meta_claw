package meta.claw.cli;

import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.shortterm.ShortMemoryManager;
import meta.claw.core.memory.shortterm.ShortMemoryStore;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCommandTest {

    @Test
    void toSpiMessages_shouldRestoreConversationMessagesButSkipSystem() {
        List<SpiMessage> restored = ChatCommand.toSpiMessages(List.of(
                MemoryMessageConverter.fromSpiMessage(SpiMessage.system("old system")),
                MemoryMessageConverter.fromSpiMessage(SpiMessage.user("hello")),
                MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant("hi"))
        ));

        assertEquals(2, restored.size());
        assertEquals("user", restored.get(0).role());
        assertEquals("hello", restored.get(0).content());
        assertEquals("assistant", restored.get(1).role());
        assertEquals("hi", restored.get(1).content());
    }

    @Test
    void selectSession_shouldInitializeNewConversationImmediately() {
        RecordingShortMemoryStore store = new RecordingShortMemoryStore();
        ShortMemoryManager manager = shortMemoryManager(store);

        String sessionId = ChatCommand.selectSession(manager, "default", null, () -> "new-session");

        assertEquals("new-session", sessionId);
        assertTrue(store.initializedSessions.contains("new-session"));
        assertTrue(manager.conversationExists("new-session"));
    }

    @Test
    void selectSession_shouldResumeExistingConversationWithoutInitializing() {
        RecordingShortMemoryStore store = new RecordingShortMemoryStore();
        store.existingSessions.add("existing-session");
        ShortMemoryManager manager = shortMemoryManager(store);

        String sessionId = ChatCommand.selectSession(manager, "default", "existing-session", () -> "new-session");

        assertEquals("existing-session", sessionId);
        assertFalse(store.initializedSessions.contains("new-session"));
        assertFalse(store.initializedSessions.contains("existing-session"));
    }

    @Test
    void selectSession_shouldRejectMissingResumeSession() {
        ShortMemoryManager manager = shortMemoryManager(new RecordingShortMemoryStore());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ChatCommand.selectSession(manager, "default", "missing-session", () -> "new-session"));

        assertEquals("Session not found for vessel 'default': missing-session", error.getMessage());
    }

    @Test
    void historyFilePath_shouldPointToVesselConversationHistory() {
        Path historyFile = ChatCommand.historyFilePath(Path.of(".meta-claw", "vessels"),
                "default", "session-1");

        assertEquals(Path.of(".meta-claw", "vessels", "default",
                "conversations", "session-1", "history.jsonl"), historyFile);
    }

    private static ShortMemoryManager shortMemoryManager(ShortMemoryStore store) {
        return new ShortMemoryManager(new MemoryConfig(), Map.of("jsonl", store));
    }

    private static class RecordingShortMemoryStore implements ShortMemoryStore {
        private final Set<String> initializedSessions = new HashSet<>();
        private final Set<String> existingSessions = new HashSet<>();

        @Override
        public void initializeConversation(String sessionKey) {
            initializedSessions.add(sessionKey);
            existingSessions.add(sessionKey);
        }

        @Override
        public void appendMessage(String sessionKey, MemoryMessage message) {
            existingSessions.add(sessionKey);
        }

        @Override
        public List<MemoryMessage> getHistory(String sessionKey, int limit) {
            return new ArrayList<>();
        }

        @Override
        public List<SessionMemory> listSessions(String vesselId) {
            return new ArrayList<>();
        }

        @Override
        public boolean clearHistory(String sessionKey) {
            return existingSessions.remove(sessionKey);
        }

        @Override
        public boolean conversationExists(String sessionKey) {
            return existingSessions.contains(sessionKey);
        }

        @Override
        public List<MemoryMessage> getHistoryByToken(String sessionKey, int maxTokens) {
            return new ArrayList<>();
        }

        @Override
        public SessionMemory loadSummary(String sessionKey) {
            return null;
        }

        @Override
        public void saveSummary(String sessionKey, SessionMemory summary) {
        }

        @Override
        public String summarizeConversation(List<MemoryMessage> history) {
            return "";
        }
    }
}
