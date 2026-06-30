package meta.claw.tool.knowledge.asset;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAssetManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void storesTextSource() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            LocalAssetManager manager = new LocalAssetManager();
            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("text/plain")
                    .content("hello")
                    .build();

            AssetRef ref = manager.store(source, "v1");
            assertTrue(Files.exists(ref.getOriginalPath()));
            assertEquals("hello", Files.readString(ref.getOriginalPath()));
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            } else {
                System.clearProperty("user.dir");
            }
        }
    }
}
