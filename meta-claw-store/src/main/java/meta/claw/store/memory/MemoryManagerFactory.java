package meta.claw.store.memory;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemoryManager;
import meta.claw.core.memory.longterm.LongMemoryStore;
import meta.claw.core.memory.shortterm.ShortMemoryManager;
import meta.claw.core.memory.shortterm.ShortMemoryStore;
import meta.claw.store.memory.longterm.FileLongMemoryStore;
import meta.claw.store.memory.shortterm.JsonlShortMemoryStore;

import java.nio.file.Path;
import java.util.Map;

/**
 * 默认 Memory 组合根。
 *
 * manager 负责配置驱动的 backend 选择；这个工厂只负责把当前可用的
 * 文件型 backend 注册给 manager，避免 CLI 调用层直接依赖具体实现类。
 */
public class MemoryManagerFactory {
    private final Path vesselsDir;

    public MemoryManagerFactory(Path vesselsDir) {
        this.vesselsDir = vesselsDir;
    }

    public ShortMemoryManager createShortTerm(MemoryConfig config, String vesselId) {
        Map<String, ShortMemoryStore> stores = Map.of(
                "jsonl", new JsonlShortMemoryStore(vesselsDir, vesselId)
        );
        return new ShortMemoryManager(config, stores);
    }

    public LongMemoryManager createLongTerm(MemoryConfig config) {
        Map<String, LongMemoryStore> stores = Map.of(
                "file", new FileLongMemoryStore(vesselsDir)
        );
        return new LongMemoryManager(config, stores);
    }
}
