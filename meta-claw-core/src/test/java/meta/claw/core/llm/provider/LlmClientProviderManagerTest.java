package meta.claw.core.llm.provider;

import meta.claw.core.config.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmClientProviderManagerTest {

    @Test
    void routesCreateChatModelToProvider() {
        LlmClientProviderManager manager = new LlmClientProviderManager();

        ProviderConfig config = new ProviderConfig();
        config.setProvider("openai");

        ChatModel chatModel = mock(ChatModel.class);
        LlmClientProvider provider = mock(LlmClientProvider.class);
        when(provider.providerName()).thenReturn("openai");
        when(provider.createChatModel(config)).thenReturn(chatModel);

        ReflectionTestUtils.setField(manager, "allProviders", Map.of("openai", provider));

        ChatModel result = manager.createChatModel(config);

        assertSame(chatModel, result);
    }

    @Test
    void throwsWhenNoProviderForCreateChatModel() {
        LlmClientProviderManager manager = new LlmClientProviderManager();
        ReflectionTestUtils.setField(manager, "allProviders", Map.of());

        ProviderConfig config = new ProviderConfig();
        config.setProvider("missing");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.createChatModel(config));
        assertTrue(ex.getMessage().contains("No LlmClientProvider supports provider"));
    }

    @Test
    void routesCreateToProvider() {
        LlmClientProviderManager manager = new LlmClientProviderManager();

        ProviderConfig config = new ProviderConfig();
        config.setProvider("openai");

        ChatClient chatClient = mock(ChatClient.class);
        LlmClientProvider provider = mock(LlmClientProvider.class);
        when(provider.providerName()).thenReturn("openai");
        when(provider.create(config)).thenReturn(chatClient);

        ReflectionTestUtils.setField(manager, "allProviders", Map.of("openai", provider));

        ChatClient result = manager.create(config);

        assertSame(chatClient, result);
    }
}
