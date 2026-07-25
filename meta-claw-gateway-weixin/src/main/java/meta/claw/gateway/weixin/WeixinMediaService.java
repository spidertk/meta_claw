package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.exception.ILinkException;
import com.openilink.exception.NoContextTokenException;
import com.openilink.model.CDNMedia;
import com.openilink.model.FileItem;
import com.openilink.model.ImageItem;
import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.MessageState;
import com.openilink.model.MessageType;
import com.openilink.model.UploadMediaType;
import com.openilink.model.VideoItem;
import com.openilink.model.WeixinMessage;
import com.openilink.model.request.GetUploadURLReq;
import com.openilink.model.request.SendMessageReq;
import com.openilink.model.response.GetUploadURLResp;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 微信 CDN 媒体服务
 * <p>
 * 实现 iLink 协议的媒体上传/下载管线（与官方 Go SDK openilink-sdk-go 语义对齐）：
 * <ul>
 *   <li>下载：{@code GET <cdnBaseUrl>/download?encrypted_query_param=...} → AES-128-ECB 解密；</li>
 *   <li>上传：MD5 + 随机 AES key → getUploadUrl 预签名 → AES-128-ECB 加密 → POST CDN
 *       → 响应头 {@code x-encrypted-param} 作为消息引用；</li>
 *   <li>发送：按媒体类型构造 SendMessageReq（IMAGE/VIDEO/FILE），aes_key 为 base64(hex) 编码。</li>
 * </ul>
 * aes_key 两种编码兼容：base64(16 字节原始 key)（图片）与 base64(32 字符 hex)（文件/语音/视频）。
 * </p>
 */
@Slf4j
public class WeixinMediaService {

    private static final int AES_BLOCK = 16;
    private static final int ENCRYPT_TYPE_AES128_ECB = 1;
    private static final Duration CDN_TIMEOUT = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ILinkClient client;
    private final HttpClient httpClient;

    public WeixinMediaService(ILinkClient client) {
        this(client, HttpClient.newBuilder().connectTimeout(CDN_TIMEOUT).build());
    }

    /**
     * 可注入 HttpClient 的构造（测试用）
     */
    public WeixinMediaService(ILinkClient client, HttpClient httpClient) {
        this.client = client;
        this.httpClient = httpClient;
    }

    // ========== 下载 ==========

