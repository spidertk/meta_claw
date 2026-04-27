package com.meta_claw.knowledge.core.application.intake;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 来源快照扫描器。
 * 负责生成稳定 sourceId、内容指纹、snapshotId 和最小 UnitRef 列表。
 */
public final class SourceSnapshotScanner {
    private SourceSnapshotScanner() {
    }

    /** 确保来源拥有稳定 sourceId；调用方可显式传入，也可由关键来源属性派生。 */
    public static String ensureSourceId(SourceRecord sourceRecord) {
        if (sourceRecord.getSourceId() != null && !sourceRecord.getSourceId().isBlank()) {
            return sourceRecord.getSourceId();
        }

        String seed = sourceRecord.getSpaceId() + "|" + sourceRecord.getSourceType() + "|" + normalizePath(sourceRecord.getLocation());
        return "src_" + slug(sourceRecord.getSourceType()) + "_" + shortHash(seed);
    }

    /** 基于完整请求对象创建一批不可变快照记录。 */
    public static SnapshotRecord createSnapshot(SourceIntakeRequest request) {
        SourceRecord sourceRecord = request.getSourceRecord();
        SourceIntakeConfig config = request.getConfig() == null ? SourceIntakeConfig.defaultConfig() : request.getConfig();
        int unitLimit = config.getUnitLimit();
        Path path = Path.of(sourceRecord.getLocation()).toAbsolutePath().normalize();
        String fingerprint = fingerprint(path, sourceRecord.getSourceType(), unitLimit);
        String snapshotSeed = sourceRecord.getSpaceId() + "|" + sourceRecord.getSourceId() + "|" + fingerprint + "|" + nullSafe(request.getScanCursor()) + "|" + unitLimit;
        String snapshotId = "snapshot_" + shortHash(snapshotSeed);
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
        UnitBuildResult unitBuildResult = buildUnits(snapshotId, path, rootUnit, request.getScanCursor(), unitLimit);
        return SnapshotRecord.builder()
                .spaceId(sourceRecord.getSpaceId())
                .snapshotId(snapshotId)
                .sourceId(sourceRecord.getSourceId())
                .contentFingerprint(fingerprint)
                .capturedAt(capturedAt)
                .units(unitBuildResult.units())
                .scanStatus(unitBuildResult.scanStatus())
                .unitLimit(unitLimit)
                .includedUnitCount(unitBuildResult.units().size())
                .scanBatchCount(1)
                .nextScanCursor(unitBuildResult.nextScanCursor())
                .build();
    }

    /** 生成内容指纹；文件按内容 hash，目录按稳定排序后的相对路径和文件内容 hash。 */
    private static String fingerprint(Path path, String sourceType, int unitLimit) {
        try {
            if (!Files.exists(path)) {
                return "missing:" + shortHash(sourceType + "|" + path);
            }

            if (Files.isRegularFile(path)) {
                return "sha256:" + hashFile(path);
            }

            if (Files.isDirectory(path)) {
                return "sha256:" + hashDirectory(path, unitLimit);
            }

            return shortHash(sourceType + "|" + path);
        } catch (IOException e) {
            return "error:" + shortHash(sourceType + "|" + path + "|" + e.getClass().getSimpleName());
        }
    }

