package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.nio.file.Path;


import meta.claw.core.infra.config.MemoryConfig;
import meta.claw.core.infra.config.ProviderConfig;
import meta.claw.core.user.VesselMeta;
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {
    private Path vesselsDir;
    private String vesselName;
    private String vesselDescription;
    private String identity;
    private String soul;
    private String capabilities;
    private String guidelines;

    @Builder.Default
    private String knowledge = "";

    private Path workspaceDir;


    private MemoryConfig memoryConfig;
    private ProviderConfig providerConfig;
    private VesselMeta vesselMeta;
}
