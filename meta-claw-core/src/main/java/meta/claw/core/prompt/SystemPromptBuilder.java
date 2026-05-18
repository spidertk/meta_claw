package meta.claw.core.prompt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 系统提示构建器。
 * 加载模板，用 PromptContext 数据替换占位符，生成完整的系统提示。
 */
@RequiredArgsConstructor
@Component
public class SystemPromptBuilder {

    private final TemplateLoader templateLoader;

    /**
     * 构建完整的系统提示文本。
     *
     * @param context 提示上下文
     * @return 完整的系统提示
     */
    public String build(PromptContext context) {
        String systemPart = buildSystemPart(context);
        String contextPart = buildContextPart(context);
        return systemPart + "\n\n" + contextPart;
    }

    private String buildSystemPart(PromptContext context) {
        String template = templateLoader.loadSystemTemplate();
        template = replaceOrRemove(template, "{vessel_name}", context.getVesselName());
        template = replaceOrRemove(template, "{vessel_description}", context.getVesselDescription());
        template = template.replace("<IDENTITY_SECTION/>", buildIdentitySection(context));
        template = template.replace("<TOOLS_SECTION/>", buildToolsSection(context));
        template = template.replace("<SKILLS_SECTION/>", buildSkillsSection(context));
        template = template.replace("<KNOWLEDGE_SECTION/>", buildKnowledgeSection(context));
        return template.trim();
    }

    private String buildContextPart(PromptContext context) {
        String template = templateLoader.loadContextTemplate();
        template = template.replace("<WORKSPACE_SECTION/>", buildWorkspaceSection(context));
        template = template.replace("<RUNTIME_SECTION/>", buildRuntimeSection(context));
        template = template.replace("<PREFERENCES_SECTION/>", buildPreferencesSection(context));
        template = template.replace("<CONVERSATION_HISTORY_SECTION/>", buildConversationHistorySection(context));
        return template.trim();
    }

    private String buildIdentitySection(PromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Identity\n\n");
        if (!isBlank(context.getIdentity())) {
            sb.append(context.getIdentity()).append("\n\n");
        }
        if (!isBlank(context.getSoul())) {
            sb.append("## Soul\n\n").append(context.getSoul()).append("\n\n");
        }
        if (!isBlank(context.getCapabilities())) {
            sb.append("## Capabilities\n\n").append(context.getCapabilities()).append("\n\n");
        }
        if (!isBlank(context.getGuidelines())) {
            sb.append("## Guidelines\n\n").append(context.getGuidelines()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String buildToolsSection(PromptContext context) {
        if (context.getTools() == null || context.getTools().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Tools\n\n");
        sb.append(context.getTools().stream()
                .map(t -> "- **" + t.getName() + "**: " + orDefault(t.getDescription(), ""))
                .collect(Collectors.joining("\n")));
        return sb.toString();
    }

    private String buildSkillsSection(PromptContext context) {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n\n");
        sb.append(context.getSkills().stream()
                .map(s -> "- **" + s.getName() + "**: " + orDefault(s.getDescription(), ""))
                .collect(Collectors.joining("\n")));
        return sb.toString();
    }

    private String buildKnowledgeSection(PromptContext context) {
        if (isBlank(context.getKnowledge())) {
            return "";
        }
        return "## Domain Knowledge\n\n" + context.getKnowledge();
    }

    private String buildPreferencesSection(PromptContext context) {
        if (isBlank(context.getPreferences())) {
            return "";
        }
        return "## User Preferences\n\n" + context.getPreferences();
    }

    private String buildWorkspaceSection(PromptContext context) {
        if (context.getWorkspaceDir() == null) {
            return "";
        }
        return "## Workspace\n\nCurrent working directory: `" + context.getWorkspaceDir() + "`";
    }

    private String buildRuntimeSection(PromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Runtime\n\n");
        sb.append("- **Current Time**: ").append(orDefault(context.getCurrentTime(), "unknown")).append("\n");
        sb.append("- **Location**: ").append(orDefault(context.getLocation(), "unknown")).append("\n");
        if (context.getRuntimeInfo() != null && !context.getRuntimeInfo().isEmpty()) {
            context.getRuntimeInfo().forEach((k, v) ->
                    sb.append("- **").append(k).append("**: ").append(v).append("\n"));
        }
        return sb.toString().trim();
    }

    private String buildConversationHistorySection(PromptContext context) {
        if (isBlank(context.getConversationSummary())
                && (context.getRecentMessages() == null || context.getRecentMessages().isEmpty())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Conversation History\n\n");
        if (!isBlank(context.getConversationSummary())) {
            sb.append("### Conversation Summary\n\n").append(context.getConversationSummary()).append("\n\n");
        }
        if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
            sb.append("### Recent Messages\n\n");
            context.getRecentMessages().forEach(m -> {
                sb.append("**").append(m.role()).append(":** ").append(orDefault(m.content(), "")).append("\n\n");
            });
        }
        return sb.toString().trim();
    }

    private static String replaceOrRemove(String template, String placeholder, String value) {
        if (isBlank(value)) {
            return template.replace(placeholder + "\n\n", "").replace(placeholder + "\n", "").replace(placeholder, "");
        }
        return template.replace(placeholder, value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
