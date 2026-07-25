package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.model.CDNMedia;
import com.openilink.model.FileItem;
import com.openilink.model.ImageItem;
import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.TextItem;
import com.openilink.model.VoiceItem;
import com.openilink.model.WeixinMessage;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeixinChannel P3 多模态测试：入站图片/文件/语音处理、出站媒体发送
 */
class WeixinChannelMediaTest {

    @TempDir
    Path tempDir;

    private Gateway gateway;
    private ILinkClient client;
    private WeixinMediaService mediaService;
    private WeixinChannel channel;

    @BeforeEach
    void setUp() {
        gateway = mock(Gateway.class);
        client = mock(ILinkClient.class);
        mediaService = mock(WeixinMediaService.class);
        WeixinProperties.Account account = new WeixinProperties.Account();
        account.setAccountId("main");
        channel = new WeixinChannel(account, new WeixinStateStore(tempDir), gateway, new WeixinMessageConverter());
        ReflectionTestUtils.setField(channel, "client", client);
        ReflectionTestUtils.setField(channel, "mediaService", mediaService);
    }

    private static CDNMedia mediaRef() {
        return CDNMedia.builder().encryptQueryParam("enc").aesKey("a2V5").build();
    }

    private ChatMessage captureInbound() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(gateway).onInboundMessage(captor.capture(), eq("weixin"), eq("weixin:main"), any());
        return captor.getValue();
    }

    @Test
    void inboundImageDownloadsAndAttachesMediaPart() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        when(mediaService.downloadMedia(any())).thenReturn(png);

        WeixinMessage msg = WeixinMessage.builder()
                .messageId(42L)
                .fromUserId("user1")
                .itemList(List.of(
                        MessageItem.builder().type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("看看这张图").build()).build(),
                        MessageItem.builder().type(MessageItemType.IMAGE)
                                .imageItem(ImageItem.builder().media(mediaRef()).build()).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);

        ChatMessage captured = captureInbound();
        // 文本 + 路径说明
        assertTrue(captured.getContent().startsWith("看看这张图"));
        assertTrue(captured.getContent().contains("[图片] 已保存到"));
        assertTrue(captured.getContent().contains("knowledgeAcquireFromFile"));
        // MediaPart 指向本地文件
        assertNotNull(captured.getMediaParts());
        assertEquals(1, captured.getMediaParts().size());
        MediaPart part = captured.getMediaParts().get(0);
        assertEquals("image_url", part.getType());
        assertEquals("image/png", part.getMimeType());
        assertTrue(part.getUrl().startsWith("file:"));
        // 文件已按魔数扩展名落盘
        Path saved = tempDir.resolve("media/42.png");
        assertTrue(Files.exists(saved));
        assertEquals(png.length, Files.size(saved));
    }

    @Test
    void inboundImageOnlyMessageStillProcessed() throws Exception {
        when(mediaService.downloadMedia(any())).thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9});
        WeixinMessage msg = WeixinMessage.builder()
                .messageId(7L)
                .fromUserId("user1")
                .itemList(List.of(MessageItem.builder().type(MessageItemType.IMAGE)
                        .imageItem(ImageItem.builder().media(mediaRef()).build()).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);

        ChatMessage captured = captureInbound();
        assertTrue(captured.getContent().contains("[图片] 已保存到"));
        assertNotNull(captured.getMediaParts());
    }

    @Test
    void inboundFileSavedWithOriginalName() throws Exception {
        when(mediaService.downloadMedia(any())).thenReturn("pdf-content".getBytes());
        WeixinMessage msg = WeixinMessage.builder()
                .messageId(8L)
                .fromUserId("user1")
                .itemList(List.of(MessageItem.builder().type(MessageItemType.FILE)
                        .fileItem(FileItem.builder().media(mediaRef()).fileName("年度报告.pdf").build()).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);

        ChatMessage captured = captureInbound();
        assertTrue(captured.getContent().contains("[文件] 年度报告.pdf 已保存到"));
        assertNull(captured.getMediaParts(), "文件不走 LLM 多模态，仅文本说明");
        assertTrue(Files.exists(tempDir.resolve("media/8-年度报告.pdf")));
    }

    @Test
    void inboundVoiceWithTranscriptionBecomesText() throws Exception {
        WeixinMessage msg = WeixinMessage.builder()
                .messageId(9L)
                .fromUserId("user1")
                .itemList(List.of(MessageItem.builder().type(MessageItemType.VOICE)
                        .voiceItem(VoiceItem.builder().text("明天下午三点开会").build()).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);

        ChatMessage captured = captureInbound();
        assertTrue(captured.getContent().contains("[语音转文字] 明天下午三点开会"));
    }

    @Test
    void inboundImageDownloadFailureStillPassesText() throws Exception {
        when(mediaService.downloadMedia(any())).thenThrow(new RuntimeException("cdn down"));
        WeixinMessage msg = WeixinMessage.builder()
                .messageId(10L)
                .fromUserId("user1")
                .itemList(List.of(
                        MessageItem.builder().type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("图呢").build()).build(),
                        MessageItem.builder().type(MessageItemType.IMAGE)
                                .imageItem(ImageItem.builder().media(mediaRef()).build()).build()))
                .build();
        ReflectionTestUtils.invokeMethod(channel, "onInboundMessage", msg);

        ChatMessage captured = captureInbound();
        assertTrue(captured.getContent().contains("图呢"));
        assertTrue(captured.getContent().contains("[图片] 下载失败"));
        assertNull(captured.getMediaParts());
    }

    @Test
    void outboundImageReplyUploadsAndSendsImage() throws Exception {
        Path image = tempDir.resolve("out.png");
        Files.write(image, new byte[]{1, 2, 3});
        WeixinMediaService.UploadResult uploaded =
                new WeixinMediaService.UploadResult("fk", "dl", "0123456789abcdef0123456789abcdef", 3, 16);
        when(mediaService.uploadFile(any(), eq("user1"), eq(com.openilink.model.UploadMediaType.IMAGE)))
                .thenReturn(uploaded);

        Reply reply = new Reply(ReplyType.TEXT, "这是你要的图");
        reply.setMediaPath(image.toString());
        Context context = new Context();
        context.setReceiver("user1");
        channel.send(reply, context);

        // 说明文字先发，媒体消息后发
        verify(client).push("user1", "这是你要的图");
        verify(mediaService).sendImage("user1", uploaded);
    }

    @Test
    void outboundMediaFailureFallsBackToText() throws Exception {
        Path file = tempDir.resolve("out.bin");
        Files.write(file, new byte[]{1});
        when(mediaService.uploadFile(any(), anyString(), any())).thenThrow(new RuntimeException("upload failed"));

        Reply reply = new Reply(ReplyType.TEXT, "附件");
        reply.setMediaPath(file.toString());
        Context context = new Context();
        context.setReceiver("user1");
        channel.send(reply, context);

        verify(client).push(eq("user1"), org.mockito.ArgumentMatchers.contains("[媒体发送失败"));
        verify(mediaService, never()).sendFileAttachment(anyString(), anyString(), any());
    }
}
