package meta.claw.core.runtime.config;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import meta.claw.core.infra.config.*;
import meta.claw.core.infra.path.ProjectRootFinder;
import meta.claw.core.user.VesselMeta;
import meta.claw.core.user.VesselMetaLoader;
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
    private VesselMetaLoader vesselMetaLoader;

    public RuntimeConfig resolve(String vesselName) {
        Path baseDir = ProjectRootFinder.getMetaClawDir();

        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new VesselException(ErrorCode.VESSEL_GLOBAL_CONFIG_EMPTY, baseDir.resolve("config.yaml"));
        }

        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselMeta vesselMeta = vesselMetaLoader.load(vesselDir);

        String providerName = resolveProviderName(vesselMeta, globalConfig);
        ProviderConfig baseProvider = globalConfig.getProviders().get(providerName);
        if (baseProvider == null) {
            throw new VesselException(ErrorCode.VESSEL_PROVIDER_NOT_FOUND, providerName, globalConfig.getProviders().keySet());
        }

        ProviderConfig merged = mergeProviderConfig(baseProvider, vesselMeta);
        validateProviderConfig(merged, providerName);

        RuntimeConfig result = new RuntimeConfig();
        result.setVesselMeta(vesselMeta);
        result.setProviderConfig(merged);
        result.setMemoryConfig(vesselMeta.getMemory());
        return result;
    }

    private String resolveProviderName(VesselMeta vesselMeta, GlobalConfig globalConfig) {
        String name = (vesselMeta != null && vesselMeta.getLlm() != null
                && StringUtils.isNotBlank(vesselMeta.getLlm().getProvider()))
                ? vesselMeta.getLlm().getProvider()
                : globalConfig.getDefaultProvider();
        if (StringUtils.isBlank(name)) {
            name = globalConfig.getProviders().keySet().iterator().next();
        }
        return name;
    }

    private ProviderConfig mergeProviderConfig(ProviderConfig base, VesselMeta vesselMeta) {
        if (vesselMeta == null || vesselMeta.getLlm() == null || vesselMeta.getLlm().getOverrides() == null) {
            return copy(base);
        }
        VesselMeta.ProviderOverride ov = vesselMeta.getLlm().getOverrides();
        ProviderConfig merged = copy(base);
        mergeField(merged::setApiKey, merged.getApiKey(), ov.getApiKey());
        mergeField(merged::setBaseUrl, merged.getBaseUrl(), ov.getBaseUrl());
        mergeField(merged::setModel, merged.getModel(), vesselMeta.getLlm().getModel());
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
