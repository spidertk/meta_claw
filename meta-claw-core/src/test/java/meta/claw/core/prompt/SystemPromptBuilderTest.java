package meta.claw.core.prompt;

import meta.claw.core.memory.shortterm.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {

    private SystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SystemPromptBuilder(new TemplateLoader());
    }

    @Test
    void build_shouldContainVesselNameAndDescription() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("TestBot")
                .vesselDescription("A test vessel.")
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("TestBot"));
        assertTrue(prompt.contains("A test vessel."));
    }

    @Test
    void build_shouldContainIdentitySection() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .identity("You are a helpful assistant.")
                .soul("Be kind.")
                .capabilities("Code review.")
                .guidelines("Always be polite.")
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Identity"));
        assertTrue(prompt.contains("You are a helpful assistant."));
        assertTrue(prompt.contains("## Soul"));
        assertTrue(prompt.contains("Be kind."));
        assertTrue(prompt.contains("## Capabilities"));
        assertTrue(prompt.contains("Code review."));
        assertTrue(prompt.contains("## Guidelines"));
        assertTrue(prompt.contains("Always be polite."));
    }

    @Test
    void build_shouldContainToolsSection_whenToolsProvided() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .tools(List.of(
                        ToolInfo.builder().name("search").description("Search the web.").build(),
                        ToolInfo.builder().name("calc").description("Calculate expressions.").build()
                ))
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Tools"));
        assertTrue(prompt.contains("- **search**: Search the web."));
        assertTrue(prompt.contains("- **calc**: Calculate expressions."));
    }

    @Test
    void build_shouldNotContainToolsSection_whenNoTools() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .build();

        String prompt = builder.build(ctx);

        assertFalse(prompt.contains("## Tools"));
    }

    @Test
    void build_shouldContainSkillsSection_whenSkillsProvided() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .skills(List.of(
                        SkillInfo.builder().name("java").description("Java programming.").build()
                ))
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Skills"));
        assertTrue(prompt.contains("- **java**: Java programming."));
    }

    @Test
    void build_shouldNotContainSkillsSection_whenNoSkills() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .build();

        String prompt = builder.build(ctx);

        assertFalse(prompt.contains("## Skills"));
    }

    @Test
    void build_shouldSeparateKnowledgePreferencesAndConversationHistory() {
        ChatMessage msg = ChatMessage.builder()
                .role("user")
                .content("Hello")
                .build();
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .knowledge("Domain: AI agents.")
                .preferences("- User likes dark mode.")
                .conversationSummary("User asked about Java.")
                .recentMessages(List.of(msg))
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Domain Knowledge"));
        assertTrue(prompt.contains("Domain: AI agents."));
        assertTrue(prompt.contains("## User Preferences"));
        assertTrue(prompt.contains("- User likes dark mode."));
        assertTrue(prompt.contains("## Conversation History"));
        assertFalse(prompt.contains("## Memory"));
        assertTrue(prompt.indexOf("## User Preferences") > prompt.indexOf("## Runtime"));
        assertTrue(prompt.contains("Conversation Summary"));
        assertTrue(prompt.contains("User asked about Java."));
        assertTrue(prompt.contains("Recent Messages"));
        assertTrue(prompt.contains("Hello"));
    }

    @Test
    void build_shouldContainWorkspaceSection() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .workspaceDir(Paths.get("/tmp/workspace"))
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Workspace"));
        assertTrue(prompt.contains("/tmp/workspace"));
    }

    @Test
    void build_shouldContainRuntimeSection() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .currentTime("2026-05-08 12:00:00 CST")
                .location("Asia/Shanghai")
                .runtimeInfo(Map.of("OS", "macOS"))
                .build();

        String prompt = builder.build(ctx);

        assertTrue(prompt.contains("## Runtime"));
        assertTrue(prompt.contains("2026-05-08 12:00:00 CST"));
        assertTrue(prompt.contains("Asia/Shanghai"));
        assertTrue(prompt.contains("OS"));
        assertTrue(prompt.contains("macOS"));
    }

    @Test
    void build_shouldHandleEmptyContext() {
        PromptContext ctx = PromptContext.builder()
                .vesselName("V")
                .build();

        String prompt = builder.build(ctx);

        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
    }
}
