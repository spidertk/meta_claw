package meta.claw.core.runtime;

import meta.claw.core.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageThreadTest {

    @Test
    void addAndSnapshot() {
        MessageThread thread = new MessageThread();
        thread.add(SpiMessage.user("hello"));
        assertEquals(1, thread.size());
        assertEquals("hello", thread.snapshot().get(0).getContent());
    }

    @Test
    void snapshot_isImmutable() {
        MessageThread thread = new MessageThread();
        thread.add(SpiMessage.user("hello"));
        assertThrows(UnsupportedOperationException.class,
                () -> thread.snapshot().add(SpiMessage.user("world")));
    }
}
