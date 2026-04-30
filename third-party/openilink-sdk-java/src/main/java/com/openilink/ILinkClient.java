package com.openilink;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openilink.auth.LoginCallbacks;
import com.openilink.exception.APIError;
import com.openilink.exception.ILinkException;
import com.openilink.exception.NoContextTokenException;
import com.openilink.http.DefaultHttpClient;
import com.openilink.http.HttpDoer;
import com.openilink.model.*;
import com.openilink.model.request.*;
import com.openilink.model.response.*;
import com.openilink.monitor.MessageHandler;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.SleepHelper;
import com.openilink.util.URLHelper;
import com.openilink.util.WechatHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client communicates with the Weixin iLink Bot API.
 *
 * <pre>{@code
 * ILinkClient client = ILinkClient.builder()
 *     .token("")
 *     .build();
 *
 * LoginResult result = client.loginWithQR(new LoginCallbacks() {
 *     @Override
 *     public void onQRCode(String url) {
 *         System.out.println("请扫码: " + url);
 *     }
 * });
 * }</pre>
 */
public class ILinkClient {

    private static final Logger log = LoggerFactory.getLogger(ILinkClient.class);

    public static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    public static final String DEFAULT_CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c";
    public static final String DEFAULT_BOT_TYPE = "3";
    public static final String DEFAULT_VERSION = "1.0.0";

    private static final long LONG_POLL_TIMEOUT_MS = 35_000L;
    private static final long API_TIMEOUT_MS = 15_000L;
    private static final long CONFIG_TIMEOUT_MS = 10_000L;
    private static final long QR_LONG_POLL_TIMEOUT_MS = 35_000L;
    private static final long DEFAULT_LOGIN_TIMEOUT_MS = 8L * 60 * 1000;
    private static final int MAX_QR_REFRESH_COUNT = 3;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long BACKOFF_DELAY_MS = 30_000L;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final long SESSION_EXPIRED_DELAY_MS = 5L * 60 * 1000;

    private volatile String baseUrl;
    private final String cdnBaseUrl;
    private volatile String token;
    private final String botType;
    private final String version;
    private final HttpDoer httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, String> contextTokens = new ConcurrentHashMap<>();

