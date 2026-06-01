package meta.claw.core.prompt;

import meta.claw.core.infra.path.ProjectRootFinder;
import meta.claw.core.runtime.config.RuntimeConfig;
import meta.claw.core.runtime.config.RuntimeConfigResolver;
import meta.claw.core.user.VesselMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class PromptContextFactory {

    @Autowired
    private RuntimeConfigResolver resolver;

    public PromptContext create(String vesselId) {
        RuntimeConfig runtime = resolver.resolve(vesselId);
        VesselMeta meta = runtime.getVesselMeta();
        Path baseDir = ProjectRootFinder.getMetaClawDir();
        Path vesselsDir = baseDir.resolve("vessels");
        Path workspaceDir = meta != null && meta.getMeta().getId() != null
                ? vesselsDir.resolve(meta.getMeta().getId()).resolve("workspace")
                : Path.of(".");

        return PromptContext.builder()
                .vesselsDir(vesselsDir)
                .vesselName(orDefault(meta != null ? meta.getMeta().getName() : null, "Vessel"))
                .vesselDescription(orDefault(meta != null ? meta.getMeta().getDescription() : null, ""))
                .workspaceDir(workspaceDir)
                .memoryConfig(runtime.getMemoryConfig())
                .providerConfig(runtime.getProviderConfig())
                .vesselMeta(meta)
                .build();
    }

    private static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
