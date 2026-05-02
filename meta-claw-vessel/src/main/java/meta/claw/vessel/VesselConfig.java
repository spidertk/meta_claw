package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;

/**
 * Vessel 配置模型，映射 vessel.md 的 YAML frontmatter。
 */
@Getter
@Setter
public class VesselConfig {
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String model;
    private String systemPrompt;
    private boolean memoryEnabled;
    private String knowledgeDir;
}
