package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiReasoningContentModuleTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new OpenAiReasoningContentModule());

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldPatchEmptyReasoningContentForToolCallMessage() throws Exception {
        OpenAiApi.ChatCompletionMessage.ToolCall toolCall = new OpenAiApi.ChatCompletionMessage.ToolCall(
                "call_1",
                "function",
                new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("foo", "{}"));

        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "hello",
                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                null,
                null,
                List.of(toolCall),
                null,
                null,
                null,
                null);

        String json = mapper.writeValueAsString(message);

        assertTrue(json.contains("\"reasoning_content\":\"\""));
    }

    @Test
    void shouldRestoreRealReasoningContentFromContext() throws Exception {
        OpenAiReasoningContentContext.push("这是真实的 reasoning 内容");

        OpenAiApi.ChatCompletionMessage.ToolCall toolCall = new OpenAiApi.ChatCompletionMessage.ToolCall(
                "call_1",
                "function",
                new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("foo", "{}"));

        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "hello",
                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                null,
                null,
                List.of(toolCall),
                null,
                null,
                null,
                null);

        String json = mapper.writeValueAsString(message);

        assertTrue(json.contains("\"reasoning_content\":\"这是真实的 reasoning 内容\""));
    }

    @Test
    void shouldNotPatchNonAssistantMessage() throws Exception {
        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "user message",
                OpenAiApi.ChatCompletionMessage.Role.USER);

        String json = mapper.writeValueAsString(message);

        assertFalse(json.contains("reasoning_content"));
    }
}
