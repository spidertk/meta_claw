package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.MemoryEntry;

import java.util.List;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemoryStore {
    void initializeConversation(String sessionKey);
    void appendEntry(String sessionKey, MemoryEntry entry);
    List<MemoryEntry> getHistory(String sessionKey, int limit);

    default List<MemoryEntry> getHistory(String sessionKey) {
        return getHistory(sessionKey, 0);
    }

    List<MemoryEntry> listSessions(String vesselId);
    boolean clearHistory(String sessionKey);
    boolean conversationExists(String sessionKey);
    List<MemoryEntry> getHistory(List<MemoryEntry> history, int maxRounds);
    List<MemoryEntry> getHistoryByToken(List<MemoryEntry> history, int maxTokens);
    String summarizeConversation(List<MemoryEntry> history);
}
