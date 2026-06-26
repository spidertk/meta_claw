package meta.claw.core.llm.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiReasoningContentContextTest {

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldPushAndPollInFifoOrder() {
        OpenAiReasoningContentContext.push("first");
        OpenAiReasoningContentContext.push("second");

        assertEquals("first", OpenAiReasoningContentContext.poll());
        assertEquals("second", OpenAiReasoningContentContext.poll());
        assertNull(OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldTreatNullAsEmptyString() {
        OpenAiReasoningContentContext.push(null);

        assertEquals("", OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldClearAllValues() {
        OpenAiReasoningContentContext.push("value");
        OpenAiReasoningContentContext.clear();

        assertTrue(OpenAiReasoningContentContext.isEmpty());
        assertNull(OpenAiReasoningContentContext.poll());
    }

    @Test
    void shouldRemoveThreadLocal() {
        OpenAiReasoningContentContext.push("value");
        OpenAiReasoningContentContext.remove();

        assertTrue(OpenAiReasoningContentContext.isEmpty());
    }
}
