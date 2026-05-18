package meta.claw.core.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiToolCall;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MemoryEntry 与外部消息模型之间的转换工具。
 */
public final class MemoryEntryConverter {
    public static final String MESSAGE_CATEGORY = "message";
    public static final String ROLE_KEY = "role";
    public static final String TOOL_CALLS_KEY = "toolCalls";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MemoryEntryConverter() {
    }

    public static MemoryEntry fromSpiMessage(String sessionId, SpiMessage message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ROLE_KEY, message.role());
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            metadata.put(TOOL_CALLS_KEY, message.toolCalls());
        }
        return MemoryEntry.builder()
                .timestamp(LocalDateTime.now())
                .category(MESSAGE_CATEGORY)
                .content(message.content())
                .metadata(metadata)
                .sessionId(sessionId)
                .build();
    }

    public static SpiMessage toSpiMessage(MemoryEntry entry) {
        Map<String, Object> metadata = entry.getMetadata() == null ? Collections.emptyMap() : entry.getMetadata();
        String role = metadata.get(ROLE_KEY) == null ? null : metadata.get(ROLE_KEY).toString();
        List<SpiToolCall> toolCalls = metadata.containsKey(TOOL_CALLS_KEY)
                ? OBJECT_MAPPER.convertValue(metadata.get(TOOL_CALLS_KEY), new TypeReference<>() {})
                : null;
        return new SpiMessage(role, entry.getContent(), toolCalls);
    }

    public static List<SpiMessage> toSpiMessages(List<MemoryEntry> entries) {
        return entries.stream()
                .map(MemoryEntryConverter::toSpiMessage)
                .toList();
    }
}
