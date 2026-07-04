package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.ReasoningAwareOpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * OpenAI 兼容协议的 ChatClient 工厂实现。
 * <p>
 * 支持所有兼容 OpenAI API 协议的 provider：OpenAI、Moonshot、DeepSeek、Azure OpenAI、GitHub Models 等。
 * </p>
 *
 * <p>
 * 本类只负责创建基础 {@link ChatClient} 与 {@link ChatModel}；公共 Advisor 栈由
 * {@link LlmClientProviderManager} 统一装配，避免每个 provider 重复开发。
 * </p>
 */
@Slf4j
@Component
@Primary
public class OpenAiLlmClientProvider implements LlmClientProvider {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ChatClient create(ProviderConfig providerConfig) {
        return ChatClient.builder(buildChatModel(providerConfig)).build();
    }

    @Override
    public ChatClient createRaw(ProviderConfig providerConfig) {
        return create(providerConfig);
    }

    @Override
    public ChatModel createChatModel(ProviderConfig providerConfig) {
        return buildChatModel(providerConfig);
    }

    public ChatModel buildChatModel(ProviderConfig providerConfig) {
        String apiKey = providerConfig.getApiKey();
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
        String model = providerConfig.getModel();

        log.info("Creating ChatModel - apiKey prefix: {}, baseUrl: {}, model: {}",
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null",
                baseUrl, model);

        // 使用 provider 配置的超时（秒），默认 5 分钟
        Duration responseTimeout = providerConfig.getTimeout() != null
                ? Duration.ofSeconds(providerConfig.getTimeout().longValue())
                : Duration.ofMinutes(5);

        // 构建 RestClient（同步 call）和 WebClient（流式 stream）
        RestClient.Builder restClientBuilder = OpenAiRestClientFactory.create(objectMapper, responseTimeout);
        WebClient.Builder webClientBuilder = OpenAiWebClientFactory.create(objectMapper, responseTimeout);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder);
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi openAiApi = apiBuilder.build();

        // 构建 ChatOptions，设置模型及可选温度参数
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .streamUsage(true);
        if (providerConfig.getTemperature() != null) {
            optionsBuilder.temperature(providerConfig.getTemperature());
        }
        OpenAiChatOptions chatOptions = optionsBuilder.build();

        // 使用 ReasoningAwareOpenAiChatModel 修复 Spring AI 1.1.8 硬编码 reasoningContent=null 的问题
        return new ReasoningAwareOpenAiChatModel(openAiApi, chatOptions, objectMapper);
    }

    /**
     * 规范化 baseUrl：仅去掉末尾斜杠。
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

    @Override
    public String providerName() {
        return "openai";
    }
}
