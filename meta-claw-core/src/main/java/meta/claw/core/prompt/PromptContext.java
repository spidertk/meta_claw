package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import meta.claw.core.spi.llm.SpiToolDefinition;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {
    private String vesselName;
    private String vesselDescription;
    private String identity;
    private String soul;
    private String capabilities;
    private String guidelines;
    @Builder.Default
    private List<SpiToolDefinition> tools = Collections.emptyList();
    @Builder.Default
    private List<SkillInfo> skills = Collections.emptyList();
    @Builder.Default
    private String knowledge = "";
    @Builder.Default
    private String preferences = "";
    private Path workspaceDir;
    private String currentTime;
    private String location;
    @Builder.Default
    private Map<String, String> runtimeInfo = Collections.emptyMap();
}
