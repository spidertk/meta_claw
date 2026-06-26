package meta.claw.core.llm.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiReasoningContentAdvisorTest {

    private final OpenAiReasoningContentAdvisor advisor = new OpenAiReasoningContentAdvisor(0);

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldExtractReasoningContentFromAssistantMessages() {
        List<org.springframework.ai.chat.messages.Message> messages = List.of(
                new UserMessage("hi"),
                AssistantMessage.builder()
                        .content("thinking...")
                        .properties(Map.of("reasoningContent", "real reasoning"))
                        .build(),
                new UserMessage("ok")
        );
        ChatClientRequest request = new ChatClientRequest(new Prompt(messages), Map.of());

        advisor.before(request, null);

        // Advisor 只 push assistant 消息，因此队列中只有一个值
        assertEquals("real reasoning", OpenAiReasoningContentContext.poll());
        assertNull(OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldPushEmptyStringForAssistantWithoutReasoning() {
        List<org.springframework.ai.chat.messages.Message> messages = List.of(
                AssistantMessage.builder().content("hello").build()
        );
        ChatClientRequest request = new ChatClientRequest(new Prompt(messages), Map.of());

        advisor.before(request, null);

        assertEquals("", OpenAiReasoningContentContext.poll());
        assertNull(OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldClearContextBeforeEachRequest() {
        OpenAiReasoningContentContext.push("stale");
        ChatClientRequest request = new ChatClientRequest(
                new Prompt(List.of(AssistantMessage.builder().content("hi").build())), Map.of());

        advisor.before(request, null);

        // 旧值已被 clear，新的 assistant 消息没有 reasoningContent，因此 poll 为空字符串
        assertEquals("", OpenAiReasoningContentContext.poll());
        assertNull(OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldRemoveContextAfterResponse() {
        OpenAiReasoningContentContext.push("value");
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(AssistantMessage.builder().content("ok").build())))
                .build();
        ChatClientResponse response = new ChatClientResponse(chatResponse, Map.of());

        advisor.after(response, null);

        assertTrue(OpenAiReasoningContentContext.isEmpty());
    }
}
