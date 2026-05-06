package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiLlmClient;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 2.0 ChatClient 的 SpiLlmClient 实现。
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

        List<Message> springMessages = request.messages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toList());

        Prompt prompt = new Prompt(springMessages);
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

        String content = safeExtractContent(response);

        return SpiChatResponse.builder()
                .content(content)
                .toolCalls(null)
                .usage(null)
                .metadata(null)
                .build();
    }

    private String safeExtractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("SpringAiLlmClient 收到空响应或响应结构不完整");
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    @Override
    public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
        List<Message> springMessages = request.messages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toList());
        Prompt prompt = new Prompt(springMessages);

        callback.onStart();
        StringBuilder contentBuilder = new StringBuilder();
        try {
            chatClient.prompt(prompt).stream().content()
                    .doOnNext(chunk -> {
                        contentBuilder.append(chunk);
                        callback.onChunk(chunk);
                    })
                    .blockLast();

            SpiChatResponse response = SpiChatResponse.builder()
                    .content(contentBuilder.toString())
                    .toolCalls(null)
                    .usage(null)
                    .metadata(null)
                    .build();
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
            case "tool" -> ToolResponseMessage.builder()
                    .responses(List.of(
                            new ToolResponseMessage.ToolResponse("tool", "tool", msg.content())
                    ))
                    .build();
            default -> {
                log.warn("Unknown message role '{}', defaulting to user message", msg.role());
                yield new UserMessage(msg.content());
            }
        };
    }
}
