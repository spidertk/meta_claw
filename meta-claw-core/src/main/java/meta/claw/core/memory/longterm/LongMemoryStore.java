package meta.claw.core.memory.longterm;

import meta.claw.core.memory.PreferenceMemory;

import java.util.List;

/**
 * 长期记忆 backend 契约。
 */
public interface LongMemoryStore {
    void addPreference(String vesselId, PreferenceMemory entry);
    List<PreferenceMemory> lookupPreference(String vesselId, String query);
    List<PreferenceMemory> listRecentPreferences(String vesselId, int limit);
    boolean deletePreference(String vesselId, String preferenceId);
    boolean clearPreferences(String vesselId);
}
