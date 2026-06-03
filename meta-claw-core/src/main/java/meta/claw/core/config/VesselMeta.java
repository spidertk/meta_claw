package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Vessel 元数据配置模型。
 * <p>
 * 映射 vessels/&lt;name&gt;/vessel.meta.yaml 的完整结构。
 * 这是 Vessel 的"结构化配置"——决定它用什么模型、什么记忆后端、什么行为模式。
 * </p>
 * <p>
 * 与 {@link meta.claw.core.vessel.VesselProfile} 的关系：
 * <ul>
 *   <li>VesselMeta（本类）= 机器可读的"硬件配置"：模型、密钥、存储后端、行为开关</li>
 *   <li>VesselProfile = 人可读的"人格说明书"：Identity、Soul、Capabilities 等</li>
 * </ul>
 * 两者共同构成一个 Vessel 的完整定义，由 {@link VesselConfigBundle} 统一聚合。
 * </p>
 *
 * @see meta.claw.core.vessel.VesselProfile
 * @see VesselConfigBundle
 */
@Getter
@Setter
public class VesselMeta {

    /** 基本身份标识（名称、描述、图标等） */
    private MetaInfo meta = new MetaInfo();

    /** 大模型连接配置（provider、模型、覆盖项） */
    private LlmConfig llm = new LlmConfig();

    /** 运行时行为配置（角色、自动服务等） */
    private RuntimeConfig runtime = new RuntimeConfig();

    /** 记忆系统配置（短期/长期存储后端） */
    private MemoryConfig memory = new MemoryConfig();

    /** 工具系统配置（排除列表等） */
    private ToolConfig tools = new ToolConfig();

    /** 短期记忆最大保留轮数（默认 20，超过则丢弃最早的消息） */
    private Integer maxHistoryRounds = 20;

    /** 单次请求最大 token 数（默认 4096） */
    private Integer maxTokens = 4096;

    /**
     * Vessel 基本身份信息。
     * <p>这些字段主要用于 CLI 展示和 prompt 渲染中的自我介绍段落。</p>
     */
    @Getter
    @Setter
    public static class MetaInfo {
        /** Vessel 唯一标识（通常与目录名一致） */
        private String id;
        /** Vessel 短名称 */
        private String name;
        /** 一句话描述 */
        private String description;
        /** 显示名称（CLI 列表、欢迎语中使用，为空则回退到 name） */
        private String displayName;
        /** 表情符号（默认 🤖） */
        private String emoji = "\uD83E\uDD16";
        /** 创建日期（ISO 格式字符串） */
        private String createdAt;
    }

    /**
     * 大模型连接配置。
     * <p>
     * provider 和 model 决定调用哪个模型的哪个版本；
     * overrides 中的非空字段会覆盖全局 config.yaml 中对应 provider 的默认值。
     * </p>
     */
    @Getter
    @Setter
    public static class LlmConfig {
        /** Provider 名称，必须对应全局 config.yaml 中 providers 下的某个 key */
        private String provider = "openapi";
        /** 模型名称（如 gpt-4o、moonshot-v1-8k），为空则使用全局默认 */
        private String model;
        /** Vessel 级覆盖配置（非空字段覆盖全局对应值） */
        private ProviderOverride overrides = new ProviderOverride();
    }

    /**
     * Provider 配置项的 Vessel 级覆盖。
     * <p>所有字段为可选：留空（null 或 ~）表示使用全局 config.yaml 中的默认值。</p>
     */
    @Getter
    @Setter
    public static class ProviderOverride {
        /** API 密钥（覆盖全局） */
        private String apiKey;
        /** 接口基地址（覆盖全局） */
        private String baseUrl;
        /** 采样温度（覆盖全局） */
        private Double temperature;
        /** 请求超时秒数（覆盖全局） */
        private Double timeout;
    }

    /**
     * 运行时行为配置。
     * <p>决定 Vessel 在系统中的角色和自动化行为。</p>
     */
    @Getter
    @Setter
    public static class RuntimeConfig {
        /** 角色：member（普通成员）/ admin（管理员）/ guest（访客） */
        private String role = "member";
        /** 是否自动响应（无需用户触发即主动执行） */
        private boolean autoServe = false;
    }

    /**
     * 工具系统配置。
     */
    @Getter
    @Setter
    public static class ToolConfig {
        /** 要排除的工具名称列表（为空表示不排除任何工具） */
        private List<String> exclude = List.of();
    }
}
