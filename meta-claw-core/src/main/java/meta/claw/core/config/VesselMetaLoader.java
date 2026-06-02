package meta.claw.core.config;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import meta.claw.core.config.SnakeYamlFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class VesselMetaLoader {

    private static final String META_FILE = "vessel.meta.yaml";
    private final Yaml yaml = SnakeYamlFactory.createCamelCaseYaml();

    public List<VesselMeta> loadFromDirectory(Path dir) {
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

    public VesselMeta load(Path vesselDir) {
        Path metaPath = vesselDir.resolve(META_FILE);
        if (!Files.exists(metaPath)) {
            log.warn("Vessel meta file not found: {}", metaPath);
            throw new VesselException(ErrorCode.VESSEL_META_NOT_FOUND, metaPath);
        }
        try (InputStream is = Files.newInputStream(metaPath)) {
            return yaml.loadAs(is, VesselMeta.class);
        } catch (IOException e) {
            log.error("Failed to load vessel meta: {}", metaPath, e);
            throw new VesselException(ErrorCode.VESSEL_META_PARSE_ERROR, e, metaPath);
        }
    }
}
