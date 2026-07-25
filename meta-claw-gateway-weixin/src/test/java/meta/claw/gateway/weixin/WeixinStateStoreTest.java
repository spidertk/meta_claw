package meta.claw.gateway.weixin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WeixinStateStore 单元测试：login.json / sync_buf 读写、缺文件、删除、权限
 */
class WeixinStateStoreTest {

    @TempDir
    Path tempDir;

    private WeixinStateStore newStore() {
        return new WeixinStateStore(tempDir.resolve("main"));
    }

    @Test
    void loginRoundTrip() {
        WeixinStateStore store = newStore();
        assertNull(store.loadLogin(), "无 login.json 时应返回 null");

        store.saveLogin(new WeixinStateStore.LoginState("token-1", "bot-1", "https://base", "user-1"));

        WeixinStateStore.LoginState loaded = store.loadLogin();
        assertEquals("token-1", loaded.botToken());
        assertEquals("bot-1", loaded.botId());
        assertEquals("https://base", loaded.baseUrl());
        assertEquals("user-1", loaded.userId());
    }

    @Test
    void deleteLoginRemovesFile() {
        WeixinStateStore store = newStore();
        store.saveLogin(new WeixinStateStore.LoginState("t", "b", "u", "x"));
        store.deleteLogin();
        assertNull(store.loadLogin());
        assertFalse(Files.exists(tempDir.resolve("main/login.json")));
    }

    @Test
    void syncBufRoundTrip() {
        WeixinStateStore store = newStore();
        assertEquals("", store.loadSyncBuf(), "无 sync_buf 时应返回空串");

        store.saveSyncBuf("cursor-abc");
        assertEquals("cursor-abc", store.loadSyncBuf());

        // 覆盖写
        store.saveSyncBuf("cursor-def");
        assertEquals("cursor-def", store.loadSyncBuf());

        // 空游标不覆盖
        store.saveSyncBuf("");
        assertEquals("cursor-def", store.loadSyncBuf());
    }

    @Test
    void loginFileHasOwnerOnlyPermissions() throws Exception {
        WeixinStateStore store = newStore();
        store.saveLogin(new WeixinStateStore.LoginState("t", "b", "u", "x"));
        Path file = tempDir.resolve("main/login.json");
        try {
            String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
            assertEquals("rw-------", perms);
        } catch (UnsupportedOperationException e) {
            // 非 POSIX 文件系统跳过权限断言
        }
    }

    @Test
    void corruptedLoginFileReturnsNull() throws Exception {
        Path dir = tempDir.resolve("main");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("login.json"), "{not-json");
        assertNull(newStore().loadLogin());
    }
}
