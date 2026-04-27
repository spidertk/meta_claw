package com.meta_claw.knowledge.core.application.intake;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.domain.SourceRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class RwaSourceResolver {

    private final Path rwaRoot;

    public RwaSourceResolver(Path rwaRoot) {
        this.rwaRoot = rwaRoot;
    }

    public List<SourceRegistrationRequest> resolveAll() {
        try (Stream<Path> paths = Files.list(rwaRoot)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::toRequest)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list .rwa directories", e);
        }
    }

    public List<SourceRegistrationRequest> resolve(String dirName) {
        Path target = rwaRoot.resolve(dirName);
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        return List.of(toRequest(target));
    }

    private SourceRegistrationRequest toRequest(Path dir) {
        String name = dir.getFileName().toString();
        String absolutePath = dir.toAbsolutePath().toString();
        return SourceRegistrationRequest.builder()
                .roleName("shared")
                .sourceType("git_repository")
                .location(absolutePath)
                .displayName(name)
                .description("Auto-resolved from .rwa/" + name)
                .workspaceIdentity(SourceRecord.WorkspaceIdentity.builder()
                        .workspaceId("ws_" + name)
                        .workspaceRoot(absolutePath)
                        .vcs("git")
                        .originMode("native_git")
                        .defaultBranch("main")
                        .build())
                .snapshotHint(SourceRecord.SnapshotHint.builder()
                        .branch("main")
                        .worktreeState("clean")
                        .build())
                .build();
    }
}
