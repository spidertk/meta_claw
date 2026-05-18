package meta.claw.store.memory.longterm;

import meta.claw.core.memory.PreferenceMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileLongMemoryStoreTest {

    @TempDir
    Path tempDir;

    private FileLongMemoryStore createStore() {
        return new FileLongMemoryStore(tempDir);
    }

    private PreferenceMemory entry(String id, String content, String category) {
        return PreferenceMemory.builder()
                .id(id)
                .content(content)
                .category(category)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private PreferenceMemory entryWithMetadata(String id, String content, Map<String, Object> metadata) {
        return PreferenceMemory.builder()
                .id(id)
                .content(content)
                .category("test")
                .timestamp(LocalDateTime.now())
                .metadata(metadata)
                .build();
    }

    @Test
    void addAndLookupPreference_shouldMatch() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "I like Java", "language"));
        store.addPreference("vessel-1", entry("p2", "I prefer Python", "language"));

        List<PreferenceMemory> results = store.lookupPreference("vessel-1", "java");
        assertEquals(1, results.size());
        assertEquals("I like Java", results.get(0).getContent());
    }

    @Test
    void lookupPreference_noMatch_shouldReturnEmpty() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "Hello", "greeting"));

        List<PreferenceMemory> results = store.lookupPreference("vessel-1", "nonexistent");
        assertTrue(results.isEmpty());
    }

    @Test
    void listRecentPreferences_withLimit() {
        FileLongMemoryStore store = createStore();
        for (int i = 0; i < 10; i++) {
            store.addPreference("vessel-1", entry("p" + i, "Content " + i, "test"));
        }

        List<PreferenceMemory> results = store.listRecentPreferences("vessel-1", 5);
        assertEquals(5, results.size());
        assertEquals("Content 5", results.get(0).getContent());
        assertEquals("Content 9", results.get(4).getContent());
    }

    @Test
    void listRecentPreferences_unlimited_shouldReturnAll() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "One", "test"));

        List<PreferenceMemory> results = store.listRecentPreferences("vessel-1", 0);
        assertEquals(1, results.size());
    }

    @Test
    void deletePreference_shouldRemove() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "To be deleted", "test"));
        store.addPreference("vessel-1", entry("p2", "Keep this", "test"));

        assertTrue(store.deletePreference("vessel-1", "p1"));

        List<PreferenceMemory> results = store.listRecentPreferences("vessel-1", 0);
        assertEquals(1, results.size());
        assertEquals("Keep this", results.get(0).getContent());
    }

    @Test
    void clearPreferences_shouldTruncate() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "Hello", "test"));
        assertTrue(store.clearPreferences("vessel-1"));

        List<PreferenceMemory> results = store.listRecentPreferences("vessel-1", 0);
        assertTrue(results.isEmpty());
    }

    @Test
    void addPreference_withMetadata_shouldPreserve() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entryWithMetadata("p1", "With metadata",
                Map.of("key1", "value1", "key2", 42)));

        List<PreferenceMemory> results = store.lookupPreference("vessel-1", "value1");
        assertEquals(1, results.size());
        assertNotNull(results.get(0).getMetadata());
        assertEquals("value1", results.get(0).getMetadata().get("key1"));
        assertEquals(42, results.get(0).getMetadata().get("key2"));
    }

    @Test
    void lookupPreference_byCategory_shouldMatch() {
        FileLongMemoryStore store = createStore();
        store.addPreference("vessel-1", entry("p1", "Content", "favorite-color"));

        List<PreferenceMemory> results = store.lookupPreference("vessel-1", "favorite-color");
        assertEquals(1, results.size());
    }
}
