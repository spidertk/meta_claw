package meta.claw.core.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRef {
    private String assetId;
    private String mediaType;
    private Path originalPath;      // absolute path to original file
    private Path extractedPath;     // absolute path to extracted.md (optional)
    private String sha256;          // content hash, used for dedup
    private boolean alreadyExists;  // true when the same content was stored before
}
