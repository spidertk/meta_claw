package meta.claw.core.prompt;

import lombok.RequiredArgsConstructor;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.memory.longterm.LongMemoryStore;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 从 VesselConfig 构建 PromptContext 的工厂。
 * 负责提取 Vessel 配置、格式化运行时信息、读取用户偏好。
 */
@RequiredArgsConstructor
@Component
public class PromptContextFactory {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * 创建 PromptContext。
     *
     * @param config          Vessel 配置
     * @param workspaceDir    当前工作区目录
     * @param preferenceStore 用户偏好存储（可为 null）
     * @return 构建好的 PromptContext
     */
    public PromptContext create(VesselConfig config, Path workspaceDir,
                                 LongMemoryStore preferenceStore) {
        return PromptContext.builder()
                .vesselName(orDefault(config.getName(), "Vessel"))
                .vesselDescription(orDefault(config.getDescription(), ""))
                .identity(orDefault(config.getIdentity(), ""))
                .soul(orDefault(config.getSoul(), ""))
                .capabilities(orDefault(config.getCapabilities(), ""))
                .guidelines(orDefault(config.getGuidelines(), ""))
                .knowledge(orDefault(config.getDomainKnowledge(), ""))
                .preferences(loadPreferences(config, preferenceStore))
                .workspaceDir(workspaceDir)
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .runtimeInfo(Collections.emptyMap())
                .recentMessages(Collections.emptyList())
                .conversationSummary("")
                .build();
    }

    private String loadPreferences(VesselConfig config, LongMemoryStore store) {
        if (store == null || !config.isPreferencesEnabled() || config.getId() == null) {
            return "";
        }
        List<MemoryEntry> entries = store.listRecentPreferences(config.getId(), 20);
        if (entries.isEmpty()) {
            return "";
        }
        return entries.stream()
                .map(e -> "- " + orDefault(e.getContent(), ""))
                .collect(Collectors.joining("\n"));
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
