package meta.claw.core.runtime.prompt.resolver;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.user.VesselMeta;
import meta.claw.core.user.VesselProfile;

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
