package meta.claw.store.memory;

import lombok.RequiredArgsConstructor;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemoryManager;
import meta.claw.core.memory.longterm.LongMemoryStore;
import meta.claw.core.memory.shortterm.ShortMemoryManager;
import meta.claw.core.memory.shortterm.ShortMemoryStore;
import meta.claw.store.memory.longterm.FileLongMemoryStore;
import meta.claw.store.memory.shortterm.JsonlShortMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * 由 Spring 托管的 memory 运行时装配入口。
 */
@Component
@RequiredArgsConstructor
public class MemoryManagerProvider {
    private final ObjectProvider<JsonlShortMemoryStore> jsonlShortMemoryStores;
    private final ObjectProvider<FileLongMemoryStore> fileLongMemoryStores;
    private final ObjectProvider<ShortMemoryManager> shortMemoryManagers;
    private final ObjectProvider<LongMemoryManager> longMemoryManagers;

    public ShortMemoryManager createShortTerm(Path vesselsDir, MemoryConfig config, String vesselId) {
        Map<String, ShortMemoryStore> stores = Map.of(
                "jsonl", jsonlShortMemoryStores.getObject(vesselsDir, vesselId)
        );
        return shortMemoryManagers.getObject(config, stores);
    }

    public LongMemoryManager createLongTerm(Path vesselsDir, MemoryConfig config) {
        Map<String, LongMemoryStore> stores = Map.of(
                "file", fileLongMemoryStores.getObject(vesselsDir)
        );
        return longMemoryManagers.getObject(config, stores);
    }
}
