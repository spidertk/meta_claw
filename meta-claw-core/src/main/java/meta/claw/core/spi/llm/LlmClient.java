package meta.claw.core.spi.llm;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    ChatResponse chat(ChatRequest request);
    void chatStream(ChatRequest request, StreamingCallback callback);
    CompletableFuture<ChatResponse> chatAsync(ChatRequest request);
    ProviderMeta getProviderMeta();
}
