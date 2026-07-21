package meta.claw.tool.knowledge.asset;

import meta.claw.core.knowledge.asset.AssetRegistry;
import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资产内容 hash 去重：相同内容重复 store 幂等命中，不产生重复资产目录。
 */
class LocalAssetManagerDedupTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void sameContentStoresOnceAndHitsRegistry() throws Exception {
        AssetRegistry registry = new AssetRegistry();
        LocalAssetManager manager = new LocalAssetManager(registry);

        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType("text/plain")
                .content("hello dedup")
                .build();

        AssetRef first = manager.store(source, "v1");
        assertFalse(first.isAlreadyExists());
        assertNotNull(first.getSha256());
        assertTrue(Files.exists(first.getOriginalPath()));

        // 新 manager 实例（模拟跨会话）+ 新 source 对象，内容相同
        LocalAssetManager manager2 = new LocalAssetManager(registry);
        AssetRef second = manager2.store(
                KnowledgeSource.builder().mediaType("text/plain").content("hello dedup").build(), "v1");

        assertTrue(second.isAlreadyExists());
        assertEquals(first.getAssetId(), second.getAssetId());
        assertEquals(first.getOriginalPath(), second.getOriginalPath());

        Path assetsDir = tempDir.resolve(".meta-claw/vessels/v1/assets");
        long assetDirs = Files.list(assetsDir).filter(Files::isDirectory).count();
        assertEquals(1, assetDirs, "Same content must not create duplicate asset directories");

        // 注册表可查询并可关联知识条目
        assertTrue(registry.findByHash("v1", first.getSha256()).isPresent());
        registry.linkKnowledge("v1", first.getSha256(), "entry001");
        assertEquals(1, registry.findByHash("v1", first.getSha256()).get().getKnowledgeEntryIds().size());
    }

    @Test
    void differentContentGetsDifferentAssetId() {
        AssetRegistry registry = new AssetRegistry();
        LocalAssetManager manager = new LocalAssetManager(registry);

        AssetRef a = manager.store(KnowledgeSource.builder().mediaType("text/plain").content("AAA").build(), "v1");
        AssetRef b = manager.store(KnowledgeSource.builder().mediaType("text/plain").content("BBB").build(), "v1");

        assertNotEquals(a.getAssetId(), b.getAssetId());
        assertFalse(a.isAlreadyExists());
        assertFalse(b.isAlreadyExists());
    }
}
