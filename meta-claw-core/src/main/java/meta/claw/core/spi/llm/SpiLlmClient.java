package meta.claw.core.spi.llm;

import java.util.concurrent.CompletableFuture;

public interface SpiLlmClient {
    SpiChatResponse chat(SpiChatRequest request);
    void chatStream(SpiChatRequest request, SpiStreamingCallback callback);
    CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request);
    SpiProviderMeta getProviderMeta();
}
