package meta.claw.core.spi.tool;

import meta.claw.core.spi.llm.SpiToolDefinition;

import java.util.List;

/**
 * 工具定义提供者接口。
 * 为 VesselRuntime 等上层组件提供解耦的工具列表查询能力，
 * 避免 core 模块直接依赖 tool 模块的具体实现。
 * <p>
 * 运行时可通过 {@link meta.claw.tool.registry.ToolRegistry} 等实现类提供动态热注入能力。
 * </p>
 */
public interface ToolDefinitionProvider {

    /**
     * 获取当前可用的工具定义列表。
     * 每次调用应返回最新状态，以支持运行时动态增减工具。
     *
     * @return 工具定义列表，不会为 null
     */
    List<SpiToolDefinition> getToolDefinitions();
}
