package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.MultiValueMap;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 包装 {@link ReactorClientHttpConnector}，在底层拦截 WebClient 的请求体和响应体，
 * 打印完整的 HTTP JSON 入参，以及包含 tool_calls / finish_reason 的关键响应 chunk。
 * <p>
 * 用于记录流式请求（{@code stream()}）和模型交互的原始入参/出参，
 * 和 {@link org.springframework.http.client.ClientHttpRequestInterceptor}（RestClient）配对使用。
 * </p>
 */
@Slf4j
public class LoggingReactorClientHttpConnector implements ClientHttpConnector {

    private final ClientHttpConnector delegate;

    public LoggingReactorClientHttpConnector(ReactorClientHttpConnector delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
        return delegate.connect(method, uri, request -> {
            AtomicReference<byte[]> bodyRef = new AtomicReference<>();

            ClientHttpRequest decoratedRequest = new ClientHttpRequestDecorator(request) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    return DataBufferUtils.join(body)
                            .flatMap(buffer -> {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                bodyRef.set(bytes);
                                DataBufferUtils.release(buffer);
                                return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                            });
                }
            };

            return requestCallback.apply(decoratedRequest)
                    .doOnSuccess(v -> {
                        byte[] body = bodyRef.get();
                        if (body != null && body.length > 0 && log.isDebugEnabled()) {
                            log.debug("[HTTP-REQUEST-WEB] {} {}\nBody:\n{}",
                                    method, uri, new String(body));
                        }
                    });
        }).map(LoggingClientHttpResponse::new);
    }

    private static final ObjectMapper CHUNK_MAPPER = new ObjectMapper();

    /**
     * 从 SSE chunk 中提取 JSON 字符串（去掉 "data:" 前缀）。
     */
    private static String extractJson(String text) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }
            if (line.startsWith("{") && line.endsWith("}")) {
                return line;
            }
        }
        return null;
    }

    /**
     * 判断 chunk 是否包含非空的 tool_calls 或非空的 finish_reason。
     */
    private static boolean isKeyChunk(String text) {
        String json = extractJson(text);
        if (json == null || "[DONE]".equals(json)) {
            return false;
        }
        try {
            JsonNode root = CHUNK_MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return false;
            }
            JsonNode choice = choices.get(0);

            // 检查 delta.tool_calls：必须是数组且非空
            JsonNode toolCalls = choice.path("delta").path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                return true;
            }

            // 检查 finish_reason：必须是文本且非空
            JsonNode finishReason = choice.path("finish_reason");
            if (finishReason.isTextual() && !finishReason.asText().isEmpty()) {
                return true;
            }

        } catch (Exception e) {
            // Jackson 解析失败时回退到正则匹配（确保值不为空）
            boolean hasToolCalls = text.contains("\"tool_calls\"")
                    && !text.matches(".*\"tool_calls\"\\s*:\\s*(\\[\\s*\\]|null).*");
            boolean hasFinishReason = text.contains("\"finish_reason\"")
                    && !text.matches(".*\"finish_reason\"\\s*:\\s*(null|\"\").*");
            return hasToolCalls || hasFinishReason;
        }
        return false;
    }

    /**
     * 包装响应，拦截 body buffer，当检测到非空 tool_calls / 非空 finish_reason 时打印关键 chunk。
     */
    private static class LoggingClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;

        LoggingClientHttpResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return delegate.getBody()
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        String text = new String(bytes);
                        if (log.isDebugEnabled()) {
                            log.debug("[HTTP-RESPONSE-WEB] raw chunk: {}", text.replaceAll("\\s+", " "));
                        }
                        if (isKeyChunk(text)) {
                            log.info("[HTTP-RESPONSE-WEB] ★ key chunk ★: {}",
                                    text.replaceAll("\\s+", " "));
                        }
                        return new DefaultDataBufferFactory().wrap(bytes);
                    });
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return delegate.getStatusCode();
        }

        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public MultiValueMap<String, ResponseCookie> getCookies() {
            return delegate.getCookies();
        }
    }
}
