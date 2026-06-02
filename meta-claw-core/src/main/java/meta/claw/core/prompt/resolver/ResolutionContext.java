package meta.claw.core.prompt.resolver;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.config.VesselMeta;
import meta.claw.core.vessel.VesselProfile;

import java.nio.file.Path;

@Getter
@Builder
public class ResolutionContext {
    private VesselMeta vesselMeta;
    private VesselProfile vesselProfile;
    private Path workspaceDir;
    private String currentTime;
    private String location;
}
