package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.model.CDNMedia;
import com.openilink.model.MessageItemType;
import com.openilink.model.UploadMediaType;
import com.openilink.model.request.SendMessageReq;
import com.openilink.model.response.GetUploadURLResp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeixinMediaService 单元测试：AES 加解密、aes_key 双编码、URL 拼接、
 * 下载解密、上传管线（预签名→加密→CDN POST）、媒体消息构造
 */
class WeixinMediaServiceTest {

    // ========== 静态工具 ==========

    @Test
    void aesEcbRoundTrip() throws Exception {
        byte[] key = new byte[16];
        new java.security.SecureRandom().nextBytes(key);
        byte[] plaintext = "你好，微信 CDN".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = WeixinMediaService.encryptAesEcb(plaintext, key);
        assertEquals(WeixinMediaService.aesEcbPaddedSize(plaintext.length), ciphertext.length);
        assertArrayEquals(plaintext, WeixinMediaService.decryptAesEcb(ciphertext, key));
    }

    @Test
    void parseAesKeySupportsRaw16Bytes() {
        byte[] raw = new byte[16];
        for (int i = 0; i < 16; i++) {
            raw[i] = (byte) i;
        }
        String b64 = Base64.getEncoder().encodeToString(raw);
        assertArrayEquals(raw, WeixinMediaService.parseAesKey(b64));
    }

    @Test
    void parseAesKeySupportsHexString() {
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        String hex = HexFormat.of().formatHex(raw);
        String b64 = Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(raw, WeixinMediaService.parseAesKey(b64));
    }

    @Test
    void mediaAesKeyIsBase64OfHexString() {
        String hex = "0123456789abcdef0123456789abcdef";
        String encoded = WeixinMediaService.mediaAesKey(hex);
        assertEquals(hex, new String(Base64.getDecoder().decode(encoded), StandardCharsets.US_ASCII));
    }

    @Test
    void urlBuilders() {
        assertEquals("https://cdn/download?encrypted_query_param=abc%3D",
                WeixinMediaService.buildDownloadUrl("https://cdn", "abc="));
        assertEquals("https://cdn/upload?encrypted_query_param=up&filekey=fk",
                WeixinMediaService.buildUploadUrl("https://cdn", "up", "fk"));
    }

    @Test
    void paddedSize() {
        assertEquals(16, WeixinMediaService.aesEcbPaddedSize(0));
        assertEquals(16, WeixinMediaService.aesEcbPaddedSize(1));
        assertEquals(16, WeixinMediaService.aesEcbPaddedSize(15));
        assertEquals(32, WeixinMediaService.aesEcbPaddedSize(16));
    }

