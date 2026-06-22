package meta.claw.core.runtime.engine.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于文件系统的 SAA checkpoint 持久化实现。
 *
 * <p>以 {@code vesselId + threadId} 作为命名空间，每个 checkpoint 存为一个 JSON 文件：
 * {@code .meta-claw/vessels/<vesselId>/checkpoints/<threadId>/<checkpointId>.json}。</p>
 *
 * <p>threadId 通常取 {@code TaskContext.task.sessionId}，保证同一会话多次调用共享同一组 checkpoint；
 * 若 sessionId 缺失，则回退到 taskId。</p>
 *
 * <p>vesselId 通过 {@link RunnableConfig#metadata()} 透传，键名为 {@code "vesselId"}，
 * 避免 saver 与 {@code TaskContext} 耦合。</p>
 */
@Slf4j
@Component
public class VesselCheckpointSaver implements BaseCheckpointSaver {

    private static final String DEFAULT_THREAD_ID = "$default";
    private static final String METADATA_KEY_VESSEL_ID = "vesselId";
    private static final String CHECKPOINT_FILE_SUFFIX = ".json";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();
    private final AtomicLong lastTimestamp = new AtomicLong(0);
    private int defaultMaxCheckpoints = 100;

    public VesselCheckpointSaver() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 设置每个 threadId 默认保留的最大 checkpoint 数；put 时会清理超出数量的旧 checkpoint。
     */
    public void setDefaultMaxCheckpoints(int defaultMaxCheckpoints) {
        this.defaultMaxCheckpoints = Math.max(1, defaultMaxCheckpoints);
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        Path dir = resolveThreadDir(config);
        if (!Files.exists(dir)) {
            return List.of();
        }
        ReentrantReadWriteLock lock = getLock(config);
        lock.readLock().lock();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(CHECKPOINT_FILE_SUFFIX))
                    .sorted(Comparator.comparing(this::lastModifiedTime).reversed())
                    .map(this::readCheckpoint)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to list checkpoints for " + threadKey(config), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        Path dir = resolveThreadDir(config);
        if (!Files.exists(dir)) {
            return Optional.empty();
        }
        return list(config).stream()
                .filter(cp -> config.checkPointId().isEmpty()
                        || config.checkPointId().get().equals(cp.getId()))
                .findFirst();
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        Path dir = resolveThreadDir(config);
        ReentrantReadWriteLock lock = getLock(config);
        lock.writeLock().lock();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(checkpoint.getId() + CHECKPOINT_FILE_SUFFIX);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), checkpoint);
            // 保证同一毫秒内的多次写入仍有单调递增的修改时间，使 list/get(latest) 顺序稳定
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(nextTimestamp()));
            cleanupOldCheckpoints(dir, resolveMaxCheckpoints(config));
            return config;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        // 文件实现无需显式释放资源
        return new Tag(null, null);
    }

    /**
     * 删除某个 vessel + threadId 下的所有 checkpoint 文件与目录。
     */
    public void clear(String vesselId, String threadId) {
        Path dir = resolveThreadDir(vesselId, threadId);
        String key = threadKey(vesselId, threadId);
        ReentrantReadWriteLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete checkpoint file {}: {}", p, e.getMessage());
                    }
                });
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear checkpoints for " + key, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void cleanupOldCheckpoints(Path dir, int maxKeep) {
        if (maxKeep <= 0) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(CHECKPOINT_FILE_SUFFIX))
                    .sorted(Comparator.comparing(this::lastModifiedTime).reversed())
                    .collect(Collectors.toList());
            if (sorted.size() <= maxKeep) {
                return;
            }
            for (int i = maxKeep; i < sorted.size(); i++) {
                Path toDelete = sorted.get(i);
                try {
                    Files.deleteIfExists(toDelete);
                } catch (IOException e) {
                    log.warn("Failed to cleanup old checkpoint {}: {}", toDelete, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup old checkpoints in {}: {}", dir, e.getMessage());
        }
    }

    private long nextTimestamp() {
        return lastTimestamp.updateAndGet(prev -> Math.max(prev + 1, System.currentTimeMillis()));
    }

    private int resolveMaxCheckpoints(RunnableConfig config) {
        Object value = config.metadata("maxCheckpointsPerThread").orElse(null);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultMaxCheckpoints;
    }

    private Path resolveThreadDir(RunnableConfig config) {
        String vesselId = extractVesselId(config);
        String threadId = config.threadId().orElse(DEFAULT_THREAD_ID);
        return resolveThreadDir(vesselId, threadId);
    }

    private Path resolveThreadDir(String vesselId, String threadId) {
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId)
                .resolve("checkpoints")
                .resolve(threadId);
    }

    private String extractVesselId(RunnableConfig config) {
        return config.metadata(METADATA_KEY_VESSEL_ID).map(Object::toString).orElse("default");
    }

    private String threadKey(RunnableConfig config) {
        return threadKey(extractVesselId(config), config.threadId().orElse(DEFAULT_THREAD_ID));
    }

    private String threadKey(String vesselId, String threadId) {
        return vesselId + "/" + threadId;
    }

    private ReentrantReadWriteLock getLock(RunnableConfig config) {
        return lockMap.computeIfAbsent(threadKey(config), k -> new ReentrantReadWriteLock());
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Checkpoint readCheckpoint(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Checkpoint.class);
        } catch (IOException e) {
            log.warn("Failed to read checkpoint file {}: {}", path, e.getMessage());
            return null;
        }
    }
}
