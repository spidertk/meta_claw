package meta.claw.core.knowledge.asset;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 本地资产存储，content-addressable：assetId 取内容 sha256 前 12 位，
 * 相同内容重复录入时幂等命中，不产生重复资产。
 */
@Slf4j
@Component
public class LocalAssetManager implements AssetManager {

    @Autowired(required = false)
    private AssetRegistry assetRegistry;

    public LocalAssetManager() {
    }

    public LocalAssetManager(AssetRegistry assetRegistry) {
        this.assetRegistry = assetRegistry;
    }

    @Override
    public AssetRef store(KnowledgeSource source, String vesselId) {
        byte[] bytes = readBytes(source);
        String sha256 = sha256Hex(bytes);

        String effectiveVessel = vesselId != null && !vesselId.isBlank() ? vesselId : "default";

        if (assetRegistry != null) {
            Optional<AssetRegistry.AssetRecord> existing = assetRegistry.findByHash(effectiveVessel, sha256);
            if (existing.isPresent()) {
                AssetRegistry.AssetRecord record = existing.get();
                Path assetDir = assetDir(effectiveVessel, record.getAssetId());
                Path originalPath = assetDir.resolve("original" + extensionForMediaType(record.getMediaType()));
                if (Files.exists(originalPath)) {
                    log.info("Asset already stored (hash {}), reusing {}", sha256.substring(0, 12), originalPath);
                    return AssetRef.builder()
                            .assetId(record.getAssetId())
                            .mediaType(record.getMediaType())
                            .originalPath(originalPath)
                            .sha256(sha256)
                            .alreadyExists(true)
                            .build();
                }
                // 索引存在但文件丢失：按原 assetId 重写
                writeBytes(originalPath, bytes);
                return AssetRef.builder()
                        .assetId(record.getAssetId())
                        .mediaType(record.getMediaType())
                        .originalPath(originalPath)
                        .sha256(sha256)
                        .alreadyExists(false)
                        .build();
            }
        }

        String assetId = sha256.substring(0, 12);
        Path originalPath = assetDir(effectiveVessel, assetId)
                .resolve("original" + extensionForMediaType(source.getMediaType()));
        writeBytes(originalPath, bytes);

        if (assetRegistry != null) {
            assetRegistry.register(effectiveVessel, sha256, assetId, source.getMediaType());
        }

        return AssetRef.builder()
                .assetId(assetId)
                .mediaType(source.getMediaType())
                .originalPath(originalPath)
                .sha256(sha256)
                .alreadyExists(false)
                .build();
    }

    @Override
    public InputStream load(AssetRef ref) {
        try {
            return Files.newInputStream(ref.getOriginalPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load asset: " + ref.getOriginalPath(), e);
        }
    }

    @Override
    public Path resolvePath(AssetRef ref) {
        return ref.getOriginalPath();
    }

    private Path assetDir(String vesselId, String assetId) {
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId)
                .resolve("assets")
                .resolve(assetId);
    }

    private byte[] readBytes(KnowledgeSource source) {
        try {
            if (source.getStream() != null) {
                return source.getStream().readAllBytes();
            }
            if (source.getUri() != null) {
                try (InputStream in = source.getUri().toURL().openStream()) {
                    return in.readAllBytes();
                }
            }
            if (source.getContent() != null) {
                return source.getContent().getBytes(StandardCharsets.UTF_8);
            }
            return new byte[0];
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source content", e);
        }
    }

    private void writeBytes(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store asset: " + path, e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String extensionForMediaType(String mediaType) {
        if (mediaType == null) return "";
        return switch (mediaType) {
            case "text/plain" -> ".txt";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "video/url.douyin" -> ".url";
            case "video/mp4" -> ".mp4";
            default -> "";
        };
    }
}