    @Test
    void sniffImageExtensionByMagicBytes() {
        assertEquals(".jpg", WeixinMediaService.sniffImageExtension(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
        assertEquals(".png", WeixinMediaService.sniffImageExtension(new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4}));
        assertEquals(".gif", WeixinMediaService.sniffImageExtension(new byte[]{'G', 'I', 'F', '8', '9', 'a'}));
        assertEquals(".webp", WeixinMediaService.sniffImageExtension(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertEquals(".jpg", WeixinMediaService.sniffImageExtension(new byte[]{1, 2, 3, 4}));
    }

    // ========== 下载 ==========

    @Test
    void downloadMediaDecryptsCdnBytes() throws Exception {
        byte[] key = new byte[16];
        new java.security.SecureRandom().nextBytes(key);
        byte[] plaintext = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = WeixinMediaService.encryptAesEcb(plaintext, key);

        ILinkClient ilink = mock(ILinkClient.class);
        when(ilink.getCdnBaseUrl()).thenReturn("https://cdn.test");
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(ciphertext);
        doReturn(resp).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        WeixinMediaService service = new WeixinMediaService(ilink, http);
        CDNMedia media = CDNMedia.builder()
                .encryptQueryParam("enc-param")
                .aesKey(Base64.getEncoder().encodeToString(key))
                .build();

        byte[] result = service.downloadMedia(media);
        assertArrayEquals(plaintext, result);

        // 验证下载 URL 拼接
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().uri().toString()
                .startsWith("https://cdn.test/download?encrypted_query_param="));
    }

    // ========== 上传 ==========

    @Test
    @SuppressWarnings("unchecked")
    void uploadFileRunsFullPipeline() throws Exception {
        byte[] plaintext = "hello-cdn".getBytes(StandardCharsets.UTF_8);

        ILinkClient ilink = mock(ILinkClient.class);
        when(ilink.getCdnBaseUrl()).thenReturn("https://cdn.test");
        GetUploadURLResp uploadResp = new GetUploadURLResp();
        uploadResp.setUploadParam("signed-param");
        when(ilink.getUploadUrl(any())).thenReturn(uploadResp);

        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of("x-encrypted-param", List.of("download-param-123")), (a, b) -> true));
        doReturn(resp).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        WeixinMediaService service = new WeixinMediaService(ilink, http);
        WeixinMediaService.UploadResult result = service.uploadFile(plaintext, "user1", UploadMediaType.FILE);

        assertEquals("download-param-123", result.downloadEncryptedQueryParam());
        assertEquals(plaintext.length, result.fileSize());
        assertEquals(WeixinMediaService.aesEcbPaddedSize(plaintext.length), result.ciphertextSize());
        assertNotNull(result.aesKeyHex());
        assertEquals(32, result.aesKeyHex().length());

        // 验证上传到了拼接的 CDN URL，且 body 是加密后字节（长度 = padded size）
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().uri().toString().startsWith("https://cdn.test/upload?encrypted_query_param="));
        assertTrue(captor.getValue().uri().toString().contains("filekey="));
    }

    // ========== 发送媒体消息 ==========

    @Test
    void sendImageBuildsCorrectMessage() {
        ILinkClient ilink = mock(ILinkClient.class);
        when(ilink.getContextToken("user1")).thenReturn(Optional.of("ctx-token"));
        WeixinMediaService service = new WeixinMediaService(ilink, mock(HttpClient.class));

        WeixinMediaService.UploadResult uploaded =
                new WeixinMediaService.UploadResult("fk", "dl-param", "0123456789abcdef0123456789abcdef", 100, 112);
        service.sendImage("user1", uploaded);

        ArgumentCaptor<SendMessageReq> captor = ArgumentCaptor.forClass(SendMessageReq.class);
        verify(ilink).sendMessage(captor.capture());
        var msg = captor.getValue().getMsg();
        assertEquals("user1", msg.getToUserId());
        assertEquals("ctx-token", msg.getContextToken());
        assertEquals(1, msg.getItemList().size());
        var item = msg.getItemList().get(0);
        assertEquals(MessageItemType.IMAGE, item.getType());
        assertEquals("dl-param", item.getImageItem().getMedia().getEncryptQueryParam());
        // aes_key = base64(hex 字符串)
        assertEquals("0123456789abcdef0123456789abcdef",
                new String(Base64.getDecoder().decode(item.getImageItem().getMedia().getAesKey()), StandardCharsets.US_ASCII));
        assertEquals(1, item.getImageItem().getMedia().getEncryptType());
        assertEquals(112L, item.getImageItem().getMidSize());
    }

    @Test
    void sendFileAttachmentCarriesFileName() {
        ILinkClient ilink = mock(ILinkClient.class);
        when(ilink.getContextToken("user1")).thenReturn(Optional.of("ctx"));
        WeixinMediaService service = new WeixinMediaService(ilink, mock(HttpClient.class));

        WeixinMediaService.UploadResult uploaded =
                new WeixinMediaService.UploadResult("fk", "dl", "0123456789abcdef0123456789abcdef", 2048, 2064);
        service.sendFileAttachment("user1", "报告.pdf", uploaded);

        ArgumentCaptor<SendMessageReq> captor = ArgumentCaptor.forClass(SendMessageReq.class);
        verify(ilink).sendMessage(captor.capture());
        var item = captor.getValue().getMsg().getItemList().get(0);
        assertEquals(MessageItemType.FILE, item.getType());
        assertEquals("报告.pdf", item.getFileItem().getFileName());
        assertEquals("2048", item.getFileItem().getLen());
    }
}
