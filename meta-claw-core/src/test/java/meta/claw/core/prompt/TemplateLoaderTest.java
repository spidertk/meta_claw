package meta.claw.core.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateLoaderTest {

    @Test
    void loadSystemTemplate_shouldNotBeNullOrEmpty() {
        TemplateLoader loader = new TemplateLoader();
        String template = loader.loadSystemTemplate();
        assertNotNull(template);
        assertTrue(template.contains("<IDENTITY_SECTION/>"));
        assertTrue(template.contains("<TOOLS_SECTION/>"));
    }

    @Test
    void loadContextTemplate_shouldNotBeNullOrEmpty() {
        TemplateLoader loader = new TemplateLoader();
        String template = loader.loadContextTemplate();
        assertNotNull(template);
        assertTrue(template.contains("<WORKSPACE_SECTION/>"));
        assertTrue(template.contains("<RUNTIME_SECTION/>"));
    }
}
