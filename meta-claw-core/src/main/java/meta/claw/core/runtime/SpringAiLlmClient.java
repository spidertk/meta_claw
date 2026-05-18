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
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 1.0.6 ChatClient 的 SpiLlmClient 实现。
 */
@Slf4j
@Component
@Scope("prototype")
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
        long startTime = System.currentTimeMillis();
        
        List<Message> springMessages = request.messages().stream()
                .map(this::toSpringMessage)
                .toList();
        Prompt prompt = new Prompt(springMessages);

        long buildPromptTime = System.currentTimeMillis() - startTime;
        log.debug("[STREAM] Build prompt took {}ms, messages={}", buildPromptTime, springMessages.size());
        
//        log.info("[STREAM] Starting stream request with {} messages", springMessages.size());
        callback.onStart();
        StringBuilder contentBuilder = new StringBuilder();
        
        try {
            long[] firstChunkTime = {-1};
            int[] chunkCount = {0};
            long[] lastChunkTime = {startTime};
            
            log.debug("[STREAM] Calling chatClient.stream() at {}ms", System.currentTimeMillis() - startTime);
            
            chatClient.prompt(prompt).stream().content()
                .doOnSubscribe(s -> {
                    log.debug("[STREAM-SUBSCRIBE] Subscribed at {}ms", System.currentTimeMillis() - startTime);
                })
                .doOnNext(chunk -> {
                    chunkCount[0]++;
                    long elapsed = System.currentTimeMillis() - startTime;
                    long gap = elapsed - lastChunkTime[0];
                    lastChunkTime[0] = elapsed;
                    
                    if (firstChunkTime[0] == -1) {
                        firstChunkTime[0] = elapsed;
                        log.debug("[STREAM] First chunk received after {}ms (TTFB)", elapsed);
                    } else {
                        log.debug("[STREAM-CHUNK #{}] at {}ms (gap: {}ms): len={}",
                            chunkCount[0], elapsed, gap, chunk.length());
                    }
                    
                    contentBuilder.append(chunk);
                    callback.onChunk(chunk);
                })
                .doOnError(error -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.error("[STREAM] Error occurred after {}ms: {}", totalTime, error.getMessage(), error);
                    callback.onError(error);
                })
                .doOnComplete(() -> {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.debug("[STREAM] Completed: totalChunks={}, totalTime={}ms, firstChunkAt={}ms, avgGap={}ms, totalLength={}",
                        chunkCount[0], 
                        totalTime, 
                        firstChunkTime[0],
                        chunkCount[0] > 1 ? (totalTime - firstChunkTime[0]) / (chunkCount[0] - 1) : 0,
                        contentBuilder.length());
                    
                    SpiChatResponse response = SpiChatResponse.builder()
                            .content(contentBuilder.toString())
                            .toolCalls(null)
                            .usage(null)
                            .metadata(null)
                            .build();
                    callback.onComplete(response);
                })
                .blockLast(); // 阻塞等待流完成，但每个chunk会实时回调
                
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("[STREAM] Exception after {}ms: {}", totalTime, e.getMessage(), e);
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
