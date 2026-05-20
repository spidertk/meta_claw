package meta.claw.core.memory.longterm;

import meta.claw.core.config.MemoryConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 长期记忆 Store 工厂。通过 Spring Map 注入持有所有单例实现，按配置或类型获取。
 */
@Component
public class LongMemoryStoreFactory {
    private final Map<String, LongMemoryStore> stores;

    public LongMemoryStoreFactory(Map<String, LongMemoryStore> stores) {
        this.stores = stores;
    }

    /**
     * 按配置自动选择实现（默认 "file"）。
     */
    public LongMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getLongTermStore() != null
                ? config.getLongTermStore() : "file";
        return getStore(type);
    }

    /**
     * 按类型标识获取实现。
     */
    public LongMemoryStore getStore(String type) {
        LongMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No LongMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
