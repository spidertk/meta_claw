package meta.claw.core.runtime.engine;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiMessageConverterTest {

    @Test
    void convertsSystemMessage() {
        Message message = SpiMessageConverter.toSpringMessage(SpiMessage.system("You are a coder."));

        assertInstanceOf(SystemMessage.class, message);
        assertEquals("You are a coder.", ((SystemMessage) message).getText());
    }

    @Test
    void convertsUserMessage() {
        Message message = SpiMessageConverter.toSpringMessage(SpiMessage.user("hello"));

        assertInstanceOf(UserMessage.class, message);
        assertEquals("hello", ((UserMessage) message).getText());
    }

    @Test
    void convertsAssistantMessageWithoutToolCalls() {
        Message message = SpiMessageConverter.toSpringMessage(SpiMessage.assistant("hi there"));

        assertInstanceOf(AssistantMessage.class, message);
        AssistantMessage am = (AssistantMessage) message;
        assertEquals("hi there", am.getText());
        assertTrue(am.getToolCalls() == null || am.getToolCalls().isEmpty());
    }

    @Test
    void convertsAssistantMessageWithToolCalls() {
        SpiToolCall toolCall = SpiToolCall.builder()
                .id("call_1")
                .name("search")
                .arguments(Map.of("query", "spring ai"))
                .build();

        Message message = SpiMessageConverter.toSpringMessage(
                SpiMessage.assistant("Let me search.", List.of(toolCall)));

        assertInstanceOf(AssistantMessage.class, message);
        AssistantMessage am = (AssistantMessage) message;
        assertEquals(1, am.getToolCalls().size());
        AssistantMessage.ToolCall tc = am.getToolCalls().get(0);
        assertEquals("call_1", tc.id());
        assertEquals("search", tc.name());
        assertEquals("function", tc.type());
        assertTrue(tc.arguments().contains("spring ai"));
    }

    @Test
    void convertsToolMessage() {
        String toolResultJson = "{\"toolCallId\":\"call_1\",\"toolName\":\"search\",\"result\":\"found\"}";
        Message message = SpiMessageConverter.toSpringMessage(SpiMessage.tool(toolResultJson));

        assertInstanceOf(ToolResponseMessage.class, message);
        ToolResponseMessage trm = (ToolResponseMessage) message;
        assertEquals(1, trm.getResponses().size());
        ToolResponseMessage.ToolResponse response = trm.getResponses().get(0);
        assertEquals("call_1", response.id());
        assertEquals("search", response.name());
        assertEquals("found", response.responseData());
    }

    @Test
    void convertsToolMessageWithFallbackWhenJsonInvalid() {
        Message message = SpiMessageConverter.toSpringMessage(SpiMessage.tool("raw result"));

        assertInstanceOf(ToolResponseMessage.class, message);
        ToolResponseMessage trm = (ToolResponseMessage) message;
        assertEquals(1, trm.getResponses().size());
        assertEquals("unknown", trm.getResponses().get(0).id());
        assertEquals("raw result", trm.getResponses().get(0).responseData());
    }

    @Test
    void convertsMultipleMessages() {
        List<Message> messages = SpiMessageConverter.toSpringMessages(List.of(
                SpiMessage.system("sys"),
                SpiMessage.user("hi"),
                SpiMessage.assistant("reply")));

        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(AssistantMessage.class, messages.get(2));
    }

    @Test
    void unknownRoleFallsBackToUserMessage() {
        SpiMessage unknown = SpiMessage.builder().role("unknown").content("?").build();
        Message message = SpiMessageConverter.toSpringMessage(unknown);

        assertInstanceOf(UserMessage.class, message);
        assertEquals("?", ((UserMessage) message).getText());
    }

    @Test
    void convertsLocalFileMediaToByteArray(@TempDir Path tempDir) throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        Path imagePath = tempDir.resolve("test.png");
        Files.write(imagePath, imageBytes);

        MediaPart part = MediaPart.builder()
                .type("image_url")
                .mimeType("image/png")
                .url(imagePath.toUri().toString())
                .build();
        Message message = SpiMessageConverter.toSpringMessage(
                SpiMessage.user("describe", List.of(part)));

        UserMessage userMessage = assertInstanceOf(UserMessage.class, message);
        assertEquals(1, userMessage.getMedia().size());
        Media media = userMessage.getMedia().get(0);
        // Moonshot/Kimi vision API 只接受 base64 data URI；本地文件必须读成 byte[]，
        // 由 Spring AI 序列化为 data:<mime>;base64,<...>
        assertInstanceOf(byte[].class, media.getData());
        assertArrayEquals(imageBytes, (byte[]) media.getData());
        assertEquals("image/png", media.getMimeType().toString());
    }

    @Test
    void keepsRemoteMediaUrlAsIs() {
        MediaPart part = MediaPart.builder()
                .type("image_url")
                .mimeType("image/png")
                .url("https://example.com/test.png")
                .build();
        Message message = SpiMessageConverter.toSpringMessage(
                SpiMessage.user("describe", List.of(part)));

        UserMessage userMessage = assertInstanceOf(UserMessage.class, message);
        assertEquals(1, userMessage.getMedia().size());
        Media media = userMessage.getMedia().get(0);
        assertEquals("https://example.com/test.png", media.getData().toString());
    }
}
