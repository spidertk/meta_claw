package meta.claw.core.memory.shortterm;

import meta.claw.core.spi.llm.SpiMessage;

import java.util.List;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemoryStore {
    void appendMessage(String sessionKey, SpiMessage message);
    List<SpiMessage> getHistory(String sessionKey, int limit);

    default List<SpiMessage> getHistory(String sessionKey) {
        return getHistory(sessionKey, 0);
    }

    List<SessionSummary> listSessions(String vesselId);
    boolean clearHistory(String sessionKey);
    boolean conversationExists(String sessionKey);
}
