package meta.claw.core.spi.llm;

public interface SpiStreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onToolCall(SpiToolCall toolCall);
    void onComplete(SpiChatResponse response);
    void onError(Throwable error);
}
