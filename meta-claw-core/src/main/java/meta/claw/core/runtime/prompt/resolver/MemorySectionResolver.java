package meta.claw.core.runtime.prompt.resolver;

import meta.claw.core.runtime.prompt.SectionRegistry;
import org.springframework.stereotype.Component;

@Component
public class MemorySectionResolver implements SectionResolver {

    @Override
    public boolean supports(SectionRegistry section) {
        return section == SectionRegistry.PREFERENCES;
    }

    @Override
    public String resolve(SectionRegistry section, ResolutionContext ctx) {
        // Phase 1: placeholder. Will integrate with actual memory store in later task.
        return "";
    }
}
