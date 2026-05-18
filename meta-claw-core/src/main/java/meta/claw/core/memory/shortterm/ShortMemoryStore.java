package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;

import java.util.List;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemoryStore {
    void initializeConversation(String sessionKey);
    void appendMessage(String sessionKey, MemoryMessage message);
    List<MemoryMessage> getHistory(String sessionKey, int limit);

    default List<MemoryMessage> getHistory(String sessionKey) {
        return getHistory(sessionKey, 0);
    }

    List<SessionMemory> listSessions(String vesselId);
    boolean clearHistory(String sessionKey);
    boolean conversationExists(String sessionKey);
    List<MemoryMessage> getHistoryByToken(String sessionKey, int maxTokens);
    SessionMemory loadSummary(String sessionKey);
    void saveSummary(String sessionKey, SessionMemory summary);
    String summarizeConversation(List<MemoryMessage> history);
}
