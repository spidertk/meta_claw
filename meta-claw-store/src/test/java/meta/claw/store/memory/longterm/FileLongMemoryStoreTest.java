package meta.claw.store.memory.longterm;

import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.util.ProjectRootFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileLongMemoryStoreTest {

    private String vesselId;
    private FileLongMemoryStore store;

    @BeforeEach
    void setUp() {
        vesselId = "test-vessel-" + UUID.randomUUID().toString().substring(0, 8);
        store = new FileLongMemoryStore();
    }

    @AfterEach
    void tearDown() throws IOException {
        Path vesselDir = ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(vesselId);
        if (Files.exists(vesselDir)) {
            deleteDir(vesselDir);
        }
    }

    private void deleteDir(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ }
                });
        }
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
        store.addPreference(vesselId, entry("p1", "I like Java", "language"));
        store.addPreference(vesselId, entry("p2", "I prefer Python", "language"));

        List<PreferenceMemory> results = store.lookupPreference(vesselId, "java");
        assertEquals(1, results.size());
        assertEquals("I like Java", results.get(0).getContent());
    }

    @Test
    void lookupPreference_noMatch_shouldReturnEmpty() {
        store.addPreference(vesselId, entry("p1", "Hello", "greeting"));

        List<PreferenceMemory> results = store.lookupPreference(vesselId, "nonexistent");
        assertTrue(results.isEmpty());
    }

    @Test
    void listRecentPreferences_withLimit() {
        for (int i = 0; i < 10; i++) {
            store.addPreference(vesselId, entry("p" + i, "Content " + i, "test"));
        }

        List<PreferenceMemory> results = store.listRecentPreferences(vesselId, 5);
        assertEquals(5, results.size());
        assertEquals("Content 5", results.get(0).getContent());
        assertEquals("Content 9", results.get(4).getContent());
    }

    @Test
    void listRecentPreferences_unlimited_shouldReturnAll() {
        store.addPreference(vesselId, entry("p1", "One", "test"));

        List<PreferenceMemory> results = store.listRecentPreferences(vesselId, 0);
        assertEquals(1, results.size());
    }

    @Test
    void deletePreference_shouldRemove() {
        store.addPreference(vesselId, entry("p1", "To be deleted", "test"));
        store.addPreference(vesselId, entry("p2", "Keep this", "test"));

        assertTrue(store.deletePreference(vesselId, "p1"));

        List<PreferenceMemory> results = store.listRecentPreferences(vesselId, 0);
        assertEquals(1, results.size());
        assertEquals("Keep this", results.get(0).getContent());
    }

    @Test
    void clearPreferences_shouldTruncate() {
        store.addPreference(vesselId, entry("p1", "Hello", "test"));
        assertTrue(store.clearPreferences(vesselId));

        List<PreferenceMemory> results = store.listRecentPreferences(vesselId, 0);
        assertTrue(results.isEmpty());
    }

    @Test
    void addPreference_withMetadata_shouldPreserve() {
        store.addPreference(vesselId, entryWithMetadata("p1", "With metadata",
                Map.of("key1", "value1", "key2", 42)));

        List<PreferenceMemory> results = store.lookupPreference(vesselId, "value1");
        assertEquals(1, results.size());
        assertNotNull(results.get(0).getMetadata());
        assertEquals("value1", results.get(0).getMetadata().get("key1"));
        assertEquals(42, results.get(0).getMetadata().get("key2"));
    }

    @Test
    void lookupPreference_byCategory_shouldMatch() {
        store.addPreference(vesselId, entry("p1", "Content", "favorite-color"));

        List<PreferenceMemory> results = store.lookupPreference(vesselId, "favorite-color");
        assertEquals(1, results.size());
    }
}
