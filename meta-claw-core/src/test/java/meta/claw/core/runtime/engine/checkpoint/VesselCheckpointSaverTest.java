package meta.claw.core.runtime.engine.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import meta.claw.core.infra.ProjectRootFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VesselCheckpointSaverTest {

    private VesselCheckpointSaver saver;

    @BeforeEach
    void setUp() throws Exception {
        saver = new VesselCheckpointSaver();
        saver.setDefaultMaxCheckpoints(100);
        // 每个测试前清理由 ProjectRootFinder 回退生成的测试目录，避免测试间干扰
        Path testMetaClawDir = ProjectRootFinder.getMetaClawDir();
        if (Files.exists(testMetaClawDir)) {
            deleteRecursively(testMetaClawDir);
        }
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var children = Files.list(path)) {
            for (var child : children.toList()) {
                deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void putWritesCheckpointFile() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-1");
        Checkpoint checkpoint = checkpointWithState(Map.of("messages", "hello"));

        saver.put(config, checkpoint);

        Optional<Checkpoint> found = saver.get(config);
        assertTrue(found.isPresent(), "checkpoint should be found");
        assertEquals(checkpoint.getId(), found.get().getId());
        assertEquals(checkpoint.getState(), found.get().getState());
        assertEquals(checkpoint.getNodeId(), found.get().getNodeId());
        assertEquals(checkpoint.getNextNodeId(), found.get().getNextNodeId());
    }

    @Test
    void listReturnsCheckpointsInReverseOrder() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-2");
        Checkpoint cp1 = checkpointWithState(Map.of("step", 1));
        Checkpoint cp2 = checkpointWithState(Map.of("step", 2));

        saver.put(config, cp1);
        Thread.sleep(2);
        saver.put(config, cp2);

        Collection<Checkpoint> list = saver.list(config);
        assertEquals(2, list.size());
        Iterator<Checkpoint> it = list.iterator();
        assertEquals(cp2.getId(), it.next().getId());
        assertEquals(cp1.getId(), it.next().getId());
    }

    @Test
    void getReturnsLatestWhenNoCheckpointIdSpecified() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-3");
        Checkpoint cp1 = checkpointWithState(Map.of("step", 1));
        Checkpoint cp2 = checkpointWithState(Map.of("step", 2));

        saver.put(config, cp1);
        saver.put(config, cp2);

        Optional<Checkpoint> found = saver.get(config);
        assertTrue(found.isPresent());
        assertEquals(cp2.getId(), found.get().getId());
    }

    @Test
    void getFiltersByCheckpointId() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-4");
        Checkpoint cp1 = checkpointWithState(Map.of("step", 1));
        Checkpoint cp2 = checkpointWithState(Map.of("step", 2));

        saver.put(config, cp1);
        saver.put(config, cp2);

        RunnableConfig withCheckPointId = RunnableConfig.builder(config)
                .checkPointId(cp1.getId())
                .build();
        Optional<Checkpoint> found = saver.get(withCheckPointId);
        assertTrue(found.isPresent());
        assertEquals(cp1.getId(), found.get().getId());
    }

    @Test
    void releaseReturnsEmptyTag() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-5");
        BaseCheckpointSaver.Tag tag = saver.release(config);
        assertNotNull(tag);
    }

    @Test
    void threadIsolation() throws Exception {
        RunnableConfig config1 = configFor("vessel-a", "thread-6");
        RunnableConfig config2 = configFor("vessel-a", "thread-7");
        Checkpoint cp1 = checkpointWithState(Map.of("thread", 1));
        Checkpoint cp2 = checkpointWithState(Map.of("thread", 2));

        saver.put(config1, cp1);
        saver.put(config2, cp2);

        Collection<Checkpoint> list1 = saver.list(config1);
        assertEquals(1, list1.size());
        assertEquals(cp1.getId(), list1.iterator().next().getId());

        Collection<Checkpoint> list2 = saver.list(config2);
        assertEquals(1, list2.size());
        assertEquals(cp2.getId(), list2.iterator().next().getId());
    }

    @Test
    void vesselIsolation() throws Exception {
        RunnableConfig config1 = configFor("vessel-a", "thread-8");
        RunnableConfig config2 = configFor("vessel-b", "thread-8");
        Checkpoint cp1 = checkpointWithState(Map.of("vessel", "a"));
        Checkpoint cp2 = checkpointWithState(Map.of("vessel", "b"));

        saver.put(config1, cp1);
        saver.put(config2, cp2);

        Collection<Checkpoint> list1 = saver.list(config1);
        assertEquals(1, list1.size());
        assertEquals(cp1.getId(), list1.iterator().next().getId());

        Collection<Checkpoint> list2 = saver.list(config2);
        assertEquals(1, list2.size());
        assertEquals(cp2.getId(), list2.iterator().next().getId());
    }

    @Test
    void clearRemovesAllCheckpointsForThread() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-9");
        saver.put(config, checkpointWithState(Map.of("step", 1)));
        saver.put(config, checkpointWithState(Map.of("step", 2)));

        saver.clear("vessel-a", "thread-9");

        assertTrue(saver.list(config).isEmpty());
        assertFalse(saver.get(config).isPresent());
    }

    @Test
    void cleanupOldCheckpoints() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-10", 2);
        Checkpoint cp1 = checkpointWithState(Map.of("step", 1));
        Checkpoint cp2 = checkpointWithState(Map.of("step", 2));
        Checkpoint cp3 = checkpointWithState(Map.of("step", 3));

        saver.put(config, cp1);
        Thread.sleep(2);
        saver.put(config, cp2);
        Thread.sleep(2);
        saver.put(config, cp3);

        Collection<Checkpoint> list = saver.list(config);
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(cp -> cp.getId().equals(cp2.getId())));
        assertTrue(list.stream().anyMatch(cp -> cp.getId().equals(cp3.getId())));
        assertFalse(list.stream().anyMatch(cp -> cp.getId().equals(cp1.getId())));
    }

    @Test
    void corruptedFileIsIgnored() throws Exception {
        RunnableConfig config = configFor("vessel-a", "thread-11");
        saver.put(config, checkpointWithState(Map.of("step", 1)));

        Path dir = resolveThreadDir(config);
        Files.writeString(dir.resolve("corrupted.json"), "not valid json");

        Collection<Checkpoint> list = saver.list(config);
        assertEquals(1, list.size());
    }

    @Test
    void emptyDirReturnsEmptyList() {
        RunnableConfig config = configFor("vessel-a", "thread-empty");
        assertTrue(saver.list(config).isEmpty());
        assertFalse(saver.get(config).isPresent());
    }

    private RunnableConfig configFor(String vesselId, String threadId) {
        return configFor(vesselId, threadId, 100);
    }

    private RunnableConfig configFor(String vesselId, String threadId, int maxCheckpoints) {
        return RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("vesselId", vesselId)
                .addMetadata("maxCheckpointsPerThread", maxCheckpoints)
                .build();
    }

    private Checkpoint checkpointWithState(Map<String, Object> state) {
        return Checkpoint.builder()
                .id(UUID.randomUUID().toString())
                .state(state)
                .nodeId("agent_llm")
                .nextNodeId("agent_tool")
                .build();
    }

    private Path resolveThreadDir(RunnableConfig config) {
        String vesselId = config.metadata("vesselId").map(Object::toString).orElse("default");
        String threadId = config.threadId().orElse("$default");
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId)
                .resolve("checkpoints")
                .resolve(threadId);
    }
}
