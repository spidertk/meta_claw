package meta.claw.core.llm.provider;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 客户端代理工厂管理器。
 * <p>
 * 根据 provider 名称路由到对应的 {@link LlmClientProviderManager} 实现。
 * 收集所有 Spring 容器中注册的工厂实现，按优先级选择第一个匹配的工厂。
 * </p>
 */
@Slf4j
@Component
public class LlmClientProviderManager implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Map<String, LlmClientProvider> allProviders = new HashMap<>();
    private boolean initialized = false;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    /**
     * 监听 ContextRefreshedEvent 事件，在 Spring 容器刷新完成后执行。
     * 此时所有 bean 都已初始化完成，可以安全地获取所有 ShortMemoryStore 实现。
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 防止重复初始化（在父子容器场景下可能会触发多次）
        if (initialized) {
            return;
        }

        // 只处理根容器的刷新事件
        if (event.getApplicationContext().getParent() == null) {
            initializeStores();
            initialized = true;
        }
    }

    private void initializeStores() {
        // 从 Spring 上下文中获取所有 LlmClientProvider 实现
        Map<String, LlmClientProvider> allProviders = applicationContext.getBeansOfType(LlmClientProvider.class);

        log.info("Found {} LlmClientProvider implementation(s): {}", allProviders.size(), allProviders.keySet());

        // 构建 type -> store 的映射，并检查 type 重名
        this.allProviders = new HashMap<>();
        Map<String, String> typeToBeanName = new HashMap<>(); // 用于检测 type 重名

        for (Map.Entry<String, LlmClientProvider> entry : allProviders.entrySet()) {
            String beanName = entry.getKey();
            LlmClientProvider provider = entry.getValue();
            String providerName = provider.providerName();

            log.debug("Registering LlmClientProvider: beanName={}, providerName={}", beanName, providerName);

            // 检查 type 是否已经存在
            if (typeToBeanName.containsKey(providerName)) {
                String existingBeanName = typeToBeanName.get(providerName);
                String errorMsg = String.format(
                        "Duplicate LlmClientProvider type '%s' found! Bean names: '%s' and '%s'. " +
                                "Each LlmClientProvider implementation must return a unique type().",
                        providerName, existingBeanName, beanName
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            typeToBeanName.put(providerName, beanName);
            this.allProviders.put(providerName, provider);
        }

        log.info("Successfully registered {} LlmClientProvider(s) with types: {}",
                allProviders.size(), allProviders.keySet());
    }



    /**
     * 根据 provider 名称创建 ChatClient
     *
     * @param providerConfig provider 配置
     * @return ChatClient 实例
     * @throws IllegalArgumentException 如果没有找到支持该 provider 的工厂
     */
    public ChatClient create(ProviderConfig providerConfig) {
        LlmClientProvider provider = resolveProvider(providerConfig);
        log.debug("Routing provider '{}' to factory: {}", provider.providerName(), provider.getClass().getSimpleName());
        return provider.create(providerConfig);
    }

    /**
     * 根据 provider 名称创建不带自动 tool-call advisor 的 ChatClient
     *
     * @param providerConfig provider 配置
     * @return ChatClient 实例
     * @throws IllegalArgumentException 如果没有找到支持该 provider 的工厂
     */
    public ChatClient createRaw(ProviderConfig providerConfig) {
        LlmClientProvider provider = resolveProvider(providerConfig);
        log.debug("Routing provider '{}' to raw ChatClient factory: {}", provider.providerName(), provider.getClass().getSimpleName());
        return provider.createRaw(providerConfig);
    }

    /**
     * 根据 provider 名称创建 ChatModel，供 Spring AI Alibaba ReactAgent 直接消费。
     *
     * @param providerConfig provider 配置
     * @return ChatModel 实例
     * @throws IllegalArgumentException 如果没有找到支持该 provider 的工厂
     */
    public ChatModel createChatModel(ProviderConfig providerConfig) {
        LlmClientProvider provider = resolveProvider(providerConfig);
        log.debug("Routing provider '{}' to ChatModel factory: {}", provider.providerName(), provider.getClass().getSimpleName());
        return provider.createChatModel(providerConfig);
    }

    private LlmClientProvider resolveProvider(ProviderConfig providerConfig) {
        if (allProviders == null || allProviders.isEmpty()) {
            String errorMsg = String.format(
                    "No LlmClientProvider supports provider: '%s'",
                    providerConfig.getProvider()
            );
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        LlmClientProvider provider = allProviders.get(providerConfig.getProvider());
        if (provider == null) {
            String errorMsg = String.format(
                    "No LlmClientProvider supports provider: '%s'. Available providers: %s",
                    providerConfig.getProvider(),
                    allProviders.values().stream().map(LlmClientProvider::providerName).toList()
            );
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        return provider;
    }

}
