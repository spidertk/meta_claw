package meta.claw.core.runtime.skill;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;

/**
 * Skill 领域模型。
 */
@Builder
@Getter
public class Skill {
    private final String name;
    private final String description;
    private final String content;
    private final Path location;
    private final boolean vesselPrivate;
}
