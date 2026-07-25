package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.CDNMedia;
import com.openilink.model.FileItem;
import com.openilink.model.ImageItem;
import com.openilink.model.MessageItem;
import com.openilink.model.UploadMediaType;
import com.openilink.model.VoiceItem;
import com.openilink.model.WeixinMessage;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatChannel;
import meta.claw.gateway.channel.ChatMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 微信渠道实现类
 * <p>
 * 基于 openilink SDK 实现与微信生态的对接。核心语义（见 docs/weixin-channel-integration-design.md §6.2）：
 * <ul>
 *   <li>登录态持久化：状态目录存在 login.json 时重启免扫码，直接 monitor；</li>
 *   <li>断点续传：sync_buf 游标随 onBufUpdate 持久化，重启不漏消息；</li>
 *   <li>会话过期自愈：onSessionExpired 触发 relogin（删除登录态 → 重新扫码 → 重启 monitor）；</li>
 *   <li>多实例：1 账号 = 1 实例，channelKey = weixin:&lt;accountId&gt;；</li>
 *   <li>入站经 allow-from 白名单过滤后直推 EventBus（monitor 本身已异步，绕过 ChatChannel 队列）。</li>
 * </ul>
 * </p>
 */
@Slf4j
public class WeixinChannel extends ChatChannel {

    /**
     * 账号配置
     */
    private final WeixinProperties.Account account;

    /**
     * 账号状态存储（login.json / sync_buf）
     */
    private final WeixinStateStore stateStore;

    /**
     * openilink 消息转换器
     */
    private final WeixinMessageConverter converter;

    /**
     * 网关中央控制器，用于将入站消息直接发布到 EventBus
     */
    private final Gateway gateway;

    /**
     * openilink 客户端实例，生命周期与渠道一致
     */
    private volatile ILinkClient client;

    /**
     * CDN 媒体服务（随 client 重建而重建，relogin 后旧 client 的 token 已失效）
     */
    private volatile WeixinMediaService mediaService;

    /**
     * Monitor 独立线程执行器
     */
    private final ExecutorService monitorExecutor;

    /**
     * 登录/重登录生命周期执行器（与 monitor 线程隔离，避免在 monitor 回调内重登录造成阻塞）
     */
    private final ExecutorService lifecycleExecutor;

    /**
     * monitor 停止标志位；每次重启 monitor 时更换新实例
     */
    private volatile AtomicBoolean stopFlag = new AtomicBoolean(false);

    /**
     * monitor 线程引用，用于在线状态判断与 relogin 等待
     */
    private volatile Thread monitorThread;

    /**
     * 登录成功后的 botId（来自 login.json 或扫码结果）
     */
    private volatile String botId;

    /**
     * 等待手机确认期间最新的二维码 URL，其余时间为 null
     */
    private volatile String pendingQrUrl;

    /**
     * 最近一次入站消息时间
     */
    private volatile Instant lastInboundAt;

    /**
     * 最近一次入站消息的发送者（管理端点发媒体默认收件人）
     */
    private volatile String lastInboundUserId;

