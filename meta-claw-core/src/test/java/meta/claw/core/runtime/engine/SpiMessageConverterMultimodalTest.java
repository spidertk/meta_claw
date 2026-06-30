package meta.claw.core.runtime.engine;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpiMessageConverterMultimodalTest {

    @Test
    void userMessageWithImagePart() {
        SpiMessage spi = SpiMessage.user(
                "Describe this image",
                List.of(MediaPart.builder()
                        .type("image_url")
                        .mimeType("image/png")
                        .url("file:///tmp/test.png")
                        .build()));

        Message message = SpiMessageConverter.toSpringMessage(spi);
        assertEquals(UserMessage.class, message.getClass());
        UserMessage userMessage = (UserMessage) message;
        assertEquals("Describe this image", userMessage.getText());
        assertFalse(userMessage.getMedia().isEmpty());
    }
}