    /** 为快照生成最小可追踪单元；目录会展开为根单元加若干文件单元。 */
    private static UnitBuildResult buildUnits(String snapshotId, Path rootPath, UnitRef rootUnit, String scanCursor, int unitLimit) {
        if (!Files.isDirectory(rootPath)) {
            return new UnitBuildResult(List.of(rootUnit), "complete", null);
        }

        try (Stream<Path> stream = Files.walk(rootPath)) {
            List<Path> filePaths = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .filter(path -> isAfterCursor(rootPath, path, scanCursor))
                    .limit(unitLimit + 1L)
                    .toList();
            boolean hasNextBatch = filePaths.size() > unitLimit;
            List<Path> includedFilePaths = hasNextBatch ? filePaths.subList(0, unitLimit) : filePaths;
            String nextScanCursor = hasNextBatch && !includedFilePaths.isEmpty()
                    ? rootPath.relativize(includedFilePaths.get(includedFilePaths.size() - 1)).toString()
                    : null;
            List<UnitRef> fileUnits = includedFilePaths.stream()
                    .map(path -> buildFileUnit(snapshotId, rootPath, rootUnit.getUnitId(), path))
                    .toList();
            List<String> neighbors = fileUnits.stream()
                    .map(UnitRef::getUnitId)
                    .toList();
            UnitRef linkedRootUnit = rootUnit.toBuilder()
                    .neighbors(neighbors)
                    .build();
            List<UnitRef> units = new ArrayList<>();
            units.add(linkedRootUnit);
            units.addAll(fileUnits);
            return new UnitBuildResult(units, hasNextBatch ? "partial" : "complete", nextScanCursor);
        } catch (IOException e) {
            return new UnitBuildResult(List.of(rootUnit), "failed", null);
        }
    }

    /** 判断文件相对路径是否位于扫描游标之后；空游标表示从头开始。 */
    private static boolean isAfterCursor(Path rootPath, Path filePath, String scanCursor) {
        if (scanCursor == null || scanCursor.isBlank()) {
            return true;
        }
        String relativePath = rootPath.relativize(filePath).toString();
        return relativePath.compareTo(scanCursor) > 0;
    }

    /** 构造目录快照中的单个文件单元，并把它挂到根单元下。 */
    private static UnitRef buildFileUnit(String snapshotId, Path rootPath, String rootUnitId, Path filePath) {
        Path relativePath = rootPath.relativize(filePath);
        return UnitRef.builder()
                .unitId("unit_" + shortHash(snapshotId + "|" + relativePath))
                .snapshotId(snapshotId)
                .parentUnitId(rootUnitId)
                .unitType("file")
                .path(relativePath.toString())
                .displayName(filePath.getFileName().toString())
                .neighbors(List.of())
                .build();
    }

    /** 计算目录内容 hash；目录本身不读文件元数据，只读相对路径和文件内容。 */
    private static String hashDirectory(Path rootPath, int unitLimit) throws IOException {
        MessageDigest digest = newSha256();
        try (Stream<Path> stream = Files.walk(rootPath)) {
            List<Path> paths = stream
                    .filter(path -> !path.equals(rootPath))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(unitLimit)
                    .toList();
            for (Path path : paths) {
                Path relativePath = rootPath.relativize(path);
                if (Files.isDirectory(path)) {
                    updateDigest(digest, "dir:" + relativePath);
                } else if (Files.isRegularFile(path)) {
                    updateDigest(digest, "file:" + relativePath + ":" + hashFile(path));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 计算单个文件的 SHA-256 内容 hash。 */
    private static String hashFile(Path path) throws IOException {
        MessageDigest digest = newSha256();
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    /** 根据路径形态推断最小 unit 类型。 */
    private static String inferUnitType(Path path) {
        if (Files.isDirectory(path)) {
            return "repo";
        }
        return "file";
    }

    /** 将来源路径规范化为绝对路径，避免同一位置生成多个 sourceId。 */
    private static String normalizePath(String value) {
        return Path.of(value).toAbsolutePath().normalize().toString();
    }

    /** 将来源类型转换为可读的 sourceId 片段。 */
    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /** 空值归一化，确保 snapshotId 派生输入稳定。 */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 生成短 hash，用于稳定但紧凑的内部标识。 */
    private static String shortHash(String value) {
        MessageDigest digest = newSha256();
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, 12);
    }

    /** 以零字节分隔 digest 片段，避免路径和值拼接后产生歧义。 */
    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    /** 创建 SHA-256 digest；运行环境缺失算法时视为不可恢复配置错误。 */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    /** UnitRef 生成结果和本批扫描覆盖状态。 */
    private record UnitBuildResult(List<UnitRef> units, String scanStatus, String nextScanCursor) {
    }
}
