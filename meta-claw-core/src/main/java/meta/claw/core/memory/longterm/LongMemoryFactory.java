package meta.claw.core.memory.longterm;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.PreferenceMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 长期记忆工厂注册类
 */
@Slf4j
@Component
public class LongMemoryFactory implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;
    private Map<String, LongMemory> stores = new HashMap<>();
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
        Map<String, LongMemory> allStores = applicationContext.getBeansOfType(LongMemory.class);
        log.info("Found {} LongMemoryStore implementation(s): {}", allStores.size(), allStores.keySet());

        this.stores = new HashMap<>();
        Map<String, String> typeToBeanName = new HashMap<>();

        for (Map.Entry<String, LongMemory> entry : allStores.entrySet()) {
            String beanName = entry.getKey();
            LongMemory store = entry.getValue();
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


    /**
     * 运行时注册单个 Store（主要用于测试场景）。
     */
    public void register(String type, LongMemory store) {
        if (this.stores == null) {
            this.stores = new HashMap<>();
        }
        this.stores.put(type, store);
    }

    public LongMemory get(String memoryType) {
        String type = StringUtils.isBlank(memoryType) ?"file":memoryType;
        LongMemory store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                    "No LongMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }


}
