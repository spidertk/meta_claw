package org.springframework.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningAwareOpenAiChatModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ReasoningAwareOpenAiChatModel createChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("test-key")
                .baseUrl("https://api.example.com")
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("test-model")
                .build();
        return new ReasoningAwareOpenAiChatModel(openAiApi, options, objectMapper);
    }

    private Prompt createPrompt(List<org.springframework.ai.chat.messages.Message> messages) {
        return new Prompt(messages, OpenAiChatOptions.builder().model("test-model").build());
    }

    @Test
    void shouldPatchReasoningContentForAssistantToolCallMessage() {
        ReasoningAwareOpenAiChatModel chatModel = createChatModel();

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .properties(Map.of("reasoningContent", "这是真实的 reasoning"))
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "calculate", "{}")))
                .build();

        Prompt prompt = createPrompt(List.of(
                new UserMessage("1+1=?"),
                assistantMessage
        ));

        OpenAiApi.ChatCompletionRequest request = chatModel.createRequest(prompt, true);

        List<OpenAiApi.ChatCompletionMessage> messages = request.messages();
        assertEquals(2, messages.size());
        OpenAiApi.ChatCompletionMessage patched = messages.get(1);
        assertEquals("assistant", patched.role().name().toLowerCase());
        assertEquals("这是真实的 reasoning", patched.reasoningContent());
    }

    @Test
    void shouldNotPatchWhenNoReasoningContent() {
        ReasoningAwareOpenAiChatModel chatModel = createChatModel();

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "calculate", "{}")))
                .build();

        Prompt prompt = createPrompt(List.of(assistantMessage));

        OpenAiApi.ChatCompletionRequest request = chatModel.createRequest(prompt, true);

        OpenAiApi.ChatCompletionMessage patched = request.messages().get(0);
        assertTrue(patched.reasoningContent() == null || patched.reasoningContent().isEmpty());
    }
}
