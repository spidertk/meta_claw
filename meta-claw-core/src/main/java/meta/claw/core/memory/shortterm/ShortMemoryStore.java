package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.spi.llm.SpiMessage;

import java.util.List;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemoryStore {
    void initializeConversation(String sessionKey);
    void appendMessage(String sessionKey, SpiMessage message);
    List<SpiMessage> getHistory(String sessionKey, int limit);

    default List<SpiMessage> getHistory(String sessionKey) {
        return getHistory(sessionKey, 0);
    }

    List<MemoryEntry> listSessions(String vesselId);
    boolean clearHistory(String sessionKey);
    boolean conversationExists(String sessionKey);
    List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds);
    List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens);
    String summarizeConversation(List<SpiMessage> history);
}
