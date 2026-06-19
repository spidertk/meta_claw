package meta.claw.core.config.bundle;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.config.AgentFlowConfig;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselAgentConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.vessel.VesselProfile;

import java.nio.file.Path;
import java.util.List;

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
        return stringValue(vesselProfile != null ? vesselProfile.getIdentity() : null);
    }

    public String getSoul() {
        return stringValue(vesselProfile != null ? vesselProfile.getSoul() : null);
    }

    public String getCapabilities() {
        return stringValue(vesselProfile != null ? vesselProfile.getCapabilities() : null);
    }

    public String getGuidelines() {
        return stringValue(vesselProfile != null ? vesselProfile.getGuidelines() : null);
    }

    public String getDomainKnowledge() {
        return stringValue(vesselProfile != null ? vesselProfile.getDomainKnowledge() : null);
    }

    public String getPreferences() {
        return stringValue(vesselProfile != null ? vesselProfile.getPreferences() : null);
    }

    private String stringValue(String value) {
        return value != null ? value : "";
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

    public String getAgentEngine() {
        VesselConfig config = getRuntimeVesselConfig();
        if (config != null && config.getAgentEngine() != null && !config.getAgentEngine().isBlank()) {
            return config.getAgentEngine();
        }
        return "native";
    }

    public VesselConfig.AlibabaAgentConfig getAlibabaAgentConfig() {
        VesselConfig config = getRuntimeVesselConfig();
        return config != null && config.getAlibabaAgent() != null
                ? config.getAlibabaAgent()
                : new VesselConfig.AlibabaAgentConfig();
    }

    public List<VesselAgentConfig> getAgents() {
        VesselConfig config = getRuntimeVesselConfig();
        return config != null && config.getAgents() != null
                ? config.getAgents()
                : List.of();
    }

    public AgentFlowConfig getFlow() {
        VesselConfig config = getRuntimeVesselConfig();
        return config != null && config.getFlow() != null
                ? config.getFlow()
                : new AgentFlowConfig();
    }

    public boolean hasAgents() {
        return !getAgents().isEmpty();
    }
}
