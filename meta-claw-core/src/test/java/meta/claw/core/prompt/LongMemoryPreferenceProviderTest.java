package meta.claw.core.prompt;

import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LongMemoryPreferenceProviderTest {

    @Test
    void getPreferences_shouldReturnFormattedEntries() {
        LongMemoryStore store = new LongMemoryStore() {
            @Override
            public void addPreference(String vesselId, PreferenceMemory entry) {}

            @Override
            public List<PreferenceMemory> lookupPreference(String vesselId, String query) {
                return List.of();
            }

            @Override
            public List<PreferenceMemory> listRecentPreferences(String vesselId, int limit) {
                return List.of(
                        PreferenceMemory.builder().content("Likes dark mode").build(),
                        PreferenceMemory.builder().content("Prefers Java over Python").build()
                );
            }

            @Override
            public boolean deletePreference(String vesselId, String preferenceId) {
                return false;
            }

            @Override
            public boolean clearPreferences(String vesselId) {
                return false;
            }
        };

        LongMemoryPreferenceProvider provider = new LongMemoryPreferenceProvider(store);
        String preferences = provider.getPreferences("vessel-a");

        assertTrue(preferences.contains("- Likes dark mode"));
        assertTrue(preferences.contains("- Prefers Java over Python"));
    }

    @Test
    void getPreferences_shouldReturnEmptyString_whenNoEntries() {
        LongMemoryStore store = new LongMemoryStore() {
            @Override
            public void addPreference(String vesselId, PreferenceMemory entry) {}

            @Override
            public List<PreferenceMemory> lookupPreference(String vesselId, String query) {
                return List.of();
            }

            @Override
            public List<PreferenceMemory> listRecentPreferences(String vesselId, int limit) {
                return List.of();
            }

            @Override
            public boolean deletePreference(String vesselId, String preferenceId) {
                return false;
            }

            @Override
            public boolean clearPreferences(String vesselId) {
                return false;
            }
        };

        LongMemoryPreferenceProvider provider = new LongMemoryPreferenceProvider(store);
        assertEquals("", provider.getPreferences("vessel-a"));
    }

    @Test
    void getPreferences_shouldReturnEmptyString_whenVesselIdIsNull() {
        LongMemoryPreferenceProvider provider = new LongMemoryPreferenceProvider(null);
        assertEquals("", provider.getPreferences(null));
    }

    @Test
    void getPreferences_shouldHandleNullContent() {
        LongMemoryStore store = new LongMemoryStore() {
            @Override
            public void addPreference(String vesselId, PreferenceMemory entry) {}

            @Override
            public List<PreferenceMemory> lookupPreference(String vesselId, String query) {
                return List.of();
            }

            @Override
            public List<PreferenceMemory> listRecentPreferences(String vesselId, int limit) {
                return List.of(
                        PreferenceMemory.builder().content(null).build()
                );
            }

            @Override
            public boolean deletePreference(String vesselId, String preferenceId) {
                return false;
            }

            @Override
            public boolean clearPreferences(String vesselId) {
                return false;
            }
        };

        LongMemoryPreferenceProvider provider = new LongMemoryPreferenceProvider(store);
        String preferences = provider.getPreferences("vessel-a");
        assertEquals("- ", preferences);
    }
}
