package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.spi.llm.*;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 0.8.0 ChatClient 的 LlmClient 实现。
 */
@Slf4j
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;
    private final ProviderMeta providerMeta;

    public SpringAiLlmClient(ChatClient chatClient, ProviderMeta providerMeta) {
        this.chatClient = chatClient;
        this.providerMeta = providerMeta;
    }

    @Override
    public meta.claw.core.spi.llm.ChatResponse chat(meta.claw.core.spi.llm.ChatRequest request) {
        log.debug("SpringAiLlmClient chat: messages={}", request.messages().size());

        List<Message> springMessages = request.messages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toList());

        Prompt prompt = new Prompt(springMessages);
        ChatResponse response = chatClient.call(prompt);

        String content = response.getResult().getOutput().getContent();

        return meta.claw.core.spi.llm.ChatResponse.builder()
                .content(content)
                .toolCalls(null)
                .usage(null)
                .metadata(null)
                .build();
    }

    @Override
    public void chatStream(meta.claw.core.spi.llm.ChatRequest request, StreamingCallback callback) {
        callback.onStart();
        try {
            meta.claw.core.spi.llm.ChatResponse response = chat(request);
            callback.onChunk(response.content());
            callback.onComplete(response);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public CompletableFuture<meta.claw.core.spi.llm.ChatResponse> chatAsync(meta.claw.core.spi.llm.ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    @Override
    public ProviderMeta getProviderMeta() {
        return providerMeta;
    }

    private Message toSpringMessage(meta.claw.core.spi.llm.Message msg) {
        return switch (msg.role()) {
            case "system" -> new SystemMessage(msg.content());
            case "user" -> new UserMessage(msg.content());
            case "assistant" -> new AssistantMessage(msg.content());
            default -> new UserMessage(msg.content());
        };
    }
}
