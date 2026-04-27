package com.meta_claw.knowledge.core.application.archive;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
public class ArtifactArchiveService {

    private final Path archiveRoot;

    public ArtifactArchiveService(Path archiveRoot) {
        this.archiveRoot = archiveRoot;
    }

    /**
     * 归档 artifact 到规范路径。
     * @return 归档后的文件路径
     */
    public Path archive(String spaceId, String sourceId, String snapshotId, String jobId,
                        String artifactType, Path tempFile) throws Exception {
        Path dir = archiveRoot
                .resolve("spaces")
                .resolve(spaceId)
                .resolve("sources")
                .resolve(sourceId)
                .resolve("snapshots")
                .resolve(snapshotId)
                .resolve("artifacts")
                .resolve(jobId);
        Files.createDirectories(dir);

        String ext = artifactType.equals("wiki") ? "md" : "json";
        Path target = dir.resolve(artifactType + "." + ext);
        Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

        log.info("Archived {} to {}", artifactType, target);
        return target;
    }
}
