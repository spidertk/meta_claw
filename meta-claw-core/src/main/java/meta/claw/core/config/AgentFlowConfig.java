package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Vessel 多 Agent 编排配置。
 *
 * <p>决定当 Vessel 配置多个子 Agent 时如何组合它们：顺序、并行或 LLM 路由。</p>
 */
@Getter
@Setter
public class AgentFlowConfig {

    /** 编排模式字符串（sequential / parallel / routing），默认顺序执行 */
    private String mode = "sequential";

    /** 路由模式下的系统提示（告诉 LLM 如何选择子 Agent） */
    private String routingPrompt;

    /** 路由模式下的补充指令（可给出示例与约束） */
    private String instruction;

    /** 路由模式下的兜底子 Agent 名称（当 LLM 无法决策时使用） */
    private String fallbackAgent;

    /**
     * 获取编排模式枚举值。
     */
    public AgentFlowMode getModeEnum() {
        if (mode == null || mode.isBlank()) {
            return AgentFlowMode.SEQUENTIAL;
        }
        return AgentFlowMode.valueOf(mode.toUpperCase());
    }
}
