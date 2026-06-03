package meta.claw.core.config.loader;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import meta.claw.core.infra.SnakeYamlFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 加载 Vessel 结构化配置（vessel.meta.yaml）。
 * <p>
 * 支持旧版 YAML key 自动迁移：
 * <ul>
 *   <li>{@code meta:}     → {@code identity:}</li>
 *   <li>{@code runtime:}  → {@code behavior:}</li>
 * </ul>
 * 已有 Vessel 的 vessel.meta.yaml 无需手动修改，加载时会自动转换。
 * </p>
 */
@Slf4j
@Component
public class VesselConfigLoader {

    private static final String META_FILE = "vessel.meta.yaml";
    private final Yaml yaml = SnakeYamlFactory.createCamelCaseYaml();

    public List<VesselConfig> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Vessel directory not found: {}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::load)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to scan vessel directory: {}", dir, e);
            return Collections.emptyList();
        }
    }

    public VesselConfig load(Path vesselDir) {
        Path metaPath = vesselDir.resolve(META_FILE);
        if (!Files.exists(metaPath)) {
            log.warn("Vessel config file not found: {}", metaPath);
            throw new VesselException(ErrorCode.VESSEL_META_NOT_FOUND, metaPath);
        }
        try {
            String content = Files.readString(metaPath);
            String migrated = migrateLegacyKeys(content);
            return yaml.loadAs(migrated, VesselConfig.class);
        } catch (IOException e) {
            log.error("Failed to load vessel config: {}", metaPath, e);
            throw new VesselException(ErrorCode.VESSEL_META_PARSE_ERROR, e, metaPath);
        }
    }

    /**
     * 将旧版 YAML key 迁移为新版 key，保持已有文件兼容。
     */
    private String migrateLegacyKeys(String yaml) {
        // (?m) 启用多行模式，^ 匹配每行行首
        // 仅替换作为独立 key 出现的行首 meta:/runtime:，不影响注释或值中的文本
        String result = yaml.replaceAll("(?m)^meta:", "identity:");
        result = result.replaceAll("(?m)^runtime:", "behavior:");
        return result;
    }
}
