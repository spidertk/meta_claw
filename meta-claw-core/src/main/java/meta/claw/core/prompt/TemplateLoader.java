package meta.claw.core.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TemplateLoader {
    private static final String SYSTEM_TEMPLATE = "templates/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "templates/context.tmpl.md";

    private String systemTemplate;
    private String contextTemplate;

    public String loadSystemTemplate() {
        if (systemTemplate == null) {
            systemTemplate = loadFromClasspath(SYSTEM_TEMPLATE);
        }
        return systemTemplate;
    }

    public String loadContextTemplate() {
        if (contextTemplate == null) {
            contextTemplate = loadFromClasspath(CONTEXT_TEMPLATE);
        }
        return contextTemplate;
    }

    private String loadFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }
}
