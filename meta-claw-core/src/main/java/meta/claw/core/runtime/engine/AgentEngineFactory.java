package meta.claw.core.runtime.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 Vessel 配置选择 AgentEngine 实现。
 *
 * <p>所有 {@link AgentEngine} 实现由 Spring 自动收集，按 {@code name()} 注册。
 * 默认引擎为 {@code native}，可通过 {@code agentEngine} 切换。</p>
 */
@Component
public class AgentEngineFactory {

    private final Map<String, AgentEngine> engines = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public void setEngines(List<AgentEngine> engineList) {
        engines.clear();
        if (engineList == null) {
            return;
        }
        for (AgentEngine engine : engineList) {
            AgentEngine previous = engines.put(engine.name(), engine);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate AgentEngine name: " + engine.name());
            }
        }
    }

    public AgentEngine getEngine(String name) {
        AgentEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException(
                    "No AgentEngine for name: " + name + ". Available: " + engines.keySet());
        }
        return engine;
    }

    public AgentEngine getDefaultEngine() {
        return getEngine("native");
    }
}
