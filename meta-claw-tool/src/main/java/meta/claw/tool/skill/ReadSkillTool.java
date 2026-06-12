package meta.claw.tool.skill;

import meta.claw.core.runtime.subsystem.SkillSubSystem;
import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 供 LLM 按需读取 Skill 完整内容的工具。
 */
@ToolService
public class ReadSkillTool {

    @Autowired
    private SkillSubSystem skillSubSystem;

    @Tool(description = "读取指定技能的完整指令文档。当需要使用某个技能时调用此工具。")
    public String readSkill(@ToolParam(description = "技能名称，如 travel-planner") String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "Error: skill name is empty";
        }
        return skillSubSystem.readSkillContent(skillName);
    }
}
