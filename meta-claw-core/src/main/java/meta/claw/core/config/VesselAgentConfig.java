package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Vessel 子 Agent 配置。
 *
 * <p>用于 {@code agent_engine: alibaba} 多 Agent 编排场景，每个子 Agent 可以拥有
 * 独立的名称、描述、模型与系统提示。</p>
 */
@Getter
@Setter
public class VesselAgentConfig {

    /** 子 Agent 唯一名称（在 Vessel 内唯一） */
    private String name;

    /** 子 Agent 描述（用于路由 prompt 与日志） */
    private String description;

    /** 子 Agent 使用的模型名称（可选，为空则使用 Vessel 主模型） */
    private String model;

    /** 子 Agent 的系统提示 */
    private String systemPrompt;

    /** 子 Agent 独占的工具名称列表（为空则继承 Vessel 全部工具） */
    private List<String> tools;
}
