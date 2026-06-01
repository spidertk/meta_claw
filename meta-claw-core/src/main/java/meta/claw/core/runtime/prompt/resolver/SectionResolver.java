package meta.claw.core.runtime.prompt.resolver;

import meta.claw.core.runtime.prompt.SectionRegistry;

public interface SectionResolver {
    boolean supports(SectionRegistry section);
    String resolve(SectionRegistry section, ResolutionContext ctx);
}
