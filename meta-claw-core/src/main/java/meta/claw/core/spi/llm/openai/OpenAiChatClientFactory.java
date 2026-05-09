package meta.claw.core.spi.llm.openai;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ProviderConfig;
import meta.claw.core.spi.llm.LlmClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

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
public class OpenAiChatClientFactory implements LlmClientFactory {

    @Override
    public ChatClient create(ProviderConfig providerConfig, String model) {
        String apiKey = providerConfig.getApiKey();
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
    
        log.info("Creating ChatClient - apiKey prefix: {}, baseUrl: {}, model: {}",
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "null",
                baseUrl, model);
    
        // 配置 Reactor Netty 连接池，避免每次请求重新建立连接
        ConnectionProvider connectionProvider = ConnectionProvider.builder("llm-pool")
                .maxConnections(50)                    // 最大连接数
                .pendingAcquireMaxCount(100)           // 最大等待获取连接的请求数
                .pendingAcquireTimeout(Duration.ofSeconds(30))  // 获取连接超时时间
                .maxIdleTime(Duration.ofMinutes(5))    // 连接最大空闲时间
                .maxLifeTime(Duration.ofHours(1))      // 连接最大存活时间
                .evictInBackground(Duration.ofMinutes(2))  // 后台清理间隔
                .build();
    
        // 创建优化的 HttpClient，启用连接池和 Keep-Alive
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .keepAlive(true)                       // 启用 HTTP Keep-Alive
                .responseTimeout(Duration.ofMinutes(5)); // 响应超时时间
    
        // 使用 Spring AI 1.1.4 的 Builder API 编程式创建 OpenAiApi
        // 添加 RestClient 拦截器（同步 call() 请求）和 WebClient 过滤器（流式 stream() 请求）
        ClientHttpRequestInterceptor restInterceptor = (request, body, execution) -> {
            long start = System.currentTimeMillis();
            log.debug("[HTTP-REQUEST-REST] Start: {} {}", request.getMethod(), request.getURI());
            try {
                var response = execution.execute(request, body);
                long elapsed = System.currentTimeMillis() - start;
                log.debug("[HTTP-RESPONSE-REST] Status: {}, Time: {}ms", response.getStatusCode(), elapsed);
                return response;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.error("[HTTP-ERROR-REST] Time: {}ms, Error: {}", elapsed, e.getMessage());
                throw e;
            }
        };
    
        ExchangeFilterFunction webFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            long start = System.currentTimeMillis();
            log.debug("[HTTP-REQUEST-WEB] Start: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest)
                .doOnSubscribe(s -> log.debug("[HTTP-SUBSCRIBE-WEB] Subscribed at {}ms", System.currentTimeMillis() - start))
                .doOnSuccess(r -> log.debug("[HTTP-SENT-WEB] Sent at {}ms", System.currentTimeMillis() - start));
        });
    
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestInterceptor(restInterceptor))
                .webClientBuilder(WebClient.builder()
                        .filter(webFilter)
                        .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient)));
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi openAiApi = apiBuilder.build();

        // 构建 ChatOptions，设置模型及可选温度参数
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model);
        if (providerConfig.getTemperature() != null) {
            optionsBuilder.temperature(providerConfig.getTemperature());
        }
        OpenAiChatOptions chatOptions = optionsBuilder.build();

        // 编程式创建 OpenAiChatModel
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        // 创建 ChatClient 并返回
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        
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

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "openai", "moonshot", "deepseek", "azure", "github", "siliconflow",
            "volcengine", "baichuan", "zhipu", "qwen", "dashscope"
    );

    @Override
    public boolean supports(String providerName) {
        return providerName != null && SUPPORTED.contains(providerName.toLowerCase());
    }
}
