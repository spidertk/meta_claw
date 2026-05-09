package meta.claw.core.spi.llm;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ProviderConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 客户端工厂管理器。
 * <p>
 * 根据 provider 名称路由到对应的 {@link LlmClientFactory} 实现。
 * 收集所有 Spring 容器中注册的工厂实现，按优先级选择第一个匹配的工厂。
 * </p>
 */
@Slf4j
@Component
public class LlmClientFactoryManager {

    private final List<LlmClientFactory> factories;

    public LlmClientFactoryManager(List<LlmClientFactory> factories) {
        this.factories = factories != null ? factories : List.of();
        log.debug("Loaded {} LlmClientFactory implementations", this.factories.size());
    }

    /**
     * 根据 provider 名称创建 ChatClient
     *
     * @param providerName   provider 名称，如 "moonshot", "openai"
     * @param providerConfig provider 配置
     * @return ChatClient 实例
     * @throws IllegalArgumentException 如果没有找到支持该 provider 的工厂
     */
    public ChatClient create(String providerName, ProviderConfig providerConfig) {
        for (LlmClientFactory factory : factories) {
            if (factory.supports(providerName)) {
                log.debug("Routing provider '{}' to factory: {}", providerName, factory.getClass().getSimpleName());
                return factory.create(providerConfig);
            }
        }
        throw new IllegalArgumentException(
                "No LlmClientFactory supports provider: '" + providerName + "'. " +
                "Available factories: " + factories.stream().map(f -> f.getClass().getSimpleName()).toList()
        );
    }
}
