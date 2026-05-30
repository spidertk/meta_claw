package meta.claw.core.prompt;

import lombok.RequiredArgsConstructor;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统提示构建器。
 * 加载模板，用 PromptContext 数据替换占位符，生成完整的系统提示。
 */
@RequiredArgsConstructor
@Component
public class PromptRuntimeBuilder {

    @Autowired
    private LongMemoryFactory longMemory;
    private final TemplateLoader templateLoader;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    private String detectLocation() {
        ZoneId zone = ZoneId.systemDefault();
        return zone.getId();
    }

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
//        template = template.replace("<TOOLS_SECTION/>", buildToolsSection(context));
        template = template.replace("<SKILLS_SECTION/>", buildSkillsSection(context));
        template = template.replace("<KNOWLEDGE_SECTION/>", buildKnowledgeSection(context));
        return template.trim();
    }

    private String buildContextPart(PromptContext context) {
        String template = templateLoader.loadContextTemplate();
        template = template.replace("<WORKSPACE_SECTION/>", buildWorkspaceSection(context));
        template = template.replace("<RUNTIME_SECTION/>", buildRuntimeSection(context));
        template = template.replace("<PREFERENCES_SECTION/>", buildPreferencesSection(context));
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

//    private String buildToolsSection(PromptContext context) {
//        if (context.getTools() == null || context.getTools().isEmpty()) {
//            return "";
//        }
//        StringBuilder sb = new StringBuilder();
//        sb.append("## Tools\n\n");
//        sb.append(context.getTools().stream()
//                .map(t -> "- **" + t.getName() + "**: " + orDefault(t.getDescription(), ""))
//                .collect(Collectors.joining("\n")));
//        return sb.toString();
//    }

    private String buildSkillsSection(PromptContext context) {
       List<SkillInfo> skills= loadSkills(context);
        if (CollectionUtils.isEmpty( skills)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n\n");
        sb.append(skills.stream()
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
        String preferences = loadPreferences(context);
        if (isBlank(preferences)) {
            return "";
        }
        return "## User Preferences\n\n" + preferences;
    }
    private  List<SkillInfo>  loadSkills(PromptContext context) {
        return Collections.emptyList();
    }
    private Map<String, String> loadRuntimeInfo(PromptContext context) {

        return Collections.emptyMap();
    }
    private String loadPreferences(PromptContext context) {
         VesselConfig config=  context.getVesselConfig();
        if (!config.isPreferencesEnabled() || config.getId() == null) {
            return "";
        }
        LongMemory store = longMemory.get(context.getMemoryConfig().getShortTermStore());
        List<PreferenceMemory> prefs = store.listRecentPreferences(config.getId(), 10);
        if (prefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PreferenceMemory p : prefs) {
            sb.append("- ").append(p.getContent()).append("\n");
        }
        return sb.toString().trim();
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
        sb.append("- **Current Time**: ").append(orDefault(formatCurrentTime(), "unknown")).append("\n");
        sb.append("- **Location**: ").append(orDefault( detectLocation(), "unknown")).append("\n");
        Map<String,String> runtimeInfo= loadRuntimeInfo(context);
        if (!CollectionUtils.isEmpty(runtimeInfo)) {
            runtimeInfo.forEach((k, v) ->
                    sb.append("- **").append(k).append("**: ").append(v).append("\n"));
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
