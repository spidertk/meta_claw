package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryConfig {
    private String shortTermStore = "jsonl";
    private String longTermStore = "file";
}
