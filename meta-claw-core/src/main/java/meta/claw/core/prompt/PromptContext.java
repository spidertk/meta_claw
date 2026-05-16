package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import meta.claw.core.spi.llm.SpiMessage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private List<ToolInfo> tools = Collections.emptyList();
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
    @Builder.Default
    private List<SpiMessage> recentMessages = Collections.emptyList();
    @Builder.Default
    private String conversationSummary = "";
}
