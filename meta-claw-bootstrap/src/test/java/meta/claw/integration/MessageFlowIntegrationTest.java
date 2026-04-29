package meta.claw.integration;

import com.google.common.eventbus.Subscribe;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.ExpertResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.runtime.AgentLoop;
import meta.claw.runtime.ExpertManager;
import meta.claw.runtime.ExpertRuntime;
import meta.claw.runtime.model.ExpertConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * EventBus 消息流闭环集成测试
 * <p>
 * 验证 Meta-Claw 系统中从用户消息接收到 Expert 回复就绪的完整异步事件流：
 * 1. Gateway 发布 UserMessageReceived 事件
 * 2. AgentLoop 订阅并处理用户消息
 * 3. AgentLoop 调用 ExpertRuntime 生成回复
 * 4. AgentLoop 发布 ExpertResponseReady 事件
 * 5. Gateway 与测试订阅者同时收到回复就绪事件
 * </p>
 */
class MessageFlowIntegrationTest {

    /** 事件总线封装实例，所有组件通过它进行异步通信 */
    private EventBusWrapper eventBus;

    /** 渠道注册表，管理所有对外消息渠道的映射 */
    private ChannelRegistry channelRegistry;

    /** 网关控制器，负责消息的入站处理和回复路由 */
    private Gateway gateway;

    /** Expert 管理器，提供 Expert 配置与运行时实例 */
    private ExpertManager expertManager;

    /** Agent 事件循环处理器，订阅用户消息并调度 Expert */
    private AgentLoop agentLoop;

    /** 异步等待锁，用于在异步事件流中同步等待结果 */
    private CountDownLatch latch;

    /** 捕获到的 ExpertResponseReady 事件，用于断言验证 */
    private ExpertResponseReady capturedEvent;

    /**
     * 每个测试方法执行前的初始化操作
     * <p>
     * 构建完整的组件链路：EventBus → Gateway + AgentLoop，
     * 并用 Mockito 模拟 ExpertManager 和 ExpertRuntime，
     * 避免在纯单元测试环境中初始化真实的 Spring AI ChatModel。
     * </p>
     */
    @BeforeEach
    void setUp() {
        // 初始化异步事件总线
        eventBus = new EventBusWrapper();

        // 初始化渠道注册表与网关，网关会自动注册为 EventBus 订阅者
        channelRegistry = new ChannelRegistry();
        gateway = new Gateway(channelRegistry, eventBus);

        // 使用 Mockito 模拟 ExpertManager，控制可用 Expert 列表
        expertManager = mock(ExpertManager.class);
        ExpertConfig testConfig = new ExpertConfig();
        testConfig.setId("test-expert");
        testConfig.setName("测试专家");
        when(expertManager.listAvailableExperts()).thenReturn(Collections.singletonList(testConfig));

        // 使用 Mockito 模拟 ExpertRuntime，使其返回预设的测试回复
        ExpertRuntime mockRuntime = mock(ExpertRuntime.class);
        when(mockRuntime.chat(anyString())).thenReturn(new Reply(ReplyType.TEXT, "集成测试回复内容"));
        when(expertManager.getRuntime("test-expert")).thenReturn(mockRuntime);

        // 初始化 AgentLoop，它会自动注册为 EventBus 订阅者
        agentLoop = new AgentLoop(eventBus, expertManager);

        // 初始化异步等待锁与事件捕获变量
        latch = new CountDownLatch(1);
        capturedEvent = null;

        // 注册测试订阅者，用于捕获 ExpertResponseReady 事件并释放等待锁
        eventBus.register(new TestResponseSubscriber());
    }

    /**
     * 测试：验证完整的 EventBus 消息流闭环
     * <p>
     * 场景：模拟用户从 "wechat" 渠道发送一条文本消息，
     * 期望 AgentLoop 调度 Expert 处理后，通过 EventBus 发布 ExpertResponseReady 事件，
     * 且事件中携带的 channelType 与原始消息一致。
     * </p>
     *
     * @throws InterruptedException 当等待锁中断时抛出
     */
    @Test
    void testMessageFlowLoop() throws InterruptedException {
        // 构造用户消息上下文
        String sessionId = "session-test-001";
        String channelType = "wechat";
        String userContent = "你好，请回复一条测试消息";
        Context context = new Context(ContextType.TEXT, userContent);

        // 发布用户消息接收事件，触发 AgentLoop 处理流程
        eventBus.post(new UserMessageReceived(context, sessionId, channelType));

        // 等待异步事件处理完成，最多等待 5 秒
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // 断言一：必须在超时前收到 ExpertResponseReady 事件
        assertTrue(received, "应在 5 秒内收到 ExpertResponseReady 事件");

        // 断言二：捕获的事件对象不能为 null
        assertNotNull(capturedEvent, "捕获的事件不应为 null");

        // 断言三：channelType 必须正确传递，确保回复能路由到正确的渠道
        assertEquals(channelType, capturedEvent.getChannelType(),
                "ExpertResponseReady 中的 channelType 应与原始消息一致");

        // 断言四：回复类型应为 TEXT，因为 mockRuntime 返回的是文本回复
        assertEquals(ReplyType.TEXT, capturedEvent.getReply().getType(),
                "回复类型应为 TEXT");

        // 断言五：回复内容应与 mockRuntime 预设的返回值一致
        assertEquals("集成测试回复内容", capturedEvent.getReply().getContent(),
                "回复内容应与 ExpertRuntime 生成的回复一致");
    }

