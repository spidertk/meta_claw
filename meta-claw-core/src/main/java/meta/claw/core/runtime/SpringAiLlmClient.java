package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiLlmClient;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 0.8.0 ChatClient 的 SpiLlmClient 实现。
 */
@Slf4j
public class SpringAiLlmClient implements SpiLlmClient {

    private final ChatClient chatClient;
    private final SpiProviderMeta providerMeta;

    public SpringAiLlmClient(ChatClient chatClient, SpiProviderMeta providerMeta) {
        this.chatClient = chatClient;
        this.providerMeta = providerMeta;
    }

    @Override
    public SpiChatResponse chat(SpiChatRequest request) {
        log.debug("SpringAiLlmClient chat: messages={}", request.messages().size());

        List<org.springframework.ai.chat.messages.Message> springMessages = request.messages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toList());

        Prompt prompt = new Prompt(springMessages);
        ChatResponse response = chatClient.call(prompt);

        String content = response.getResult().getOutput().getContent();

        return SpiChatResponse.builder()
                .content(content)
                .toolCalls(null)
                .usage(null)
                .metadata(null)
                .build();
    }

    @Override
    public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
        callback.onStart();
        try {
            SpiChatResponse response = chat(request);
            callback.onChunk(response.content());
            callback.onComplete(response);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    @Override
    public SpiProviderMeta getProviderMeta() {
        return providerMeta;
    }

    private Message toSpringMessage(SpiMessage msg) {
        return switch (msg.role()) {
            case "system" -> new SystemMessage(msg.content());
            case "user" -> new UserMessage(msg.content());
            case "assistant" -> new AssistantMessage(msg.content());
            default -> new UserMessage(msg.content());
        };
    }
}
