package meta.claw.core.memory.longterm;

import java.util.List;

/**
 * 长期记忆 backend 契约。
 */
public interface LongMemoryStore {
    void addPreference(String vesselId, PreferenceEntry entry);
    List<PreferenceEntry> lookupPreference(String vesselId, String query);
    List<PreferenceEntry> listRecentPreferences(String vesselId, int limit);
    boolean deletePreference(String vesselId, String preferenceId);
    boolean clearPreferences(String vesselId);
}
