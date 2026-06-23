package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 全局配置模型。
 * <p>
 * 映射 ~/.meta-claw/config.yaml 的结构。
 * 所有 Vessel 的默认配置来源；Vessel 可通过 vessel.meta.yaml 中的
 * {@link VesselConfig.LlmConfig#overrides} 覆盖其中任意字段。
 * </p>
 *
 * @see ProviderConfig
 * @see VesselConfig.LlmConfig
 * @see meta.claw.core.config.loader.GlobalConfigLoader
 */
@Getter
@Setter
public class GlobalConfig {

    /** 默认使用的 provider 名称，必须对应 {@link #providers} 中的某个 key */
    private String defaultProvider;

    /**
     * Provider 配置映射表。
     * <p>key = provider 名称（如 openai、moonshot、deepseek），
     * value = 该 provider 的完整连接配置。</p>
     */
    private Map<String, ProviderConfig> providers;

    /**
     * 是否开启 DEBUG 日志（仅作用于 meta.claw 包）。
     * <p>true 时输出详细内部调用链路，生产环境建议保持 false。</p>
     */
    private Boolean logDebug;

    /**
     * 全局 HITL 审批策略默认配置。
     * <p>Vessel 级 vessel.meta.yaml 中的 hitl 字段可覆盖此处任意字段；
     * 未覆盖字段由本全局配置提供。</p>
     */
    private HitlConfig hitl = new HitlConfig();
}
