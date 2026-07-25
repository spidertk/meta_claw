package meta.claw.tool;

import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendMediaToolTest {

    private final SendMediaTool tool = new SendMediaTool();
    private final String originalUserDir = System.getProperty("user.dir");

    @AfterEach
    void tearDown() {
        VesselContext.clear();
        System.setProperty("user.dir", originalUserDir);
    }

    private TaskContext bindCtx(String vesselId) {
        TaskContext ctx = TaskContext.builder()
                .vesselId(vesselId)
                .sessionId("s1")
                .build();
        VesselContext.bind(ctx);
        return ctx;
    }

    @Test
    void noTaskContextReturnsError() {
        VesselContext.clear();
        String result = tool.sendMedia("/tmp/whatever.jpg", null);
        assertTrue(result.startsWith("Error: no active task context"), "got: " + result);
    }

    @Test
    void absolutePathSchedulesMedia(@TempDir Path tempDir) throws Exception {
        Path img = tempDir.resolve("photo.png");
        Files.write(img, new byte[]{1, 2, 3});
        TaskContext ctx = bindCtx("v1");

        String result = tool.sendMedia(img.toString(), null);

        assertTrue(result.startsWith("Media scheduled:"), "got: " + result);
        assertEquals(img.toString(), ctx.getPendingMediaPath());
        assertEquals("IMAGE", ctx.getPendingMediaType());
    }

    @Test
    void vesselRelativePathResolved(@TempDir Path tempDir) throws Exception {
        // 伪造项目根：pom.xml 标记 + .meta-claw/vessels/v1/assets/...（知识库 source_asset 结构）
        Files.write(tempDir.resolve("pom.xml"), new byte[0]);
        Path asset = tempDir.resolve(".meta-claw/vessels/v1/assets/abc123/original.jpg");
        Files.createDirectories(asset.getParent());
        Files.write(asset, new byte[]{9, 9});
        System.setProperty("user.dir", tempDir.toString());
        TaskContext ctx = bindCtx("v1");

        String result = tool.sendMedia("assets/abc123/original.jpg", null);

        assertTrue(result.startsWith("Media scheduled:"), "got: " + result);
        assertEquals(asset.toString(), ctx.getPendingMediaPath());
        assertEquals("IMAGE", ctx.getPendingMediaType());
    }

    @Test
    void projectRelativePathResolved(@TempDir Path tempDir) throws Exception {
        Files.write(tempDir.resolve("pom.xml"), new byte[0]);
        Path file = tempDir.resolve("docs/report.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});
        System.setProperty("user.dir", tempDir.toString());
        TaskContext ctx = bindCtx("v1");

        String result = tool.sendMedia("docs/report.pdf", null);

        assertTrue(result.startsWith("Media scheduled:"), "got: " + result);
        assertEquals(file.toString(), ctx.getPendingMediaPath());
        assertEquals("FILE", ctx.getPendingMediaType());
    }

    @Test
    void typeInferenceAndExplicitHint(@TempDir Path tempDir) throws Exception {
        Path video = tempDir.resolve("clip.mp4");
        Files.write(video, new byte[]{1});
        TaskContext ctx = bindCtx("v1");

        tool.sendMedia(video.toString(), null);
        assertEquals("VIDEO", ctx.getPendingMediaType());

        tool.sendMedia(video.toString(), "file");
        assertEquals("FILE", ctx.getPendingMediaType());
    }

    @Test
    void missingFileReturnsError() {
        TaskContext ctx = bindCtx("v1");
        String result = tool.sendMedia("assets/not-exist.jpg", null);
        assertTrue(result.startsWith("Error: file not found"), "got: " + result);
        assertNull(ctx.getPendingMediaPath());
    }
}
