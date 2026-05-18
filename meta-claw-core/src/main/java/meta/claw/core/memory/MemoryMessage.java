package meta.claw.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import meta.claw.core.spi.llm.SpiToolCall;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单条短期记忆消息。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MemoryMessage {
    private LocalDateTime timestamp;
    private String role;
    private String content;
    private List<SpiToolCall> toolCalls;
}
