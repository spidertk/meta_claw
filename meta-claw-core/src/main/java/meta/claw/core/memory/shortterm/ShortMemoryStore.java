package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;

import java.util.List;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemoryStore {
    void initializeConversation(String vesselId, String sessionKey);
    void appendMessage(String vesselId, String sessionKey, MemoryMessage message);
    List<MemoryMessage> getHistory(String vesselId, String sessionKey, int limit);

    default List<MemoryMessage> getHistory(String vesselId, String sessionKey) {
        return getHistory(vesselId, sessionKey, 0);
    }

    List<SessionMemory> listSessions(String vesselId);
    boolean clearHistory(String vesselId, String sessionKey);
    boolean conversationExists(String vesselId, String sessionKey);
    List<MemoryMessage> getHistoryByToken(String vesselId, String sessionKey, int maxTokens);
    SessionMemory loadSummary(String vesselId, String sessionKey);
    void saveSummary(String vesselId, String sessionKey, SessionMemory summary);
    String summarizeConversation(String vesselId, List<MemoryMessage> history);
}
