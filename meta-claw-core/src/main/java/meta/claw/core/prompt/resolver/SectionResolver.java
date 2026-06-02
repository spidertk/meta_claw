package meta.claw.core.prompt.resolver;

import meta.claw.core.prompt.SectionRegistry;

public interface SectionResolver {
    boolean supports(SectionRegistry section);
    String resolve(SectionRegistry section, ResolutionContext ctx);
}