    public WeixinChannel(WeixinProperties.Account account, WeixinStateStore stateStore,
                         Gateway gateway, WeixinMessageConverter converter) {
        this.account = account;
        this.stateStore = stateStore;
        this.converter = converter;
        this.gateway = gateway;
        this.monitorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "weixin-monitor-" + account.getAccountId());
            t.setDaemon(true);
            return t;
        });
        this.lifecycleExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "weixin-lifecycle-" + account.getAccountId());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String getChannelType() {
        return "weixin";
    }

    @Override
    public String getChannelKey() {
        return "weixin:" + account.getAccountId();
    }

    /**
     * 启动微信渠道
     * <p>
     * 整个"登录 → monitor"流程在独立生命周期线程中异步执行（扫码最长 8 分钟，不得阻塞 Spring 启动）。
     * </p>
     */
    @Override
    public void startup() {
        log.info("[WeixinChannel] 账号 {} 启动中, stateDir={}", account.getAccountId(), stateStore.getStateDir());
        lifecycleExecutor.submit(this::connectSafely);
    }

    /**
     * 建立连接：有持久化登录态则免扫码直连，否则走扫码登录
     */
    private void connectSafely() {
        try {
            WeixinStateStore.LoginState login = stateStore.loadLogin();
            if (login != null) {
                log.info("[WeixinChannel] 发现持久化登录态, botId={}, 跳过扫码直接监听", login.botId());
                client = createClient(login.botToken(), login.baseUrl());
                mediaService = createMediaService(client);
                botId = login.botId();
                startMonitor();
            } else {
                doQrLogin();
                startMonitor();
            }
        } catch (Exception e) {
            log.error("[WeixinChannel] 账号 {} 连接失败: {}", account.getAccountId(), e.getMessage(), e);
        }
    }

    /**
     * 扫码登录并持久化登录态
     */
    private void doQrLogin() {
        client = createClient(account.getToken(), account.getBaseUrl());

        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String url) {
                pendingQrUrl = url;
                log.info("[WeixinChannel] 请使用微信扫码登录(账号 {}): {}", account.getAccountId(), url);
            }

            @Override
            public void onScanned() {
                log.info("[WeixinChannel] 已扫码，等待手机端确认...");
            }

            @Override
            public void onExpired(int attempt, int max) {
                log.warn("[WeixinChannel] 登录二维码已过期，正在自动刷新... ({}/{})", attempt, max);
            }
        });
        pendingQrUrl = null;

        if (!result.isConnected()) {
            throw new RuntimeException("微信登录失败，无法建立连接: " + result.getMessage());
        }

        botId = result.getBotId();
        mediaService = createMediaService(client);
        stateStore.saveLogin(new WeixinStateStore.LoginState(
                result.getBotToken(), result.getBotId(), result.getBaseUrl(), result.getUserId()));
        log.info("[WeixinChannel] 微信登录成功, account={}, botId={}", account.getAccountId(), botId);
    }

    /**
     * 重新登录：停 monitor → 删登录态 → 重新扫码 → 重启 monitor
     * <p>同步执行；调用方负责异步化（如 onSessionExpired 回调提交到 lifecycleExecutor）。</p>
     */
    public synchronized void relogin() {
        log.info("[WeixinChannel] 账号 {} 开始重新登录...", account.getAccountId());
        stopMonitor();
        stateStore.deleteLogin();
        try {
            doQrLogin();
            startMonitor();
        } catch (Exception e) {
            log.error("[WeixinChannel] 账号 {} 重新登录失败: {}", account.getAccountId(), e.getMessage(), e);
        }
    }

    private void stopMonitor() {
        stopFlag.set(true);
        Thread t = monitorThread;
        if (t != null) {
            try {
                t.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 启动消息监听线程（断点续传 + 会话过期自愈）
     */
    private void startMonitor() {
        AtomicBoolean flag = new AtomicBoolean(false);
        stopFlag = flag;
        String initialBuf = stateStore.loadSyncBuf();

        MonitorOptions options = MonitorOptions.builder()
                .initialBuf(initialBuf)
                .onBufUpdate(stateStore::saveSyncBuf)
                .onError(e -> log.warn("[WeixinChannel] monitor 轮询错误(自动重试): {}", e.getMessage()))
                .onSessionExpired(() -> {
                    log.warn("[WeixinChannel] 会话已过期(errcode -14)，触发重新登录, account={}", account.getAccountId());
                    lifecycleExecutor.submit(this::relogin);
                })
                .build();

        monitorExecutor.submit(() -> {
            monitorThread = Thread.currentThread();
            log.info("[WeixinChannel] Monitor 线程已启动, account={}, 断点游标长度={}", account.getAccountId(), initialBuf.length());
            client.monitor(this::onInboundMessage, options, flag);
            log.info("[WeixinChannel] Monitor 线程已退出, account={}", account.getAccountId());
        });
    }

    /**
     * 入站消息处理：白名单过滤 → 文本/媒体拆分 → 媒体下载落盘 → 直推 EventBus
     * <p>
     * P3 多模态：图片下载后以 MediaPart（本地 file:// 路径）随消息进 LLM，
     * 文本中附带保存路径，agent 可自行调用 knowledgeAcquireFromFile 采集进知识库；
     * 文件/视频/语音落盘为附件并以文本说明形式进入对话。
     * </p>
     */
    private void onInboundMessage(WeixinMessage msg) {
        // 白名单过滤（私聊按 fromUserId；空名单不限制）
        if (!account.getAllowFrom().isEmpty() && !account.getAllowFrom().contains(msg.getFromUserId())) {
            log.info("[WeixinChannel] 消息来源不在白名单，已忽略: from={}", msg.getFromUserId());
            return;
        }

        String text = MessageHelper.extractText(msg);
        List<MediaPart> mediaParts = new ArrayList<>();
        StringBuilder notes = new StringBuilder();

        List<MessageItem> items = msg.getItemList() != null ? msg.getItemList() : List.of();
        for (MessageItem item : items) {
            if (item.getType() == null) {
                continue;
            }
            switch (item.getType()) {
                case IMAGE -> handleInboundImage(msg, item, mediaParts, notes);
                case FILE -> handleInboundFile(msg, item, notes);
                case VIDEO -> handleInboundVideo(msg, item, notes);
                case VOICE -> handleInboundVoice(msg, item, notes);
                default -> {
                    // TEXT 已由 extractText 提取，其余类型忽略
                }
            }
        }

        boolean hasText = text != null && !text.isEmpty();
        if (!hasText && notes.length() == 0) {
            log.debug("[WeixinChannel] 收到空消息，跳过: from={}", msg.getFromUserId());
            return;
        }

        String content = hasText ? text + notes : notes.substring(1);
        lastInboundAt = Instant.now();
        lastInboundUserId = msg.getFromUserId();
        log.info("[WeixinChannel] 收到消息, account={}, from={}, 文本长度={}, 媒体数={}",
                account.getAccountId(), msg.getFromUserId(), hasText ? text.length() : 0, mediaParts.size());

        ChatMessage chatMessage = converter.convert(msg);
        chatMessage.setContent(content);
        chatMessage.setMediaParts(mediaParts.isEmpty() ? null : mediaParts);
        gateway.onInboundMessage(chatMessage, getChannelType(), getChannelKey(), account.getDefaultVesselId());
    }

    /**
     * 入站图片：下载解密 → 落盘 media/ → 生成 MediaPart + 路径说明
     */
    private void handleInboundImage(WeixinMessage msg, MessageItem item, List<MediaPart> mediaParts, StringBuilder notes) {
        try {
            ImageItem image = item.getImageItem();
            CDNMedia media = image != null ? image.getMedia() : null;
            if (media == null) {
                notes.append("\n[图片] 无法获取媒体引用");
                return;
            }
            byte[] data = requireMediaService().downloadMedia(media);
            String ext = WeixinMediaService.sniffImageExtension(data);
            Path path = saveInboundMedia(msg.getMessageId(), ext, data);
            mediaParts.add(MediaPart.builder()
                    .type("image_url")
                    .mimeType(mimeOf(ext))
                    .url(path.toUri().toString())
                    .build());
            notes.append("\n[图片] 已保存到 ").append(path)
                    .append("（如需采集进知识库可调用 knowledgeAcquireFromFile）");
        } catch (Exception e) {
            log.warn("[WeixinChannel] 图片下载失败: {}", e.getMessage());
            notes.append("\n[图片] 下载失败: ").append(e.getMessage());
        }
    }

    /**
     * 入站文件：下载解密 → 按原始文件名落盘 → 文本说明
     */
    private void handleInboundFile(WeixinMessage msg, MessageItem item, StringBuilder notes) {
        try {
            FileItem file = item.getFileItem();
            CDNMedia media = file != null ? file.getMedia() : null;
            if (media == null) {
                notes.append("\n[文件] 无法获取媒体引用");
                return;
            }
            byte[] data = requireMediaService().downloadMedia(media);
            String fileName = file.getFileName() != null && !file.getFileName().isBlank()
                    ? file.getFileName() : "attachment.bin";
            Path path = saveInboundMedia(msg.getMessageId(), "-" + sanitizeFileName(fileName), data);
            notes.append("\n[文件] ").append(fileName).append(" 已保存到 ").append(path)
                    .append("（如需采集进知识库可调用 knowledgeAcquireFromFile）");
        } catch (Exception e) {
            log.warn("[WeixinChannel] 文件下载失败: {}", e.getMessage());
            notes.append("\n[文件] 下载失败: ").append(e.getMessage());
        }
    }

    /**
     * 入站视频：下载解密 → 落盘 → 文本说明（当前不做视频理解）
     */
    private void handleInboundVideo(WeixinMessage msg, MessageItem item, StringBuilder notes) {
        try {
            CDNMedia media = item.getVideoItem() != null ? item.getVideoItem().getMedia() : null;
            if (media == null) {
                notes.append("\n[视频] 无法获取媒体引用");
                return;
            }
            byte[] data = requireMediaService().downloadMedia(media);
            Path path = saveInboundMedia(msg.getMessageId(), ".mp4", data);
            notes.append("\n[视频] 已保存到 ").append(path).append("（暂不支持视频内容理解，已存档）");
        } catch (Exception e) {
            log.warn("[WeixinChannel] 视频下载失败: {}", e.getMessage());
            notes.append("\n[视频] 下载失败: ").append(e.getMessage());
        }
    }

    /**
     * 入站语音：有转写文本时按文本处理；否则下载 silk 存档
     */
    private void handleInboundVoice(WeixinMessage msg, MessageItem item, StringBuilder notes) {
        VoiceItem voice = item.getVoiceItem();
        if (voice != null && voice.getText() != null && !voice.getText().isBlank()) {
            notes.append("\n[语音转文字] ").append(voice.getText());
            return;
        }
        try {
            CDNMedia media = voice != null ? voice.getMedia() : null;
            if (media == null) {
                notes.append("\n[语音] 无法获取媒体引用");
                return;
            }
            byte[] data = requireMediaService().downloadMedia(media);
            Path path = saveInboundMedia(msg.getMessageId(), ".silk", data);
            notes.append("\n[语音] 已保存到 ").append(path).append("（silk 格式，暂不支持解码）");
        } catch (Exception e) {
            log.warn("[WeixinChannel] 语音下载失败: {}", e.getMessage());
            notes.append("\n[语音] 下载失败: ").append(e.getMessage());
        }
    }

    /**
     * 入站媒体落盘：.meta-claw/channels/weixin/<accountId>/media/<msgId><suffix>
     */
    private Path saveInboundMedia(Long messageId, String suffix, byte[] data) throws IOException {
        Path mediaDir = stateStore.getStateDir().resolve("media");
        Files.createDirectories(mediaDir);
        String base = messageId != null ? String.valueOf(messageId) : String.valueOf(System.currentTimeMillis());
        Path path = mediaDir.resolve(base + suffix);
        Files.write(path, data);
        return path;
    }

    private WeixinMediaService requireMediaService() {
        WeixinMediaService ms = mediaService;
        if (ms == null) {
            throw new IllegalStateException("媒体服务未初始化（client 未连接）");
        }
        return ms;
    }

    private static String mimeOf(String ext) {
        return switch (ext) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[/\\\\\\s]+", "_");
    }

    /**
     * 发送回复消息到微信
     * <p>
     * 纯文本回复直接 push；Reply 携带 mediaPath 时走出站媒体管线
     * （上传 CDN → 按类型发送媒体消息），文本内容作为说明单独先发一条。
     * </p>
     */
    @Override
    public void send(Reply reply, Context context) {
        ILinkClient c = client;
        if (c == null) {
            log.error("[WeixinChannel] 客户端未初始化，无法发送消息");
            return;
        }

        String userId = context.getReceiver();
        if (userId == null || userId.isEmpty()) {
            log.error("[WeixinChannel] 接收者用户 ID 为空，无法发送消息");
            return;
        }

        try {
            // 出站媒体：Reply 携带本地文件路径时上传 CDN 并发送媒体消息
            if (reply.getMediaPath() != null && !reply.getMediaPath().isBlank()) {
                sendMedia(userId, reply);
                return;
            }

            ReplyType type = reply.getType();
            String content = reply.getContent();

            switch (type) {
                case TEXT, ERROR, INFO -> c.push(userId, content);
                default -> {
                    log.warn("[WeixinChannel] 暂不支持的回复类型: {}，以文本方式兜底发送", type);
                    c.push(userId, content);
                }
            }

            log.info("[WeixinChannel] 回复已发送, account={}, to={}", account.getAccountId(), userId);
        } catch (Exception e) {
            log.error("[WeixinChannel] 发送微信消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送微信消息失败", e);
        }
    }

    /**
     * 出站媒体发送：文本说明先行 → 上传 CDN → 按类型发媒体消息；失败回退为文本说明
     */
    private void sendMedia(String userId, Reply reply) {
        String caption = reply.getContent();
        try {
            Path path = Path.of(reply.getMediaPath());
            byte[] data = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            UploadMediaType uploadType = resolveUploadMediaType(reply.getMediaType(), fileName);

            if (caption != null && !caption.isBlank()) {
                client.push(userId, caption);
            }

            WeixinMediaService.UploadResult uploaded = requireMediaService().uploadFile(data, userId, uploadType);
            switch (uploadType) {
                case IMAGE -> requireMediaService().sendImage(userId, uploaded);
                case VIDEO -> requireMediaService().sendVideo(userId, uploaded);
                default -> requireMediaService().sendFileAttachment(userId, fileName, uploaded);
            }
            log.info("[WeixinChannel] 媒体回复已发送, account={}, to={}, type={}, file={}",
                    account.getAccountId(), userId, uploadType, fileName);
        } catch (Exception e) {
            log.error("[WeixinChannel] 媒体发送失败，回退文本说明: {}", e.getMessage(), e);
            client.push(userId, (caption != null && !caption.isBlank() ? caption + "\n" : "")
                    + "[媒体发送失败: " + reply.getMediaPath() + "]");
        }
    }

    /**
     * 解析出站媒体类型：优先 Reply.mediaType 显式指定，否则按扩展名推断
     */
    private static UploadMediaType resolveUploadMediaType(String mediaType, String fileName) {
        if (mediaType != null && !mediaType.isBlank()) {
            return UploadMediaType.valueOf(mediaType.toUpperCase(Locale.ROOT));
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) {
            return UploadMediaType.IMAGE;
        }
        if (lower.matches(".*\\.(mp4|mov|m4v|avi)$")) {
            return UploadMediaType.VIDEO;
        }
        return UploadMediaType.FILE;
    }

    @Override
    public void stop() {
        log.info("[WeixinChannel] 账号 {} 停止中...", account.getAccountId());
        stopFlag.set(true);
        monitorExecutor.shutdownNow();
        lifecycleExecutor.shutdownNow();
        super.stop();
        log.info("[WeixinChannel] 账号 {} 已停止", account.getAccountId());
    }

    /**
     * 创建 openilink 客户端（抽成 protected 便于测试替换）
     */
    protected ILinkClient createClient(String token, String baseUrl) {
        ILinkClient.Builder builder = ILinkClient.builder();
        if (token != null && !token.isBlank()) {
            builder.token(token);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /**
     * 创建 CDN 媒体服务（抽成 protected 便于测试替换）
     */
    protected WeixinMediaService createMediaService(ILinkClient client) {
        return new WeixinMediaService(client);
    }

    // ========== 管理端点状态访问器 ==========

    public String getAccountId() {
        return account.getAccountId();
    }

    /**
     * 是否在线（monitor 线程存活）
     */
    public boolean isOnline() {
        Thread t = monitorThread;
        return t != null && t.isAlive();
    }

    public String getBotId() {
        return botId;
    }

    /**
     * 等待手机确认期间的二维码 URL，其余时间为 null
     */
    public String getPendingQrUrl() {
        return pendingQrUrl;
    }

    public Instant getLastInboundAt() {
        return lastInboundAt;
    }

    /**
     * 最近一次入站消息的发送者 userId，未收到过消息时为 null
     */
    public String getLastInboundUserId() {
        return lastInboundUserId;
    }
}
