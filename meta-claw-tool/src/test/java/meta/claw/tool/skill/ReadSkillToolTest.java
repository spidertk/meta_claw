package meta.claw.tool.skill;

import meta.claw.core.runtime.subsystem.SkillSubSystem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadSkillToolTest {

    @Test
    void returnsSkillContent() {
        ReadSkillTool tool = new ReadSkillTool();
        SkillSubSystem skillSub = mock(SkillSubSystem.class);
        when(skillSub.readSkillContent("travel-planner")).thenReturn("# Travel Planner");
        ReflectionTestUtils.setField(tool, "skillSubSystem", skillSub);

        assertEquals("# Travel Planner", tool.readSkill("travel-planner"));
    }

    @Test
    void rejectsEmptySkillName() {
        ReadSkillTool tool = new ReadSkillTool();
        assertTrue(tool.readSkill("").startsWith("Error"));
    }
}
