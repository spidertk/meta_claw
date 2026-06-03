package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 记忆系统配置。
 * <p>
 * 定义短期记忆和长期记忆的存储后端类型。
 * 后端实现由 {@link meta.claw.core.memory.shortterm.ShortMemoryFactory}
 * 和 {@link meta.claw.core.memory.longterm.LongMemoryFactory} 根据此配置选择。
 * </p>
 *
 * @see meta.claw.core.memory.shortterm.ShortMemory
 * @see meta.claw.core.memory.longterm.LongMemory
 */
@Getter
@Setter
public class MemoryConfig {

    /**
     * 短期记忆存储后端类型。
     * <p>当前支持：jsonl（JSONL 文件落盘）。</p>
     */
    private String shortTermStore = "jsonl";

    /**
     * 长期记忆存储后端类型。
     * <p>当前支持：file（本地文件系统）。</p>
     */
    private String longTermStore = "file";
}
