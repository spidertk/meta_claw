package meta.claw.core.runtime;

import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.resolver.RuntimeConfigResolver;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 LlmClientManager.streamWithTools() 能正确累积分段到达的流式 tool calls，
 * 并在 Spring AI 返回大写 finishReason ("TOOL_CALLS") 时仍然触发 callback.onToolCall()。
 */
class LlmClientManagerStreamWithToolsTest {

    @Test
    void accumulatesAndNotifiesToolCallsWhenFinishReasonIsUpperCase() {
        LlmClientManager manager = new LlmClientManager();

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setProvider("openai");
        providerConfig.setModel("moonshot");

        RuntimeConfig runtimeConfig = new RuntimeConfig();
        runtimeConfig.setProviderConfig(providerConfig);

        RuntimeConfigResolver resolver = mock(RuntimeConfigResolver.class);
        when(resolver.resolve("v1")).thenReturn(runtimeConfig);
        ReflectionTestUtils.setField(manager, "runtimeConfigResolver", resolver);

        LlmClientProviderManager providerManager = mock(LlmClientProviderManager.class);
        when(providerManager.createRaw(any(ProviderConfig.class)))
                .thenReturn(ChatClient.builder(new StubChatModel()).build());
        ReflectionTestUtils.setField(manager, "llmClientProviderManager", providerManager);

        ToolCallback toolCallback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("calculator");
        when(toolCallback.getToolDefinition()).thenReturn(definition);

        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId("v1")
                .sessionId("s1")
                .messages(List.of(SpiMessage.user("1+1")))
                .build();

        AtomicReference<SpiToolCall> captured = new AtomicReference<>();
        SpiChatResponse response = manager.streamWithTools(request, new ToolCallback[]{toolCallback},
                new SpiStreamingCallback() {
                    @Override public void onStart() { }
                    @Override public void onChunk(String chunk) { }
                    @Override public void onReasoningChunk(String chunk) { }
                    @Override public void onToolCall(SpiToolCall toolCall) { captured.set(toolCall); }
                    @Override public void onUsage(SpiUsage usage) { }
                    @Override public void onComplete(SpiChatResponse response) { }
                    @Override public void onError(Throwable error) { }
                });

        assertNotNull(captured.get(), "callback.onToolCall should be invoked");
        assertEquals("call_1", captured.get().getId());
        assertEquals("calculator", captured.get().getName());
        assertEquals(Map.of("expression", "1+1"), captured.get().getArguments());

        assertNotNull(response.toolCalls());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_1", response.toolCalls().get(0).getId());
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
