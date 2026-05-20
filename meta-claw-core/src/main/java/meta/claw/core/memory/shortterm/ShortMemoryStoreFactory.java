package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短期记忆 Store 工厂。通过 Spring Map 注入持有所有单例实现，按配置或类型获取。
 */
@Component
public class ShortMemoryStoreFactory {
    private final Map<String, ShortMemoryStore> stores;

    public ShortMemoryStoreFactory(Map<String, ShortMemoryStore> stores) {
        this.stores = stores;
    }

    /**
     * 按配置自动选择实现（默认 "jsonl"）。
     */
    public ShortMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        return getStore(type);
    }

    /**
     * 按类型标识获取实现。
     */
    public ShortMemoryStore getStore(String type) {
        ShortMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No ShortMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
