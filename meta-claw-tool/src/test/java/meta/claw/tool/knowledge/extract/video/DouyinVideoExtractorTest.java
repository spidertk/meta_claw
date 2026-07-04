package meta.claw.tool.knowledge.extract.video;

import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.extract.video.DouyinVideoExtractor;
import meta.claw.core.knowledge.extract.video.YtDlpVideoExtractor;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DouyinVideoExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIf("ytDlpInstalled")
    void extractsDouyinVideo() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            LocalAssetManager assetManager = new LocalAssetManager();
            YtDlpVideoExtractor ytDlp = new YtDlpVideoExtractor();
            DouyinVideoExtractor extractor = new DouyinVideoExtractor(List.of(ytDlp));

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("video/url.douyin")
                    .uri(URI.create("https://www.douyin.com/video/1234567890"))
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertNotNull(doc.getMarkdownBody());
        } finally {
            System.clearProperty("user.dir");
        }
    }

    boolean ytDlpInstalled() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
