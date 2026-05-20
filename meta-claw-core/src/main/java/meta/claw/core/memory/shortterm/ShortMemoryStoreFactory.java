package meta.claw.core.memory.shortterm;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 短期记忆 Store 工厂。通过 Spring 上下文自动发现所有 ShortMemoryStore 实现。
 */
@Slf4j
@Component
public class ShortMemoryStoreFactory implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Map<String, ShortMemoryStore> stores = new HashMap<>();
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
        // 从 Spring 上下文中获取所有 ShortMemoryStore 实现
        Map<String, ShortMemoryStore> allStores = applicationContext.getBeansOfType(ShortMemoryStore.class);
        
        log.info("Found {} ShortMemoryStore implementation(s): {}", allStores.size(), allStores.keySet());
        
        // 构建 type -> store 的映射，并检查 type 重名
        this.stores = new HashMap<>();
        Map<String, String> typeToBeanName = new HashMap<>(); // 用于检测 type 重名
        
        for (Map.Entry<String, ShortMemoryStore> entry : allStores.entrySet()) {
            String beanName = entry.getKey();
            ShortMemoryStore store = entry.getValue();
            String type = store.type();
            
            log.debug("Registering ShortMemoryStore: beanName={}, type={}", beanName, type);
            
            // 检查 type 是否已经存在
            if (typeToBeanName.containsKey(type)) {
                String existingBeanName = typeToBeanName.get(type);
                String errorMsg = String.format(
                    "Duplicate ShortMemoryStore type '%s' found! Bean names: '%s' and '%s'. " +
                    "Each ShortMemoryStore implementation must return a unique type().",
                    type, existingBeanName, beanName
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            
            typeToBeanName.put(type, beanName);
            this.stores.put(type, store);
        }
        
        log.info("Successfully registered {} ShortMemoryStore(s) with types: {}", 
                 stores.size(), stores.keySet());
    }

    /**
     * 按配置自动选择实现（默认 "jsonl"）。
     */
    public ShortMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        return getStore(type);
    }

    /**
     * 按类型标识获取实现。
     */
    /**
     * 运行时注册单个 Store（主要用于测试场景）。
     */
    public void registerStore(String type, ShortMemoryStore store) {
        if (this.stores == null) {
            this.stores = new HashMap<>();
        }
        this.stores.put(type, store);
    }

    public ShortMemoryStore getStore(String type) {
        ShortMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No ShortMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
