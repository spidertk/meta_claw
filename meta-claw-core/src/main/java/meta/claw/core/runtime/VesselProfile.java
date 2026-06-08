package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.config.resolver.RuntimeConfigResolver;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import meta.claw.core.vessel.VesselProfileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Vessel 配置画像（内置子系统，name="profile", priority=0）。
 * <p>替代原 PromptContext，作为 Vessel 配置的缓存入口和基础 prompt 变量贡献者。</p>
 */
@Slf4j
@Component
@Scope("prototype")
public class VesselProfile implements VesselSubSystem {

    @Autowired
    private RuntimeConfigResolver runtimeConfigResolver;
    @Autowired
    private VesselProfileLoader profileLoader;

    private String vesselId;
    private VesselConfigBundle bundle;
    private SubSystemRegistry registry;

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int priority() {
        return 0;
    }

    /** 由 VesselRuntime 在注册后调用，完成本 Vessel 的配置加载 */
    public void loadForVessel(String vesselId) {
        this.vesselId = vesselId;
        Path baseDir = ProjectRootFinder.getMetaClawDir();
        Path vesselDir = baseDir.resolve("vessels").resolve(vesselId);

        RuntimeConfig runtime = runtimeConfigResolver.resolve(vesselId);
        meta.claw.core.vessel.VesselProfile profile = loadProfileSafe(vesselDir);
        Path workspaceDir = vesselDir.resolve("workspace");

        this.bundle = VesselConfigBundle.builder()
                .vesselConfig(runtime.getVesselConfig())
                .vesselProfile(profile)
                .runtimeConfig(runtime)
                .workspaceDir(workspaceDir)
                .build();
    }

    @Override
    public PromptVars promptVars() {
        if (bundle == null) {
            return PromptVars.empty();
        }
        return PromptVars.builder()
                .vars(java.util.Map.of(
                        "vessel_name", bundle.getVesselName(),
                        "vessel_description", bundle.getVesselDescription(),
                        "identity", bundle.getIdentity(),
                        "soul", bundle.getSoul(),
                        "capabilities", bundle.getCapabilities(),
                        "guidelines", bundle.getGuidelines(),
                        "domain_knowledge", bundle.getDomainKnowledge(),
                        "preferences", bundle.getPreferences(),
                        "workspace", bundle.getWorkspaceDir() != null ? bundle.getWorkspaceDir().toString() : ""
                ))
                .build();
    }

    public VesselConfigBundle getBundle() {
        return bundle;
    }

    private meta.claw.core.vessel.VesselProfile loadProfileSafe(Path vesselDir) {
        try {
            return profileLoader.load(vesselDir);
        } catch (Exception e) {
            log.warn("Vessel profile not found for {}, using empty profile: {}", vesselId, e.getMessage());
            return meta.claw.core.vessel.VesselProfile.builder().build();
        }
    }
}
