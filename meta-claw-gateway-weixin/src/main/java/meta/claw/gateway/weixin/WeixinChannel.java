package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.Message;
import com.openilink.model.response.LoginResult;
import com.openilink.util.MessageHelper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatChannel;
import meta.claw.gateway.channel.ChatMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 微信渠道实现类
 * <p>
 * 基于 openilink SDK 实现与微信生态（公众号/个人号/企业微信）的对接。
 * 通过扫码登录建立长连接，在独立线程中监听消息，并将收到的文本消息
 * 直接通过 {@link Gateway#onInboundMessage(ChatMessage, String)} 送入事件总线，
 * 绕过 ChatChannel 内部的生产/消费队列（因 openilink monitor 本身已是异步回调模式）。
 * </p>
 */
@Slf4j
public class WeixinChannel extends ChatChannel {

    /**
     * 微信渠道配置参数
     */
    private final WeixinConfig config;

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
    private ILinkClient client;

    /**
     * Monitor 独立线程执行器，负责在后台运行 client.monitor()
     */
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();

    /**
     * 停止标志位，传入 monitor 以控制其优雅退出
     */
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    /**
     * 构造微信渠道实例
     *
     * @param config  微信配置对象，包含 token 等连接参数
     * @param gateway 网关控制器，用于入站消息直推 EventBus
     */
    public WeixinChannel(WeixinConfig config, Gateway gateway) {
        this.config = config;
        this.converter = new WeixinMessageConverter();
        this.gateway = gateway;
    }

    /**
     * 获取渠道类型标识
     *
     * @return 固定返回 "weixin"
     */
    @Override
    public String getChannelType() {
        return "weixin";
    }

    /**
     * 启动微信渠道
     * <p>
     * 执行流程：
     * 1. 使用配置 token 构建 ILinkClient；
     * 2. 调用扫码登录，在回调中打印二维码状态日志；
     * 3. 登录成功后启动 monitor 线程监听消息。
     * </p>
     *
     * @throws Exception 当登录失败或客户端初始化异常时抛出
     */
    @Override
    public void startup() throws Exception {
        log.info("[WeixinChannel] 开始初始化 openilink 客户端...");

        // 构建 ILinkClient，token 为必填认证参数
        client = ILinkClient.builder()
                .token(config.getToken())
                .build();

        // 执行扫码登录，并通过回调接收登录状态事件
        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String url) {
                log.info("[WeixinChannel] 请使用微信扫码登录: {}", url);
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

        // 校验登录结果，未成功则抛出异常阻止后续启动
        if (!result.isConnected()) {
            throw new RuntimeException("微信登录失败，无法建立连接");
        }

        log.info("[WeixinChannel] 微信登录成功, botId={}", result.getBotId());

        // 登录成功后启动消息监听器
        startMonitor();
    }

    /**
     * 启动消息监听线程
     * <p>
     * 在独立后台线程中调用 {@link ILinkClient#monitor}，持续接收微信消息。
     * 当收到文本消息时，先通过 {@link WeixinMessageConverter} 转换为 {@link ChatMessage}，
     * 再调用 {@link Gateway#onInboundMessage(ChatMessage, String)} 将消息直推 EventBus，
     * 从而 bypass ChatChannel 的内部队列，避免双重异步带来的时序问题。
     * </p>
     */
    private void startMonitor() {
        monitorExecutor.submit(() -> {
            log.info("[WeixinChannel] Monitor 线程已启动，开始监听微信消息...");

            // 调用 openilink 的 monitor 方法，传入消息处理器、选项（null 表示默认）和停止标志
            client.monitor((Message msg) -> {
                // 提取消息中的纯文本内容
                String text = MessageHelper.extractText(msg);
                if (text == null || text.isEmpty()) {
                    log.debug("[WeixinChannel] 收到空文本消息，跳过处理");
                    return;
                }

                log.info("[WeixinChannel] 收到文本消息 from {}: {}", msg.getFromUserId(), text);

                // 将原始 Message 转换为内部 ChatMessage
                ChatMessage chatMessage = converter.convert(msg);

                // 直接通过 Gateway 进入 EventBus 流程，不经过 ChatChannel 的 produce/consume 队列
                gateway.onInboundMessage(chatMessage, getChannelType());
            }, null, stopFlag);

            log.info("[WeixinChannel] Monitor 线程已退出");
        });
    }

    /**
     * 发送回复消息到微信
     * <p>
     * 根据 {@link Reply} 的类型决定发送策略：
     * <ul>
     *   <li>TEXT / ERROR / INFO：直接调用 client.push 发送纯文本；</li>
     *   <li>其他类型（IMAGE、VOICE、FILE 等）：当前 P1 阶段暂以文本方式兜底发送，并记录警告。</li>
     * </ul>
     *
     * @param reply   系统生成的回复对象
     * @param context 当前消息上下文，包含会话 ID、接收者等信息
     */
    @Override
    public void send(Reply reply, Context context) {
        if (client == null) {
            log.error("[WeixinChannel] 客户端未初始化，无法发送消息");
            return;
        }

        // 从上下文中获取目标用户 ID
        String userId = context.getReceiver();
        if (userId == null || userId.isEmpty()) {
            log.error("[WeixinChannel] 接收者用户 ID 为空，无法发送消息");
            return;
        }

        try {
            ReplyType type = reply.getType();
            String content = reply.getContent();

            switch (type) {
                case TEXT:
                case ERROR:
                case INFO:
                    // 文本类回复直接推送
                    client.push(userId, content);
                    break;
                default:
                    // 非文本类型在 P1 阶段先以文本方式兜底，避免消息丢失
                    log.warn("[WeixinChannel] 暂不支持的回复类型: {}，以文本方式兜底发送", type);
                    client.push(userId, content);
                    break;
            }

            log.info("[WeixinChannel] 回复已发送 to {}: {}", userId, content);
        } catch (Exception e) {
            log.error("[WeixinChannel] 发送微信消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送微信消息失败", e);
        }
    }

    /**
     * 停止微信渠道
     * <p>
     * 执行顺序：
     * 1. 设置停止标志位，通知 monitor 线程优雅退出；
     * 2. 关闭 monitor 独立线程执行器；
     * 3. 调用父类 {@link ChatChannel#stop()} 关闭内部消费者线程池。
     * </p>
     */
    @Override
    public void stop() {
        log.info("[WeixinChannel] 开始执行停止流程...");

        // 通知 monitor 循环退出
        stopFlag.set(true);

        // 关闭 monitor 线程执行器
        monitorExecutor.shutdownNow();

        // 调用父类 stop，关闭 ChatChannel 的消费者与处理线程池
        super.stop();

        log.info("[WeixinChannel] 停止流程执行完毕");
    }
}
