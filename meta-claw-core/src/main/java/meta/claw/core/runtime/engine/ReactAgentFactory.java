package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 {@link TaskContext} 构造 SAA {@link ReactAgent} 实例。
 *
 * <p>每个 Vessel 缓存一个 ReactAgent；工具/HITL/模型配置变化后可通过 {@link #invalidate(String)} 重建。</p>
 */
@Component
public class ReactAgentFactory {

    @Autowired
    private LlmClientProviderManager llmClientProviderManager;

    private final Map<String, ReactAgent> cache = new ConcurrentHashMap<>();

    public ReactAgent get(TaskContext ctx) {
        String vesselId = ctx.getTask().getVesselId();
        return cache.computeIfAbsent(vesselId, id -> build(ctx));
    }

    private ReactAgent build(TaskContext ctx) {
        var bundle = ctx.getProfile().getBundle();
        ProviderConfig providerConfig = bundle.getProviderConfig();
        ChatModel chatModel = llmClientProviderManager.createChatModel(providerConfig);

        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
        List<ToolCallback> toolCallbacks = toolSubSystem != null ? toolSubSystem.getToolCallbacks() : List.of();

        return ReactAgent.builder()
                .name(bundle.getVesselName())
                .description(bundle.getVesselDescription())
                .model(chatModel)
                .systemPrompt("") // system prompt 已由 VesselRuntime 组装进 messages
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .build();
    }

    public void invalidate(String vesselId) {
        cache.remove(vesselId);
    }
}
