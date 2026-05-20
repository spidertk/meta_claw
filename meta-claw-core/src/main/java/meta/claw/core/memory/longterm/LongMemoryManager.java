package meta.claw.core.memory.longterm;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.PreferenceMemory;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 长期记忆编排器。Spring 单例，通过 Factory 按配置选择 Store 实现。
 */
@Component
public class LongMemoryManager {
    private final LongMemoryStoreFactory storeFactory;

    public LongMemoryManager(LongMemoryStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public void addPreference(MemoryConfig config, String vesselId, PreferenceMemory entry) {
        storeFactory.getStore(config).addPreference(vesselId, entry);
    }

    public List<PreferenceMemory> lookupPreference(MemoryConfig config, String vesselId, String query) {
        return storeFactory.getStore(config).lookupPreference(vesselId, query);
    }

    public List<PreferenceMemory> listRecentPreferences(MemoryConfig config, String vesselId, int limit) {
        return storeFactory.getStore(config).listRecentPreferences(vesselId, limit);
    }

    public boolean deletePreference(MemoryConfig config, String vesselId, String preferenceId) {
        return storeFactory.getStore(config).deletePreference(vesselId, preferenceId);
    }

    public boolean clearPreferences(MemoryConfig config, String vesselId) {
        return storeFactory.getStore(config).clearPreferences(vesselId);
    }
}
