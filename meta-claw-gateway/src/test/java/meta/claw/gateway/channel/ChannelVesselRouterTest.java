package meta.claw.gateway.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChannelVesselRouter 单元测试：三级解析优先级、bind 持久化往返、未知回退
 */
class ChannelVesselRouterTest {

    @TempDir
    Path tempDir;

    private ChannelVesselRouter newRouter() {
        return new ChannelVesselRouter(tempDir.resolve("routes.json"));
    }

    @Test
    void resolveReturnsNullWhenNothingConfigured() {
        ChannelVesselRouter router = newRouter();
        assertNull(router.resolve("weixin:main", "user1", null));
    }

    @Test
    void resolveFallsBackToAccountDefault() {
        ChannelVesselRouter router = newRouter();
        assertEquals("defaultV", router.resolve("weixin:main", "user1", "defaultV"));
    }

    @Test
    void boundRouteTakesPrecedenceOverDefault() {
        ChannelVesselRouter router = newRouter();
        router.bind("weixin:main", "user1", "alibaba");
        assertEquals("alibaba", router.resolve("weixin:main", "user1", "defaultV"));
        // 其他对话不受绑定影响
        assertEquals("defaultV", router.resolve("weixin:main", "user2", "defaultV"));
        // 其他渠道不受绑定影响
        assertEquals("defaultV", router.resolve("weixin:work", "user1", "defaultV"));
    }

    @Test
    void bindPersistsAndReloads() throws Exception {
        ChannelVesselRouter router = newRouter();
        router.bind("weixin:main", "user1", "alibaba");

        Path file = tempDir.resolve("routes.json");
        assertTrue(Files.exists(file));

        // 重新加载（模拟重启）
        ChannelVesselRouter reloaded = newRouter();
        assertEquals("alibaba", reloaded.resolve("weixin:main", "user1", null));
    }

    @Test
    void missingRoutesFileTreatedAsEmpty() {
        ChannelVesselRouter router = new ChannelVesselRouter(tempDir.resolve("not-exist/routes.json"));
        assertTrue(router.listRoutes().isEmpty());
    }

    @Test
    void saveCreatesParentDirectories() {
        Path nested = tempDir.resolve("a/b/c/routes.json");
        ChannelVesselRouter router = new ChannelVesselRouter(nested);
        router.bind("weixin:main", "user1", "v1");
        assertTrue(Files.exists(nested));
    }
}
