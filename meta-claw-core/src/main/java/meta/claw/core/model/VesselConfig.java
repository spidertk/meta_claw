package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import meta.claw.core.model.SessionConfig;

/**
 * Vessel 配置模型，映射 vessel.md 的 YAML frontmatter 及 Markdown body 各 section。
 */
@Getter
@Setter
public class VesselConfig {
    // YAML frontmatter 字段
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String model;
    private String systemPrompt;
    private boolean preferencesEnabled;
    private String knowledgeDir;

    // 新增 frontmatter 字段
    private String role;           // teamleader / member
    private boolean autoServe;     // 无参数 serve 时是否自动启动
    private List<String> excludeTools;

    // Markdown body section 内容
    private String identity;         // ## Identity
    private String soul;             // ## Soul
    private String domainKnowledge;  // ## Domain Knowledge
    private String capabilities;     // ## Capabilities
    private String guidelines;       // ## Guidelines
    private String preferences;      // ## Preferences

    // Phase 2: 上下文窗口管理配置
    private Integer maxHistoryRounds = 20;
    private Integer maxTokens = 4096;

}
