package meta.claw.tool.memory;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.runtime.subsystem.MemorySubSystem;
import meta.claw.store.memory.longterm.FileLongMemoryStore;
import meta.claw.tool.MemoryTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryTool 端到端测试：真实 FileLongMemoryStore + JSONL 落盘，无需 mock LLM。
 * 模式参照 KnowledgeAcquisitionSmokeTest（tempDir + user.dir 切换 + VesselContext）。
 */
class MemoryToolTest {

    @TempDir
    Path tempDir;

    private MemoryTool tool;
    private MemorySubSystem memorySubSystem;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        VesselContext.setVesselId("vesselA");

        LongMemoryFactory factory = new LongMemoryFactory();
        factory.register("file", new FileLongMemoryStore());
        tool = new MemoryTool(factory);

        memorySubSystem = new MemorySubSystem();
        ReflectionTestUtils.setField(memorySubSystem, "longMemoryFactory", factory);
    }

    @AfterEach
    void tearDown() {
        VesselContext.clear();
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void saveAndSearchPreference() {
        String saved = tool.memorySave("用户喜欢简洁的回答", null);
        assertTrue(saved.contains("Saved to long-term memory"), "Expected save confirmation, got: " + saved);
        assertTrue(saved.contains("category: preference"), "Default category should be preference, got: " + saved);

        // 真实落盘
        Path jsonl = tempDir.resolve(".meta-claw/vessels/vesselA/preferences/preferences.jsonl");
        assertTrue(Files.exists(jsonl), "preferences.jsonl should be persisted");

        String found = tool.memorySearch("简洁", null, null);
        assertTrue(found.contains("用户喜欢简洁的回答"), "Search should hit saved preference, got: " + found);
    }

    @Test
    void saveDeduplicatesIdenticalContent() throws Exception {
        tool.memorySave("用户喜欢 Python", null);
        String second = tool.memorySave("  用户喜欢 Python  ", "preference");
        assertTrue(second.contains("Already remembered"), "Identical content should be deduplicated, got: " + second);

        Path jsonl = tempDir.resolve(".meta-claw/vessels/vesselA/preferences/preferences.jsonl");
        long lines = Files.lines(jsonl).filter(l -> !l.isBlank()).count();
        assertEquals(1, lines, "Duplicate save must not append a second line");
    }

    @Test
    void listAndDeletePreference() {
        tool.memorySave("用户偏好中文交流", null);
        String list = tool.memoryList(null, null);
        assertTrue(list.contains("用户偏好中文交流"), "List should contain saved preference, got: " + list);

        String id = list.split("\\(id: ")[1].split("\\)")[0].trim();
        String deleted = tool.memoryDelete(id);
        assertTrue(deleted.contains("Deleted preference"), "Expected delete confirmation, got: " + deleted);

        String after = tool.memorySearch("中文", null, null);
        assertTrue(after.contains("No saved preferences"), "Deleted preference must not be searchable, got: " + after);
    }

    @Test
    void deleteUnknownIdReturnsError() {
        String result = tool.memoryDelete("notexist");
        assertTrue(result.contains("not found"), "Unknown id should report not found, got: " + result);
    }

    @Test
    void categoryFilterWorks() {
        tool.memorySave("用户是一名 Java 工程师", "fact");
        tool.memorySave("用户喜欢极简风格", "preference");

        String facts = tool.memoryList("fact", null);
        assertTrue(facts.contains("Java 工程师"), "fact filter should list facts, got: " + facts);
        assertFalse(facts.contains("极简风格"), "fact filter must not list preferences, got: " + facts);
    }

    @Test
    void preferencesAreIsolatedPerVessel() {
        tool.memorySave("用户喜欢 Python", null);

        VesselContext.setVesselId("vesselB");
        String other = tool.memorySearch("Python", null, null);
        assertTrue(other.contains("No saved preferences"),
                "Vessel B must not see vessel A's preferences, got: " + other);
    }

    @Test
    void preferencesSummaryReflectsSavedEntries() {
        // 无偏好时返回空串（system prompt 区块自动折叠）
        assertEquals("", memorySubSystem.buildPreferencesSummary("vesselA", new MemoryConfig()));

        tool.memorySave("用户喜欢简洁的回答", null);
        String summary = memorySubSystem.buildPreferencesSummary("vesselA", new MemoryConfig());
        assertTrue(summary.contains("[preference] 用户喜欢简洁的回答"),
                "Summary should contain formatted preference, got: " + summary);
    }
}
