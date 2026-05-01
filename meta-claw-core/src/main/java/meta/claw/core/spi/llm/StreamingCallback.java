package meta.claw.core.spi.llm;

public interface StreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onToolCall(ToolCall toolCall);
    void onComplete(ChatResponse response);
    void onError(Throwable error);
}
