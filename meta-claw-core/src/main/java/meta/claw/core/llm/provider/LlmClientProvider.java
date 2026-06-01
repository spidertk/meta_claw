package meta.claw.core.llm.provider;

import meta.claw.core.infra.config.ProviderConfig;
import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM 客户端工厂接口。
 * <p>
 * 根据 ProviderConfig 创建对应的 Spring AI ChatClient。
 * 支持 OpenAI 兼容协议、Anthropic、Ollama 等不同 provider。
 * </p>
 */
public interface LlmClientProvider {

    /**
     * 创建 ChatClient
     *
     * @param providerConfig 全局 provider 配置（apiKey, baseUrl 等）
     * @return Spring AI ChatClient 实例
     */
    ChatClient create(ProviderConfig providerConfig);

    /**
     *  代理名称
     ** @return 代理名称 
     */
    String providerName();
}
