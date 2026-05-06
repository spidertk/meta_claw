package meta.claw.core.spi.llm;

import meta.claw.core.model.ProviderConfig;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM 客户端工厂接口。
 * <p>
 * 根据 ProviderConfig 创建对应的 Spring AI ChatClient。
 * 支持 OpenAI 兼容协议、Anthropic、Ollama 等不同 provider。
 * </p>
 */
public interface LlmClientFactory {

    /**
     * 创建 ChatClient
     *
     * @param providerConfig 全局 provider 配置（apiKey, baseUrl 等）
     * @param model          实际使用的模型名称（vessel 可覆盖 provider 默认值）
     * @return Spring AI ChatClient 实例
     */
    ChatClient create(ProviderConfig providerConfig, String model);

    /**
     * 是否支持该 provider
     *
     * @param providerName provider 名称，如 "openai", "moonshot", "deepseek"
     * @return true 表示支持
     */
    boolean supports(String providerName);
}
