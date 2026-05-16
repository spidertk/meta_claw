package meta.claw.store.memory.shortterm;

import meta.claw.core.memory.shortterm.ChatMessage;
import meta.claw.core.memory.shortterm.ConversationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class JsonlConversationStoreTest {

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

    @Test
    void appendMessage_andGetHistory_roundTrip() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test-session", msg("user", "Hello"));
        store.appendMessage("test-session", msg("assistant", "Hi there"));
        store.appendMessage("test-session", msg("user", "How are you?"));

        List<ChatMessage> history = store.getHistory("test-session");
        assertEquals(3, history.size());
        assertEquals("Hello", history.get(0).getContent());
        assertEquals("Hi there", history.get(1).getContent());
        assertEquals("How are you?", history.get(2).getContent());
    }

    @Test
    void getHistory_withLimit() {
        JsonlConversationStore store = createStore();
        for (int i = 0; i < 10; i++) {
            store.appendMessage("test-session", msg("user", "Message " + i));
        }

        List<ChatMessage> history = store.getHistory("test-session", 5);
        assertEquals(5, history.size());
        assertEquals("Message 5", history.get(0).getContent());
        assertEquals("Message 9", history.get(4).getContent());
    }

    @Test
    void getHistory_unlimited_shouldReturnAll() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test-session", msg("user", "Only one"));

        List<ChatMessage> history = store.getHistory("test-session", 0);
        assertEquals(1, history.size());
    }

    @Test
    void clearHistory_shouldTruncate() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test-session", msg("user", "Hello"));
        assertTrue(store.clearHistory("test-session"));

        List<ChatMessage> history = store.getHistory("test-session");
        assertTrue(history.isEmpty());
    }

    @Test
    void deleteConversation_shouldRemoveDir() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test-session", msg("user", "Hello"));
        assertTrue(store.deleteConversation("test-session"));

        assertFalse(store.conversationExists("test-session"));
    }

    @Test
    void conversationExists_shouldReturnCorrectly() {
        JsonlConversationStore store = createStore();
        assertFalse(store.conversationExists("nonexistent"));
        store.appendMessage("exists", msg("user", "Hello"));
        assertTrue(store.conversationExists("exists"));
    }

    @Test
    void concurrentAppend_shouldNotCorrupt() throws InterruptedException {
        JsonlConversationStore store = createStore();
        int threadCount = 10;
        int messagesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int m = 0; m < messagesPerThread; m++) {
                        store.appendMessage("concurrent", msg("user", "T" + threadId + "M" + m));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        List<ChatMessage> history = store.getHistory("concurrent");
        assertEquals(threadCount * messagesPerThread, history.size());
    }

    @Test
    void listConversations_shouldReturnSortedByUpdatedAt() {
        JsonlConversationStore store = createStore();
        store.appendMessage("session-a", msg("user", "First"));
        store.appendMessage("session-b", msg("user", "Second"));
        store.appendMessage("session-a", msg("user", "Third"));

        List<ConversationInfo> conversations = store.listConversations();
        assertEquals(2, conversations.size());
        // session-a should be first (most recently updated)
        assertEquals("session-a", conversations.get(0).getSessionId());
        assertEquals("session-b", conversations.get(1).getSessionId());
        assertEquals(2, conversations.get(0).getMessageCount());
        assertEquals(1, conversations.get(1).getMessageCount());
    }

    @Test
    void listConversations_withVesselId_shouldOnlyReturnThatVesselsSessions() {
        JsonlConversationStore vesselAStore = new JsonlConversationStore(tempDir, "vessel-a");
        JsonlConversationStore vesselBStore = new JsonlConversationStore(tempDir, "vessel-b");

        vesselAStore.appendMessage("session-a", msg("user", "A"));
        vesselBStore.appendMessage("session-b", msg("user", "B"));

        List<ConversationInfo> conversations = vesselAStore.listConversations("vessel-a");

        assertEquals(1, conversations.size());
        assertEquals("session-a", conversations.get(0).getSessionId());
    }
}
