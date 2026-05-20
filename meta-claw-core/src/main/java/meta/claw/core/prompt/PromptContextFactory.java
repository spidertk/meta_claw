package meta.claw.core.prompt;

import lombok.RequiredArgsConstructor;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.spi.llm.SpiToolDefinition;
import meta.claw.core.spi.tool.ToolDefinitionProvider;
import meta.claw.core.spi.workspace.WorkspaceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 从 VesselConfig 构建 PromptContext 的工厂。
 * 负责提取 Vessel 配置、格式化运行时信息、读取用户偏好。
 * <p>
 * 工作区目录与可用工具列表由注入的 {@link WorkspaceProvider} 和 {@link ToolDefinitionProvider}
 * 在内部自动解析，调用方只需传入 {@link VesselConfig}。
 * </p>
 */
@Component
public class PromptContextFactory {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final WorkspaceProvider workspaceProvider;
    private final ToolDefinitionProvider toolProvider;

    @Autowired(required = false)
    private PreferenceProvider preferenceProvider;

    public PromptContextFactory(WorkspaceProvider workspaceProvider, ToolDefinitionProvider toolProvider) {
        this.workspaceProvider = workspaceProvider;
        this.toolProvider = toolProvider;
    }

    /**
     * 创建 PromptContext，使用注入的 {@link PreferenceProvider}（如有）。
     *
     * @param config Vessel 配置
     * @return 构建好的 PromptContext
     */
    public PromptContext create(VesselConfig config) {
        return create(config, preferenceProvider);
    }

    /**
     * 创建 PromptContext，使用指定的 {@link PreferenceProvider}。
     *
     * @param config              Vessel 配置
     * @param overridePreferenceProvider 覆盖的偏好提供者，优先于注入的实例
     * @return 构建好的 PromptContext
     */
    public PromptContext create(VesselConfig config, PreferenceProvider overridePreferenceProvider) {
        Path workspaceDir = resolveWorkspaceDir(config);
        List<SpiToolDefinition> tools = resolveTools();

        return PromptContext.builder()
                .vesselName(orDefault(config.getName(), "Vessel"))
                .vesselDescription(orDefault(config.getDescription(), ""))
                .identity(orDefault(config.getIdentity(), ""))
                .soul(orDefault(config.getSoul(), ""))
                .capabilities(orDefault(config.getCapabilities(), ""))
                .guidelines(orDefault(config.getGuidelines(), ""))
                .knowledge(orDefault(config.getDomainKnowledge(), ""))
                .preferences(loadPreferences(config, overridePreferenceProvider))
                .workspaceDir(workspaceDir)
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .runtimeInfo(Collections.emptyMap())
                .tools(tools)
                .build();
    }

    private Path resolveWorkspaceDir(VesselConfig config) {
        if (workspaceProvider != null && config.getId() != null) {
            Path dir = workspaceProvider.getWorkspaceDir(config.getId());
            if (dir != null) {
                return dir;
            }
        }
        // Fallback: current directory when no provider available
        return Path.of(".");
    }

    private List<SpiToolDefinition> resolveTools() {
        if (toolProvider != null) {
            return toolProvider.getToolDefinitions();
        }
        return Collections.emptyList();
    }

    private String loadPreferences(VesselConfig config, PreferenceProvider provider) {
        PreferenceProvider effective = provider != null ? provider : preferenceProvider;
        if (!config.isPreferencesEnabled() || config.getId() == null || effective == null) {
            return "";
        }
        return effective.getPreferences(config.getId());
    }

    private String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    private String detectLocation() {
        ZoneId zone = ZoneId.systemDefault();
        return zone.getId();
    }

    private static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
