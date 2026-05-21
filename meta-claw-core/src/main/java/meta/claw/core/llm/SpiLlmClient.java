package meta.claw.core.llm;

import java.util.concurrent.CompletableFuture;

public interface SpiLlmClient {
    //同步调用
    SpiChatResponse chat(SpiChatRequest request);
    //流式调用
    void chatStream(SpiChatRequest request, SpiStreamingCallback callback);
    //异步同步调用
    CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request);
}
