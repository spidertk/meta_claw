package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.GlobalConfigLoader;
import org.springframework.stereotype.Component;
import meta.claw.core.config.VesselConfigLoader;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.GlobalConfig;
import meta.claw.core.config.ProviderConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class VesselConfigResolver {

    private final GlobalConfigLoader globalConfigLoader;
    private final VesselConfigLoader vesselConfigLoader;

    public VesselConfigResolver() {
        this(new GlobalConfigLoader(), new VesselConfigLoader());
    }

    public VesselConfigResolver(GlobalConfigLoader globalConfigLoader,
                                 VesselConfigLoader vesselConfigLoader) {
        this.globalConfigLoader = globalConfigLoader;
        this.vesselConfigLoader = vesselConfigLoader;
    }

    public List<ResolvedVesselConfig> resolveAll(Path baseDir) {
        Path vesselsDir = baseDir.resolve("vessels");
        if (!Files.exists(vesselsDir) || !Files.isDirectory(vesselsDir)) {
            log.warn("Vessel 配置目录不存在: {}", vesselsDir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(vesselsDir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(p -> resolve(baseDir, p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描 Vessel 配置目录失败: {}", vesselsDir, e);
            return Collections.emptyList();
        }
    }

    public ResolvedVesselConfig resolve(Path baseDir, String vesselName) {
        // 1. 加载全局配置
        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new IllegalStateException("全局配置未找到或 providers 为空: " + baseDir.resolve("config.yaml"));
        }

        // 2. 加载 Vessel 配置（config.yaml + vessel.md，含 vessel 级 provider 覆盖）
        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselConfig vesselConfig = vesselConfigLoader.loadFromVesselDir(vesselDir);

        // 3. 确定 providerName
        String providerName = (vesselConfig != null && vesselConfig.getProvider() != null && !vesselConfig.getProvider().isBlank())
                ? vesselConfig.getProvider()
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

        // 6. 代理配置，通过员工配置覆盖
        ProviderConfig merged = copyProviderConfig(baseProviderConfig);

        merged.setApiKey(vesselConfig != null && !StringUtils.isBlank(vesselConfig.getApiKey())
                ? vesselConfig.getApiKey()
                : merged.getApiKey());
        merged.setBaseUrl(vesselConfig != null && !StringUtils.isBlank(vesselConfig.getBaseUrl())
                ? vesselConfig.getBaseUrl()
                : merged.getBaseUrl());
        merged.setModel(vesselConfig != null && !StringUtils.isBlank(vesselConfig.getModel())
                ? vesselConfig.getModel()
                : merged.getModel());
        merged.setTemperature(vesselConfig != null && vesselConfig.getTemperature() != null
                ? vesselConfig.getTemperature()
                : merged.getTemperature());
        merged.setTimeout(vesselConfig != null && vesselConfig.getTimeout() != null
                ? vesselConfig.getTimeout()
                : merged.getTimeout());
        merged.setProvider(vesselConfig != null && !StringUtils.isBlank(vesselConfig.getProvider())
                ? vesselConfig.getProvider()
                : merged.getProvider());

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
