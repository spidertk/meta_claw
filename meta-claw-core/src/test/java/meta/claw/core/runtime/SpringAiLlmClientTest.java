package meta.claw.core.runtime;

import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringAiLlmClientTest {

    @Test
    void testChat() {
        ChatClient mockClient = mock(ChatClient.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGen = mock(Generation.class);
        AssistantMessage mockMsg = new AssistantMessage("Hello from AI");

        when(mockClient.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGen);
        when(mockGen.getOutput()).thenReturn(mockMsg);

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name("kimi").model("moonshot-v1-8k").baseUrl("https://api.moonshot.cn").build();

        SpringAiLlmClient client = new SpringAiLlmClient(mockClient, meta);

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user("hi")))
                .build();

        SpiChatResponse response = client.chat(request);

        assertEquals("Hello from AI", response.content());
        assertEquals("kimi", client.getProviderMeta().name());
    }
}
