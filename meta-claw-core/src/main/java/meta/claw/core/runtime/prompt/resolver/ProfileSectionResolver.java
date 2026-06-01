package meta.claw.core.runtime.prompt.resolver;

import meta.claw.core.runtime.prompt.SectionRegistry;
import meta.claw.core.user.VesselProfile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProfileSectionResolver implements SectionResolver {

    private static final Map<SectionRegistry, String> SECTION_HEADING = Map.of(
            SectionRegistry.IDENTITY, "Identity",
            SectionRegistry.SOUL, "Soul",
            SectionRegistry.CAPABILITIES, "Capabilities",
            SectionRegistry.GUIDELINES, "Guidelines",
            SectionRegistry.KNOWLEDGE, "Domain Knowledge"
    );

    @Override
    public boolean supports(SectionRegistry section) {
        return SECTION_HEADING.containsKey(section);
    }

    @Override
    public String resolve(SectionRegistry section, ResolutionContext ctx) {
        VesselProfile profile = ctx.getVesselProfile();
        if (profile == null) {
            return "";
        }
        String content = profile.getSection(section.getId());
        if (content == null || content.isBlank()) {
            return "";
        }
        String heading = SECTION_HEADING.get(section);
        return "## " + heading + "\n\n" + content;
    }
}
