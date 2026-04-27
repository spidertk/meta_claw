package com.meta_claw.knowledge.core.application.intake;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RwaSourceResolverTest {

    @Test
    @DisplayName("解析 .rwa 下的指定子目录为 source request")
    void resolveSingleDirectory(@TempDir Path tempDir) throws Exception {
        Path rwa = tempDir.resolve(".rwa");
        Files.createDirectories(rwa.resolve("my-project"));

        RwaSourceResolver resolver = new RwaSourceResolver(rwa);
        List<SourceRegistrationRequest> requests = resolver.resolve("my-project");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getSourceType()).isEqualTo("git_repository");
        assertThat(requests.get(0).getDisplayName()).isEqualTo("my-project");
        assertThat(requests.get(0).getLocation()).contains("my-project");
    }

    @Test
    @DisplayName("解析全部子目录")
    void resolveAllDirectories(@TempDir Path tempDir) throws Exception {
        Path rwa = tempDir.resolve(".rwa");
        Files.createDirectories(rwa.resolve("project-a"));
        Files.createDirectories(rwa.resolve("project-b"));

        RwaSourceResolver resolver = new RwaSourceResolver(rwa);
        List<SourceRegistrationRequest> requests = resolver.resolveAll();

        assertThat(requests).hasSize(2);
    }
}
