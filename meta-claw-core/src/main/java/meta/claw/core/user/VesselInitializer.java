package meta.claw.core.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class VesselInitializer {

    private static final String META_TEMPLATE = "/templates/user/vessel.meta.tmpl.yaml";
    private static final String PROFILE_TEMPLATE = "/templates/user/vessel.profile.tmpl.md";

    public void createDefaultVessel(Path vesselsDir) throws IOException {
        createVessel(vesselsDir, "default", "A general-purpose AI assistant");
    }

    public void createVessel(Path vesselsDir, String name, String description) throws IOException {
        Path vesselDir = vesselsDir.resolve(name);
        Files.createDirectories(vesselDir);
        Files.createDirectories(vesselDir.resolve("skills"));
        Files.createDirectories(vesselDir.resolve("knowledge"));
        Files.createDirectories(vesselDir.resolve("conversations"));
        Files.createDirectories(vesselDir.resolve("preferences"));

        Map<String, String> vars = Map.of(
                "name", name,
                "created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "description", description != null ? description : ""
        );

        String metaTemplate = loadTemplate(META_TEMPLATE);
        Files.writeString(vesselDir.resolve("vessel.meta.yaml"), renderTemplate(metaTemplate, vars));

        String profileTemplate = loadTemplate(PROFILE_TEMPLATE);
        Files.writeString(vesselDir.resolve("vessel.profile.md"), renderTemplate(profileTemplate, vars));

        log.info("Created vessel: {}", vesselDir);
    }

    private String loadTemplate(String resourcePath) {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalStateException("Template not found: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }

    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}
