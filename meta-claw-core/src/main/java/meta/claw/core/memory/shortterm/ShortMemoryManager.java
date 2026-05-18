package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.spi.llm.SpiMessage;

import java.util.List;
import java.util.Map;

/**
 * 短期记忆编排器。
 */
public class ShortMemoryManager {
    private final ShortMemoryStore store;

    public ShortMemoryManager(MemoryConfig config, Map<String, ShortMemoryStore> stores) {
        String backend = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        this.store = requireStore(stores, backend);
    }

    public void appendMessage(String sessionKey, SpiMessage message) {
        store.appendMessage(sessionKey, message);
    }

    public List<SpiMessage> getHistory(String sessionKey) {
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

    public List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds) {
        return store.truncateByRound(history, maxRounds);
    }

    public List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens) {
        return store.truncateByToken(history, maxTokens);
    }

    public String summarizeConversation(List<SpiMessage> history) {
        return store.summarizeConversation(history);
    }

    private static ShortMemoryStore requireStore(Map<String, ShortMemoryStore> stores, String backend) {
        if (stores == null || !stores.containsKey(backend)) {
            throw new IllegalArgumentException("Unsupported short-term memory backend: " + backend);
        }
        return stores.get(backend);
    }
}
