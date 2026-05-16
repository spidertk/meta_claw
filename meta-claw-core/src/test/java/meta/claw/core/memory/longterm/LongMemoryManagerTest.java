package meta.claw.core.memory.longterm;

import meta.claw.core.config.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LongMemoryManagerTest {

    @Test
    void usesConfiguredBackend() {
        MemoryConfig config = new MemoryConfig();
        config.setLongTermStore("secondary");
        RecordingStore primary = new RecordingStore();
        RecordingStore secondary = new RecordingStore();
        LongMemoryManager manager = new LongMemoryManager(config, Map.of(
                "file", primary,
                "secondary", secondary
        ));

        manager.addPreference("v1", PreferenceEntry.builder().content("Pref").build());

        assertEquals(0, primary.entries.size());
        assertEquals(1, secondary.entries.size());
        assertEquals("Pref", secondary.entries.get(0).getContent());
    }

    @Test
    void rejectsUnknownBackend() {
        MemoryConfig config = new MemoryConfig();
        config.setLongTermStore("missing");

        assertThrows(IllegalArgumentException.class,
                () -> new LongMemoryManager(config, Map.of("file", new RecordingStore())));
    }

    private static class RecordingStore implements LongMemoryStore {
        private final List<PreferenceEntry> entries = new ArrayList<>();

        @Override
        public void addPreference(String vesselId, PreferenceEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<PreferenceEntry> lookupPreference(String vesselId, String query) {
            return List.copyOf(entries);
        }

        @Override
        public List<PreferenceEntry> listRecentPreferences(String vesselId, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public boolean deletePreference(String vesselId, String preferenceId) {
            return false;
        }

        @Override
        public boolean clearPreferences(String vesselId) {
            entries.clear();
            return true;
        }
    }
}
