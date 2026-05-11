package meta.claw.core.prompt;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillInfo {
    private String name;
    private String description;
    private String location;
}
