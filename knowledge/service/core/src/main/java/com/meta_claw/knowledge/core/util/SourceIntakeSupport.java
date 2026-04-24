package com.meta_claw.knowledge.core.util;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.UnitRef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SourceIntakeSupport {
    private SourceIntakeSupport() {
    }

    public static String ensureSourceId(SourceRecord sourceRecord) {
        if (sourceRecord.getSourceId() != null && !sourceRecord.getSourceId().isBlank()) {
            return sourceRecord.getSourceId();
        }

        String seed = sourceRecord.getSpaceId() + "|" + sourceRecord.getSourceType() + "|" + normalizePath(sourceRecord.getLocation());
        return "src_" + slug(sourceRecord.getSourceType()) + "_" + shortHash(seed);
    }

    public static SnapshotRecord createSnapshot(SourceRecord sourceRecord) {
        Path path = Path.of(sourceRecord.getLocation()).toAbsolutePath().normalize();
        String fingerprint = fingerprint(path, sourceRecord.getSourceType());
        String snapshotId = "snapshot_" + shortHash(sourceRecord.getSpaceId() + "|" + sourceRecord.getSourceId() + "|" + fingerprint);
        Instant capturedAt = Instant.now();
        UnitRef rootUnit = UnitRef.builder()
                .unitId("unit_" + shortHash(snapshotId + "|" + path))
                .snapshotId(snapshotId)
                .parentUnitId(null)
                .unitType(inferUnitType(path))
                .path(path.toString())
                .displayName(sourceRecord.getDisplayName())
                .neighbors(List.of())
                .build();
        return SnapshotRecord.builder()
                .spaceId(sourceRecord.getSpaceId())
                .snapshotId(snapshotId)
                .sourceId(sourceRecord.getSourceId())
                .contentFingerprint(fingerprint)
                .capturedAt(capturedAt)
                .units(List.of(rootUnit))
                .build();
    }

    private static String fingerprint(Path path, String sourceType) {
        try {
            if (!Files.exists(path)) {
                return "missing:" + shortHash(sourceType + "|" + path);
            }

            if (Files.isRegularFile(path)) {
                return shortHash(sourceType + "|" + path + "|" + Files.size(path) + "|" + Files.getLastModifiedTime(path).toMillis());
            }

            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.walk(path)) {
                    DirectorySummary summary = stream
                            .filter(p -> !p.equals(path))
                            .sorted(Comparator.comparing(Path::toString))
                            .limit(512)
                            .collect(DirectorySummary::new, DirectorySummary::accept, DirectorySummary::combine);
                    return shortHash(sourceType + "|" + path + "|" + summary.fileCount + "|" + summary.directoryCount + "|" + summary.latestModifiedMillis);
                }
            }

            return shortHash(sourceType + "|" + path);
        } catch (IOException e) {
            return "error:" + shortHash(sourceType + "|" + path + "|" + e.getClass().getSimpleName());
        }
    }

    private static String inferUnitType(Path path) {
        if (Files.isDirectory(path)) {
            return "repo";
        }
        return "file";
    }

    private static String normalizePath(String value) {
        return Path.of(value).toAbsolutePath().normalize().toString();
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static final class DirectorySummary {
        private long fileCount;
        private long directoryCount;
        private long latestModifiedMillis;

        private void accept(Path path) {
            try {
                if (Files.isDirectory(path)) {
                    directoryCount++;
                } else {
                    fileCount++;
                }
                latestModifiedMillis = Math.max(latestModifiedMillis, Files.getLastModifiedTime(path).toMillis());
            } catch (IOException ignored) {
                // Keep fingerprint generation resilient for Sprint 2 skeleton.
            }
        }

        private void combine(DirectorySummary other) {
            fileCount += other.fileCount;
            directoryCount += other.directoryCount;
            latestModifiedMillis = Math.max(latestModifiedMillis, other.latestModifiedMillis);
        }
    }
}
