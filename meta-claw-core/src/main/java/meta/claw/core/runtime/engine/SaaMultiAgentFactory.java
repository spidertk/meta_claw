package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent.MergeStrategy;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent.ConcatenationMergeStrategy;
import meta.claw.core.config.AgentFlowConfig;
import meta.claw.core.config.AgentFlowMode;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselAgentConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据 Vessel 多 Agent 配置构建 Spring AI Alibaba FlowAgent。
 *
 * <p>支持三种编排模式：</p>
 * <ul>
 *   <li>{@code sequential}：子 Agent 按顺序依次处理输入</li>
 *   <li>{@code parallel}：子 Agent 并行处理输入，结果按合并策略聚合</li>
 *   <li>{@code routing}：由 LLM 根据用户输入选择最合适的子 Agent</li>
 * </ul>
 */
@Component
public class SaaMultiAgentFactory {

    @Autowired
    private ReactAgentFactory reactAgentFactory;

    @Autowired
    private LlmClientProviderManager llmClientProviderManager;

    /**
     * 根据当前 {@link TaskContext} 构建对应的 FlowAgent。
     *
     * @param ctx 任务上下文
     * @return FlowAgent 实例
     * @throws IllegalStateException 当配置不包含子 Agent 或模式不支持时
     */
    public Agent get(TaskContext ctx) {
        VesselConfigBundle bundle = ctx.getProfile().getBundle();
        if (!bundle.hasAgents()) {
            throw new IllegalStateException("Vessel does not define any sub-agents: " + bundle.getVesselId());
        }

        List<VesselAgentConfig> agentConfigs = bundle.getAgents();
        List<Agent> subAgents = new ArrayList<>(agentConfigs.size());
        for (VesselAgentConfig config : agentConfigs) {
            subAgents.add(reactAgentFactory.buildSubAgent(ctx, config));
        }

        AgentFlowConfig flow = bundle.getFlow();
        AgentFlowMode mode = flow.getModeEnum();

        return switch (mode) {
            case SEQUENTIAL -> buildSequentialAgent(bundle, subAgents);
            case PARALLEL -> buildParallelAgent(bundle, subAgents, flow);
            case ROUTING -> buildRoutingAgent(bundle, subAgents, flow);
        };
    }

    private Agent buildSequentialAgent(VesselConfigBundle bundle, List<Agent> subAgents) {
        return SequentialAgent.builder()
                .name(flowAgentName(bundle))
                .description(defaultString(bundle.getVesselDescription()))
                .subAgents(subAgents)
                .build();
    }

    private Agent buildParallelAgent(VesselConfigBundle bundle, List<Agent> subAgents, AgentFlowConfig flow) {
        ParallelAgent.ParallelAgentBuilder builder = ParallelAgent.builder()
                .name(flowAgentName(bundle))
                .description(defaultString(bundle.getVesselDescription()))
                .subAgents(subAgents);

        MergeStrategy mergeStrategy = resolveMergeStrategy(flow);
        if (mergeStrategy != null) {
            builder.mergeStrategy(mergeStrategy);
        }
        return builder.build();
    }

    private MergeStrategy resolveMergeStrategy(AgentFlowConfig flow) {
        // 当前使用 ConcatenationMergeStrategy 作为默认合并策略；未来可从 flow 配置读取策略名称。
        return new ConcatenationMergeStrategy("\n");
    }

    private Agent buildRoutingAgent(VesselConfigBundle bundle, List<Agent> subAgents, AgentFlowConfig flow) {
        ProviderConfig providerConfig = bundle.getProviderConfig();
        if (providerConfig == null) {
            throw new IllegalStateException("Routing agent requires a provider config for vessel: " + bundle.getVesselId());
        }
        ChatModel routingModel = llmClientProviderManager.createChatModel(providerConfig);

        LlmRoutingAgent.LlmRoutingAgentBuilder builder = LlmRoutingAgent.builder()
                .name(flowAgentName(bundle))
                .description(defaultString(bundle.getVesselDescription()))
                .model(routingModel)
                .systemPrompt(defaultString(flow.getRoutingPrompt()))
                .instruction(defaultString(flow.getInstruction()))
                .subAgents(subAgents);

        if (flow.getFallbackAgent() != null && !flow.getFallbackAgent().isBlank()) {
            builder.fallbackAgent(flow.getFallbackAgent());
        }

        return builder.build();
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private String flowAgentName(VesselConfigBundle bundle) {
        String name = bundle.getVesselName();
        return (name != null && !name.isBlank()) ? name : "vessel";
    }
}