    private ILinkClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.cdnBaseUrl = builder.cdnBaseUrl;
        this.token = builder.token;
        this.botType = builder.botType;
        this.version = builder.version;
        this.httpClient = builder.httpClient;
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new client with default settings.
     */
    public static ILinkClient create(String token) {
        return builder().token(token).build();
    }

    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String cdnBaseUrl = DEFAULT_CDN_BASE_URL;
        private String token = "";
        private String botType = DEFAULT_BOT_TYPE;
        private String version = DEFAULT_VERSION;
        private HttpDoer httpClient = new DefaultHttpClient();

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder cdnBaseUrl(String cdnBaseUrl) {
            this.cdnBaseUrl = cdnBaseUrl;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder botType(String botType) {
            this.botType = botType;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder httpClient(HttpDoer httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public ILinkClient build() {
            return new ILinkClient(this);
        }
    }

    // --- Getters & Setters ---

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // --- Internal helpers ---

    private BaseInfo buildBaseInfo() {
        return BaseInfo.builder().channelVersion(version).build();
    }

    private Map<String, String> buildHeaders(byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("AuthorizationType", "ilink_bot_token");
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("X-WECHAT-UIN", WechatHelper.randomWechatUIN());
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private byte[] doPost(String endpoint, Object body, long timeoutMs) throws IOException {
        byte[] data = objectMapper.writeValueAsBytes(body);
        String url = URLHelper.joinPath(baseUrl, endpoint);
        Map<String, String> headers = buildHeaders(data);
        return httpClient.doPost(url, data, headers, timeoutMs);
    }

    private byte[] doGet(String rawUrl, Map<String, String> extraHeaders, long timeoutMs) throws IOException {
        return httpClient.doGet(rawUrl, extraHeaders, timeoutMs);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ILinkException("ilink: url encode failed", e);
        }
    }

    // --- API methods ---

    /**
     * Performs a long-poll request to receive new messages.
     * Returns an empty response (not an error) on client-side timeout.
     */
    public GetUpdatesResp getUpdates(String getUpdatesBuf) {
        GetUpdatesReq reqBody = GetUpdatesReq.builder()
                .getUpdatesBuf(getUpdatesBuf)
                .baseInfo(buildBaseInfo())
                .build();
        long timeout = LONG_POLL_TIMEOUT_MS + 5_000L;

        try {
            byte[] data = doPost("ilink/bot/getupdates", reqBody, timeout);
            return objectMapper.readValue(data, GetUpdatesResp.class);
        } catch (Exception e) {
            // Client-side timeout is normal for long-poll
            log.debug("getUpdates timeout or error: {}", e.getMessage());
            return GetUpdatesResp.builder()
                    .ret(0)
                    .getUpdatesBuf(getUpdatesBuf)
                    .build();
        }
    }

    /**
     * Sends a raw message request.
     */
    public void sendMessage(SendMessageReq msg) {
        msg.setBaseInfo(buildBaseInfo());
        try {
            doPost("ilink/bot/sendmessage", msg, API_TIMEOUT_MS);
        } catch (IOException e) {
            throw new ILinkException("ilink: sendMessage failed", e);
        }
    }

    /**
     * Sends a plain text message to a user.
     *
     * @param to           the target user ID
     * @param text         the text content
     * @param contextToken must come from the inbound message's contextToken
     * @return the generated client ID
     */
    public String sendText(String to, String text, String contextToken) {
        String clientId = "sdk-" + System.currentTimeMillis();

        SendMessageReq msg = SendMessageReq.builder()
                .msg(WeixinMessage.builder()
                        .toUserId(to)
                        .clientId(clientId)
                        .messageType(MessageType.BOT)
                        .messageState(MessageState.FINISH)
                        .contextToken(contextToken)
                        .itemList(Collections.singletonList(
                                MessageItem.builder()
                                        .type(MessageItemType.TEXT)
                                        .textItem(TextItem.builder().text(text).build())
                                        .build()
                        ))
                        .build())
                .build();

        sendMessage(msg);
        return clientId;
    }

    /**
     * Fetches bot config (includes typing_ticket) for a given user.
     */
    public GetConfigResp getConfig(String userId, String contextToken) {
        GetConfigReq reqBody = GetConfigReq.builder()
                .ilinkUserId(userId)
                .contextToken(contextToken)
                .baseInfo(buildBaseInfo())
                .build();

        try {
            byte[] data = doPost("ilink/bot/getconfig", reqBody, CONFIG_TIMEOUT_MS);
            return objectMapper.readValue(data, GetConfigResp.class);
        } catch (IOException e) {
            throw new ILinkException("ilink: getConfig failed", e);
        }
    }

    /**
     * Sends or cancels a typing indicator.
     */
    public void sendTyping(String userId, String typingTicket, TypingStatus status) {
        SendTypingReq reqBody = SendTypingReq.builder()
                .ilinkUserId(userId)
                .typingTicket(typingTicket)
                .status(status)
                .baseInfo(buildBaseInfo())
                .build();

        try {
            doPost("ilink/bot/sendtyping", reqBody, CONFIG_TIMEOUT_MS);
        } catch (IOException e) {
            throw new ILinkException("ilink: sendTyping failed", e);
        }
    }

    /**
     * Requests a pre-signed CDN upload URL.
     */
    public GetUploadURLResp getUploadUrl(GetUploadURLReq req) {
        req.setBaseInfo(buildBaseInfo());
        try {
            byte[] data = doPost("ilink/bot/getuploadurl", req, API_TIMEOUT_MS);
            return objectMapper.readValue(data, GetUploadURLResp.class);
        } catch (IOException e) {
            throw new ILinkException("ilink: getUploadUrl failed", e);
        }
    }

    // --- Context token cache ---

    /**
     * Caches a context token for a user.
     * Called automatically by {@link #monitor}; can also be called manually.
     */
    public void setContextToken(String userId, String ctxToken) {
        contextTokens.put(userId, ctxToken);
    }

    /**
     * Returns the cached context token for a user, if any.
     */
    public Optional<String> getContextToken(String userId) {
        return Optional.ofNullable(contextTokens.get(userId));
    }

    /**
     * Sends a proactive text message using a cached context token.
     * The target user must have previously sent a message so that a context
     * token is available. Throws {@link NoContextTokenException} otherwise.
     */
    public String push(String to, String text) {
        String ctxToken = contextTokens.get(to);
        if (ctxToken == null) {
            throw new NoContextTokenException(to);
        }
        return sendText(to, text, ctxToken);
    }

    // --- QR Login ---

    /**
     * Requests a new login QR code from the API.
     */
    public QRCodeResponse fetchQRCode() {
        String bt = (botType != null && !botType.isEmpty()) ? botType : DEFAULT_BOT_TYPE;
        String url = URLHelper.joinPath(baseUrl, "ilink/bot/get_bot_qrcode")
                + "?bot_type=" + urlEncode(bt);

        try {
            byte[] data = doGet(url, null, API_TIMEOUT_MS);
            return objectMapper.readValue(data, QRCodeResponse.class);
        } catch (IOException e) {
            throw new ILinkException("ilink: fetch QR code failed", e);
        }
    }

    /**
     * Polls the scan status of a QR code.
     */
    public QRStatusResponse pollQRStatus(String qrCode) {
        String url = URLHelper.joinPath(baseUrl, "ilink/bot/get_qrcode_status")
                + "?qrcode=" + urlEncode(qrCode);

        Map<String, String> headers = new HashMap<>();
        headers.put("iLink-App-ClientVersion", "1");

        try {
            byte[] data = doGet(url, headers, QR_LONG_POLL_TIMEOUT_MS + 5_000L);
            return objectMapper.readValue(data, QRStatusResponse.class);
        } catch (Exception e) {
            // Timeout is normal for long-poll
            log.debug("pollQRStatus timeout or error: {}", e.getMessage());
            return QRStatusResponse.builder().status("wait").build();
        }
    }

    /**
     * Performs the full QR code login flow.
     * On success the client's token and base URL are updated automatically.
     */
    public LoginResult loginWithQR(LoginCallbacks callbacks) {
        if (callbacks == null) {
            callbacks = new LoginCallbacks() {};
        }

        long deadline = System.currentTimeMillis() + DEFAULT_LOGIN_TIMEOUT_MS;

        QRCodeResponse qr = fetchQRCode();
        callbacks.onQRCode(qr.getQrCodeImgContent());

        boolean scannedNotified = false;
        int refreshCount = 1;
        String currentQR = qr.getQrCode();

        while (System.currentTimeMillis() < deadline) {
            QRStatusResponse status = pollQRStatus(currentQR);

            switch (status.getStatus()) {
                case "wait":
                    break;

                case "scaned":
                    if (!scannedNotified) {
                        scannedNotified = true;
                        callbacks.onScanned();
                    }
                    break;

                case "expired":
                    refreshCount++;
                    if (refreshCount > MAX_QR_REFRESH_COUNT) {
                        return LoginResult.builder()
                                .message("QR code expired too many times")
                                .build();
                    }
                    callbacks.onExpired(refreshCount, MAX_QR_REFRESH_COUNT);
                    QRCodeResponse newQR = fetchQRCode();
                    currentQR = newQR.getQrCode();
                    scannedNotified = false;
                    callbacks.onQRCode(newQR.getQrCodeImgContent());
                    break;

                case "confirmed":
                    if (status.getIlinkBotId() == null || status.getIlinkBotId().isEmpty()) {
                        return LoginResult.builder()
                                .message("server did not return bot ID")
                                .build();
                    }
                    setToken(status.getBotToken());
                    if (status.getBaseUrl() != null && !status.getBaseUrl().isEmpty()) {
                        setBaseUrl(status.getBaseUrl());
                    }
                    return LoginResult.builder()
                            .connected(true)
                            .botToken(status.getBotToken())
                            .botId(status.getIlinkBotId())
                            .baseUrl(status.getBaseUrl())
                            .userId(status.getIlinkUserId())
                            .message("connected")
                            .build();

                default:
                    log.warn("Unknown QR status: {}", status.getStatus());
                    break;
            }

            SleepHelper.sleepInterruptibly(1_000L);
        }

        return LoginResult.builder().message("login timeout").build();
    }

    // --- Monitor ---

    /**
     * Runs a long-poll loop, invoking handler for each inbound message.
     * It blocks until stopped and handles retries/backoff automatically.
     * Context tokens are cached automatically for use with {@link #push}.
     *
     * @param handler the message handler
     * @param options monitor options (nullable)
     * @param stopFlag set to true to stop monitoring
     */
    public void monitor(MessageHandler handler, MonitorOptions options, AtomicBoolean stopFlag) {
        if (options == null) {
            options = MonitorOptions.builder().build();
        }

        java.util.function.Consumer<Exception> onError = options.getOnError() != null
                ? options.getOnError()
                : e -> {};

        String buf = options.getInitialBuf() != null ? options.getInitialBuf() : "";
        int failures = 0;

        while (!stopFlag.get()) {
            GetUpdatesResp resp;
            try {
                resp = getUpdates(buf);
            } catch (Exception e) {
                if (stopFlag.get()) {
                    return;
                }
                failures++;
                onError.accept(new ILinkException(
                        String.format("getUpdates (%d/%d): %s", failures, MAX_CONSECUTIVE_FAILURES, e.getMessage()), e));
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    failures = 0;
                    if (SleepHelper.sleepInterruptibly(BACKOFF_DELAY_MS)) return;
                } else {
                    if (SleepHelper.sleepInterruptibly(RETRY_DELAY_MS)) return;
                }
                continue;
            }

            // API-level error
            int ret = resp.getRet() != null ? resp.getRet() : 0;
            int errCode = resp.getErrCode() != null ? resp.getErrCode() : 0;

            if (ret != 0 || errCode != 0) {
                APIError apiErr = new APIError(ret, errCode, resp.getErrMsg());

                if (apiErr.isSessionExpired()) {
                    if (options.getOnSessionExpired() != null) {
                        options.getOnSessionExpired().run();
                    }
                    onError.accept(apiErr);
                    if (SleepHelper.sleepInterruptibly(SESSION_EXPIRED_DELAY_MS)) return;
                    continue;
                }

                failures++;
                onError.accept(new ILinkException(
                        String.format("getUpdates (%d/%d): %s", failures, MAX_CONSECUTIVE_FAILURES, apiErr.getMessage()), apiErr));
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    failures = 0;
                    if (SleepHelper.sleepInterruptibly(BACKOFF_DELAY_MS)) return;
                } else {
                    if (SleepHelper.sleepInterruptibly(RETRY_DELAY_MS)) return;
                }
                continue;
            }

            failures = 0;

            // Update sync cursor
            if (resp.getGetUpdatesBuf() != null && !resp.getGetUpdatesBuf().isEmpty()) {
                buf = resp.getGetUpdatesBuf();
                if (options.getOnBufUpdate() != null) {
                    options.getOnBufUpdate().accept(buf);
                }
            }

            // Dispatch messages
            if (resp.getMsgs() != null) {
                for (WeixinMessage msg : resp.getMsgs()) {
                    if (msg.getContextToken() != null && !msg.getContextToken().isEmpty()
                            && msg.getFromUserId() != null && !msg.getFromUserId().isEmpty()) {
                        setContextToken(msg.getFromUserId(), msg.getContextToken());
                    }
                    handler.handle(msg);
                }
            }
        }
    }
}
