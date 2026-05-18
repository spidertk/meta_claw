package meta.claw.core.memory;

import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemoryMessageConverterTest {

    @Test
    void fromSpiMessage_shouldBuildMemoryMessage() {
        MemoryMessage message = MemoryMessageConverter.fromSpiMessage(SpiMessage.user("hello"));

        assertEquals("user", message.getRole());
        assertEquals("hello", message.getContent());
        assertNotNull(message.getTimestamp());
    }

    @Test
    void toSpiMessage_shouldRestoreToolCalls() {
        MemoryMessage message = MemoryMessage.builder()
                .role("assistant")
                .content("done")
                .toolCalls(List.of(SpiToolCall.builder()
                        .id("tool-1")
                        .name("lookup")
                        .arguments(Map.of("q", "meta"))
                        .build()))
                .build();

        SpiMessage restored = MemoryMessageConverter.toSpiMessage(message);

        assertEquals("assistant", restored.role());
        assertEquals("lookup", restored.toolCalls().get(0).name());
    }
}
