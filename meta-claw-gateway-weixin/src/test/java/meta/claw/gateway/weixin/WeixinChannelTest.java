package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.TextItem;
import com.openilink.model.WeixinMessage;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * WeixinChannel 单元测试：send 文本推送、白名单过滤、channelKey、状态访问器
 */
class WeixinChannelTest {

    @TempDir
    Path tempDir;

    private Gateway gateway;
    private ILinkClient client;
    private WeixinChannel channel;
    private WeixinProperties.Account account;

    @BeforeEach
    void setUp() {
        gateway = mock(Gateway.class);
        client = mock(ILinkClient.class);
        account = new WeixinProperties.Account();
        account.setAccountId("main");
        account.setAllowFrom(List.of("allowed-user"));
        channel = new WeixinChannel(account, new WeixinStateStore(tempDir), gateway, new WeixinMessageConverter());
        ReflectionTestUtils.setField(channel, "client", client);
    }

    private Context contextOf(String receiver) {
        Context context = new Context();
        context.setReceiver(receiver);
        return context;
    }

    @Test
    void channelKeyFollowsAccountId() {
        assertEquals("weixin:main", channel.getChannelKey());
        assertEquals("weixin", channel.getChannelType());
    }

    @Test
    void sendTextPushesToUser() {
        channel.send(new Reply(ReplyType.TEXT, "你好"), contextOf("user1"));
        verify(client).push("user1", "你好");
    }

    @Test
    void sendInfoAndErrorAlsoPush() {
        channel.send(new Reply(ReplyType.INFO, "提示"), contextOf("user1"));
        channel.send(new Reply(ReplyType.ERROR, "错误"), contextOf("user1"));
        verify(client).push("user1", "提示");
        verify(client).push("user1", "错误");
    }

    @Test
    void sendWithoutReceiverDoesNothing() {
        channel.send(new Reply(ReplyType.TEXT, "hi"), contextOf(null));
        verify(client, never()).push(anyString(), anyString());
    }

    @Test
    void allowFromBlocksStrangers() {
        WeixinMessage msg = WeixinMessage.builder()
                .fromUserId("stranger")
                .itemList(List.of(MessageItem.builder()
                        .type(MessageItemType.TEXT)
                        .textItem(TextItem.builder().text("hi").build())
                        .build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);
        verify(gateway, never()).onInboundMessage(any(ChatMessage.class), anyString(), anyString(), ArgumentMatchers.<String>any());
    }

    @Test
    void allowFromAdmitsListedUser() {
        WeixinMessage msg = WeixinMessage.builder()
                .fromUserId("allowed-user")
                .itemList(List.of(MessageItem.builder()
                        .type(MessageItemType.TEXT)
                        .textItem(TextItem.builder().text("hi").build())
                        .build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);
        verify(gateway).onInboundMessage(any(ChatMessage.class), eq("weixin"), eq("weixin:main"), ArgumentMatchers.<String>isNull());
    }

    @Test
    void emptyAllowFromAdmitsAnyone() {
        account.setAllowFrom(List.of());
        WeixinMessage msg = WeixinMessage.builder()
                .fromUserId("anyone")
                .itemList(List.of(MessageItem.builder()
                        .type(MessageItemType.TEXT)
                        .textItem(TextItem.builder().text("hi").build())
                        .build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);
        verify(gateway).onInboundMessage(any(ChatMessage.class), eq("weixin"), eq("weixin:main"), ArgumentMatchers.<String>isNull());
    }

    @Test
    void nonTextMessageSkipped() {
        WeixinMessage msg = WeixinMessage.builder()
                .fromUserId("allowed-user")
                .itemList(List.of(MessageItem.builder().type(MessageItemType.IMAGE).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);
        verify(gateway, never()).onInboundMessage(any(ChatMessage.class), anyString(), anyString(), ArgumentMatchers.<String>any());
    }

    @Test
    void initialStatusIsOffline() {
        assertFalse(channel.isOnline());
        assertNull(channel.getPendingQrUrl());
        assertNull(channel.getBotId());
        assertNull(channel.getLastInboundAt());
        assertEquals("main", channel.getAccountId());
    }
}