    /**
     * 测试：验证 EventBus 的基础发布/订阅机制
     * <p>
     * 场景：直接发布 ExpertResponseReady 事件，不经过 AgentLoop，
     * 验证测试订阅者能否正确接收并解析事件中的 channelType 与 Reply。
     * </p>
     *
     * @throws InterruptedException 当等待锁中断时抛出
     */
    @Test
    void testEventBusPubSub() throws InterruptedException {
        // 构造测试用的回复对象与上下文
        String channelType = "slack";
        Reply reply = new Reply(ReplyType.INFO, "系统通知：测试事件");
        Context context = new Context(ContextType.TEXT, "触发内容");

        // 直接发布 ExpertResponseReady 事件
        eventBus.post(new ExpertResponseReady(channelType, reply, context));

        // 等待异步事件分发完成
        boolean received = latch.await(3, TimeUnit.SECONDS);

        // 断言：必须在超时前收到事件
        assertTrue(received, "应在 3 秒内收到 ExpertResponseReady 事件");
        assertNotNull(capturedEvent, "捕获的事件不应为 null");
        assertEquals(channelType, capturedEvent.getChannelType(),
                "channelType 应正确传递");
        assertEquals(ReplyType.INFO, capturedEvent.getReply().getType(),
                "回复类型应为 INFO");
        assertEquals("系统通知：测试事件", capturedEvent.getReply().getContent(),
                "回复内容应一致");
    }

    /**
     * 测试：当系统中没有可用 Expert 时，AgentLoop 应发布 ERROR 类型回复
     * <p>
     * 场景：使用独立的事件总线，模拟空的 ExpertManager，发布用户消息后，
     * 验证 AgentLoop 能否优雅降级并发布 ERROR 回复事件。
     * </p>
     *
     * @throws InterruptedException 当等待锁中断时抛出
     */
    @Test
    void testMessageFlowWithNoExpert() throws InterruptedException {
        // 创建独立的事件总线，避免与 setUp 中的 AgentLoop 产生干扰
        EventBusWrapper isolatedEventBus = new EventBusWrapper();

        // 初始化一个空的 ExpertManager（不返回任何可用 Expert）
        ExpertManager emptyManager = mock(ExpertManager.class);
        when(emptyManager.listAvailableExperts()).thenReturn(Collections.emptyList());

        // 创建 AgentLoop，绑定到空的 ExpertManager，自动注册为订阅者
        AgentLoop emptyAgentLoop = new AgentLoop(isolatedEventBus, emptyManager);

        // 初始化独立的等待锁与事件捕获变量
        CountDownLatch noExpertLatch = new CountDownLatch(1);
        ExpertResponseReady[] noExpertCaptured = new ExpertResponseReady[1];

        // 注册测试订阅者到独立事件总线
        isolatedEventBus.register(new Object() {
            @Subscribe
            public void onExpertResponseReady(ExpertResponseReady event) {
                noExpertCaptured[0] = event;
                noExpertLatch.countDown();
            }
        });

        // 发布用户消息事件
        Context context = new Context(ContextType.TEXT, "没有 Expert 时的测试消息");
        isolatedEventBus.post(new UserMessageReceived(context, "session-no-expert", "douyin"));

        // 等待异步处理
        boolean received = noExpertLatch.await(3, TimeUnit.SECONDS);

        // 断言：即使没有 Expert，也应在超时前收到 ERROR 回复事件
        assertTrue(received, "即使没有 Expert，也应在 3 秒内收到回复事件");
        assertNotNull(noExpertCaptured[0], "捕获的事件不应为 null");
        assertEquals("douyin", noExpertCaptured[0].getChannelType(),
                "channelType 应正确传递");
        assertEquals(ReplyType.ERROR, noExpertCaptured[0].getReply().getType(),
                "当没有可用 Expert 时，回复类型应为 ERROR");
    }

    /**
     * 内部测试订阅者类
     * <p>
     * 使用 Guava EventBus 的 @Subscribe 注解订阅 ExpertResponseReady 事件，
     * 当事件到达时将事件对象保存到 capturedEvent，并释放 CountDownLatch。
     * </p>
     */
    private class TestResponseSubscriber {

        /**
         * 订阅 ExpertResponseReady 事件的处理方法
         *
         * @param event Expert 回复就绪领域事件
         */
        @Subscribe
        public void onExpertResponseReady(ExpertResponseReady event) {
            capturedEvent = event;
            latch.countDown();
        }
    }
}
