package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.advisor.MetaClawResponseCallAdvisor;
import meta.claw.core.llm.advisor.MetaClawResponseStreamAdvisor;
import meta.claw.core.llm.advisor.ShortMemoryAdvisor;
import meta.claw.core.llm.advisor.ToolRegistryAdvisor;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import meta.claw.core.tool.registry.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 客户端代理工厂管理器。
 * <p>
 * 根据 provider 名称路由到对应的 {@link LlmClientProvider} 实现，并在基础 {@link ChatClient}
 * 上统一装配公共 Advisor 栈（tool registry、response extraction、metrics、short memory 等）。
 * 各 provider 实现只需关心如何创建基础 ChatClient/ChatModel，无需重复装配 cross-cutting Advisors。
 * </p>
 */
@Slf4j
@Component
public class LlmClientProviderManager implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Map<String, LlmClientProvider> allProviders = new HashMap<>();
    private boolean initialized = false;

    /**
     * ChatClient 缓存：相同配置（baseUrl + model + temperature + apiKeyHash）复用已创建的实例。
     */
    private final ConcurrentHashMap<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    private Advisor[] defaultAdvisors;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    /**
     * 监听 ContextRefreshedEvent 事件，在 Spring 容器刷新完成后执行。
     * 此时所有 bean 都已初始化完成，可以安全地获取所有 LlmClientProvider 实现并装配公共 Advisors。
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 防止重复初始化（在父子容器场景下可能会触发多次）
        if (initialized) {
            return;
        }

        // 只处理根容器的刷新事件
        if (event.getApplicationContext().getParent() == null) {
            initializeProviders();
            initializeAdvisors();
            initialized = true;
        }
    }

    private void initializeProviders() {
        // 从 Spring 上下文中获取所有 LlmClientProvider 实现
        Map<String, LlmClientProvider> allProviders = applicationContext.getBeansOfType(LlmClientProvider.class);

        log.info("Found {} LlmClientProvider implementation(s): {}", allProviders.size(), allProviders.keySet());

        // 构建 type -> provider 的映射，并检查 type 重名
        this.allProviders = new HashMap<>();
        Map<String, String> typeToBeanName = new HashMap<>();

        for (Map.Entry<String, LlmClientProvider> entry : allProviders.entrySet()) {
            String beanName = entry.getKey();
            LlmClientProvider provider = entry.getValue();
            String providerName = provider.providerName();

            log.debug("Registering LlmClientProvider: beanName={}, providerName={}", beanName, providerName);

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

    private void initializeAdvisors() {
        ToolRegistry toolRegistry = applicationContext.getBean(ToolRegistry.class);
        ObjectProvider<MetricsRecorder> metricsRecorderProvider = applicationContext.getBeanProvider(MetricsRecorder.class);
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        ShortMemoryAdvisor shortMemoryAdvisor = applicationContext.getBean(ShortMemoryAdvisor.class);

        this.defaultAdvisors = new Advisor[] {
                shortMemoryAdvisor,                                    // 流式响应持久化到 ShortMemory
                new ToolRegistryAdvisor(toolRegistry),                 // 注入可用工具定义（internalToolExecutionEnabled=false）
                new MetaClawResponseCallAdvisor(                       // 同步响应提取与指标
                        metricsRecorderProvider.getIfAvailable(), objectMapper),
                new MetaClawResponseStreamAdvisor(                     // 流式响应提取与指标
                        metricsRecorderProvider.getIfAvailable(), objectMapper)
        };

        log.info("Initialized default ChatClient advisor stack with {} advisor(s)", defaultAdvisors.length);
    }

    /**
     * 根据 provider 名称创建带公共 Advisor 栈的 ChatClient。
     *
     * @param providerConfig provider 配置
     * @return ChatClient 实例
     * @throws IllegalArgumentException 如果没有找到支持该 provider 的工厂
     */
    public ChatClient create(ProviderConfig providerConfig) {
        String cacheKey = buildCacheKey(providerConfig);
        return clientCache.computeIfAbsent(cacheKey, k -> buildAdvisedChatClient(providerConfig));
    }

    private ChatClient buildAdvisedChatClient(ProviderConfig providerConfig) {
        LlmClientProvider provider = resolveProvider(providerConfig);
        ChatClient baseClient = provider.create(providerConfig);
        ChatClient advisedClient = baseClient.mutate()
                .defaultAdvisors(defaultAdvisors)
                .build();
        log.debug("Built advised ChatClient for provider '{}' with {} default advisor(s)",
                provider.providerName(), defaultAdvisors.length);
        return advisedClient;
    }

    /**
     * 根据 provider 名称创建不带公共 Advisor 栈的 ChatClient，供测试或需要完全手动控制的场景使用。
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

    /**
     * 构建缓存 key：baseUrl + model + temperature + timeout + apiKeyHash。
     * 任一配置项变化都会创建新的 ChatClient。
     */
    private String buildCacheKey(ProviderConfig config) {
        return String.join("#",
                String.valueOf(config.getBaseUrl()),
                String.valueOf(config.getModel()),
                String.valueOf(config.getTemperature()),
                String.valueOf(config.getTimeout()),
                String.valueOf(config.getApiKey() != null ? config.getApiKey().hashCode() : 0));
    }
}
