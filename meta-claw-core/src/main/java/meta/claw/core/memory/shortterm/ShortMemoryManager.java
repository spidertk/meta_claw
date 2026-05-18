package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.MemoryEntry;
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

    public void appendEntry(String sessionKey, MemoryEntry entry) {
        store.appendEntry(sessionKey, entry);
    }

    public void initializeConversation(String sessionKey) {
        store.initializeConversation(sessionKey);
    }

    public List<MemoryEntry> getHistory(String sessionKey) {
        return store.getHistory(sessionKey);
    }

    public List<MemoryEntry> listSessions(String vesselId) {
        return store.listSessions(vesselId);
    }

    public boolean clearHistory(String sessionKey) {
        return store.clearHistory(sessionKey);
    }

    public boolean conversationExists(String sessionKey) {
        return store.conversationExists(sessionKey);
    }

    public List<MemoryEntry> getHistory(List<MemoryEntry> history, int maxRounds) {
        return store.getHistory(history, maxRounds);
    }

    public List<MemoryEntry> getHistoryByToken(List<MemoryEntry> history, int maxTokens) {
        return store.getHistoryByToken(history, maxTokens);
    }

    public String summarizeConversation(List<MemoryEntry> history) {
        return store.summarizeConversation(history);
    }

    private static ShortMemoryStore requireStore(Map<String, ShortMemoryStore> stores, String backend) {
        if (stores == null || !stores.containsKey(backend)) {
            throw new IllegalArgumentException("Unsupported short-term memory backend: " + backend);
        }
        return stores.get(backend);
    }
}
