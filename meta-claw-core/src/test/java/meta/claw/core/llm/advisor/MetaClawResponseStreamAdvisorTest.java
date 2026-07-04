package meta.claw.core.llm.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link MetaClawResponseStreamAdvisor} 能正确累积分段到达的流式 tool calls，
 * 并在 Spring AI 返回大写 finishReason ("TOOL_CALLS") 时仍然触发 callback.onToolCall()。
 */
class MetaClawResponseStreamAdvisorTest {

    @Test
    void accumulatesAndNotifiesToolCallsWhenFinishReasonIsUpperCase() {
        MetaClawResponseStreamAdvisor advisor = new MetaClawResponseStreamAdvisor(null, new ObjectMapper());
        ChatClient chatClient = ChatClient.builder(new StubChatModel())
                .defaultAdvisors(advisor)
                .build();

        MetaClawCallContext ctx = new MetaClawCallContext("v1", "s1");
        AtomicReference<SpiToolCall> captured = new AtomicReference<>();
        SpiStreamingCallback callback = new SpiStreamingCallback() {
            @Override public void onStart() { }
            @Override public void onChunk(String chunk) { }
            @Override public void onReasoningChunk(String chunk) { }
            @Override public void onToolCall(SpiToolCall toolCall) { captured.set(toolCall); }
            @Override public void onUsage(SpiUsage usage) { }
            @Override public void onComplete(SpiChatResponse response) { }
            @Override public void onError(Throwable error) { }
        };
        ctx.setStreamingCallback(callback);

        chatClient.prompt("1+1")
                .advisors(spec -> spec.param(MetaClawCallContext.CONTEXT_KEY, ctx))
                .stream()
                .chatResponse()
                .blockLast();

        assertNotNull(captured.get(), "callback.onToolCall should be invoked");
        assertEquals("call_1", captured.get().getId());
        assertEquals("calculator", captured.get().getName());
        assertEquals(Map.of("expression", "1+1"), captured.get().getArguments());

        assertNotNull(ctx.getToolCalls());
        assertEquals(1, ctx.getToolCalls().size());
        assertEquals("call_1", ctx.getToolCalls().get(0).getId());
    }

    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return ChatResponse.builder().build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                    responseWithToolCall(new AssistantMessage.ToolCall("call_1", "function", "calculator", "{")),
                    responseWithToolCall(new AssistantMessage.ToolCall("call_1", "function", "calculator", "\"expression\":\"1+1\"}")),
                    responseWithFinishReason("TOOL_CALLS")
            );
        }

        private ChatResponse responseWithToolCall(AssistantMessage.ToolCall tc) {
            AssistantMessage message = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(tc))
                    .build();
            Generation generation = new Generation(message, ChatGenerationMetadata.builder().build());
            return ChatResponse.builder().generations(List.of(generation)).build();
        }

        private ChatResponse responseWithFinishReason(String finishReason) {
            AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of()).build();
            Generation generation = new Generation(message,
                    ChatGenerationMetadata.builder().finishReason(finishReason).build());
            return ChatResponse.builder().generations(List.of(generation)).build();
        }
    }
}
