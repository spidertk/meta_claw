package meta.claw.core.runtime;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 验证一次性 chat 调用（无 TaskContext，如 KnowledgeAnalyzer/VisionDescriber）
 * 能从 LlmCallContext 组装出非空的 SpiChatResponse，而不是永远返回空 content。
 */
class LlmClientManagerChatResponseTest {

    private static TaskContext.LlmCallContext newCallContext() {
        TaskContext fallback = TaskContext.builder()
                .taskId("fallback")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage(null)
                .profile(null)
                .registry(null)
                .build();
        return fallback.beginCall(null);
    }

    @Test
    void buildsResponseFromCallContext() {
        LlmClientManager manager = new LlmClientManager();
        TaskContext.LlmCallContext ctx = newCallContext();
        ctx.setContent("[\"a\", \"b\"]");
        ctx.setReasoningContent("reasoning");
        ctx.setUsage(SpiUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build());
        ctx.setToolCalls(List.of(SpiToolCall.builder()
                .id("call_1").name("t").arguments(Map.of()).build()));

        SpiChatResponse response = ReflectionTestUtils.invokeMethod(
                manager, "buildResponseFromCallContext", ctx);

        assertEquals("[\"a\", \"b\"]", response.content());
        assertEquals("reasoning", response.reasoningContent());
        assertEquals(15, response.usage().totalTokens());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_1", response.toolCalls().get(0).getId());
    }

    @Test
    void buildsEmptySafeResponseWhenNothingExtracted() {
        LlmClientManager manager = new LlmClientManager();
        TaskContext.LlmCallContext ctx = newCallContext();

        SpiChatResponse response = ReflectionTestUtils.invokeMethod(
                manager, "buildResponseFromCallContext", ctx);

        assertEquals("", response.content());
        assertEquals(0, response.toolCalls().size());
    }

    @Test
    void convertsUserMessageWithLocalImageToMultimediaMessage(@TempDir Path tempDir) throws Exception {
        byte[] imageBytes = new byte[]{9, 8, 7};
        Path imagePath = tempDir.resolve("pic.png");
        Files.write(imagePath, imageBytes);

        LlmClientManager manager = new LlmClientManager();
        SpiMessage userMsg = SpiMessage.user("描述图片", List.of(MediaPart.builder()
                .type("image_url")
                .mimeType("image/png")
                .url(imagePath.toUri().toString())
                .build()));

        Message springMsg = (Message) ReflectionTestUtils.invokeMethod(manager, "toSpringMessage", userMsg);

        UserMessage userMessage = assertInstanceOf(UserMessage.class, springMsg);
        assertEquals(1, userMessage.getMedia().size());
        Media media = userMessage.getMedia().get(0);
        // 本地文件必须读成 byte[]，由 Spring AI 序列化为 base64 data URI（Moonshot 不支持 file:// URL）
        assertInstanceOf(byte[].class, media.getData());
        assertArrayEquals(imageBytes, (byte[]) media.getData());
    }
}
