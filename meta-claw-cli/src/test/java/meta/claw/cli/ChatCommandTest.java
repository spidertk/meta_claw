package meta.claw.cli;

import meta.claw.core.session.ChatMessage;
import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCommandTest {

    @Test
    void toSpiMessages_shouldRestoreConversationMessagesButSkipSystem() {
        List<SpiMessage> restored = ChatCommand.toSpiMessages(List.of(
                ChatMessage.builder().role("system").content("old system").build(),
                ChatMessage.builder().role("user").content("hello").build(),
                ChatMessage.builder().role("assistant").content("hi").build()
        ));

        assertEquals(2, restored.size());
        assertEquals("user", restored.get(0).role());
        assertEquals("hello", restored.get(0).content());
        assertEquals("assistant", restored.get(1).role());
        assertEquals("hi", restored.get(1).content());
    }
}
