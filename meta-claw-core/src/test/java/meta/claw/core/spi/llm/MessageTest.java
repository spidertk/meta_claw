package meta.claw.core.spi.llm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testBuilder() {
        Message msg = Message.builder()
                .role("user")
                .content("hello")
                .build();
        assertEquals("user", msg.role());
        assertEquals("hello", msg.content());
    }

    @Test
    void testFactoryMethods() {
        assertEquals("system", Message.system("sys").role());
        assertEquals("user", Message.user("hi").role());
        assertEquals("assistant", Message.assistant("ok").role());
        assertEquals("tool", Message.tool("result").role());
    }
}
