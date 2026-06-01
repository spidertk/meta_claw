package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.config.ProviderConfig;
import meta.claw.core.llm.advisor.ShortMemoryAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

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
        String apiKey = providerConfig.getApiKey();
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
        String model = providerConfig.getModel();

        log.info("Creating ChatClient - apiKey prefix: {}, baseUrl: {}, model: {}",
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null",
                baseUrl, model);

        // 根据模型选择 ObjectMapper：Moonshot K2.5/K2.6 需要特殊序列化补丁
        ObjectMapper mapper = selectObjectMapper(model);

        // 构建 RestClient（同步 call）和 WebClient（流式 stream）
        RestClient.Builder restClientBuilder = OpenAiRestClientFactory.create(mapper);
        WebClient.Builder webClientBuilder = OpenAiWebClientFactory.create(mapper);

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

        // 编程式创建 OpenAiChatModel，先不传入 ObservationRegistry 以激活可观测性
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
//                .observationRegistry(observationRegistry)
                .build();

//        // 创建 ChatClient，传入 ObservationRegistry 以激活 ChatClient 层面的可观测性
//        ChatClient chatClient = ChatClient.builder(chatModel, observationRegistry, null, null)
//                .defaultAdvisors(
//                        ToolCallAdvisor.builder().build()  // 外层：自动处理 tool calling 循环
//                        toolCallTraceAdvisor                  // 内层：记录每次 ChatModel 调用的完整消息
//                )
//                .build();

        ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(
                        ToolCallAdvisor.builder().build(),  // 外层：自动处理 tool calling 循环
                        shortMemoryAdvisor                     // 内层：流式响应持久化到 ShortMemory
                )
                .build();

        // 异步预热连接（可选），在后台发起一个轻量级请求以建立连接池
        // 注意：这会增加启动时间，但能消除首次请求的延迟
        if (log.isDebugEnabled()) {
            log.debug("ChatClient created successfully for model: {}", model);
        }

        return chatClient;
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
     * 根据模型名称选择 ObjectMapper。
     * <ul>
     *   <li>非 Moonshot：直接复用 Spring 容器里的默认 {@link ObjectMapper}（含 {@code spring.jackson.*} 配置）</li>
     *   <li>Moonshot K2.5/K2.6：copy 默认 ObjectMapper 并注册 {@link MoonshotSerializerModule}，避免污染共享实例</li>
     * </ul>
     */
    private ObjectMapper selectObjectMapper(String model) {
        if (model != null && model.toLowerCase().contains("kimi")) {
            ObjectMapper copy = objectMapper.copy();
            copy.registerModule(new MoonshotSerializerModule());
            log.debug("Registered MoonshotSerializerModule for model: {}", model);
            return copy;
        }
        return objectMapper;
    }

    @Override
    public String providerName() {
        return "openai";
    }


}
