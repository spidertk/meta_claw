package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 创建带日志拦截器和自定义 {@link ObjectMapper} 的 {@link RestClient.Builder}。
 * <p>
 * 用于同步调用（{@code call()}），打印完整的 HTTP 请求/响应 JSON。
 * </p>
 */
@Slf4j
public final class OpenAiRestClientFactory {

    private OpenAiRestClientFactory() {
    }

    public static RestClient.Builder create(ObjectMapper objectMapper) {
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            long start = System.currentTimeMillis();
            if (body != null && body.length > 0 && log.isDebugEnabled()) {
                String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                log.debug("[HTTP-REQUEST-REST] {} {}\nBody:\n{}", request.getMethod(), request.getURI(), bodyStr);
            }
            try {
                var response = execution.execute(request, body);
                long elapsed = System.currentTimeMillis() - start;
                if (log.isDebugEnabled()) {
                    var wrapped = new BufferingClientHttpResponse(response);
                    if (wrapped.body.length > 0) {
                        String respStr = new String(wrapped.body, java.nio.charset.StandardCharsets.UTF_8);
                        log.debug("[HTTP-RESPONSE-REST] Status: {}, Time: {}ms\nBody:\n{}",
                                response.getStatusCode(), elapsed, respStr);
                    } else {
                        log.debug("[HTTP-RESPONSE-REST] Status: {}, Time: {}ms (empty body)",
                                response.getStatusCode(), elapsed);
                    }
                    return wrapped;
                }
                return response;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.error("[HTTP-ERROR-REST] Time: {}ms, Error: {}", elapsed, e.getMessage());
                throw e;
            }
        };

        return RestClient.builder()
                .requestInterceptor(interceptor)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }

    /**
     * 包装 ClientHttpResponse，允许读取响应体后仍能重复读取。
     */
    static class BufferingClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        final byte[] body;

        BufferingClientHttpResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            try (InputStream is = delegate.getBody()) {
                this.body = is != null ? is.readAllBytes() : new byte[0];
            }
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return delegate.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
