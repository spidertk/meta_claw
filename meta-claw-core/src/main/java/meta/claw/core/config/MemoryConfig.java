package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Memory backend 配置。
 */
@Getter
@Setter
public class MemoryConfig {
    private String shortTermStore = "jsonl";
    private String longTermStore = "file";
}
