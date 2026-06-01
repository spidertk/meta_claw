package meta.claw.core.runtime.prompt;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.runtime.prompt.resolver.ResolutionContext;
import meta.claw.core.runtime.prompt.resolver.SectionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class PromptAssembler {

    private static final String SYSTEM_TEMPLATE = "/templates/runtime/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "/templates/runtime/context.tmpl.md";

    @Autowired
    private List<SectionResolver> resolvers;

    public String assembleSystem(ResolutionContext ctx) {
        return assemble(loadTemplate(SYSTEM_TEMPLATE), SectionRegistry.Target.SYSTEM, ctx);
    }

    public String assembleContext(ResolutionContext ctx) {
        return assemble(loadTemplate(CONTEXT_TEMPLATE), SectionRegistry.Target.CONTEXT, ctx);
    }

    String assemble(String template, SectionRegistry.Target target, ResolutionContext ctx) {
        String result = template;
        for (SectionRegistry section : SectionRegistry.forTarget(target)) {
            String content = resolveSection(section, ctx);
            result = result.replace("<SECTION id=\"" + section.getId() + "\"/>", content);
        }
        result = result.replaceAll("<SECTION id=\"[^\"]+\"\\s*/>", "").trim();
        return result;
    }

    private String resolveSection(SectionRegistry section, ResolutionContext ctx) {
        for (SectionResolver resolver : resolvers) {
            if (resolver.supports(section)) {
                return resolver.resolve(section, ctx);
            }
        }
        if (section.isRequired()) {
            log.warn("No resolver found for required section: {}", section.getId());
        }
        return "";
    }

    private String loadTemplate(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }
}
