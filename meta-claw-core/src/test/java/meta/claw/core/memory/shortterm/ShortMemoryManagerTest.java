package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortMemoryManagerTest {

    @Test
    void usesConfiguredBackend() {
        MemoryConfig config = new MemoryConfig();
        config.setShortTermStore("secondary");
        RecordingStore primary = new RecordingStore();
        RecordingStore secondary = new RecordingStore();

        ShortMemoryManager manager = new ShortMemoryManager(config, Map.of(
                "jsonl", primary,
                "secondary", secondary
        ));

        manager.appendMessage("s1", SpiMessage.user("hello"));

        assertEquals(0, primary.messages.size());
        assertEquals(1, secondary.messages.size());
        assertEquals("hello", secondary.messages.get(0).content());
    }

    @Test
    void rejectsUnknownBackend() {
        MemoryConfig config = new MemoryConfig();
        config.setShortTermStore("missing");

        assertThrows(IllegalArgumentException.class,
                () -> new ShortMemoryManager(config, Map.of("jsonl", new RecordingStore())));
    }

    private static class RecordingStore implements ShortMemoryStore {
        private final List<SpiMessage> messages = new ArrayList<>();

        @Override
        public void appendMessage(String sessionKey, SpiMessage message) {
            messages.add(message);
        }

        @Override
        public List<SpiMessage> getHistory(String sessionKey, int limit) {
            return List.copyOf(messages);
        }

        @Override
        public List<SessionSummary> listSessions(String vesselId) {
            return List.of();
        }

        @Override
        public boolean clearHistory(String sessionKey) {
            messages.clear();
            return true;
        }

        @Override
        public boolean conversationExists(String sessionKey) {
            return !messages.isEmpty();
        }
    }
}