    /**
     * 下载并解密 CDN 媒体
     *
     * @param media 入站消息中的 CDNMedia 引用
     * @return 解密后的原始字节
     */
    public byte[] downloadMedia(CDNMedia media) {
        if (media == null || media.getEncryptQueryParam() == null || media.getEncryptQueryParam().isBlank()) {
            throw new ILinkException("ilink: CDNMedia 缺少 encrypt_query_param，无法下载");
        }
        String url = buildDownloadUrl(client.getCdnBaseUrl(), media.getEncryptQueryParam());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(CDN_TIMEOUT).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ILinkException("ilink: CDN 下载失败, status=" + response.statusCode());
            }
            byte[] ciphertext = response.body();
            // 无 aes_key 时按未加密内容返回（协议上存在明文媒体的可能）
            if (media.getAesKey() == null || media.getAesKey().isBlank()) {
                return ciphertext;
            }
            return decryptAesEcb(ciphertext, parseAesKey(media.getAesKey()));
        } catch (ILinkException e) {
            throw e;
        } catch (Exception e) {
            throw new ILinkException("ilink: CDN 下载失败: " + e.getMessage(), e);
        }
    }

    // ========== 上传 ==========

    /**
     * CDN 上传结果
     *
     * @param fileKey                       上传凭证 filekey
     * @param downloadEncryptedQueryParam   下载引用（CDN 响应头 x-encrypted-param）
     * @param aesKeyHex                     随机 AES key 的 hex 编码
     * @param fileSize                      原始字节数
     * @param ciphertextSize                加密后字节数
     */
    public record UploadResult(String fileKey, String downloadEncryptedQueryParam, String aesKeyHex,
                               long fileSize, long ciphertextSize) {
    }

    /**
     * 上传文件到微信 CDN（MD5 → 预签名 → AES 加密 → POST）
     *
     * @param plaintext 原始文件字节
     * @param toUserId  目标用户 ID（预签名要求）
     * @param mediaType 媒体类型
     * @return 上传结果（含发送消息所需的下载引用与密钥）
     */
    public UploadResult uploadFile(byte[] plaintext, String toUserId, UploadMediaType mediaType) {
        try {
            long rawSize = plaintext.length;
            String rawMd5 = md5Hex(plaintext);
            long fileSize = aesEcbPaddedSize((int) rawSize);
            String fileKey = randomHex(16);
            byte[] aesKey = new byte[16];
            RANDOM.nextBytes(aesKey);
            String aesKeyHex = HexFormat.of().formatHex(aesKey);

            // 1. 获取预签名上传 URL
            GetUploadURLReq req = new GetUploadURLReq();
            req.setFileKey(fileKey);
            req.setMediaType(mediaType.getValue());
            req.setToUserId(toUserId);
            req.setRawSize(rawSize);
            req.setRawFileMd5(rawMd5);
            req.setFileSize(fileSize);
            req.setNoNeedThumb(true);
            req.setAesKey(aesKeyHex);
            GetUploadURLResp resp = client.getUploadUrl(req);

            String cdnUrl;
            if (resp.getUploadFullUrl() != null && !resp.getUploadFullUrl().isBlank()) {
                cdnUrl = resp.getUploadFullUrl();
            } else if (resp.getUploadParam() != null && !resp.getUploadParam().isBlank()) {
                cdnUrl = buildUploadUrl(client.getCdnBaseUrl(), resp.getUploadParam(), fileKey);
            } else {
                throw new ILinkException("ilink: getUploadUrl 未返回上传地址（需要 upload_full_url 或 upload_param）");
            }

            // 2. 加密并上传
            byte[] ciphertext = encryptAesEcb(plaintext, aesKey);
            String downloadParam = postToCdn(cdnUrl, ciphertext);

            log.info("[WeixinMediaService] 上传成功: fileKey={}, rawSize={}, cipherSize={}", fileKey, rawSize, ciphertext.length);
            return new UploadResult(fileKey, downloadParam, aesKeyHex, rawSize, ciphertext.length);
        } catch (ILinkException e) {
            throw e;
        } catch (Exception e) {
            throw new ILinkException("ilink: CDN 上传失败: " + e.getMessage(), e);
        }
    }

    private String postToCdn(String cdnUrl, byte[] ciphertext) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(cdnUrl))
                .timeout(CDN_TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(ciphertext))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            String errMsg = response.headers().firstValue("x-error-message").orElse("status " + response.statusCode());
            throw new ILinkException("ilink: CDN 上传失败: " + errMsg);
        }
        return response.headers().firstValue("x-encrypted-param")
                .orElseThrow(() -> new ILinkException("ilink: CDN 响应缺少 x-encrypted-param 头"));
    }

    // ========== 发送媒体消息 ==========

    /**
     * 发送图片消息（需先 uploadFile 获得 UploadResult）
     */
    public String sendImage(String to, UploadResult uploaded) {
        MessageItem item = MessageItem.builder()
                .type(MessageItemType.IMAGE)
                .imageItem(ImageItem.builder()
                        .media(buildMediaRef(uploaded))
                        .midSize(uploaded.ciphertextSize())
                        .build())
                .build();
        return sendMediaMessage(to, item);
    }

    /**
     * 发送视频消息（需先 uploadFile 获得 UploadResult）
     */
    public String sendVideo(String to, UploadResult uploaded) {
        MessageItem item = MessageItem.builder()
                .type(MessageItemType.VIDEO)
                .videoItem(VideoItem.builder()
                        .media(buildMediaRef(uploaded))
                        .videoSize(uploaded.ciphertextSize())
                        .build())
                .build();
        return sendMediaMessage(to, item);
    }

    /**
     * 发送文件附件消息（需先 uploadFile 获得 UploadResult）
     */
    public String sendFileAttachment(String to, String fileName, UploadResult uploaded) {
        MessageItem item = MessageItem.builder()
                .type(MessageItemType.FILE)
                .fileItem(FileItem.builder()
                        .media(buildMediaRef(uploaded))
                        .fileName(fileName)
                        .len(String.valueOf(uploaded.fileSize()))
                        .build())
                .build();
        return sendMediaMessage(to, item);
    }

    private CDNMedia buildMediaRef(UploadResult uploaded) {
        return CDNMedia.builder()
                .encryptQueryParam(uploaded.downloadEncryptedQueryParam())
                .aesKey(mediaAesKey(uploaded.aesKeyHex()))
                .encryptType(ENCRYPT_TYPE_AES128_ECB)
                .build();
    }

    private String sendMediaMessage(String to, MessageItem item) {
        String contextToken = client.getContextToken(to)
                .orElseThrow(() -> new NoContextTokenException(to));
        String clientId = "sdk-media-" + System.currentTimeMillis();
        SendMessageReq req = SendMessageReq.builder()
                .msg(WeixinMessage.builder()
                        .toUserId(to)
                        .clientId(clientId)
                        .messageType(MessageType.BOT)
                        .messageState(MessageState.FINISH)
                        .contextToken(contextToken)
                        .itemList(List.of(item))
                        .build())
                .build();
        client.sendMessage(req);
        return clientId;
    }

    // ========== 静态工具（协议语义与官方 Go SDK 对齐） ==========

    static String buildDownloadUrl(String cdnBaseUrl, String encryptedQueryParam) {
        return cdnBaseUrl + "/download?encrypted_query_param=" + URLEncoder.encode(encryptedQueryParam, StandardCharsets.UTF_8);
    }

    static String buildUploadUrl(String cdnBaseUrl, String uploadParam, String fileKey) {
        return cdnBaseUrl + "/upload?encrypted_query_param=" + URLEncoder.encode(uploadParam, StandardCharsets.UTF_8)
                + "&filekey=" + URLEncoder.encode(fileKey, StandardCharsets.UTF_8);
    }

    /**
     * 解析 aes_key：base64(16 字节原始 key) 或 base64(32 字符 hex)
     */
    static byte[] parseAesKey(String aesKeyBase64) {
        byte[] decoded = java.util.Base64.getDecoder().decode(aesKeyBase64);
        if (decoded.length == 16) {
            return decoded;
        }
        if (decoded.length == 32 && isHex(decoded)) {
            return HexFormat.of().parseHex(new String(decoded, StandardCharsets.US_ASCII));
        }
        throw new ILinkException("ilink: aes_key 必须解码为 16 字节原始 key 或 32 字符 hex，实际 " + decoded.length + " 字节");
    }

    /**
     * 发送消息时 aes_key 的编码：base64(hex 字符串)
     */
    static String mediaAesKey(String hexKey) {
        return java.util.Base64.getEncoder().encodeToString(hexKey.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] encryptAesEcb(byte[] plaintext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(plaintext);
    }

    static byte[] decryptAesEcb(byte[] ciphertext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(ciphertext);
    }

    static int aesEcbPaddedSize(int plaintextSize) {
        return ((plaintextSize + 1 + AES_BLOCK - 1) / AES_BLOCK) * AES_BLOCK;
    }

    /**
     * 按魔数嗅探图片扩展名，默认 .jpg
     */
    static String sniffImageExtension(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return ".png";
        }
        if (data.length >= 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return ".gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return ".webp";
        }
        if (data.length >= 2 && data[0] == 'B' && data[1] == 'M') {
            return ".bmp";
        }
        return ".jpg";
    }

    private static boolean isHex(byte[] b) {
        for (byte c : b) {
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static String md5Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
