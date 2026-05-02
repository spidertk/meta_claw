package meta.claw.core.spi.llm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testBuilder() {
        SpiMessage msg = SpiMessage.builder()
                .role("user")
                .content("hello")
                .build();
        assertEquals("user", msg.role());
        assertEquals("hello", msg.content());
    }

    @Test
    void testFactoryMethods() {
        assertEquals("system", SpiMessage.system("sys").role());
        assertEquals("user", SpiMessage.user("hi").role());
        assertEquals("assistant", SpiMessage.assistant("ok").role());
        assertEquals("tool", SpiMessage.tool("result").role());
    }
}
