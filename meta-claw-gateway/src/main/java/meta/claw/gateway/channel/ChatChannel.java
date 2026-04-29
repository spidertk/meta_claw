package meta.claw.gateway.channel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.core.model.Reply;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ChatChannel 抽象类
 * 封装了与具体消息渠道无关的通用聊天处理逻辑，包括上下文构造、消息生产消费、
 * 回复生成/装饰/发送以及带重试的消息投递。
 * 所有具体渠道（如微信、钉钉、Slack 等）继承此类后只需实现渠道特有的发送与启动逻辑。
 */
@Slf4j
public abstract class ChatChannel implements Channel {

    /**
     * 会话级消息队列映射，key 为 sessionId，value 为对应会话的队列包装
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final ConcurrentHashMap<String, SessionQueue> sessions = new ConcurrentHashMap<>();

    /**
     * 处理消息的线程池，固定 8 个线程，用于并发处理不同 session 的消息
     */
    private final ExecutorService handlerPool = Executors.newFixedThreadPool(8);

    /**
     * 消费者单线程执行器，负责轮询各 session 队列并将消息提交到 handlerPool
     */
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();

    /**
     * 消费者循环开关标志，用于优雅停止
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 构造方法，启动消费者线程
     */
    public ChatChannel() {
        consumerExecutor.submit(this::consume);
    }

    /**
     * 构造上下文对象
     * 设置渠道类型、会话 ID、接收者、群聊标记，并将原始消息放入 kwargs 中
     *
     * @param type    消息上下文类型
     * @param content 消息内容
     * @param msg     原始聊天消息对象
     * @return 构造好的 Context 对象
     */
    protected Context composeContext(ContextType type, String content, ChatMessage msg) {
        Context context = new Context(type, content);
        context.setChannelType(getChannelType());
        context.setGroup(msg.isGroup());

        String otherUserId = msg.getOtherUserId();
        context.setSessionId(otherUserId);
        context.setReceiver(otherUserId);

        context.getKwargs().put("msg", msg);

        log.debug("[ChatChannel] 构造上下文完成, sessionId={}, isGroup={}", otherUserId, msg.isGroup());
        return context;
    }

    /**
     * 处理消息上下文
     * 执行流程：生成回复 -> 装饰回复 -> 发送回复
     *
     * @param context 当前消息上下文
     */
    protected void handle(Context context) {
        if (context == null) {
            log.debug("[ChatChannel] context 为空，跳过处理");
            return;
        }

        log.debug("[ChatChannel] 开始处理消息, sessionId={}", context.getSessionId());

        Reply reply = generateReply(context);
        if (reply == null || reply.getContent() == null) {
            log.debug("[ChatChannel] generateReply 返回空，跳过后续流程");
            return;
        }

        reply = decorateReply(context, reply);
        if (reply != null && reply.getContent() != null) {
            sendReply(context, reply);
        }
    }

    /**
     * 生成回复
     * P1 阶段占位实现，直接返回 null，由 EventBus / AgentLoop 负责后续处理
     *
     * @param context 当前消息上下文
     * @return 当前阶段固定返回 null
     */
    protected Reply generateReply(Context context) {
        return null;
    }

    /**
     * 装饰回复内容
     * 群聊场景下在回复文本前添加 @实际发送者 前缀
     *
     * @param context 当前消息上下文
     * @param reply   原始回复对象
     * @return 装饰后的回复对象
     */
    protected Reply decorateReply(Context context, Reply reply) {
        if (reply == null || reply.getContent() == null) {
            return reply;
        }

        if (context.isGroup()) {
            Object msgObj = context.getKwargs().get("msg");
            if (msgObj instanceof ChatMessage) {
                ChatMessage msg = (ChatMessage) msgObj;
                if (msg.getActualUserNickname() != null && !msg.getActualUserNickname().isEmpty()) {
                    String decorated = "@" + msg.getActualUserNickname() + "\n" + reply.getContent();
                    reply.setContent(decorated);
                    log.debug("[ChatChannel] 群聊回复已添加 @前缀");
                }
            }
        }

        return reply;
    }

    /**
     * 发送回复
     * 调用带重试机制的内部发送方法
     *
     * @param context 当前消息上下文
     * @param reply   待发送的回复对象
     */
    protected void sendReply(Context context, Reply reply) {
        _send(reply, context, 0);
    }

