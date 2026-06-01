package meta.claw.core.user;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class VesselProfileLoader {

    private static final String PROFILE_FILE = "vessel.profile.md";

    public VesselProfile load(Path vesselDir) {
        Path profilePath = vesselDir.resolve(PROFILE_FILE);
        if (!Files.exists(profilePath)) {
            log.warn("Vessel profile not found: {}", profilePath);
            throw new VesselException(ErrorCode.VESSEL_PROFILE_NOT_FOUND, profilePath);
        }
        try {
            String content = Files.readString(profilePath);
            Map<String, String> sections = parseSections(content);
            return VesselProfile.builder().sections(sections).build();
        } catch (IOException e) {
            log.error("Failed to load vessel profile: {}", profilePath, e);
            throw new VesselException(ErrorCode.VESSEL_PROFILE_PARSE_ERROR, e, profilePath);
        }
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> sections = new HashMap<>();
        String[] lines = content.split("\n");
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                if (currentSection != null) {
                    sections.put(currentSection, currentContent.toString().trim());
                }
                currentSection = trimmed.substring(3).trim().toLowerCase();
                currentContent = new StringBuilder();
            } else if (currentSection != null) {
                currentContent.append(line).append("\n");
            }
        }
        if (currentSection != null) {
            sections.put(currentSection, currentContent.toString().trim());
        }
        return sections;
    }
}
