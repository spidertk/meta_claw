package meta.claw.core.infra.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoryConfig {
    private String shortTermStore = "jsonl";
    private String longTermStore = "file";
}
