package meta.claw.cli;

import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCommandTest {

    @Test
    void toSpiMessages_shouldRestoreConversationMessagesButSkipSystem() {
        List<SpiMessage> restored = ChatCommand.toSpiMessages(List.of(
                SpiMessage.system("old system"),
                SpiMessage.user("hello"),
                SpiMessage.assistant("hi")
        ));

        assertEquals(2, restored.size());
        assertEquals("user", restored.get(0).role());
        assertEquals("hello", restored.get(0).content());
        assertEquals("assistant", restored.get(1).role());
        assertEquals("hi", restored.get(1).content());
    }
}
