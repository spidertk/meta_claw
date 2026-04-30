package com.openilink.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ModelSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void weixinMessage_serializeDeserialize() throws Exception {
        WeixinMessage msg = WeixinMessage.builder()
                .fromUserId("user1")
                .toUserId("bot1")
                .messageType(MessageType.USER)
                .messageState(MessageState.FINISH)
                .contextToken("ctx-123")
                .itemList(Collections.singletonList(
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("hello").build())
                                .build()
                ))
                .build();

        String json = objectMapper.writeValueAsString(msg);
        assertTrue(json.contains("\"from_user_id\":\"user1\""));
        assertTrue(json.contains("\"message_type\":1"));
        assertTrue(json.contains("\"context_token\":\"ctx-123\""));

        WeixinMessage deserialized = objectMapper.readValue(json, WeixinMessage.class);
        assertEquals("user1", deserialized.getFromUserId());
        assertEquals(MessageType.USER, deserialized.getMessageType());
        assertEquals("ctx-123", deserialized.getContextToken());
        assertEquals(1, deserialized.getItemList().size());
        assertEquals("hello", deserialized.getItemList().get(0).getTextItem().getText());
    }

    @Test
    void messageType_serializesToInt() throws Exception {
        assertEquals("1", objectMapper.writeValueAsString(MessageType.USER));
        assertEquals("2", objectMapper.writeValueAsString(MessageType.BOT));
    }

    @Test
    void messageState_serializesToInt() throws Exception {
        assertEquals("0", objectMapper.writeValueAsString(MessageState.NEW));
        assertEquals("2", objectMapper.writeValueAsString(MessageState.FINISH));
    }

    @Test
    void messageItemType_serializesToInt() throws Exception {
        assertEquals("1", objectMapper.writeValueAsString(MessageItemType.TEXT));
        assertEquals("2", objectMapper.writeValueAsString(MessageItemType.IMAGE));
    }

    @Test
    void typingStatus_serializesToInt() throws Exception {
        assertEquals("1", objectMapper.writeValueAsString(TypingStatus.TYPING));
        assertEquals("2", objectMapper.writeValueAsString(TypingStatus.CANCEL_TYPING));
    }

    @Test
    void baseInfo_serializeDeserialize() throws Exception {
        BaseInfo info = BaseInfo.builder().channelVersion("1.0.0").build();
        String json = objectMapper.writeValueAsString(info);
        assertTrue(json.contains("\"channel_version\":\"1.0.0\""));

        BaseInfo deserialized = objectMapper.readValue(json, BaseInfo.class);
        assertEquals("1.0.0", deserialized.getChannelVersion());
    }

    @Test
    void cdnMedia_serializeDeserialize() throws Exception {
        CDNMedia media = CDNMedia.builder()
                .encryptQueryParam("param1")
                .aesKey("key1")
                .encryptType(1)
                .build();

        String json = objectMapper.writeValueAsString(media);
        CDNMedia deserialized = objectMapper.readValue(json, CDNMedia.class);

        assertEquals("param1", deserialized.getEncryptQueryParam());
        assertEquals("key1", deserialized.getAesKey());
        assertEquals(1, deserialized.getEncryptType());
    }

    @Test
    void imageItem_serializeDeserialize() throws Exception {
        ImageItem item = ImageItem.builder()
                .url("https://img.example.com/1.jpg")
                .thumbHeight(100)
                .thumbWidth(200)
                .build();

        String json = objectMapper.writeValueAsString(item);
        ImageItem deserialized = objectMapper.readValue(json, ImageItem.class);

        assertEquals("https://img.example.com/1.jpg", deserialized.getUrl());
        assertEquals(100, deserialized.getThumbHeight());
    }

    @Test
    void fileItem_serializeDeserialize() throws Exception {
        FileItem item = FileItem.builder()
                .fileName("test.pdf")
                .md5("abc123")
                .len("1024")
                .build();

        String json = objectMapper.writeValueAsString(item);
        FileItem deserialized = objectMapper.readValue(json, FileItem.class);

        assertEquals("test.pdf", deserialized.getFileName());
    }

    @Test
    void videoItem_serializeDeserialize() throws Exception {
        VideoItem item = VideoItem.builder()
                .videoSize(1024L)
                .playLength(60)
                .videoMd5("md5hash")
                .build();

        String json = objectMapper.writeValueAsString(item);
        VideoItem deserialized = objectMapper.readValue(json, VideoItem.class);

        assertEquals(1024L, deserialized.getVideoSize());
    }

    @Test
    void voiceItem_serializeDeserialize() throws Exception {
        VoiceItem item = VoiceItem.builder()
                .encodeType(1)
                .sampleRate(16000)
                .playTime(10)
                .text("语音文字")
                .build();

        String json = objectMapper.writeValueAsString(item);
        VoiceItem deserialized = objectMapper.readValue(json, VoiceItem.class);

        assertEquals(16000, deserialized.getSampleRate());
        assertEquals("语音文字", deserialized.getText());
    }

    @Test
    void refMessage_serializeDeserialize() throws Exception {
        RefMessage ref = RefMessage.builder()
                .title("原消息")
                .messageItem(MessageItem.builder()
                        .type(MessageItemType.TEXT)
                        .textItem(TextItem.builder().text("引用内容").build())
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(ref);
        RefMessage deserialized = objectMapper.readValue(json, RefMessage.class);

        assertEquals("原消息", deserialized.getTitle());
        assertEquals("引用内容", deserialized.getMessageItem().getTextItem().getText());
    }

    @Test
    void deserialize_unknownFields_ignored() throws Exception {
        String json = "{\"from_user_id\":\"user1\",\"unknown_field\":\"value\"}";
        WeixinMessage msg = objectMapper.readValue(json, WeixinMessage.class);
        assertEquals("user1", msg.getFromUserId());
    }
}
