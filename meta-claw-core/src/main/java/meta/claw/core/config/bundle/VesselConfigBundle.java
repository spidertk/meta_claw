package meta.claw.core.config.bundle;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.vessel.VesselProfile;

import java.nio.file.Path;

/**
 * Vessel 配置的统一视图。
 * <p>
 * 把异构的配置源（YAML 结构化元数据、Markdown 段落、运行时合并配置）
 * 封装为一个按 prompt 语义聚合的只读访问入口。
 * 不复制数据，所有字段委托给底层配置对象。
 * </p>
 */
@Getter
@Builder
public class VesselConfigBundle {

    private final VesselConfig vesselConfig;
    private final VesselProfile vesselProfile;
    private final RuntimeConfig runtimeConfig;
    private final Path workspaceDir;

    // ── Meta 便捷访问 ──

    public String getVesselName() {
        return vesselConfig != null && vesselConfig.getIdentity() != null
                ? vesselConfig.getIdentity().getName()
                : "Vessel";
    }

    public String getVesselDescription() {
        return vesselConfig != null && vesselConfig.getIdentity() != null
                ? vesselConfig.getIdentity().getDescription()
                : "";
    }

    public String getDisplayName() {
        return vesselConfig != null && vesselConfig.getIdentity() != null
                ? vesselConfig.getIdentity().getDisplayName()
                : getVesselName();
    }

    public String getEmoji() {
        return vesselConfig != null && vesselConfig.getIdentity() != null
                ? vesselConfig.getIdentity().getEmoji()
                : "\uD83E\uDD16";
    }

    public String getVesselId() {
        return vesselConfig != null && vesselConfig.getIdentity() != null
                ? vesselConfig.getIdentity().getId()
                : null;
    }

    public Integer getMaxHistoryRounds() {
        return vesselConfig != null ? vesselConfig.getMaxHistoryRounds() : 20;
    }

    public Integer getMaxTokens() {
        return vesselConfig != null ? vesselConfig.getMaxTokens() : 4096;
    }

    // ── Profile 便捷访问 ──

    public String getIdentity() {
        return vesselProfile != null ? vesselProfile.getIdentity() : "";
    }

    public String getSoul() {
        return vesselProfile != null ? vesselProfile.getSoul() : "";
    }

    public String getCapabilities() {
        return vesselProfile != null ? vesselProfile.getCapabilities() : "";
    }

    public String getGuidelines() {
        return vesselProfile != null ? vesselProfile.getGuidelines() : "";
    }

    public String getDomainKnowledge() {
        return vesselProfile != null ? vesselProfile.getDomainKnowledge() : "";
    }

    public String getPreferences() {
        return vesselProfile != null ? vesselProfile.getPreferences() : "";
    }

    // ── Runtime 便捷访问 ──

    public ProviderConfig getProviderConfig() {
        return runtimeConfig != null ? runtimeConfig.getProviderConfig() : null;
    }

    public MemoryConfig getMemoryConfig() {
        return runtimeConfig != null ? runtimeConfig.getMemoryConfig() : null;
    }

    public VesselConfig getRuntimeVesselConfig() {
        return runtimeConfig != null ? runtimeConfig.getVesselConfig() : vesselConfig;
    }
}
