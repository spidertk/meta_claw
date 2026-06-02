package meta.claw.core.prompt;

import meta.claw.core.config.RuntimeConfig;
import meta.claw.core.config.VesselConfigBundle;
import meta.claw.core.config.VesselMeta;
import meta.claw.core.config.resolver.RuntimeConfigResolver;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.vessel.VesselProfile;
import meta.claw.core.vessel.VesselProfileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PromptContextFactory {

    @Autowired
    private RuntimeConfigResolver resolver;
    @Autowired
    private VesselProfileLoader profileLoader;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public PromptContext create(String vesselId) {
        RuntimeConfig runtime = resolver.resolve(vesselId);
        VesselMeta meta = runtime.getVesselMeta();

        Path baseDir = ProjectRootFinder.getMetaClawDir();
        Path vesselsDir = baseDir.resolve("vessels");
        Path vesselDir = vesselsDir.resolve(vesselId);
        Path workspaceDir = meta != null && meta.getMeta() != null && meta.getMeta().getId() != null
                ? vesselsDir.resolve(meta.getMeta().getId()).resolve("workspace")
                : Path.of(".");

        // 加载 VesselProfile（Markdown 段落）
        VesselProfile profile = loadProfileSafe(vesselDir);

        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .vesselMeta(meta)
                .vesselProfile(profile)
                .runtimeConfig(runtime)
                .workspaceDir(workspaceDir)
                .build();

        return PromptContext.builder()
                .bundle(bundle)
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .build();
    }

    private VesselProfile loadProfileSafe(Path vesselDir) {
        try {
            return profileLoader.load(vesselDir);
        } catch (Exception e) {
            // Profile 是可选的，缺失时返回空对象
            return VesselProfile.builder().build();
        }
    }

    private static String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    private static String detectLocation() {
        return ZoneId.systemDefault().getId();
    }
}
