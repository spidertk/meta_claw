package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * 创建带日志过滤器和自定义 {@link ObjectMapper} 的 {@link WebClient.Builder}。
 * <p>
 * 用于流式调用（{@code stream()}），通过 {@link LoggingReactorClientHttpConnector}
 * 在底层拦截请求体和包含 tool_calls / finish_reason 的关键响应 chunk。
 * </p>
 * <p>
 * {@link ConnectionProvider} 和 {@link HttpClient} 以静态内部类单例（Bill Pugh）持有，
 * 确保全应用生命周期只创建一份连接池，避免内存泄漏。
 * </p>
 */
@Slf4j
public final class OpenAiWebClientFactory {

    private OpenAiWebClientFactory() {
    }

    /**
     * 静态内部类单例：JVM 加载时才初始化，天然线程安全，只创建一次。
     */
    private static class NettyHolder {
        static final ConnectionProvider CONNECTION_PROVIDER = ConnectionProvider.builder("llm-pool")
                .maxConnections(50)
                .pendingAcquireMaxCount(100)
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .maxIdleTime(Duration.ofMinutes(5))
                .maxLifeTime(Duration.ofHours(1))
                .evictInBackground(Duration.ofMinutes(2))
                .build();

        static final HttpClient HTTP_CLIENT = HttpClient.create(CONNECTION_PROVIDER)
                .keepAlive(true)
                .responseTimeout(Duration.ofMinutes(5));
    }

    public static WebClient.Builder create(ObjectMapper objectMapper) {
        ExchangeFilterFunction requestLogFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            long start = System.currentTimeMillis();
            log.debug("[HTTP-REQUEST-WEB] Start: {} {} Headers: {}",
                    clientRequest.method(), clientRequest.url(), clientRequest.headers());
            return Mono.just(clientRequest)
                    .doOnSubscribe(s -> log.debug("[HTTP-SUBSCRIBE-WEB] Subscribed at {}ms",
                            System.currentTimeMillis() - start))
                    .doOnSuccess(r -> log.debug("[HTTP-SENT-WEB] Sent at {}ms",
                            System.currentTimeMillis() - start));
        });

        ExchangeFilterFunction errorLogFilter = ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().isError()) {
                return response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.error("[HTTP-RESPONSE-WEB] {} {} - Body: {}",
                                    response.statusCode(), response.request().getURI(), body);
                            return Mono.just(ClientResponse.create(response.statusCode())
                                    .headers(h -> h.addAll(response.headers().asHttpHeaders()))
                                    .body(body)
                                    .build());
                        });
            }
            return Mono.just(response);
        });

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(config -> {
                    config.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    config.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                })
                .build();

        return WebClient.builder()
                .filter(requestLogFilter)
                .filter(errorLogFilter)
                .exchangeStrategies(exchangeStrategies)
                .clientConnector(new LoggingReactorClientHttpConnector(
                        new ReactorClientHttpConnector(NettyHolder.HTTP_CLIENT)));
    }
}
