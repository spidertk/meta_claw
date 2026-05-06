package meta.claw.core.spi.llm.openai;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ProviderConfig;
import meta.claw.core.spi.llm.LlmClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议的 ChatClient 工厂实现。
 * <p>
 * 支持所有兼容 OpenAI API 协议的 provider：
 * OpenAI、Moonshot、DeepSeek、Azure OpenAI、GitHub Models 等。
 * 显式创建同步/异步 {@link com.openai.client.OpenAIClient} 并传入 {@link OpenAiChatModel}。
 * </p>
 */
@Slf4j
@Component
@Primary
public class OpenAiChatClientFactory implements LlmClientFactory {

    @Override
    public ChatClient create(ProviderConfig providerConfig, String model) {
        String apiKey = providerConfig.getApiKey();
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());

        log.info("Creating ChatClient - apiKey prefix: {}, baseUrl: {}, model: {}",
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null",
                baseUrl, model);

        var optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            optionsBuilder.baseUrl(baseUrl);
        }
        var options = optionsBuilder.build();

        var clientBuilder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        var asyncClientBuilder = OpenAIOkHttpClientAsync.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            clientBuilder.baseUrl(baseUrl);
            asyncClientBuilder.baseUrl(baseUrl);
        }

        var chatModel = OpenAiChatModel.builder()
                .openAiClient(clientBuilder.build())
                .openAiClientAsync(asyncClientBuilder.build())
                .options(options)
                .build();

        log.debug("Created OpenAiChatModel for model={}, baseUrl={}", model, baseUrl);

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 规范化 baseUrl：仅去掉末尾斜杠，保留 /v1 路径。
     * 官方 OpenAI Java SDK 将 baseUrl 与相对路径直接拼接，不会自动补 /v1。
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "openai", "moonshot", "deepseek", "azure", "github", "siliconflow",
            "volcengine", "baichuan", "zhipu", "qwen", "dashscope"
    );

    @Override
    public boolean supports(String providerName) {
        return providerName != null && SUPPORTED.contains(providerName.toLowerCase());
    }
}
