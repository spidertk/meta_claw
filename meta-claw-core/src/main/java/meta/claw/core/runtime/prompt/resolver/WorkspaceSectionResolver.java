package meta.claw.core.runtime.prompt.resolver;

import meta.claw.core.runtime.prompt.SectionRegistry;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSectionResolver implements SectionResolver {

    @Override
    public boolean supports(SectionRegistry section) {
        return section == SectionRegistry.WORKSPACE;
    }

    @Override
    public String resolve(SectionRegistry section, ResolutionContext ctx) {
        if (ctx.getWorkspaceDir() == null) {
            return "";
        }
        return "## Workspace\n\nCurrent working directory: `" + ctx.getWorkspaceDir() + "`";
    }
}