    /**
     * 内部发送方法，支持失败重试与指数退避
     * 最大重试 3 次，退避间隔依次为 3s、6s、9s
     *
     * @param reply    待发送的回复对象
     * @param context  当前消息上下文
     * @param retryCnt 当前已重试次数
     */
    private void _send(Reply reply, Context context, int retryCnt) {
        try {
            send(reply, context);
            log.debug("[ChatChannel] 消息发送成功, sessionId={}, retryCnt={}", context.getSessionId(), retryCnt);
        } catch (Exception e) {
            log.error("[ChatChannel] 消息发送失败: {}", e.getMessage());
            if (retryCnt < 3) {
                int backoffSeconds = 3 * (retryCnt + 1);
                log.warn("[ChatChannel] 将在 {} 秒后进行第 {} 次重试", backoffSeconds, retryCnt + 1);
                try {
                    Thread.sleep(backoffSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[ChatChannel] 重试等待被中断");
                    return;
                }
                _send(reply, context, retryCnt + 1);
            } else {
                log.error("[ChatChannel] 消息发送最终失败，已重试 3 次");
            }
        }
    }

    /**
     * 生产者方法
     * 根据上下文中的 sessionId 将消息放入对应会话的队列，若该会话队列不存在则自动创建
     *
     * @param context 当前消息上下文
     */
    protected void produce(Context context) {
        if (context == null || context.getSessionId() == null) {
            log.warn("[ChatChannel] produce 失败，context 或 sessionId 为空");
            return;
        }

        String sessionId = context.getSessionId();
        SessionQueue sessionQueue = sessions.computeIfAbsent(sessionId, k -> new SessionQueue());
        sessionQueue.getQueue().offer(context);
        log.debug("[ChatChannel] 消息已入队, sessionId={}", sessionId);
    }

    /**
     * 消费者循环
     * 独立线程运行，轮询各 session 队列，取出消息后提交到 handlerPool 处理
     * 通过信号量保证同一 session 同时仅处理一条消息
     */
    private void consume() {
        while (running.get()) {
            try {
                for (Map.Entry<String, SessionQueue> entry : sessions.entrySet()) {
                    String sessionId = entry.getKey();
                    SessionQueue sessionQueue = entry.getValue();

                    // 非阻塞尝试获取信号量，保证 session 级串行处理
                    if (sessionQueue.getSemaphore().tryAcquire()) {
                        Context context = sessionQueue.getQueue().poll();
                        if (context != null) {
                            handlerPool.submit(() -> {
                                try {
                                    handle(context);
                                } catch (Exception e) {
                                    log.error("[ChatChannel] 处理消息异常, sessionId={}", sessionId, e);
                                } finally {
                                    sessionQueue.getSemaphore().release();
                                }
                            });
                        } else {
                            // 队列为空，释放信号量
                            sessionQueue.getSemaphore().release();
                        }
                    }
                }

                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[ChatChannel] 消费者线程被中断，即将退出");
                break;
            } catch (Exception e) {
                log.error("[ChatChannel] 消费者循环发生异常", e);
            }
        }
    }

    /**
     * 优雅关闭
     * 停止消费者循环，关闭消费者线程执行器和消息处理线程池
     */
    public void shutdown() {
        log.info("[ChatChannel] 开始执行 shutdown...");
        running.set(false);

        consumerExecutor.shutdownNow();
        handlerPool.shutdown();
        try {
            if (!handlerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                handlerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            handlerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[ChatChannel] shutdown 完成");
    }

    /**
     * 会话队列内部类
     * 封装 LinkedBlockingQueue 用于存储上下文，以及 Semaphore 用于控制并发
     */
    protected static class SessionQueue {
        private final LinkedBlockingQueue<Context> queue = new LinkedBlockingQueue<>();
        private final Semaphore semaphore = new Semaphore(1);

        public LinkedBlockingQueue<Context> getQueue() {
            return queue;
        }

        public Semaphore getSemaphore() {
            return semaphore;
        }
    }

    @Override
    public void handleText(ChatMessage msg) {
        Context context = composeContext(ContextType.TEXT, msg.getContent(), msg);
        if (context != null) {
            produce(context);
        }
    }

    @Override
    public void stop() {
        shutdown();
    }
}
