package meta.claw.core.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Prompt 统一渲染引擎。
 * <p>
 * 合并原 PromptAssembler + PromptRuntimeBuilder 的职责，
 * 直接从 PromptContext（通过 VesselConfigBundle）取值，
 * 用占位符替换生成最终 prompt，无需 SectionRegistry / SectionResolver 中间层。
 * </p>
 */
@Slf4j
@Component
public class PromptRenderer {

    private static final String SYSTEM_TEMPLATE = "/templates/runtime/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "/templates/runtime/context.tmpl.md";

    public String renderSystem(PromptContext ctx) {
        return render(loadTemplate(SYSTEM_TEMPLATE), ctx);
    }

    public String renderContext(PromptContext ctx) {
        return render(loadTemplate(CONTEXT_TEMPLATE), ctx);
    }

    String render(String template, PromptContext ctx) {
        if (ctx.getBundle() == null) {
            log.warn("PromptContext has no bundle, returning empty prompt");
            return "";
        }

        String result = template
                .replace("{vessel_name}",        ctx.getBundle().getVesselName())
                .replace("{vessel_description}", ctx.getBundle().getVesselDescription())
                .replace("{identity}",           sectionOrEmpty(ctx.getBundle().getIdentity(), "Identity"))
                .replace("{soul}",               sectionOrEmpty(ctx.getBundle().getSoul(), "Soul"))
                .replace("{capabilities}",       sectionOrEmpty(ctx.getBundle().getCapabilities(), "Capabilities"))
                .replace("{guidelines}",         sectionOrEmpty(ctx.getBundle().getGuidelines(), "Guidelines"))
                .replace("{domain_knowledge}",   sectionOrEmpty(ctx.getBundle().getDomainKnowledge(), "Domain Knowledge"))
                .replace("{workspace}",          workspaceSection(ctx))
                .replace("{current_time}",       orEmpty(ctx.getCurrentTime()))
                .replace("{location}",           orEmpty(ctx.getLocation()))
                .replace("{preferences}",        sectionOrEmpty(ctx.getBundle().getPreferences(), "Preferences"))
                .trim();

        // 清理连续空行，提升可读性
        return result.replaceAll("\n{3,}", "\n\n");
    }

    private String sectionOrEmpty(String content, String heading) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return "## " + heading + "\n\n" + content;
    }

    private String workspaceSection(PromptContext ctx) {
        Path dir = ctx.getBundle() != null ? ctx.getBundle().getWorkspaceDir() : null;
        if (dir == null) {
            return "";
        }
        return "## Workspace\n\nCurrent working directory: `" + dir + "`";
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
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
