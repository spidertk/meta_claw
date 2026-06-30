package meta.claw.tool.knowledge.asset;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class LocalAssetManager implements AssetManager {

    @Override
    public AssetRef store(KnowledgeSource source, String vesselId) {
        String assetId = source.getSourceId() != null && !source.getSourceId().isBlank()
                ? source.getSourceId()
                : UUID.randomUUID().toString().substring(0, 8);

        Path vesselDir = ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId != null && !vesselId.isBlank() ? vesselId : "default");
        Path assetDir = vesselDir.resolve("assets").resolve(assetId);

        try {
            Files.createDirectories(assetDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create asset directory: " + assetDir, e);
        }

        String extension = extensionForMediaType(source.getMediaType());
        Path originalPath = assetDir.resolve("original" + extension);

        try {
            if (source.getStream() != null) {
                Files.copy(source.getStream(), originalPath);
            } else if (source.getUri() != null) {
                Files.copy(source.getUri().toURL().openStream(), originalPath);
            } else if (source.getContent() != null && "text/plain".equals(source.getMediaType())) {
                Files.writeString(originalPath, source.getContent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store asset: " + originalPath, e);
        }

        return AssetRef.builder()
                .assetId(assetId)
                .mediaType(source.getMediaType())
                .originalPath(originalPath)
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

    private String extensionForMediaType(String mediaType) {
        if (mediaType == null) return "";
        return switch (mediaType) {
            case "text/plain" -> ".txt";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "video/url.douyin", "video/mp4" -> ".mp4";
            default -> "";
        };
    }
}
