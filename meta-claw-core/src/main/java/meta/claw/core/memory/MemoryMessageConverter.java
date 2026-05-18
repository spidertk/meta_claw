package meta.claw.core.memory;

import meta.claw.core.spi.llm.SpiMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MemoryMessage 与外部消息模型之间的转换工具。
 */
public final class MemoryMessageConverter {
    private MemoryMessageConverter() {
    }

    public static MemoryMessage fromSpiMessage(SpiMessage message) {
        return MemoryMessage.builder()
                .timestamp(LocalDateTime.now())
                .role(message.role())
                .content(message.content())
                .toolCalls(message.toolCalls())
                .build();
    }

    public static SpiMessage toSpiMessage(MemoryMessage message) {
        return new SpiMessage(message.getRole(), message.getContent(), message.getToolCalls());
    }

    public static List<SpiMessage> toSpiMessages(List<MemoryMessage> messages) {
        return messages.stream()
                .map(MemoryMessageConverter::toSpiMessage)
                .toList();
    }
}
