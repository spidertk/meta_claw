package meta.claw.cli;

import meta.claw.core.memory.MemoryEntryConverter;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCommandTest {

    @Test
    void toSpiMessages_shouldRestoreConversationMessagesButSkipSystem() {
        List<SpiMessage> restored = ChatCommand.toSpiMessages(List.of(
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.system("old system")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.user("hello")),
                MemoryEntryConverter.fromSpiMessage("s1", SpiMessage.assistant("hi"))
        ));

        assertEquals(2, restored.size());
        assertEquals("user", restored.get(0).role());
        assertEquals("hello", restored.get(0).content());
        assertEquals("assistant", restored.get(1).role());
        assertEquals("hi", restored.get(1).content());
    }
}
