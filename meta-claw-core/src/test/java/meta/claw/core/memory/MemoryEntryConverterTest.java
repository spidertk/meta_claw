package meta.claw.core.memory;

import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemoryEntryConverterTest {

    @Test
    void fromSpiMessage_shouldBuildMessageEntry() {
        MemoryEntry entry = MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("hello"));

        assertEquals("s1", entry.getSessionId());
        assertEquals("message", entry.getCategory());
        assertEquals("hello", entry.getContent());
        assertEquals("user", entry.getMetadata().get("role"));
        assertNotNull(entry.getTimestamp());
    }

    @Test
    void toSpiMessage_shouldRestoreToolCalls() {
        MemoryEntry entry = MemoryEntry.builder()
                .category("message")
                .content("done")
                .metadata(Map.of(
                        "role", "assistant",
                        "toolCalls", List.of(SpiToolCall.builder()
                                .id("tool-1")
                                .name("lookup")
                                .arguments(Map.of("q", "meta"))
                                .build())
                ))
                .build();

        SpiMessage message = MemoryEntryConverter.toSpiMessage(entry);

        assertEquals("assistant", message.role());
        assertEquals("done", message.content());
        assertEquals("lookup", message.toolCalls().get(0).name());
    }
}
