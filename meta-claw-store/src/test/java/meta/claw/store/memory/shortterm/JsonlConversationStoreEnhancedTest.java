package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.shortterm.ChatMessage;
import meta.claw.core.memory.shortterm.ConversationStats;
import meta.claw.core.memory.shortterm.MediaReference;
import meta.claw.core.memory.shortterm.MessageFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonlConversationStoreEnhancedTest {

    @TempDir
    Path tempDir;

    private JsonlConversationStore createStore() {
        return new JsonlConversationStore(tempDir);
    }

    private ChatMessage msg(String role, String content) {
        return ChatMessage.builder()
                .sessionKey("test-session")
                .role(role)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private ChatMessage msg(String role, String content, LocalDateTime timestamp) {
        return ChatMessage.builder()
                .sessionKey("test-session")
                .role(role)
                .content(content)
                .timestamp(timestamp)
                .build();
    }

    @Test
    void getHistory_withRoleFilter() {
        JsonlConversationStore store = createStore();
        store.appendMessage("s1", msg("user", "Hello"));
        store.appendMessage("s1", msg("assistant", "Hi"));
        store.appendMessage("s1", msg("user", "Bye"));

        List<ChatMessage> result = store.getHistory("s1", 0,
                MessageFilter.builder().role("user").build());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void getHistory_withTimeFilter() {
        JsonlConversationStore store = createStore();
        LocalDateTime now = LocalDateTime.now();
        store.appendMessage("s1", msg("user", "Old", now.minusDays(2)));
        store.appendMessage("s1", msg("user", "Recent", now.minusHours(1)));

        List<ChatMessage> result = store.getHistory("s1", 0,
                MessageFilter.builder().after(now.minusDays(1)).build());

        assertEquals(1, result.size());
        assertEquals("Recent", result.get(0).getContent());
    }

    @Test
    void getStats_shouldReturnCorrectCounts() {
        JsonlConversationStore store = createStore();
        store.appendMessage("s1", msg("system", "Sys"));
        store.appendMessage("s1", msg("user", "U1"));
        store.appendMessage("s1", msg("assistant", "A1"));
        store.appendMessage("s1", msg("user", "U2"));
        store.appendMessage("s1", msg("assistant", "A2"));

        ConversationStats stats = store.getStats("s1");

        assertNotNull(stats);
        assertEquals(5, stats.getMessageCount());
        assertEquals(2, stats.getUserMessages());
        assertEquals(2, stats.getAssistantMessages());
        assertEquals(1, stats.getSystemMessages());
        assertNotNull(stats.getFirstMessage());
        assertNotNull(stats.getLastMessage());
        assertTrue(stats.getFileSizeBytes() > 0);
    }

    @Test
    void getStats_emptySession_shouldReturnZeroCount() {
        JsonlConversationStore store = createStore();
        store.appendMessage("s1", msg("user", "X"));
        store.clearHistory("s1");

        ConversationStats stats = store.getStats("s1");

        assertNotNull(stats);
        assertEquals(0, stats.getMessageCount());
    }

    @Test
    void saveMedia_shouldWriteToMediaDir() throws IOException {
        JsonlConversationStore store = createStore();
        byte[] data = "test image data".getBytes();

        MediaReference ref = store.saveMedia("s1", data, "image.png", "image/png");

        assertNotNull(ref);
        assertTrue(ref.getAbsolutePath().contains("media"));
        assertTrue(ref.getRelativePath().startsWith("media/"));
        assertEquals("image/png", ref.getMediaType());

        Path saved = Path.of(ref.getAbsolutePath());
        assertTrue(Files.exists(saved));
        assertArrayEquals(data, Files.readAllBytes(saved));
    }

    @Test
    void getMediaPath_shouldResolveCorrectly() {
        JsonlConversationStore store = createStore();
        Path path = store.getMediaPath("s1", "media/image.png");
        assertTrue(path.toString().contains("media/image.png"));
    }

    @Test
    void appendMessage_shouldStripBase64() {
        JsonlConversationStore store = createStore();
        String base64Payload = "data:image/png;base64," + "A".repeat(300);
        store.appendMessage("s1", msg("user", "See this: " + base64Payload + " ok?"));

        List<ChatMessage> history = store.getHistory("s1");
        assertEquals(1, history.size());
        String content = history.get(0).getContent();
        assertFalse(content.contains("base64," + "A".repeat(50)));
        assertTrue(content.contains("[media:image/png:base64:<stripped>]"));
    }

    @Test
    void appendMessage_shouldNotModifyPlainText() {
        JsonlConversationStore store = createStore();
        store.appendMessage("s1", msg("user", "Just plain text."));

        List<ChatMessage> history = store.getHistory("s1");
        assertEquals(1, history.size());
        assertEquals("Just plain text.", history.get(0).getContent());
    }

    @Test
    void getHistory_withNullFilter_shouldReturnAll() {
        JsonlConversationStore store = createStore();
        store.appendMessage("s1", msg("user", "A"));
        store.appendMessage("s1", msg("assistant", "B"));

        List<ChatMessage> result = store.getHistory("s1", 0, null);
        assertEquals(2, result.size());
    }
}
