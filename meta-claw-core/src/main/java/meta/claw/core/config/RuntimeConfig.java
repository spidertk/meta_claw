package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 运行时配置（合并后的生效配置）。
 * <p>
 * 由 {@link meta.claw.core.config.resolver.RuntimeConfigResolver} 生成，
 * 合并过程：
 * <ol>
 *   <li>读取全局 config.yaml 中的默认 provider 配置</li>
 *   <li>读取 Vessel 级 vessel.meta.yaml 中的 llm.overrides</li>
 *   <li>非空覆盖字段优先使用 Vessel 级值，其余使用全局默认值</li>
 * </ol>
 * 此对象代表"该 Vessel 实际运行时使用的最终配置"。
 * </p>
 *
 * @see meta.claw.core.config.resolver.RuntimeConfigResolver
 * @see VesselConfigBundle
 */
@Getter
@Setter
public class RuntimeConfig {

    /** Vessel 原始元数据（未合并的原始配置，保留用于调试和回显） */
    private VesselMeta vesselMeta;

    /** 合并后的 Provider 配置（含 API 密钥、模型、温度等） */
    private ProviderConfig providerConfig;

    /** 合并后的记忆系统配置 */
    private MemoryConfig memoryConfig;
}
