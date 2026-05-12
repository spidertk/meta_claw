package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import meta.claw.core.model.SessionConfig;

/**
 * Vessel 配置模型，映射 vessel.md 和 config.xml 配置
 */
@Getter
@Setter
public class VesselConfig {

    //------BEGIN--Markdown ---------

    private String identity;         // ## Identity
    private String soul;             // ## Soul
    private String domainKnowledge;  // ## Domain Knowledge
    private String capabilities;     // ## Capabilities
    private String guidelines;       // ## Guidelines
    private String preferences;      // ## Preferences

    //------END--Markdown ---------

    //------BEGIN--YAML ---------
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String systemPrompt;
    private boolean preferencesEnabled;
    private String knowledgeDir;
    private String role;           // teamleader / member
    private boolean autoServe;     // 无参数 serve 时是否自动启动
    private List<String> excludeTools;

    // Vessel 级 provider 覆盖配置
    private String provider;
    private String model;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Double timeout;
    //------END--YAML ---------

    // Phase 2: 上下文窗口管理配置
    private Integer maxHistoryRounds = 20;
    private Integer maxTokens = 4096;

}
