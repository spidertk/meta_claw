package com.meta_claw.knowledge.core.adapter.outbound.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON Lines 文件适配器共享的 Jackson 配置。
 * 统一使用 snake_case 字段名，与 `knowledge/contracts` 中的 JSON schema 保持一致。
 */
final class JacksonJsonLineSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JacksonJsonLineSupport() {
    }

    /** 将 domain 对象写成一行 JSON。 */
    static String writeLine(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write JSON line", e);
        }
    }

    /** 从一行 JSON 读取 domain 对象。 */
    static <T> T readLine(String line, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(line, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read JSON line", e);
        }
    }
}
