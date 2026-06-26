package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.advisor.ShortMemoryAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 兼容协议的 ChatClient 工厂实现。
 * <p>
 * 支持所有兼容 OpenAI API 协议的 provider：
 * OpenAI、Moonshot、DeepSeek、Azure OpenAI、GitHub Models 等。
 * </p>
 *
 * 基于 Spring AI 1.1.4 稳定版 API，通过编程方式动态创建 ChatClient。
 */
@Slf4j
@Component
@Primary
public class OpenAiLlmClientProvider implements LlmClientProvider {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShortMemoryAdvisor shortMemoryAdvisor;
//    @Autowired
//    private ObservationRegistry observationRegistry;
//    @Autowired
//    private ToolCallTraceAdvisor toolCallTraceAdvisor;

    /**
     * ChatClient 缓存：相同配置（baseUrl + model + temperature + apiKeyHash）复用已创建的实例，
     * 避免重复创建连接池和序列化器。
     */
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();


    @Override
    public ChatClient create(ProviderConfig providerConfig) {
        String cacheKey = buildCacheKey(providerConfig);
        return clientCache.computeIfAbsent(cacheKey, k -> buildChatClient(providerConfig));
    }

    @Override
    public ChatClient createRaw(ProviderConfig providerConfig) {
        return ChatClient.builder(buildChatModel(providerConfig))
                .defaultAdvisors(new OpenAiReasoningContentAdvisor(100))
                .build();
    }

    @Override
    public ChatModel createChatModel(ProviderConfig providerConfig) {
        return buildChatModel(providerConfig);
    }

    /**
     * 构建缓存 key：baseUrl + model + temperature + timeout + apiKeyHash。
     * 任一配置项变化都会创建新的 ChatClient。
     */
    private String buildCacheKey(ProviderConfig config) {
        return String.join("#",
                String.valueOf(config.getBaseUrl()),
                String.valueOf(config.getModel()),
                String.valueOf(config.getTemperature()),
                String.valueOf(config.getTimeout()),
                String.valueOf(config.getApiKey() != null ? config.getApiKey().hashCode() : 0));
    }

    private ChatClient buildChatClient(ProviderConfig providerConfig) {
        ChatClient chatClient = ChatClient.builder(buildChatModel(providerConfig))
                .defaultAdvisors(
                        new OpenAiReasoningContentAdvisor(100),  // 最外层：在请求发送前提取 reasoningContent
                        ToolCallAdvisor.builder().build(),       // 外层：自动处理 tool calling 循环
                        shortMemoryAdvisor                         // 内层：流式响应持久化到 ShortMemory
                )
                .build();

        if (log.isDebugEnabled()) {
            log.debug("ChatClient created successfully for model: {}", providerConfig.getModel());
        }

        return chatClient;
    }

    public org.springframework.ai.chat.model.ChatModel buildChatModel(ProviderConfig providerConfig) {
        String apiKey = providerConfig.getApiKey();
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
        String model = providerConfig.getModel();

        log.info("Creating ChatModel - apiKey prefix: {}, baseUrl: {}, model: {}",
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null",
                baseUrl, model);

        // 根据模型选择 ObjectMapper：Moonshot K2.5/K2.6 需要特殊序列化补丁
        ObjectMapper mapper = selectObjectMapper(model);

        // 使用 provider 配置的超时（秒），默认 5 分钟
        Duration responseTimeout = providerConfig.getTimeout() != null
                ? Duration.ofSeconds(providerConfig.getTimeout().longValue())
                : Duration.ofMinutes(5);

        // 构建 RestClient（同步 call）和 WebClient（流式 stream）
        RestClient.Builder restClientBuilder = OpenAiRestClientFactory.create(mapper, responseTimeout);
        WebClient.Builder webClientBuilder = OpenAiWebClientFactory.create(mapper, responseTimeout);

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

        // 编程式创建 OpenAiChatModel
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
//                .observationRegistry(observationRegistry)
                .build();
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


    /**
     * 选择 ObjectMapper。
     * 所有 OpenAI 兼容 provider 都需要修复 Spring AI 1.1.8 在把 AssistantMessage
     * 序列化为 ChatCompletionMessage 时硬编码 reasoningContent = null 的缺陷，
     * 因此统一 copy 默认 ObjectMapper 并注册 {@link OpenAiReasoningContentModule}。
     */
    private ObjectMapper selectObjectMapper(String model) {
        ObjectMapper copy = objectMapper.copy();
        copy.registerModule(new OpenAiReasoningContentModule());
        log.debug("Registered OpenAiReasoningContentModule for model: {}", model);
        return copy;
    }

    @Override
    public String providerName() {
        return "openai";
    }


}
