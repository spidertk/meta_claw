package meta.claw.core.runtime.prompt.resolver;

import meta.claw.core.runtime.prompt.SectionRegistry;
import meta.claw.core.user.VesselMeta;
import org.springframework.stereotype.Component;

@Component
public class MetaSectionResolver implements SectionResolver {

    @Override
    public boolean supports(SectionRegistry section) {
        return section == SectionRegistry.META;
    }

    @Override
    public String resolve(SectionRegistry section, ResolutionContext ctx) {
        VesselMeta.MetaInfo meta = ctx.getVesselMeta() != null ? ctx.getVesselMeta().getMeta() : null;
        if (meta == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(orDefault(meta.getName(), "Vessel")).append("\n\n");
        if (meta.getDescription() != null && !meta.getDescription().isBlank()) {
            sb.append(meta.getDescription()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
