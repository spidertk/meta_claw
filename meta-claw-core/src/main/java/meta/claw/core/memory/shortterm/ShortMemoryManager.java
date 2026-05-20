package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 短期记忆编排器。Spring 单例，通过 Factory 按配置选择 Store 实现。
 */
@Component
public class ShortMemoryManager {
    private final ShortMemoryStoreFactory storeFactory;

    public ShortMemoryManager(ShortMemoryStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public void appendMessage(MemoryConfig config, String vesselId, String sessionKey, MemoryMessage message) {
        storeFactory.getStore(config).appendMessage(vesselId, sessionKey, message);
    }

    public void initializeConversation(MemoryConfig config, String vesselId, String sessionKey) {
        storeFactory.getStore(config).initializeConversation(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistory(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).getHistory(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistory(MemoryConfig config, String vesselId, String sessionKey, int limit) {
        return storeFactory.getStore(config).getHistory(vesselId, sessionKey, limit);
    }

    public List<SessionMemory> listSessions(String vesselId) {
        return storeFactory.getStore("jsonl").listSessions(vesselId);
    }

    public boolean clearHistory(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).clearHistory(vesselId, sessionKey);
    }

    public boolean conversationExists(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).conversationExists(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistoryByToken(MemoryConfig config, String vesselId, String sessionKey, int maxTokens) {
        return storeFactory.getStore(config).getHistoryByToken(vesselId, sessionKey, maxTokens);
    }

    public SessionMemory loadSummary(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).loadSummary(vesselId, sessionKey);
    }

    public void saveSummary(MemoryConfig config, String vesselId, String sessionKey, SessionMemory summary) {
        storeFactory.getStore(config).saveSummary(vesselId, sessionKey, summary);
    }

    public String summarizeConversation(MemoryConfig config, String vesselId, List<MemoryMessage> history) {
        return storeFactory.getStore(config).summarizeConversation(vesselId, history);
    }
}
