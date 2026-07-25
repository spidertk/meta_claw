package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.WeixinMessage;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatChannel;
import meta.claw.gateway.channel.ChatMessage;

import java.time.Instant;
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
     * 入站消息处理：白名单过滤 → 转换 → 直推 EventBus
     */
    private void onInboundMessage(WeixinMessage msg) {
        // 白名单过滤（私聊按 fromUserId；空名单不限制）
        if (!account.getAllowFrom().isEmpty() && !account.getAllowFrom().contains(msg.getFromUserId())) {
            log.info("[WeixinChannel] 消息来源不在白名单，已忽略: from={}", msg.getFromUserId());
            return;
        }

        String text = MessageHelper.extractText(msg);
        if (text == null || text.isEmpty()) {
            log.debug("[WeixinChannel] 收到非文本消息，本期跳过(P3 多模态接管): from={}, groupId={}",
                    msg.getFromUserId(), msg.getGroupId());
            return;
        }

        lastInboundAt = Instant.now();
        log.info("[WeixinChannel] 收到文本消息, account={}, from={}: {}", account.getAccountId(), msg.getFromUserId(), text);

        ChatMessage chatMessage = converter.convert(msg);
        gateway.onInboundMessage(chatMessage, getChannelType(), getChannelKey(), account.getDefaultVesselId());
    }

    /**
     * 发送回复消息到微信（文本直发，媒体类型本期文本兜底）
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
}
