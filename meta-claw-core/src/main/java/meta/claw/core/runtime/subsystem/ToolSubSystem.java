package meta.claw.core.runtime.subsystem;

import meta.claw.core.prompt.PromptVars;
import meta.claw.core.tool.registry.ToolRegistry;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool 子系统。
 * <p>聚合本地 @Tool 工具与 MCP 客户端工具，向 prompt 贡献工具列表，并向执行引擎提供 ToolCallback。</p>
 */
@Component
public class ToolSubSystem implements VesselSubSystem {

    @Autowired
    private ToolRegistry toolRegistry;
    @Autowired(required = false)
    private List<ToolCallbackProvider> mcpToolProviders;

    private SubSystemRegistry registry;

    @Override
    public String name() {
        return "tool";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PromptVars promptVars() {
        List<ToolCallback> callbacks = getToolCallbacks();
        if (callbacks.isEmpty()) {
            return PromptVars.empty();
        }
        String toolsText = callbacks.stream()
                .map(tc -> "- " + tc.getToolDefinition().name()
                        + ": " + tc.getToolDefinition().description())
                .collect(Collectors.joining("\n"));
        return PromptVars.of("tools", toolsText);
    }

    @Override
    public int priority() {
        return 20;
    }

    /**
     * 获取本 Vessel 可用的所有 ToolCallback（本地工具 + MCP 工具）。
     */
    public List<ToolCallback> getToolCallbacks() {
        List<ToolCallback> all = new ArrayList<>();

        Object[] localBeans = toolRegistry.getToolInstances().toArray();
        if (localBeans.length > 0) {
            all.addAll(Arrays.asList(ToolCallbacks.from(localBeans)));
        }

        if (mcpToolProviders != null) {
            for (ToolCallbackProvider provider : mcpToolProviders) {
                all.addAll(Arrays.asList(provider.getToolCallbacks()));
            }
        }

        return all;
    }
}
