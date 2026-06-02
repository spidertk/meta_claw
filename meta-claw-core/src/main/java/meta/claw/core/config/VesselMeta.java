package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.config.MemoryConfig;

import java.util.List;

@Getter
@Setter
public class VesselMeta {

    private MetaInfo meta = new MetaInfo();
    private LlmConfig llm = new LlmConfig();
    private RuntimeConfig runtime = new RuntimeConfig();
    private MemoryConfig memory = new MemoryConfig();
    private ToolConfig tools = new ToolConfig();
    private Integer maxHistoryRounds = 20;
    private Integer maxTokens = 4096;

    @Getter
    @Setter
    public static class MetaInfo {
        private String id;
        private String name;
        private String description;
        private String displayName;
        private String emoji = "\uD83E\uDD16";
        private String createdAt;
    }

    @Getter
    @Setter
    public static class LlmConfig {
        private String provider = "openapi";
        private String model;
        private ProviderOverride overrides = new ProviderOverride();
    }

    @Getter
    @Setter
    public static class ProviderOverride {
        private String apiKey;
        private String baseUrl;
        private Double temperature;
        private Double timeout;
    }

    @Getter
    @Setter
    public static class RuntimeConfig {
        private String role = "member";
        private boolean autoServe = false;
    }

    @Getter
    @Setter
    public static class ToolConfig {
        private List<String> exclude = List.of();
    }
}
