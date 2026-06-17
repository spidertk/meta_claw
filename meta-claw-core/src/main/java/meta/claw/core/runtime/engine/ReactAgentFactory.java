package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselAgentConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.engine.alibabahook.MetaClawAgentMetricsHook;
import meta.claw.core.runtime.engine.alibabahook.MetaClawHitlHook;
import meta.claw.core.runtime.engine.alibabahook.MetaClawModelMetricsHook;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired(required = false)
    private BaseCheckpointSaver checkpointSaver;

    public ReactAgent get(TaskContext ctx) {
        return buildSingleAgent(ctx);
    }

    /**
     * 构建 Vessel 主 ReactAgent（单 Agent 模式）。
     */
    public ReactAgent buildSingleAgent(TaskContext ctx) {
        var bundle = ctx.getProfile().getBundle();
        ProviderConfig providerConfig = bundle.getProviderConfig();
        ChatModel chatModel = llmClientProviderManager.createChatModel(providerConfig);

        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
        List<ToolCallback> toolCallbacks = toolSubSystem != null ? toolSubSystem.getToolCallbacks() : List.of();

        return buildReactAgent(ctx, bundle.getVesselName(), bundle.getVesselDescription(),
                chatModel, "", toolCallbacks);
    }

    /**
     * 根据 {@link VesselAgentConfig} 构建子 Agent（多 Agent 编排模式）。
     *
     * <p>子 Agent 可覆盖主模型，可指定独占工具列表；其余配置继承 Vessel 主配置。</p>
     */
    public ReactAgent buildSubAgent(TaskContext ctx, VesselAgentConfig config) {
        var bundle = ctx.getProfile().getBundle();

        ProviderConfig providerConfig = resolveProviderConfig(bundle, config.getModel());
        ChatModel chatModel = llmClientProviderManager.createChatModel(providerConfig);

        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
        List<ToolCallback> toolCallbacks = filterToolCallbacks(toolSubSystem, config.getTools());

        return buildReactAgent(ctx, config.getName(), config.getDescription(),
                chatModel, defaultString(config.getSystemPrompt()), toolCallbacks);
    }

    private ReactAgent buildReactAgent(TaskContext ctx, String name, String description,
                                       ChatModel chatModel, String systemPrompt,
                                       List<ToolCallback> toolCallbacks) {
        MetaClawAgentMetricsHook agentMetricsHook = new MetaClawAgentMetricsHook(ctx, metricsRecorder);
        MetaClawModelMetricsHook modelMetricsHook = new MetaClawModelMetricsHook(ctx, metricsRecorder);
        MetaClawHitlHook hitlHook = new MetaClawHitlHook(ctx);

        var bundle = ctx.getProfile().getBundle();
        boolean checkpointEnabled = bundle.getAlibabaAgentConfig().isCheckpointEnabled();

        var builder = ReactAgent.builder()
                .name(name)
                .description(description)
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(toolCallbacks.toArray(new ToolCallback[0]))
                .hooks(agentMetricsHook, modelMetricsHook, hitlHook);

        if (checkpointEnabled && checkpointSaver != null) {
            builder.saver(checkpointSaver);
        }

        return builder.build();
    }

    private ProviderConfig resolveProviderConfig(VesselConfigBundle bundle, String subAgentModel) {
        ProviderConfig baseConfig = bundle.getProviderConfig();
        if (baseConfig == null) {
            baseConfig = new ProviderConfig();
        }
        if (subAgentModel == null || subAgentModel.isBlank()) {
            return baseConfig;
        }
        ProviderConfig copy = copyProviderConfig(baseConfig);
        copy.setModel(subAgentModel);
        return copy;
    }

    private ProviderConfig copyProviderConfig(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        copy.setProvider(source.getProvider());
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setTimeout(source.getTimeout());
        return copy;
    }

    private List<ToolCallback> filterToolCallbacks(ToolSubSystem toolSubSystem, List<String> allowedTools) {
        List<ToolCallback> all = toolSubSystem != null ? toolSubSystem.getToolCallbacks() : List.of();
        if (allowedTools == null || allowedTools.isEmpty()) {
            return all;
        }
        Set<String> allowed = allowedTools.stream()
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toSet());
        return all.stream()
                .filter(tc -> allowed.contains(tc.getToolDefinition().name()))
                .collect(Collectors.toList());
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    /**
     * 保留以兼容现有调用方；当前实现每次请求都重新构建 ReactAgent，因此无需手动失效。
     */
    public void invalidate(String vesselId) {
        // no-op: per-request build makes cache invalidation unnecessary
    }
}
