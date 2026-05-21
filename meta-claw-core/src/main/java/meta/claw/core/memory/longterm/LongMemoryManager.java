package meta.claw.core.memory.longterm;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.PreferenceMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import meta.claw.core.vessel.VesselConfigResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 长期记忆编排器
 */
@Slf4j
@Component
public class LongMemoryManager  implements ApplicationContextAware, ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private VesselConfigResolver resolver;
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


    public void addPreference(MemoryConfig config, String vesselId, PreferenceMemory entry) {
        getStore( resolver.loadMemoryConfig(vesselId)).addPreference(vesselId, entry);
    }

    public List<PreferenceMemory> lookupPreference(MemoryConfig config, String vesselId, String query) {
        return  getStore( resolver.loadMemoryConfig(vesselId)).lookupPreference(vesselId, query);
    }

    public List<PreferenceMemory> listRecentPreferences(MemoryConfig config, String vesselId, int limit) {
        return  getStore( resolver.loadMemoryConfig(vesselId)).listRecentPreferences(vesselId, limit);
    }

    public boolean deletePreference(MemoryConfig config, String vesselId, String preferenceId) {
        return  getStore( resolver.loadMemoryConfig(vesselId)).deletePreference(vesselId, preferenceId);
    }

    public boolean clearPreferences(MemoryConfig config, String vesselId) {
        return  getStore( resolver.loadMemoryConfig(vesselId)).clearPreferences(vesselId);
    }
}
