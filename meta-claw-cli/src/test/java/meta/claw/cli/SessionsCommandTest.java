package meta.claw.cli;

import meta.claw.core.memory.shortterm.ConversationInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsCommandTest {

    @Test
    void printSessions_shouldRenderOnlyProvidedSessions() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(output));
        try {
            SessionsCommand.printSessions("default", List.of(
                    ConversationInfo.builder()
                            .sessionId("session-default")
                            .updatedAt(LocalDateTime.of(2026, 5, 16, 16, 40))
                            .messageCount(3)
                            .build()
            ));
        } finally {
            System.setOut(original);
        }

        String text = output.toString();
        assertTrue(text.contains("Sessions for vessel 'default'"));
        assertTrue(text.contains("session-default"));
        assertTrue(text.contains("3"));
    }
}
