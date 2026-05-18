package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 短期记忆编排器。
 */
@Component
@Scope("prototype")
public class ShortMemoryManager {
    private final ShortMemoryStore store;

    public ShortMemoryManager(MemoryConfig config, Map<String, ShortMemoryStore> stores) {
        String backend = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        this.store = requireStore(stores, backend);
    }

    public void appendMessage(String sessionKey, MemoryMessage message) {
        store.appendMessage(sessionKey, message);
    }

    public void initializeConversation(String sessionKey) {
        store.initializeConversation(sessionKey);
    }

    public List<MemoryMessage> getHistory(String sessionKey) {
        return store.getHistory(sessionKey);
    }

    public List<MemoryMessage> getHistory(String sessionKey, int limit) {
        return store.getHistory(sessionKey, limit);
    }

    public List<SessionMemory> listSessions(String vesselId) {
        return store.listSessions(vesselId);
    }

    public boolean clearHistory(String sessionKey) {
        return store.clearHistory(sessionKey);
    }

    public boolean conversationExists(String sessionKey) {
        return store.conversationExists(sessionKey);
    }

    public List<MemoryMessage> getHistoryByToken(String sessionKey, int maxTokens) {
        return store.getHistoryByToken(sessionKey, maxTokens);
    }

    public SessionMemory loadSummary(String sessionKey) {
        return store.loadSummary(sessionKey);
    }

    public void saveSummary(String sessionKey, SessionMemory summary) {
        store.saveSummary(sessionKey, summary);
    }

    public String summarizeConversation(List<MemoryMessage> history) {
        return store.summarizeConversation(history);
    }

    private static ShortMemoryStore requireStore(Map<String, ShortMemoryStore> stores, String backend) {
        if (stores == null || !stores.containsKey(backend)) {
            throw new IllegalArgumentException("Unsupported short-term memory backend: " + backend);
        }
        return stores.get(backend);
    }
}
