package meta.claw.core.memory.longterm;

import meta.claw.core.config.MemoryConfig;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆编排器。
 */
public class LongMemoryManager implements UserPreferenceStore {
    private final LongMemoryStore store;

    public LongMemoryManager(MemoryConfig config, Map<String, LongMemoryStore> stores) {
        String backend = config != null && config.getLongTermStore() != null
                ? config.getLongTermStore() : "file";
        this.store = requireStore(stores, backend);
    }

    @Override
    public void addPreference(String vesselId, PreferenceEntry entry) {
        store.addPreference(vesselId, entry);
    }

    @Override
    public List<PreferenceEntry> lookupPreference(String vesselId, String query) {
        return store.lookupPreference(vesselId, query);
    }

    @Override
    public List<PreferenceEntry> listRecentPreferences(String vesselId, int limit) {
        return store.listRecentPreferences(vesselId, limit);
    }

    @Override
    public boolean deletePreference(String vesselId, String preferenceId) {
        return store.deletePreference(vesselId, preferenceId);
    }

    @Override
    public boolean clearPreferences(String vesselId) {
        return store.clearPreferences(vesselId);
    }

    private static LongMemoryStore requireStore(Map<String, LongMemoryStore> stores, String backend) {
        if (stores == null || !stores.containsKey(backend)) {
            throw new IllegalArgumentException("Unsupported long-term memory backend: " + backend);
        }
        return stores.get(backend);
    }
}
