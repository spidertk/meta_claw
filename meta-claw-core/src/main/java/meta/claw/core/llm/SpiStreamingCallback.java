package meta.claw.core.llm;

import meta.claw.core.tool.SpiToolCall;

public interface SpiStreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onToolCall(SpiToolCall toolCall);
    void onComplete(SpiChatResponse response);
    void onError(Throwable error);
}
