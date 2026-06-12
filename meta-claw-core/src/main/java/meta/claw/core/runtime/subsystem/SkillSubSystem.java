package meta.claw.core.runtime.subsystem;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.skill.Skill;
import meta.claw.core.runtime.skill.SkillRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Skill 子系统。
 * <p>负责加载系统级与 Vessel 私有 SKILL.md，将可用技能列表注入 prompt，并提供读取技能全文的入口。</p>
 */
@Slf4j
@Component
public class SkillSubSystem implements VesselSubSystem, VesselAwareSubSystem {

    @Autowired
    private SkillRegistry skillRegistry;

    private String vesselId;
    private SubSystemRegistry registry;

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public void loadForVessel(String vesselId) {
        this.vesselId = vesselId;
        skillRegistry.load(vesselId);
        log.debug("Loaded {} skills for vessel {}", skillRegistry.getAvailableSkills().size(), vesselId);
    }

    @Override
    public PromptVars promptVars() {
        if (vesselId == null) {
            return PromptVars.empty();
        }
        String skillsText = skillRegistry.getAvailableSkills().stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        return skillsText.isEmpty() ? PromptVars.empty() : PromptVars.of("skills", skillsText);
    }

    /**
     * 读取指定技能的完整内容。
     */
    public String readSkillContent(String skillName) {
        return skillRegistry.findByName(skillName)
                .map(Skill::getContent)
                .orElse("Skill not found: " + skillName);
    }
}
