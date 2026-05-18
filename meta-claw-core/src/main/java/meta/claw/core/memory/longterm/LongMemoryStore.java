package meta.claw.core.memory.longterm;

import meta.claw.core.memory.MemoryEntry;

import java.util.List;

/**
 * 长期记忆 backend 契约。
 */
public interface LongMemoryStore {
    void addPreference(String vesselId, MemoryEntry entry);
    List<MemoryEntry> lookupPreference(String vesselId, String query);
    List<MemoryEntry> listRecentPreferences(String vesselId, int limit);
    boolean deletePreference(String vesselId, String preferenceId);
    boolean clearPreferences(String vesselId);
}
