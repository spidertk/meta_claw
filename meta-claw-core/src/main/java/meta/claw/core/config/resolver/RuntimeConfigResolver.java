package meta.claw.core.config.resolver;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.GlobalConfig;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.loader.GlobalConfigLoader;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.config.loader.VesselConfigLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.Consumer;

@Slf4j
@Component
public class RuntimeConfigResolver {

    @Autowired
    private GlobalConfigLoader globalConfigLoader;
    @Autowired
    private VesselConfigLoader vesselConfigLoader;

    public RuntimeConfig resolve(String vesselName) {
        Path baseDir = ProjectRootFinder.getMetaClawDir();

        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new VesselException(ErrorCode.VESSEL_GLOBAL_CONFIG_EMPTY, baseDir.resolve("config.yaml"));
        }

        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselConfig vesselConfig = vesselConfigLoader.load(vesselDir);

        String providerName = resolveProviderName(vesselConfig, globalConfig);
        ProviderConfig baseProvider = globalConfig.getProviders().get(providerName);
        if (baseProvider == null) {
            throw new VesselException(ErrorCode.VESSEL_PROVIDER_NOT_FOUND, providerName, globalConfig.getProviders().keySet());
        }

        ProviderConfig merged = mergeProviderConfig(baseProvider, vesselConfig);
        validateProviderConfig(merged, providerName);

        RuntimeConfig result = new RuntimeConfig();
        result.setVesselConfig(vesselConfig);
        result.setProviderConfig(merged);
        result.setMemoryConfig(vesselConfig.getMemory());
        return result;
    }

    private String resolveProviderName(VesselConfig vesselConfig, GlobalConfig globalConfig) {
        String name = (vesselConfig != null && vesselConfig.getLlm() != null
                && StringUtils.isNotBlank(vesselConfig.getLlm().getProvider()))
                ? vesselConfig.getLlm().getProvider()
                : globalConfig.getDefaultProvider();
        if (StringUtils.isBlank(name)) {
            name = globalConfig.getProviders().keySet().iterator().next();
        }
        return name;
    }

    private ProviderConfig mergeProviderConfig(ProviderConfig base, VesselConfig vesselConfig) {
        if (vesselConfig == null || vesselConfig.getLlm() == null || vesselConfig.getLlm().getOverrides() == null) {
            return copy(base);
        }
        VesselConfig.ProviderOverride ov = vesselConfig.getLlm().getOverrides();
        ProviderConfig merged = copy(base);
        mergeField(merged::setApiKey, merged.getApiKey(), ov.getApiKey());
        mergeField(merged::setBaseUrl, merged.getBaseUrl(), ov.getBaseUrl());
        mergeField(merged::setModel, merged.getModel(), vesselConfig.getLlm().getModel());
        mergeField(merged::setTemperature, merged.getTemperature(), ov.getTemperature());
        mergeField(merged::setTimeout, merged.getTimeout(), ov.getTimeout());
        return merged;
    }

    private <T> void mergeField(Consumer<T> setter, T current, T override) {
        if (override != null && !(override instanceof String s && s.isBlank())) {
            setter.accept(override);
        }
    }

    private ProviderConfig copy(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        copy.setProvider(source.getProvider());
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setTimeout(source.getTimeout());
        return copy;
    }

    private void validateProviderConfig(ProviderConfig config, String providerName) {
        String apiKey = config.getApiKey();
        if (StringUtils.isBlank(apiKey) || "your-api-key".equals(apiKey)) {
            throw new VesselException(ErrorCode.VESSEL_API_KEY_MISSING, providerName);
        }
        if (StringUtils.isBlank(config.getModel())) {
            throw new VesselException(ErrorCode.VESSEL_MODEL_MISSING, providerName);
        }
    }
}
