package meta.claw.core.config;

/**
 * Vessel 多 Agent 编排模式。
 */
public enum AgentFlowMode {

    /** 顺序执行：子 Agent 按列表顺序依次处理输入 */
    SEQUENTIAL,

    /** 并行执行：子 Agent 同时处理输入，结果按合并策略聚合 */
    PARALLEL,

    /** 路由执行：由 LLM 根据用户输入选择最合适的子 Agent */
    ROUTING
}
