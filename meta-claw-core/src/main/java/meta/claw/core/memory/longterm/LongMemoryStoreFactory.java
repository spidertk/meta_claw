package meta.claw.core.memory.longterm;

import meta.claw.core.config.MemoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 长期记忆 Store 工厂。Spring 自动将所有 LongMemoryStore 实现注入到 Map 中。
 */
@Component
public class LongMemoryStoreFactory {

    /**
     * Spring 自动收集所有 {@link LongMemoryStore} 实现，以 bean name 为 key 注入。
     * 例如：{"file": FileLongMemoryStore 实例}
     */
    @Autowired
    private Map<String, LongMemoryStore> stores;

    /**
     * 测试用：手动设置 stores Map。
     */
    public void setStores(Map<String, LongMemoryStore> stores) {
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
