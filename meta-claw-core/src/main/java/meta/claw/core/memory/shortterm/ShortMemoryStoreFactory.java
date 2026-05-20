package meta.claw.core.memory.shortterm;

import meta.claw.core.config.MemoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短期记忆 Store 工厂。Spring 自动将所有 ShortMemoryStore 实现注入到 Map 中。
 */
@Component
public class ShortMemoryStoreFactory {

    /**
     * Spring 自动收集所有 {@link ShortMemoryStore} 实现，以 bean name 为 key 注入。
     * 例如：{"jsonl": JsonlShortMemoryStore 实例}
     */
    @Autowired
    private Map<String, ShortMemoryStore> stores;

    /**
     * 测试用：手动设置 stores Map。
     */
    public void setStores(Map<String, ShortMemoryStore> stores) {
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
