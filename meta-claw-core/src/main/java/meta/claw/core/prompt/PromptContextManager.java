package meta.claw.core.prompt;

import meta.claw.core.config.VesselConfig;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemoryManager;
import meta.claw.core.memory.longterm.LongMemoryStore;
import meta.claw.core.util.ProjectRootFinder;
import meta.claw.core.vessel.VesselConfigResolver;
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
 */
@Component
public class PromptContextManager {
    @Autowired
    private VesselConfigResolver resolver;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    @Autowired
    private LongMemoryManager longMemoryManager;

    /**
     * 创建 PromptContext，使用注入的 LongMemoryStoreFactory 解析偏好。
     *
     * @param vesselId VesselId
     * @return 构建好的 PromptContext（tools 为空，由调用方补充）
     */
    public PromptContext create(String vesselId) {
        VesselConfig config = resolver.loadVesselConfig(vesselId);
        Path workspaceDir = resolveWorkspaceDir(config);
        Path configDir = ProjectRootFinder.getMetaClawDir();
        Path vesselsDir = configDir.resolve("vessels");
        return PromptContext.builder()
                .vesselsDir(vesselsDir)
                .vesselName(orDefault(config.getName(), "Vessel"))
                .vesselDescription(orDefault(config.getDescription(), ""))
                .identity(orDefault(config.getIdentity(), ""))
                .soul(orDefault(config.getSoul(), ""))
                .capabilities(orDefault(config.getCapabilities(), ""))
                .guidelines(orDefault(config.getGuidelines(), ""))
                .knowledge(orDefault(config.getDomainKnowledge(), ""))
                .preferences(loadPreferences(config))
                .workspaceDir(workspaceDir)
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .runtimeInfo(Collections.emptyMap())
                .memoryConfig(resolver.loadMemoryConfig(vesselId))
                .providerConfig(resolver.loadProviderConfig(vesselId))
                .vesselConfig(resolver.loadVesselConfig(vesselId))
                .build();
    }

    private Path resolveWorkspaceDir(VesselConfig config) {
        if (config.getId() == null) {
            return Path.of(".");
        }
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(config.getId())
                .resolve("workspace");
    }

    private String loadPreferences(VesselConfig config) {
        if (!config.isPreferencesEnabled() || config.getId() == null) {
            return "";
        }
        LongMemoryStore store = longMemoryManager.getStore(config.getMemory());
        List<PreferenceMemory> prefs = store.listRecentPreferences(config.getId(), 10);
        if (prefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PreferenceMemory p : prefs) {
            sb.append("- ").append(p.getContent()).append("\n");
        }
        return sb.toString().trim();
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
