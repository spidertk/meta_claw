package meta.claw.core.runtime.prompt;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public enum SectionRegistry {

    META("meta", Source.VESSEL_META, Target.SYSTEM, true),
    IDENTITY("identity", Source.VESSEL_PROFILE, Target.SYSTEM, true),
    SOUL("soul", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    CAPABILITIES("capabilities", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    GUIDELINES("guidelines", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    KNOWLEDGE("knowledge", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    WORKSPACE("workspace", Source.RUNTIME, Target.CONTEXT, false),
    RUNTIME("runtime", Source.RUNTIME, Target.CONTEXT, false),
    PREFERENCES("preferences", Source.MEMORY, Target.CONTEXT, false);

    private final String id;
    private final Source source;
    private final Target target;
    private final boolean required;

    SectionRegistry(String id, Source source, Target target, boolean required) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.required = required;
    }

    public static Optional<SectionRegistry> byId(String id) {
        return Arrays.stream(values())
                .filter(s -> s.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public static List<SectionRegistry> forTarget(Target target) {
        return Arrays.stream(values())
                .filter(s -> s.target == target)
                .toList();
    }

    public enum Source {
        VESSEL_META,
        VESSEL_PROFILE,
        RUNTIME,
        MEMORY
    }

    public enum Target {
        SYSTEM,
        CONTEXT
    }
}
