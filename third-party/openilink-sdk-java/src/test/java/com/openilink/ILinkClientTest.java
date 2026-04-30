package com.openilink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openilink.exception.ILinkException;
import com.openilink.exception.NoContextTokenException;
import com.openilink.http.HttpDoer;
import com.openilink.model.*;
import com.openilink.model.request.SendMessageReq;
import com.openilink.model.response.GetConfigResp;
import com.openilink.model.response.GetUpdatesResp;
import com.openilink.model.response.GetUploadURLResp;
import com.openilink.model.response.QRCodeResponse;
import com.openilink.model.response.QRStatusResponse;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MessageHandler;
import com.openilink.monitor.MonitorOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ILinkClientTest {

    @Mock
    private HttpDoer mockHttpDoer;

    private ILinkClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        client = ILinkClient.builder()
                .token("test-token")
                .baseUrl("https://test.example.com")
                .httpClient(mockHttpDoer)
                .build();
        objectMapper = new ObjectMapper();
    }

    // --- Builder / create tests ---

    @Test
    void builder_defaultValues() {
        ILinkClient defaultClient = ILinkClient.builder().build();
        assertEquals(ILinkClient.DEFAULT_BASE_URL, defaultClient.getBaseUrl());
        assertEquals("", defaultClient.getToken());
    }

    @Test
    void create_setsToken() {
        ILinkClient c = ILinkClient.builder().token("my-token").build();
        assertEquals("my-token", c.getToken());
    }

    @Test
    void setToken_updatesToken() {
        client.setToken("new-token");
        assertEquals("new-token", client.getToken());
    }

    @Test
    void setBaseUrl_updatesBaseUrl() {
        client.setBaseUrl("https://new.example.com");
        assertEquals("https://new.example.com", client.getBaseUrl());
    }

    // --- Context token cache ---

    @Test
    void contextToken_setAndGet() {
        client.setContextToken("user1", "ctx-token-1");
        assertTrue(client.getContextToken("user1").isPresent());
        assertEquals("ctx-token-1", client.getContextToken("user1").get());
    }

    @Test
    void contextToken_missing() {
        assertFalse(client.getContextToken("unknown").isPresent());
    }

    // --- push ---

    @Test
    void push_noContextToken_throws() {
        assertThrows(NoContextTokenException.class, () -> client.push("user1", "hello"));
    }

    @Test
    void push_withContextToken_sendsMessage() throws IOException {
        client.setContextToken("user1", "ctx-token");
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn("{}".getBytes());

        String clientId = client.push("user1", "hello");
        assertNotNull(clientId);
        assertTrue(clientId.startsWith("sdk-"));
        verify(mockHttpDoer).doPost(contains("sendmessage"), any(byte[].class), anyMap(), anyLong());
    }

    // --- getUpdates ---

    @Test
    void getUpdates_success() throws IOException {
        GetUpdatesResp expected = GetUpdatesResp.builder()
                .ret(0)
                .getUpdatesBuf("new-buf")
                .msgs(Collections.singletonList(
                        WeixinMessage.builder()
                                .fromUserId("user1")
                                .contextToken("ctx-1")
                                .itemList(Collections.singletonList(
                                        MessageItem.builder()
                                                .type(MessageItemType.TEXT)
                                                .textItem(TextItem.builder().text("hi").build())
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn(objectMapper.writeValueAsBytes(expected));

        GetUpdatesResp resp = client.getUpdates("old-buf");
        assertEquals(0, resp.getRet());
        assertEquals("new-buf", resp.getGetUpdatesBuf());
        assertEquals(1, resp.getMsgs().size());
    }

    @Test
    void getUpdates_timeout_returnsEmptyResponse() throws IOException {
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenThrow(new IOException("timeout"));

        GetUpdatesResp resp = client.getUpdates("buf");
        assertEquals(0, resp.getRet());
        assertEquals("buf", resp.getGetUpdatesBuf());
    }

    // --- sendText ---

    @Test
    void sendText_sendsCorrectPayload() throws IOException {
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn("{}".getBytes());

        String clientId = client.sendText("user1", "hello", "ctx-token");
        assertNotNull(clientId);
        assertTrue(clientId.startsWith("sdk-"));
        verify(mockHttpDoer).doPost(contains("sendmessage"), any(byte[].class), anyMap(), anyLong());
    }

    @Test
    void sendText_httpError_throwsILinkException() throws IOException {
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenThrow(new IOException("connection refused"));

        assertThrows(ILinkException.class, () -> client.sendText("user1", "hello", "ctx"));
    }

    // --- getConfig ---

    @Test
    void getConfig_success() throws IOException {
        GetConfigResp expected = GetConfigResp.builder()
                .ret(0)
                .typingTicket("ticket-123")
                .build();
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn(objectMapper.writeValueAsBytes(expected));

        GetConfigResp resp = client.getConfig("user1", "ctx");
        assertEquals("ticket-123", resp.getTypingTicket());
    }

    // --- sendTyping ---

    @Test
    void sendTyping_success() throws IOException {
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn("{}".getBytes());

        assertDoesNotThrow(() -> client.sendTyping("user1", "ticket", TypingStatus.TYPING));
        verify(mockHttpDoer).doPost(contains("sendtyping"), any(byte[].class), anyMap(), anyLong());
    }

    // --- getUploadUrl ---

    @Test
    void getUploadUrl_success() throws IOException {
        GetUploadURLResp expected = GetUploadURLResp.builder()
                .uploadParam("param-123")
                .build();
        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenReturn(objectMapper.writeValueAsBytes(expected));

        com.openilink.model.request.GetUploadURLReq req =
                com.openilink.model.request.GetUploadURLReq.builder()
                        .fileKey("key-1")
                        .mediaType(1)
                        .build();

        GetUploadURLResp resp = client.getUploadUrl(req);
        assertEquals("param-123", resp.getUploadParam());
    }

    // --- fetchQRCode ---

    @Test
    void fetchQRCode_success() throws IOException {
        QRCodeResponse expected = QRCodeResponse.builder()
                .qrCode("qr-code-123")
                .qrCodeImgContent("https://img.example.com/qr.png")
                .build();
        when(mockHttpDoer.doGet(contains("get_bot_qrcode"), any(), anyLong()))
                .thenReturn(objectMapper.writeValueAsBytes(expected));

        QRCodeResponse resp = client.fetchQRCode();
        assertEquals("qr-code-123", resp.getQrCode());
    }

    // --- pollQRStatus ---

    @Test
    void pollQRStatus_confirmed() throws IOException {
        QRStatusResponse expected = QRStatusResponse.builder()
                .status("confirmed")
                .botToken("bot-token-123")
                .ilinkBotId("bot-id-456")
                .build();
        when(mockHttpDoer.doGet(contains("get_qrcode_status"), anyMap(), anyLong()))
                .thenReturn(objectMapper.writeValueAsBytes(expected));

        QRStatusResponse resp = client.pollQRStatus("qr-code");
        assertEquals("confirmed", resp.getStatus());
        assertEquals("bot-token-123", resp.getBotToken());
    }

    @Test
    void pollQRStatus_timeout_returnsWait() throws IOException {
        when(mockHttpDoer.doGet(anyString(), anyMap(), anyLong()))
                .thenThrow(new IOException("timeout"));

        QRStatusResponse resp = client.pollQRStatus("qr-code");
        assertEquals("wait", resp.getStatus());
    }

    // --- monitor ---

    @Test
    void monitor_dispatchesMessages() throws IOException {
        List<WeixinMessage> messages = new ArrayList<>();
        WeixinMessage msg1 = WeixinMessage.builder()
                .fromUserId("user1")
                .contextToken("ctx-1")
                .itemList(Collections.singletonList(
                        MessageItem.builder()
                                .type(MessageItemType.TEXT)
                                .textItem(TextItem.builder().text("hello").build())
                                .build()
                ))
                .build();

        GetUpdatesResp resp = GetUpdatesResp.builder()
                .ret(0)
                .getUpdatesBuf("new-buf")
                .msgs(Collections.singletonList(msg1))
                .build();

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        AtomicReference<String> savedBuf = new AtomicReference<>();

        when(mockHttpDoer.doPost(anyString(), any(byte[].class), anyMap(), anyLong()))
                .thenAnswer(inv -> {
                    stopFlag.set(true);
                    return objectMapper.writeValueAsBytes(resp);
                });

        MonitorOptions options = MonitorOptions.builder()
                .initialBuf("")
                .onBufUpdate(savedBuf::set)
                .build();

        client.monitor(messages::add, options, stopFlag);

        assertEquals(1, messages.size());
        assertEquals("user1", messages.get(0).getFromUserId());
        assertEquals("new-buf", savedBuf.get());
        // Context token should be cached
        assertTrue(client.getContextToken("user1").isPresent());
        assertEquals("ctx-1", client.getContextToken("user1").get());
    }

    @Test
    void monitor_stopsOnStopFlag() throws IOException {
        AtomicBoolean stopFlag = new AtomicBoolean(true);

        client.monitor(msg -> {}, null, stopFlag);
        // Should return immediately without calling httpDoer
        verify(mockHttpDoer, never()).doPost(anyString(), any(byte[].class), anyMap(), anyLong());
    }
}
