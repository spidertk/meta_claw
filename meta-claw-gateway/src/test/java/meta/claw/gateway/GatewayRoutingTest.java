package meta.claw.gateway;

import com.google.common.eventbus.Subscribe;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.events.VesselResponseReady;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.gateway.channel.Channel;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.channel.ChannelVesselRouter;
import meta.claw.gateway.channel.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gateway 路由行为测试：/vessel 命令拦截、vesselId hint 注入、channelKey 回复路由
 */
class GatewayRoutingTest {

    @TempDir
    Path tempDir;

    private EventBusWrapper eventBus;
    private ChannelRegistry registry;
    private ChannelVesselRouter router;
    private Gateway gateway;
    private FakeChannel fakeChannel;

    private final List<UserMessageReceived> userMessages = new CopyOnWriteArrayList<>();
    private final List<VesselResponseReady> responses = new CopyOnWriteArrayList<>();

    /**
     * 测试用假渠道：记录收到的回复，channelKey = weixin:main
     */
    static class FakeChannel implements Channel {
        final List<Reply> sent = new CopyOnWriteArrayList<>();

        @Override
        public String getChannelType() {
            return "weixin";
        }

        @Override
        public String getChannelKey() {
            return "weixin:main";
        }

        @Override
        public void startup() {
        }

        @Override
        public void handleText(ChatMessage msg) {
        }

        @Override
        public void send(Reply reply, Context context) {
            sent.add(reply);
        }
    }

    /**
     * 事件收集器
     */
    class Collector {
        @Subscribe
        public void onUserMessage(UserMessageReceived event) {
            userMessages.add(event);
        }

        @Subscribe
        public void onResponse(VesselResponseReady event) {
            responses.add(event);
        }
    }

    @BeforeEach
    void setUp() {
        eventBus = new EventBusWrapper();
        registry = new ChannelRegistry();
        router = new ChannelVesselRouter(tempDir.resolve("routes.json"));
        gateway = new Gateway(registry, eventBus, router);
        fakeChannel = new FakeChannel();
        registry.register(fakeChannel);
        eventBus.register(new Collector());
    }

    private ChatMessage textMessage(String content) {
        return ChatMessage.builder()
                .msgId("1")
                .contentType("TEXT")
                .content(content)
                .fromUserId("user1")
                .toUserId("bot")
                .otherUserId("user1")
                .build();
    }

    private void await(java.util.function.Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
    }

    @Test
    void vesselCommandBindsRouteAndRepliesDirectly() throws Exception {
        gateway.onInboundMessage(textMessage("/vessel alibaba"), "weixin", "weixin:main", "defaultV");

        // 命令不进入 Agent 流程
        await(() -> !responses.isEmpty());
        Thread.sleep(100);
        assertTrue(userMessages.isEmpty(), "/vessel 命令不应产生 UserMessageReceived");

        // 直接回复 INFO 且经 channelKey 路由到正确渠道实例
        assertEquals(1, responses.size());
        assertEquals(ReplyType.INFO, responses.get(0).getReply().getType());
        assertTrue(responses.get(0).getReply().getContent().contains("alibaba"));
        await(() -> !fakeChannel.sent.isEmpty());
        assertEquals(1, fakeChannel.sent.size());

        // 路由表已绑定
        assertEquals("alibaba", router.resolve("weixin:main", "user1", "defaultV"));
    }

    @Test
    void normalMessageInjectsVesselIdHintFromDefault() throws Exception {
        gateway.onInboundMessage(textMessage("你好"), "weixin", "weixin:main", "defaultV");

        await(() -> !userMessages.isEmpty());
        assertEquals(1, userMessages.size());
        Context context = userMessages.get(0).getContext();
        assertEquals("defaultV", context.getKwargs().get("vesselId"));
        assertEquals("weixin:main", context.getChannelKey());
    }

    @Test
    void boundRouteOverridesAccountDefault() throws Exception {
        router.bind("weixin:main", "user1", "boundV");
        gateway.onInboundMessage(textMessage("你好"), "weixin", "weixin:main", "defaultV");

        await(() -> !userMessages.isEmpty());
        assertEquals("boundV", userMessages.get(0).getContext().getKwargs().get("vesselId"));
    }

    @Test
    void responseFallsBackToChannelTypeWhenNoChannelKey() throws Exception {
        // 注册一个以渠道类型为键的单实例渠道
        Channel typeKeyed = new Channel() {
            final List<Reply> sent = new CopyOnWriteArrayList<>();

            @Override
            public String getChannelType() {
                return "cli";
            }

            @Override
            public void startup() {
            }

            @Override
            public void handleText(ChatMessage msg) {
            }

            @Override
            public void send(Reply reply, Context context) {
                sent.add(reply);
            }
        };
        registry.register(typeKeyed);

        Context context = new Context();
        context.setChannelType("cli");
        eventBus.post(new VesselResponseReady("cli", new Reply(ReplyType.TEXT, "ok"), context));

        await(() -> !((List<?>) getSent(typeKeyed)).isEmpty());
        assertNotNull(registry.get("cli"));
    }

    @SuppressWarnings("unchecked")
    private Object getSent(Channel channel) {
        try {
            var field = channel.getClass().getDeclaredField("sent");
            field.setAccessible(true);
            return field.get(channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
