package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;

import java.nio.file.Path;

@Slf4j
public class VesselConfigResolver {

    private final GlobalConfigLoader globalConfigLoader;
    private final VesselProfileLoader profileLoader;
    private final VesselConfigLoader vesselConfigLoader;

    public VesselConfigResolver() {
        this(new GlobalConfigLoader(), new VesselProfileLoader(), new VesselConfigLoader());
    }

    public VesselConfigResolver(GlobalConfigLoader globalConfigLoader,
                                 VesselProfileLoader profileLoader,
                                 VesselConfigLoader vesselConfigLoader) {
        this.globalConfigLoader = globalConfigLoader;
        this.profileLoader = profileLoader;
        this.vesselConfigLoader = vesselConfigLoader;
    }

    public ResolvedVesselConfig resolve(Path baseDir, String vesselName) {
        // 1. 加载全局配置
        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new IllegalStateException("全局配置未找到或 providers 为空: " + baseDir.resolve("config.yaml"));
        }

        // 2. 加载 vessel.md
        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselConfig vesselConfig = vesselConfigLoader.loadSingle(vesselDir.resolve("vessel.md"));

        // 3. 加载 vessel 级覆盖配置
        VesselProfileConfig profile = profileLoader.load(vesselDir);

        // 4. 确定 providerName
        String providerName = (profile != null && profile.getProvider() != null && !profile.getProvider().isBlank())
                ? profile.getProvider()
                : globalConfig.getDefaultProvider();
        if (providerName == null || providerName.isBlank()) {
            providerName = globalConfig.getProviders().keySet().iterator().next();
        }

        // 5. 获取全局 provider 基础配置
        ProviderConfig baseProviderConfig = globalConfig.getProviders().get(providerName);
        if (baseProviderConfig == null) {
            throw new IllegalArgumentException(
                    "全局配置中未找到 provider '" + providerName + "'。可用的 providers: " + globalConfig.getProviders().keySet()
            );
        }

        // 6. 深拷贝 + 合并
        ProviderConfig merged = copyProviderConfig(baseProviderConfig);
        if (profile != null) {
            if (profile.getApiKey() != null && !profile.getApiKey().isBlank()) {
                merged.setApiKey(profile.getApiKey());
            }
            if (profile.getBaseUrl() != null && !profile.getBaseUrl().isBlank()) {
                merged.setBaseUrl(profile.getBaseUrl());
            }
            if (profile.getModel() != null && !profile.getModel().isBlank()) {
                merged.setModel(profile.getModel());
            }
            if (profile.getTemperature() != null) {
                merged.setTemperature(profile.getTemperature());
            }
            if (profile.getTimeout() != null) {
                merged.setTimeout(profile.getTimeout());
            }
        }

        // 7. vessel.md 的 model 作为最低优先级覆盖
        if (vesselConfig != null && vesselConfig.getModel() != null && !vesselConfig.getModel().isBlank()) {
            if (merged.getModel() == null || merged.getModel().isBlank()) {
                merged.setModel(vesselConfig.getModel());
            }
        }

        ResolvedVesselConfig result = new ResolvedVesselConfig();
        result.setProviderName(providerName);
        result.setProviderConfig(merged);
        result.setVesselConfig(vesselConfig);
        return result;
    }

    private ProviderConfig copyProviderConfig(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setTimeout(source.getTimeout());
        return copy;
    }
}
