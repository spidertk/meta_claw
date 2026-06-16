package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.engine.alibabahook.MetaClawAgentMetricsHook;
import meta.claw.core.runtime.engine.alibabahook.MetaClawModelMetricsHook;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 根据 {@link TaskContext} 构造 SAA {@link ReactAgent} 实例。
 *
 * <p>每个请求单独构建 ReactAgent，确保 Metrics / HITL Hook 能绑定到当前 {@link TaskContext}。
 * 若后续发现构建开销过大，可改为按 Vessel 缓存无状态部分，但需保证 Hook 中的 TaskContext 不会跨请求串用。</p>
 */
@Component
public class ReactAgentFactory {

    @Autowired
    private LlmClientProviderManager llmClientProviderManager;

    @Autowired(required = false)
    private MetricsRecorder metricsRecorder;

    public ReactAgent get(TaskContext ctx) {
        return build(ctx);
    }

    private ReactAgent build(TaskContext ctx) {
        var bundle = ctx.getProfile().getBundle();
        ProviderConfig providerConfig = bundle.getProviderConfig();
        ChatModel chatModel = llmClientProviderManager.createChatModel(providerConfig);

        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
        List<ToolCallback> toolCallbacks = toolSubSystem != null ? toolSubSystem.getToolCallbacks() : List.of();

        MetaClawAgentMetricsHook agentMetricsHook = new MetaClawAgentMetricsHook(ctx, metricsRecorder);
        MetaClawModelMetricsHook modelMetricsHook = new MetaClawModelMetricsHook(ctx, metricsRecorder);

        return ReactAgent.builder()
                .name(bundle.getVesselName())
                .description(bundle.getVesselDescription())
                .model(chatModel)
                .systemPrompt("") // system prompt 已由 VesselRuntime 组装进 messages
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .hooks(agentMetricsHook, modelMetricsHook)
                .build();
    }

    /**
     * 保留以兼容现有调用方；当前实现每次请求都重新构建 ReactAgent，因此无需手动失效。
     */
    public void invalidate(String vesselId) {
        // no-op: per-request build makes cache invalidation unnecessary
    }
}
