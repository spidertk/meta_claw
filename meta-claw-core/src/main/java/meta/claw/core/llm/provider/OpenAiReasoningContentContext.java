package meta.claw.core.llm.provider;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 在同一线程内按消息顺序传递 assistant 消息的 reasoningContent。
 * 由 OpenAiReasoningContentAdvisor 按 Prompt.messages 顺序写入，
 * 由 OpenAiReasoningContentModule 在序列化 ChatCompletionMessage 时按顺序读取。
 */
public final class OpenAiReasoningContentContext {

    private static final ThreadLocal<Deque<String>> REASONING_QUEUE = ThreadLocal.withInitial(ArrayDeque::new);

    private OpenAiReasoningContentContext() {
    }

    public static void push(String reasoningContent) {
        REASONING_QUEUE.get().offerLast(reasoningContent != null ? reasoningContent : "");
    }

    public static String poll() {
        return REASONING_QUEUE.get().pollFirst();
    }

    public static boolean isEmpty() {
        return REASONING_QUEUE.get().isEmpty();
    }

    public static void clear() {
        REASONING_QUEUE.get().clear();
    }

    public static void remove() {
        REASONING_QUEUE.remove();
    }
}
