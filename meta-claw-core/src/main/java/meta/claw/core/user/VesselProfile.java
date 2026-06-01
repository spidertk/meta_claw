package meta.claw.core.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

@Getter
@Builder
public class VesselProfile {

    @Singular
    private Map<String, String> sections;

    public String getSection(String name) {
        return sections != null ? sections.get(name) : null;
    }

    public String getIdentity() {
        return getSection("identity");
    }

    public String getSoul() {
        return getSection("soul");
    }

    public String getDomainKnowledge() {
        return getSection("domain knowledge");
    }

    public String getCapabilities() {
        return getSection("capabilities");
    }

    public String getGuidelines() {
        return getSection("guidelines");
    }

    public String getPreferences() {
        return getSection("preferences");
    }
}
