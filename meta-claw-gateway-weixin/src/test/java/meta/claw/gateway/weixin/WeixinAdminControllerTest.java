package meta.claw.gateway.weixin;

import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeixinAdminController 单元测试：status/qrcode/relogin/send-media 端点行为
 */
class WeixinAdminControllerTest {

    @TempDir
    Path tempDir;

    private WeixinChannelManager manager;
    private WeixinChannel channel;
    private WeixinAdminController controller;

    @BeforeEach
    void setUp() {
        manager = mock(WeixinChannelManager.class);
        channel = mock(WeixinChannel.class);
        controller = new WeixinAdminController(manager);
    }

    @Test
    void sendMediaDefaultsToLastInboundUser() throws Exception {
        Path image = tempDir.resolve("test.png");
        Files.write(image, new byte[]{1, 2, 3});
        when(manager.getChannel("main")).thenReturn(channel);
        when(channel.getLastInboundUserId()).thenReturn("user-last@im.wechat");

        ResponseEntity<Map<String, Object>> resp = controller.sendMedia(
                "main", null, image.toString(), null, "说明文字");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("user-last@im.wechat", resp.getBody().get("to"));

        ArgumentCaptor<Reply> replyCaptor = ArgumentCaptor.forClass(Reply.class);
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(channel).send(replyCaptor.capture(), contextCaptor.capture());
        assertEquals(image.toString(), replyCaptor.getValue().getMediaPath());
        assertEquals("说明文字", replyCaptor.getValue().getContent());
        assertEquals("user-last@im.wechat", contextCaptor.getValue().getReceiver());
    }

    @Test
    void sendMediaRejectsWhenNoReceiver() throws Exception {
        Path image = tempDir.resolve("test.png");
        Files.write(image, new byte[]{1});
        when(manager.getChannel("main")).thenReturn(channel);
        when(channel.getLastInboundUserId()).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.sendMedia("main", null, image.toString(), null, null);

        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody().get("error"));
        verify(channel, never()).send(any(), any());
    }

    @Test
    void sendMediaRejectsMissingFile() {
        when(manager.getChannel("main")).thenReturn(channel);
        when(channel.getLastInboundUserId()).thenReturn("user@im.wechat");

        ResponseEntity<Map<String, Object>> resp = controller.sendMedia(
                "main", null, tempDir.resolve("not-exist.png").toString(), null, null);

        assertEquals(400, resp.getStatusCode().value());
        assertTrue(resp.getBody().get("error").toString().contains("文件不存在"));
    }

    @Test
    void sendMediaUnknownAccountIs404() {
        when(manager.getChannel("ghost")).thenReturn(null);
        ResponseEntity<Map<String, Object>> resp = controller.sendMedia("ghost", "u", "/tmp/x.png", null, null);
        assertEquals(404, resp.getStatusCode().value());
    }

    @Test
    void qrcodeReturns404WhenNoPendingQr() {
        when(manager.getChannel("main")).thenReturn(channel);
        when(channel.getPendingQrUrl()).thenReturn(null);
        assertEquals(404, controller.qrcode("main").getStatusCode().value());
    }

    @Test
    void qrcodeReturnsUrlWhenPending() {
        when(manager.getChannel("main")).thenReturn(channel);
        when(channel.getPendingQrUrl()).thenReturn("https://qr.url/x");
        ResponseEntity<Map<String, Object>> resp = controller.qrcode("main");
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("https://qr.url/x", resp.getBody().get("qrUrl"));
    }

    @Test
    void reloginUnknownAccountIs404() {
        when(manager.getChannel("ghost")).thenReturn(null);
        assertEquals(404, controller.relogin("ghost").getStatusCode().value());
    }
}
