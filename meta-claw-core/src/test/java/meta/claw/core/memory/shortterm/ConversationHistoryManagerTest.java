package meta.claw.core.memory.shortterm;

import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationHistoryManagerTest {

    private final ConversationHistoryManager manager = new ConversationHistoryManager();

    private SpiMessage sys(String content) {
        return SpiMessage.system(content);
    }

    private SpiMessage user(String content) {
        return SpiMessage.user(content);
    }

    private SpiMessage assistant(String content) {
        return SpiMessage.assistant(content);
    }

    @Test
    void truncateByRound_shouldKeepSystemAndRecentRounds() {
        List<SpiMessage> history = new ArrayList<>();
        history.add(sys("You are a bot."));
        history.add(user("Q1"));
        history.add(assistant("A1"));
        history.add(user("Q2"));
        history.add(assistant("A2"));
        history.add(user("Q3"));
        history.add(assistant("A3"));

        List<SpiMessage> result = manager.truncateByRound(history, 2);

        assertEquals(5, result.size()); // system + Q2 + A2 + Q3 + A3
        assertTrue(result.contains(sys("You are a bot.")));
        assertFalse(result.contains(user("Q1")));
        assertTrue(result.contains(user("Q3")));
    }

    @Test
    void truncateByRound_shouldReturnAll_whenMaxRoundsIsZero() {
        List<SpiMessage> history = List.of(user("Q1"), assistant("A1"));
        List<SpiMessage> result = manager.truncateByRound(history, 0);
        assertEquals(2, result.size());
    }

    @Test
    void truncateByRound_shouldHandleNullOrEmpty() {
        assertTrue(manager.truncateByRound(null, 5).isEmpty());
        assertTrue(manager.truncateByRound(new ArrayList<>(), 5).isEmpty());
    }

    @Test
    void truncateByRound_shouldKeepAll_whenRoundsLessThanMax() {
        List<SpiMessage> history = List.of(user("Q1"), assistant("A1"));
        List<SpiMessage> result = manager.truncateByRound(history, 5);
        assertEquals(2, result.size());
    }

    @Test
    void truncateByToken_shouldTruncate_whenExceedsLimit() {
        List<SpiMessage> history = new ArrayList<>();
        history.add(sys("System prompt here."));
        // Each English word ~ a few tokens. Use long repeated string to exceed limit.
        history.add(user("word ".repeat(100)));
        history.add(assistant("reply ".repeat(100)));

        // Estimate: 500 chars / 4 = ~125 tokens + 1 per message
        List<SpiMessage> result = manager.truncateByToken(history, 50);

        // System message should always be kept
        assertTrue(result.stream().anyMatch(m -> "system".equals(m.role())));
        // Should have dropped some messages
        assertTrue(result.size() < history.size() || result.size() == 1);
    }

    @Test
    void truncateByToken_shouldKeepAll_whenUnderLimit() {
        List<SpiMessage> history = List.of(sys("S"), user("Hi"), assistant("Hello"));
        List<SpiMessage> result = manager.truncateByToken(history, 10000);
        assertEquals(3, result.size());
    }

    @Test
    void truncateByToken_shouldHandleNullOrEmpty() {
        assertTrue(manager.truncateByToken(null, 100).isEmpty());
        assertTrue(manager.truncateByToken(new ArrayList<>(), 100).isEmpty());
    }

    @Test
    void summarizeConversation_shouldReturnPlaceholder() {
        assertEquals("Earlier conversation summarized.", manager.summarizeConversation(null));
        assertEquals("Earlier conversation summarized.", manager.summarizeConversation(List.of()));
    }
}
