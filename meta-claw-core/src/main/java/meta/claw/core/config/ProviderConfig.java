package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 单个 LLM Provider 的连接配置。
 * <p>
 * 作为 {@link GlobalConfig#providers} 的 value 使用，
 * 也可被 {@link VesselMeta.ProviderOverride} 部分覆盖。
 * </p>
 *
 * @see GlobalConfig
 * @see VesselMeta.ProviderOverride
 */
@Getter
@Setter
public class ProviderConfig {

    /** Provider 标识名称（如 openai、moonshot、deepseek） */
    private String provider;

    /** API 密钥（必须替换为真实密钥，禁止提交到版本控制） */
    private String apiKey;

    /** 接口基地址（OpenAI 官方为 https://api.openai.com/v1，第三方中转时修改） */
    private String baseUrl;

    /** 模型名称（如 gpt-4o、moonshot-v1-8k） */
    private String model;

    /**
     * 采样温度。
     * <p>0.0 = 输出最确定、最保守；2.0 = 输出最随机、最有创意。
     * 推荐范围 0.5 ~ 1.0。</p>
     */
    private Double temperature;

    /** 请求超时秒数（连接异常时可适当增大） */
    private Double timeout;
}
