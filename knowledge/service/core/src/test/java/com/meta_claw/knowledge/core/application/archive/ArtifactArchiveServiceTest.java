package com.meta_claw.knowledge.core.application.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactArchiveServiceTest {

    @Test
    @DisplayName("artifact 归档到规范路径")
    void archiveToStandardPath(@TempDir Path tempDir) throws Exception {
        ArtifactArchiveService service = new ArtifactArchiveService(tempDir);
        Path sourceFile = Files.writeString(tempDir.resolve("temp_graph.json"), "{}");

        Path archived = service.archive(
                "shared", "src_1", "snap_1", "job_1",
                "graph", sourceFile
        );

        assertThat(archived).exists();
        assertThat(archived.toString()).contains("spaces/shared/sources/src_1/snapshots/snap_1/artifacts/job_1/graph.json");
    }
}
