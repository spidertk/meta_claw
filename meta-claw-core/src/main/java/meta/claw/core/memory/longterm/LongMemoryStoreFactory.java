package meta.claw.core.memory.longterm;

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
 * 长期记忆 Store 工厂。通过 Spring 上下文自动发现所有 LongMemoryStore 实现。
 */
@Slf4j
@Component
public class LongMemoryStoreFactory implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Map<String, LongMemoryStore> stores = new HashMap<>();
    private boolean initialized = false;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized) {
            return;
        }
        if (event.getApplicationContext().getParent() == null) {
            initializeStores();
            initialized = true;
        }
    }

    private void initializeStores() {
        Map<String, LongMemoryStore> allStores = applicationContext.getBeansOfType(LongMemoryStore.class);
        log.info("Found {} LongMemoryStore implementation(s): {}", allStores.size(), allStores.keySet());

        this.stores = new HashMap<>();
        Map<String, String> typeToBeanName = new HashMap<>();

        for (Map.Entry<String, LongMemoryStore> entry : allStores.entrySet()) {
            String beanName = entry.getKey();
            LongMemoryStore store = entry.getValue();
            String type = store.type();

            log.debug("Registering LongMemoryStore: beanName={}, type={}", beanName, type);

            if (typeToBeanName.containsKey(type)) {
                String existingBeanName = typeToBeanName.get(type);
                String errorMsg = String.format(
                    "Duplicate LongMemoryStore type '%s' found! Bean names: '%s' and '%s'. " +
                    "Each LongMemoryStore implementation must return a unique type().",
                    type, existingBeanName, beanName
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            typeToBeanName.put(type, beanName);
            this.stores.put(type, store);
        }

        log.info("Successfully registered {} LongMemoryStore(s) with types: {}",
                 stores.size(), stores.keySet());
    }

    public LongMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getLongTermStore() != null
                ? config.getLongTermStore() : "file";
        return getStore(type);
    }

    /**
     * 运行时注册单个 Store（主要用于测试场景）。
     */
    public void registerStore(String type, LongMemoryStore store) {
        if (this.stores == null) {
            this.stores = new HashMap<>();
        }
        this.stores.put(type, store);
    }

    public LongMemoryStore getStore(String type) {
        LongMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No LongMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
