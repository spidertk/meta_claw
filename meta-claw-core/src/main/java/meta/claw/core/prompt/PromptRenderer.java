package meta.claw.core.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt 统一渲染引擎。
 * <p>纯函数渲染器：接收 Map&lt;String, String&gt; 模板变量，返回最终 prompt 文本。</p>
 */
@Slf4j
@Component
public class PromptRenderer {

    private static final String SYSTEM_TEMPLATE = "/templates/runtime/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "/templates/runtime/context.tmpl.md";

    public String renderSystem(Map<String, String> vars) {
        return render(stripHtmlComments(loadTemplate(SYSTEM_TEMPLATE)), vars);
    }

    public String renderContext(Map<String, String> vars) {
        return render(stripHtmlComments(loadTemplate(CONTEXT_TEMPLATE)), vars);
    }

    private String stripHtmlComments(String template) {
        if (template == null) {
            return "";
        }
        return template.replaceAll("<!--[\\s\\S]*?-->", "").trim();
    }

    String render(String template, Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            log.warn("Empty prompt vars, returning empty prompt");
            return "";
        }

        String result = template
                .replace("{vessel_name}",        orEmpty(vars.get("vessel_name")))
                .replace("{vessel_description}", orEmpty(vars.get("vessel_description")))
                .replace("{identity}",           sectionOrEmpty(vars.get("identity"), "Identity"))
                .replace("{soul}",               sectionOrEmpty(vars.get("soul"), "Soul"))
                .replace("{capabilities}",       sectionOrEmpty(vars.get("capabilities"), "Capabilities"))
                .replace("{guidelines}",         sectionOrEmpty(vars.get("guidelines"), "Guidelines"))
                .replace("{domain_knowledge}",   sectionOrEmpty(vars.get("domain_knowledge"), "Domain Knowledge"))
                .replace("{workspace}",          workspaceSection(vars.get("workspace")))
                .replace("{current_time}",       orEmpty(vars.get("current_time")))
                .replace("{location}",           orEmpty(vars.get("location")))
                .replace("{preferences}",        sectionOrEmpty(vars.get("preferences"), "Preferences"))
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

    private String workspaceSection(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return "";
        }
        return "## Workspace\n\nCurrent working directory: `" + workspacePath + "`";
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
