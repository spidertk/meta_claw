package meta.claw.core.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.*;
import meta.claw.core.util.ProjectRootFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
    @Autowired
    private  GlobalConfigLoader globalConfigLoader;
    @Autowired
    private  VesselConfigLoader vesselConfigLoader;



    public List<ResolvedVesselConfig> resolveAll() {
        Path baseDir = ProjectRootFinder.getMetaClawDir();

        Path vesselsDir = baseDir.resolve("vessels");
        if (!Files.exists(vesselsDir) || !Files.isDirectory(vesselsDir)) {
            log.warn("Vessel 配置目录不存在: {}", vesselsDir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(vesselsDir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(p -> resolve( p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描 Vessel 配置目录失败: {}", vesselsDir, e);
            return Collections.emptyList();
        }
    }
    public MemoryConfig loadMemoryConfig( String vesselName) {

        ResolvedVesselConfig  globalConfig =   resolve(vesselName);
        if (globalConfig == null) {
            throw new IllegalStateException(String.format("全局配置配置未找到vesselName=%s",vesselName) );
        }
        if (globalConfig.getVesselConfig() == null) {
            throw new IllegalStateException(String.format("vessel配置配置未找到vesselName=%s ",vesselName) );
        }
        return globalConfig.getVesselConfig().getMemory();

    }

    public ProviderConfig loadProviderConfig(String vesselName) {

        ResolvedVesselConfig  globalConfig =   resolve(vesselName);
        if (globalConfig == null) {
            throw new IllegalStateException(String.format("vessel配置未找到vesselName=%s ",vesselName) );
        }
        return globalConfig.getProviderConfig();

    }


    public VesselConfig loadVesselConfig(String vesselName) {

        ResolvedVesselConfig  globalConfig =   resolve(vesselName);
        if (globalConfig == null) {
            throw new IllegalStateException(String.format("vessel配置未找到vesselName=%s",vesselName) );
        }
        return globalConfig.getVesselConfig();

    }

    public ResolvedVesselConfig resolve( String vesselName) {
        Path baseDir = ProjectRootFinder.getMetaClawDir();
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


        String apiKey = merged.getApiKey();
        if (apiKey == null || apiKey.isBlank() || "your-api-key".equals(apiKey)) {
            throw new IllegalArgumentException("API key not set for provider 'meta-claw config set providers." + providerName + ".api_key <your-key>' to configure.");
        }

        String model = merged.getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model not set for provider 'meta-claw config set providers." + providerName + ".model <model-name>' to configure.");
        }

        ResolvedVesselConfig result = new ResolvedVesselConfig();
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
