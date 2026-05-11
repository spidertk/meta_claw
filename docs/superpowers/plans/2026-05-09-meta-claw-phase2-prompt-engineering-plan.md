# Phase 2 Prompt Engineering + MemoryManager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement template-driven system prompt assembly, context window management, and enhanced session storage.

**Architecture:** Manual section building with String.replace for templates, MemoryManager for truncation, JsonlConversationStore enhancements for filter/stats/media.

**Tech Stack:** Java 21, Maven, JUnit 5, Jackson, Lombok, Spring AI

---

## File Structure

### New files
- `meta-claw-core/src/main/resources/templates/system.tmpl.md`
- `meta-claw-core/src/main/resources/templates/context.tmpl.md`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/TemplateLoader.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/MemoryManager.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/ToolInfo.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/SkillInfo.java`
- `meta-claw-core/src/main/java/meta/claw/core/session/MessageFilter.java`
- `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStats.java`
- `meta-claw-core/src/main/java/meta/claw/core/session/MediaReference.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java`
- `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreEnhancedTest.java`

### Modified files
- `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`
- `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
- `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java`
- `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
- `meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java`

---

## Task 1: Rewrite Template Files

**Files:**
- Create: `meta-claw-core/src/main/resources/templates/system.tmpl.md`
- Create: `meta-claw-core/src/main/resources/templates/context.tmpl.md`
- Delete: old template files if they exist with wrong content

- [ ] **Step 1: Create system.tmpl.md**

```bash
cat > /Users/kai/IdeaProjects/meta_claw/meta-claw-core/src/main/resources/templates/system.tmpl.md << 'EOF'
# {vessel_name}

{vessel_description}

<IDENTITY_SECTION/>

<TOOLS_SECTION/>

<SKILLS_SECTION/>

<KNOWLEDGE_SECTION/>
EOF
```

- [ ] **Step 2: Create context.tmpl.md**

```bash
cat > /Users/kai/IdeaProjects/meta_claw/meta-claw-core/src/main/resources/templates/context.tmpl.md << 'EOF'
<WORKSPACE_SECTION/>

<RUNTIME_SECTION/>

<MEMORY_SECTION/>
EOF
```

- [ ] **Step 3: Verify files exist**

```bash
cat /Users/kai/IdeaProjects/meta_claw/meta-claw-core/src/main/resources/templates/system.tmpl.md
cat /Users/kai/IdeaProjects/meta_claw/meta-claw-core/src/main/resources/templates/context.tmpl.md
```

Expected: Both files show correct content with section markers.

- [ ] **Step 4: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/resources/templates/system.tmpl.md
git add meta-claw-core/src/main/resources/templates/context.tmpl.md
git commit -m "feat(template): add system.tmpl.md and context.tmpl.md for Phase 2"
```

---

## Task 2: Create Data Classes (PromptContext, ToolInfo, SkillInfo)

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/ToolInfo.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/SkillInfo.java`

- [ ] **Step 1: Create PromptContext.java**

```java
package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import meta.claw.core.session.ChatMessage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {
    private String vesselName;
    private String vesselDescription;
    private String identity;
    private String soul;
    private String capabilities;
    private String guidelines;
    @Builder.Default
    private List<ToolInfo> tools = Collections.emptyList();
    @Builder.Default
    private List<SkillInfo> skills = Collections.emptyList();
    @Builder.Default
    private String knowledge = "";
    @Builder.Default
    private String preferences = "";
    private Path workspaceDir;
    private String currentTime;
    private String location;
    @Builder.Default
    private Map<String, String> runtimeInfo = Collections.emptyMap();
    @Builder.Default
    private List<ChatMessage> recentMessages = Collections.emptyList();
    @Builder.Default
    private String conversationSummary = "";
}
```

- [ ] **Step 2: Create ToolInfo.java**

```java
package meta.claw.core.prompt;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ToolInfo {
    private String name;
    private String description;
    private String parametersJsonSchema;
}
```

- [ ] **Step 3: Create SkillInfo.java**

```java
package meta.claw.core.prompt;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillInfo {
    private String name;
    private String description;
    private String location;
}
```

- [ ] **Step 4: Compile to verify**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes"
for jar in $(find ~/.m2/repository -name "lombok-*.jar" | head -1); do CP="$CP:$jar"; done
javac -cp "$CP" -d meta-claw-core/target/classes \
  meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java \
  meta-claw-core/src/main/java/meta/claw/core/prompt/ToolInfo.java \
  meta-claw-core/src/main/java/meta/claw/core/prompt/SkillInfo.java
```

Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java
git add meta-claw-core/src/main/java/meta/claw/core/prompt/ToolInfo.java
git add meta-claw-core/src/main/java/meta/claw/core/prompt/SkillInfo.java
git commit -m "feat(prompt): add PromptContext, ToolInfo, SkillInfo data classes"
```

---

## Task 3: Create TemplateLoader

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/TemplateLoader.java`

- [ ] **Step 1: Create TemplateLoader.java**

```java
package meta.claw.core.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TemplateLoader {
    private static final String SYSTEM_TEMPLATE = "templates/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "templates/context.tmpl.md";

    private String systemTemplate;
    private String contextTemplate;

    public String loadSystemTemplate() {
        if (systemTemplate == null) {
            systemTemplate = loadFromClasspath(SYSTEM_TEMPLATE);
        }
        return systemTemplate;
    }

    public String loadContextTemplate() {
        if (contextTemplate == null) {
            contextTemplate = loadFromClasspath(CONTEXT_TEMPLATE);
        }
        return contextTemplate;
    }

    private String loadFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }
}
```

- [ ] **Step 2: Create TemplateLoaderTest.java**

```java
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
```

- [ ] **Step 3: Compile and run test**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(junit-jupiter|junit-platform|opentest4j|apiguardian|lombok|slf4j)" | head -20); do CP="$CP:$jar"; done

javac -cp "$CP" -d /tmp/test_core meta-claw-core/src/test/java/meta/claw/core/prompt/TemplateLoaderTest.java
java -cp "$CP:/tmp/test_core" org.junit.platform.console.ConsoleLauncher --select-class=meta.claw.core.prompt.TemplateLoaderTest
```

Expected: 2 tests pass.

- [ ] **Step 4: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/prompt/TemplateLoader.java
git add meta-claw-core/src/test/java/meta/claw/core/prompt/TemplateLoaderTest.java
git commit -m "feat(prompt): add TemplateLoader with tests"
```

---

## Task 4: Create PromptContextFactory

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`

- [ ] **Step 1: Create PromptContextFactory.java**

```java
package meta.claw.core.prompt;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.VesselConfig;
import meta.claw.core.session.PreferenceEntry;
import meta.claw.core.session.UserPreferenceStore;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PromptContextFactory {

    public static PromptContext create(VesselConfig config, Path workspaceDir,
                                        UserPreferenceStore preferenceStore) {
        String preferences = "";
        if (preferenceStore != null && config.getId() != null) {
            List<PreferenceEntry> recent = preferenceStore.listRecentPreferences(config.getId(), 10);
            if (!recent.isEmpty()) {
                preferences = recent.stream()
                    .map(PreferenceEntry::getContent)
                    .collect(Collectors.joining("\n- ", "- ", ""));
            }
        }

        return PromptContext.builder()
            .vesselName(config.getName())
            .vesselDescription(config.getDescription())
            .identity(config.getIdentity())
            .soul(config.getSoul())
            .capabilities(config.getCapabilities())
            .guidelines(config.getGuidelines())
            .tools(Collections.emptyList())
            .skills(Collections.emptyList())
            .knowledge("")
            .preferences(preferences)
            .workspaceDir(workspaceDir)
            .currentTime(formatCurrentTime())
            .location(detectLocation())
            .runtimeInfo(Collections.emptyMap())
            .recentMessages(Collections.emptyList())
            .conversationSummary("")
            .build();
    }

    private static String formatCurrentTime() {
        return LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        );
    }

    private static String detectLocation() {
        try {
            String tz = ZoneId.systemDefault().getId();
            String[] parts = tz.split("/");
            if (parts.length >= 2) {
                return parts[1] + ", " + parts[0];
            }
            return tz;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java
git commit -m "feat(prompt): add PromptContextFactory"
```

---

## Task 5: Create SystemPromptBuilder

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`

- [ ] **Step 1: Create SystemPromptBuilder.java**

```java
package meta.claw.core.prompt;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Slf4j
public class SystemPromptBuilder {

    private final TemplateLoader templateLoader;
    private final PromptContext context;

    public SystemPromptBuilder(TemplateLoader templateLoader, PromptContext context) {
        this.templateLoader = templateLoader;
        this.context = context;
    }

    public String build() {
        String systemTemplate = templateLoader.loadSystemTemplate();
        String contextTemplate = templateLoader.loadContextTemplate();

        String systemSection = renderSystemTemplate(systemTemplate);
        String contextSection = renderContextTemplate(contextTemplate);

        if (contextSection == null || contextSection.isBlank()) {
            return systemSection;
        }
        return systemSection + "\n\n" + contextSection;
    }

    private String renderSystemTemplate(String template) {
        String result = template;
        result = replaceSection(result, "<IDENTITY_SECTION/>", buildIdentitySection());
        result = replaceSection(result, "<TOOLS_SECTION/>", buildToolsSection());
        result = replaceSection(result, "<SKILLS_SECTION/>", buildSkillsSection());
        result = replaceSection(result, "<KNOWLEDGE_SECTION/>", buildKnowledgeSection());
        return result;
    }

    private String renderContextTemplate(String template) {
        String result = template;
        result = replaceSection(result, "<WORKSPACE_SECTION/>", buildWorkspaceSection());
        result = replaceSection(result, "<RUNTIME_SECTION/>", buildRuntimeSection());
        result = replaceSection(result, "<MEMORY_SECTION/>", buildMemorySection());
        return result;
    }

    private String replaceSection(String template, String marker, String section) {
        if (section == null || section.isBlank()) {
            return template.replaceAll("\\n?\\s*" + java.util.regex.Pattern.quote(marker) + "\\s*\\n?", "\n");
        }
        return template.replace(marker, section);
    }

    private String buildIdentitySection() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Identity\n\n");
        sb.append("You are ").append(context.getVesselName());
        if (context.getVesselDescription() != null && !context.getVesselDescription().isBlank()) {
            sb.append(" (").append(context.getVesselDescription()).append(")");
        }
        sb.append(".\n");
        if (context.getIdentity() != null && !context.getIdentity().isBlank()) {
            sb.append("\n").append(context.getIdentity());
        }
        return sb.toString();
    }

    private String buildToolsSection() {
        if (context.getTools() == null || context.getTools().isEmpty()) {
            return "";
        }
        return "";
    }

    private String buildSkillsSection() {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            return "";
        }
        return "";
    }

    private String buildKnowledgeSection() {
        if (context.getKnowledge() == null || context.getKnowledge().isBlank()) {
            return "";
        }
        return "## Knowledge Base\n\n" + context.getKnowledge();
    }

    private String buildWorkspaceSection() {
        if (context.getWorkspaceDir() == null) {
            return "";
        }
        Path workspace = context.getWorkspaceDir();
        StringBuilder sb = new StringBuilder();
        sb.append("## Workspace\n\n");
        sb.append("Working directory: ").append(workspace).append("\n");

        String tree = getWorkspaceTree(workspace, 2);
        if (!tree.isBlank()) {
            sb.append("\n**Directories**:\n\n```\n").append(tree).append("\n```\n");
        }
        return sb.toString();
    }

    private String getWorkspaceTree(Path workspace, int maxDepth) {
        if (!Files.exists(workspace)) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(workspace.getFileName() != null ? workspace.getFileName() : workspace).append(" /\n");
        buildTreeRecursive(workspace, "", 0, maxDepth, sb);
        return sb.toString();
    }

    private void buildTreeRecursive(Path dir, String prefix, int depth, int maxDepth, StringBuilder sb) {
        if (depth > maxDepth) return;
        try {
            var entries = Files.list(dir)
                .filter(p -> Files.isDirectory(p))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .filter(p -> !p.getFileName().toString().equals("target"))
                .sorted()
                .collect(Collectors.toList());

            for (int i = 0; i < entries.size(); i++) {
                boolean isLast = (i == entries.size() - 1);
                String branch = isLast ? "└── " : "├── ";
                String name = entries.get(i).getFileName().toString();
                sb.append(prefix).append(branch).append(name).append("/\n");

                if (depth < maxDepth) {
                    String ext = isLast ? "    " : "│   ";
                    buildTreeRecursive(entries.get(i), prefix + ext, depth + 1, maxDepth, sb);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to list directory: {}", dir);
        }
    }

    private String buildRuntimeSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Runtime\n\n");
        sb.append("- Current time: ").append(context.getCurrentTime()).append("\n");
        sb.append("- Location: ").append(context.getLocation()).append("\n");
        if (context.getRuntimeInfo() != null) {
            context.getRuntimeInfo().forEach((k, v) ->
                sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }

    private String buildMemorySection() {
        if (context.getPreferences() == null || context.getPreferences().isBlank()) {
            return "";
        }
        return "## User Preferences\n\n" + context.getPreferences();
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java
git commit -m "feat(prompt): add SystemPromptBuilder with section builders"
```

---

## Task 6: Create MemoryManager

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/prompt/MemoryManager.java`

- [ ] **Step 1: Create MemoryManager.java**

```java
package meta.claw.core.prompt;

import meta.claw.core.session.ChatMessage;
import meta.claw.core.spi.llm.SpiMessage;

import java.util.ArrayList;
import java.util.List;

public class MemoryManager {

    public List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds) {
        if (maxRounds <= 0 || history == null || history.isEmpty()) {
            return new ArrayList<>(history);
        }

        int roundsFound = 0;
        int cutoffIndex = history.size();

        for (int i = history.size() - 1; i >= 0; i--) {
            String role = history.get(i).getRole();
            if ("assistant".equalsIgnoreCase(role)) {
                roundsFound++;
                if (roundsFound > maxRounds) {
                    cutoffIndex = i + 1;
                    break;
                }
            }
        }

        List<SpiMessage> result = new ArrayList<>();
        for (SpiMessage msg : history) {
            if ("system".equalsIgnoreCase(msg.getRole()) || history.indexOf(msg) >= cutoffIndex) {
                result.add(msg);
            }
        }
        return result;
    }

    public List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens) {
        if (maxTokens <= 0 || history == null || history.isEmpty()) {
            return new ArrayList<>(history);
        }

        int currentTokens = 0;
        int cutoffIndex = history.size();

        for (int i = history.size() - 1; i >= 0; i--) {
            SpiMessage msg = history.get(i);
            String content = msg.getContent() != null ? msg.getContent() : "";
            int tokens = estimateTokens(content);

            if ("system".equalsIgnoreCase(msg.getRole())) {
                currentTokens += tokens;
                continue;
            }

            if (currentTokens + tokens > maxTokens) {
                cutoffIndex = i + 1;
                break;
            }
            currentTokens += tokens;
        }

        List<SpiMessage> result = new ArrayList<>();
        for (SpiMessage msg : history) {
            if ("system".equalsIgnoreCase(msg.getRole()) || history.indexOf(msg) >= cutoffIndex) {
                result.add(msg);
            }
        }
        return result;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return chineseChars + (otherChars / 4) + 1;
    }

    public String summarizeConversation(List<ChatMessage> history) {
        return "Earlier conversation summarized.";
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/prompt/MemoryManager.java
git commit -m "feat(prompt): add MemoryManager with truncateByRound and truncateByToken"
```


---

## Task 7: Enhance ConversationStore Interface

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java`

- [ ] **Step 1: Add new methods to ConversationStore.java**

Open the file and add these methods to the interface:

```java
    /**
     * Get message history with filter conditions
     */
    List<ChatMessage> getHistory(String sessionKey, int limit, MessageFilter filter);

    /**
     * Get session statistics
     */
    ConversationStats getStats(String sessionKey);

    /**
     * Save media file to session directory
     */
    MediaReference saveMedia(String sessionKey, byte[] data, String filename, String mediaType);

    /**
     * Get full path to a media file
     */
    java.nio.file.Path getMediaPath(String sessionKey, String relativePath);
```

- [ ] **Step 2: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java
git commit -m "feat(store): add filter, stats, media methods to ConversationStore interface"
```

---

## Task 8: Create New Session Data Classes

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/session/MessageFilter.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStats.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/session/MediaReference.java`

- [ ] **Step 1: Create MessageFilter.java**

```java
package meta.claw.core.session;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageFilter {
    private String role;
    private String userId;
    private String messageType;
    private LocalDateTime after;
    private LocalDateTime before;
}
```

- [ ] **Step 2: Create ConversationStats.java**

```java
package meta.claw.core.session;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConversationStats {
    private int messageCount;
    private int userMessages;
    private int assistantMessages;
    private int systemMessages;
    private LocalDateTime firstMessage;
    private LocalDateTime lastMessage;
    private long fileSizeBytes;
}
```

- [ ] **Step 3: Create MediaReference.java**

```java
package meta.claw.core.session;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaReference {
    private String absolutePath;
    private String relativePath;
    private String mediaType;
}
```

- [ ] **Step 4: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/session/MessageFilter.java
git add meta-claw-core/src/main/java/meta/claw/core/session/ConversationStats.java
git add meta-claw-core/src/main/java/meta/claw/core/session/MediaReference.java
git commit -m "feat(store): add MessageFilter, ConversationStats, MediaReference"
```

---

## Task 9: Enhance JsonlConversationStore

**Files:**
- Modify: `meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java`

- [ ] **Step 1: Add base64 stripping pattern**

At the top of the class (after existing fields), add:

```java
    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "data:([^;]+);base64,[A-Za-z0-9+/=]{200,}"
    );
```

- [ ] **Step 2: Modify appendMessage to strip base64**

In the `appendMessage` method, before serialization, add:

```java
            String safeContent = stripBase64(message.getContent());
            message.setContent(safeContent);
```

- [ ] **Step 3: Add getHistory with filter**

Add new method:

```java
    @Override
    public List<ChatMessage> getHistory(String sessionKey, int limit, MessageFilter filter) {
        Path filePath = getHistoryFilePath(sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.readLock().lock();
        try {
            List<ChatMessage> messages = Files.lines(filePath)
                .filter(line -> !line.isBlank())
                .map(this::parseMessage)
                .filter(java.util.Objects::nonNull)
                .filter(msg -> matchesFilter(msg, filter))
                .collect(java.util.stream.Collectors.toList());

            if (limit > 0 && messages.size() > limit) {
                return messages.subList(messages.size() - limit, messages.size());
            }
            return messages;
        } catch (IOException e) {
            log.error("Read failed: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean matchesFilter(ChatMessage msg, MessageFilter filter) {
        if (filter == null) return true;
        if (filter.getRole() != null && !filter.getRole().equalsIgnoreCase(msg.getRole())) return false;
        if (filter.getUserId() != null && !filter.getUserId().equals(msg.getUserId())) return false;
        if (filter.getMessageType() != null && !filter.getMessageType().equals(msg.getMessageType())) return false;
        if (filter.getAfter() != null) {
            if (msg.getTimestamp() == null || msg.getTimestamp().isBefore(filter.getAfter())) return false;
        }
        if (filter.getBefore() != null) {
            if (msg.getTimestamp() == null || msg.getTimestamp().isAfter(filter.getBefore())) return false;
        }
        return true;
    }
```

- [ ] **Step 4: Add getStats**

```java
    @Override
    public ConversationStats getStats(String sessionKey) {
        Path filePath = getHistoryFilePath(sessionKey);
        if (!Files.exists(filePath)) return null;

        List<ChatMessage> history = getHistory(sessionKey);
        if (history.isEmpty()) {
            try {
                long size = Files.size(filePath);
                return ConversationStats.builder().messageCount(0).fileSizeBytes(size).build();
            } catch (IOException e) { return null; }
        }

        java.util.Map<String, Long> roleCounts = history.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                msg -> msg.getRole() != null ? msg.getRole().toLowerCase() : "unknown",
                java.util.stream.Collectors.counting()
            ));

        long fileSize;
        try { fileSize = Files.size(filePath); } catch (IOException e) { fileSize = 0; }

        return ConversationStats.builder()
            .messageCount(history.size())
            .userMessages(roleCounts.getOrDefault("user", 0L).intValue())
            .assistantMessages(roleCounts.getOrDefault("assistant", 0L).intValue())
            .systemMessages(roleCounts.getOrDefault("system", 0L).intValue())
            .firstMessage(history.get(0).getTimestamp())
            .lastMessage(history.get(history.size() - 1).getTimestamp())
            .fileSizeBytes(fileSize)
            .build();
    }
```

- [ ] **Step 5: Add saveMedia and getMediaPath**

```java
    @Override
    public MediaReference saveMedia(String sessionKey, byte[] data, String filename, String mediaType) {
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path mediaDir = getMediaDirPath(sessionKey);
            Files.createDirectories(mediaDir);

            String uniqueName = java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + filename;
            Path dest = mediaDir.resolve(uniqueName);
            Files.write(dest, data);

            String relativePath = "media/" + uniqueName;
            return MediaReference.builder()
                .absolutePath(dest.toString())
                .relativePath(relativePath)
                .mediaType(mediaType)
                .build();
        } catch (IOException e) {
            log.error("Save media failed: {}", e.getMessage());
            throw new RuntimeException("Media save failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Path getMediaPath(String sessionKey, String relativePath) {
        return getSessionDir(sessionKey).resolve(relativePath);
    }
```

- [ ] **Step 6: Add stripBase64 helper**

```java
    private String stripBase64(String content) {
        if (content == null || !content.contains("data:") || !content.contains(";base64,")) {
            return content;
        }
        return BASE64_PATTERN.matcher(content).replaceAll(match -> {
            String mime = match.group(1);
            return "[media:" + mime + ":base64:<stripped>]";
        });
    }
```

- [ ] **Step 7: Add getMediaDirPath helper**

```java
    private Path getMediaDirPath(String sessionKey) {
        return getSessionDir(sessionKey).resolve("media");
    }
```

- [ ] **Step 8: Verify compilation**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes:meta-claw-store/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(jackson|lombok|slf4j|logback)" | head -20); do CP="$CP:$jar"; done
javac -cp "$CP" -d meta-claw-store/target/classes meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java
```

Expected: 0 errors.

- [ ] **Step 9: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java
git commit -m "feat(store): enhance JsonlConversationStore with filter, stats, media, base64 strip"
```

---

## Task 10: Add Fields to VesselConfig

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`

- [ ] **Step 1: Add maxHistoryRounds and maxTokens**

Add to the class:

```java
    private Integer maxHistoryRounds = 20;
    private Integer maxTokens = 4096;
```

- [ ] **Step 2: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java
git commit -m "feat(config): add maxHistoryRounds and maxTokens to VesselConfig"
```

---

## Task 11: Integrate SystemPromptBuilder into VesselRuntime

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`

- [ ] **Step 1: Replace hardcoded systemPrompt with SystemPromptBuilder**

Full file replacement:

```java
package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.core.model.VesselConfig;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.SystemPromptBuilder;
import meta.claw.core.prompt.TemplateLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class VesselRuntime {

    private final VesselConfig config;
    private final ChatClient chatClient;
    private final SystemPromptBuilder promptBuilder;

    public VesselRuntime(VesselConfig config, ChatClient chatClient) {
        this.config = config;
        this.chatClient = chatClient;
        this.promptBuilder = createPromptBuilder(config);
        if (config != null) {
            log.info("VesselRuntime initialized: vesselId={}, model={}",
                    config.getId(), config.getModel());
        } else {
            log.info("VesselRuntime initialized: config=null");
        }
    }

    private SystemPromptBuilder createPromptBuilder(VesselConfig config) {
        var context = PromptContextFactory.create(config, detectWorkspaceDir(), null);
        var templateLoader = new TemplateLoader();
        return new SystemPromptBuilder(templateLoader, context);
    }

    private Path detectWorkspaceDir() {
        String dir = System.getProperty("user.dir");
        return dir != null ? Path.of(dir) : Path.of(".");
    }

    public Reply chat(String userMessage) {
        log.debug("Vessel processing: vesselId={}, messageLength={}",
                config != null ? config.getId() : "null",
                userMessage != null ? userMessage.length() : 0);

        try {
            String systemPrompt = promptBuilder.build();
            String response;
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
                ));
                ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
                response = safeExtractContent(chatResponse);
            } else {
                response = chatClient.prompt().user(userMessage).call().content();
            }

            log.debug("Vessel response: vesselId={}, responseLength={}",
                    config != null ? config.getId() : "null",
                    response != null ? response.length() : 0);

            return new Reply(ReplyType.TEXT, response);
        } catch (Exception e) {
            log.error("Vessel chat error: vesselId={}, error={}",
                    config != null ? config.getId() : "null", e.getMessage(), e);
            return new Reply(ReplyType.ERROR, "服务异常，请稍后重试");
        }
    }

    public String getVesselId() {
        return config != null ? config.getId() : "null";
    }

    private String safeExtractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("Empty or incomplete response from VesselRuntime");
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(spring-ai|jackson|lombok|slf4j|logback)" | head -20); do CP="$CP:$jar"; done
javac -cp "$CP" -d meta-claw-core/target/classes meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java
```

Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java
git commit -m "feat(runtime): integrate SystemPromptBuilder into VesselRuntime"
```

---

## Task 12: Integrate SystemPromptBuilder and MemoryManager into ChatCommand

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: Add imports and fields**

Add imports:
```java
import meta.claw.core.prompt.MemoryManager;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.SystemPromptBuilder;
import meta.claw.core.prompt.TemplateLoader;
```

Add fields:
```java
    private MemoryManager memoryManager;
```

- [ ] **Step 2: Initialize SystemPromptBuilder and MemoryManager in run()**

After resolving vesselConfig, before welcome screen, add:

```java
        var promptContext = PromptContextFactory.create(vesselConfig, configDir, null);
        var systemPromptBuilder = new SystemPromptBuilder(new TemplateLoader(), promptContext);
        String systemPrompt = systemPromptBuilder.build();
        this.memoryManager = new MemoryManager();
```

- [ ] **Step 3: Use systemPrompt instead of config.getSystemPrompt()**

Replace:
```java
        String systemPrompt = vesselConfig.getSystemPrompt();
```
with the builder output (already assigned above).

- [ ] **Step 4: Add MemoryManager truncation before LLM call**

Before `SpiChatRequest request = ...`, add:

```java
                int maxRounds = vesselConfig.getMaxHistoryRounds() != null
                    ? vesselConfig.getMaxHistoryRounds() : 20;
                List<SpiMessage> truncatedHistory = memoryManager.truncateByRound(history, maxRounds);
                SpiChatRequest request = SpiChatRequest.builder().messages(truncatedHistory).build();
```

- [ ] **Step 5: Verify compilation**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes:meta-claw-store/target/classes:meta-claw-vessel/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(picocli|spring-boot|jline|jackson|lombok|slf4j)" | head -30); do CP="$CP:$jar"; done
javac -cp "$CP" -d /tmp/compiled_cli meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
git commit -m "feat(cli): integrate SystemPromptBuilder and MemoryManager into ChatCommand"
```

---

## Task 13: Write Tests

**Files:**
- Create: `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java`
- Create: `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreEnhancedTest.java`

- [ ] **Step 1: Create SystemPromptBuilderTest.java**

```java
package meta.claw.core.prompt;

import meta.claw.core.model.VesselConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {

    @Test
    void build_shouldContainIdentity() {
        VesselConfig config = new VesselConfig();
        config.setName("TestVessel");
        config.setDescription("A test vessel");
        config.setIdentity("I am helpful.");

        var context = PromptContextFactory.create(config, Path.of("."), null);
        var builder = new SystemPromptBuilder(new TemplateLoader(), context);
        String prompt = builder.build();

        assertTrue(prompt.contains("You are TestVessel"));
        assertTrue(prompt.contains("I am helpful."));
    }

    @Test
    void build_shouldRemoveEmptySections() {
        VesselConfig config = new VesselConfig();
        config.setName("EmptyVessel");

        var context = PromptContextFactory.create(config, Path.of("."), null);
        var builder = new SystemPromptBuilder(new TemplateLoader(), context);
        String prompt = builder.build();

        assertFalse(prompt.contains("<TOOLS_SECTION/>"));
        assertFalse(prompt.contains("<SKILLS_SECTION/>"));
        assertFalse(prompt.contains("<KNOWLEDGE_SECTION/>"));
    }

    @Test
    void build_shouldIncludeRuntime() {
        VesselConfig config = new VesselConfig();
        config.setName("RuntimeVessel");

        var context = PromptContextFactory.create(config, Path.of("."), null);
        var builder = new SystemPromptBuilder(new TemplateLoader(), context);
        String prompt = builder.build();

        assertTrue(prompt.contains("Current time:"));
        assertTrue(prompt.contains("Location:"));
    }
}
```

- [ ] **Step 2: Create MemoryManagerTest.java**

```java
package meta.claw.core.prompt;

import meta.claw.core.spi.llm.SpiMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryManagerTest {

    MemoryManager manager = new MemoryManager();

    @Test
    void truncateByRound_shouldKeepRecentRounds() {
        List<SpiMessage> history = new ArrayList<>();
        history.add(SpiMessage.system("system prompt"));
        for (int i = 0; i < 5; i++) {
            history.add(SpiMessage.user("user " + i));
            history.add(SpiMessage.assistant("assistant " + i));
        }

        List<SpiMessage> result = manager.truncateByRound(history, 2);

        assertTrue(result.stream().anyMatch(m -> m.getContent().contains("system prompt")));
        assertEquals(5, result.size()); // system + 2 rounds (user+assistant)
    }

    @Test
    void truncateByToken_shouldRespectLimit() {
        List<SpiMessage> history = new ArrayList<>();
        history.add(SpiMessage.system("system"));
        for (int i = 0; i < 10; i++) {
            history.add(SpiMessage.user("message " + i));
        }

        List<SpiMessage> result = manager.truncateByToken(history, 50);

        assertTrue(result.size() < history.size());
    }
}
```

- [ ] **Step 3: Create JsonlConversationStoreEnhancedTest.java**

```java
package meta.claw.store.conversation;

import meta.claw.core.session.ChatMessage;
import meta.claw.core.session.ConversationStats;
import meta.claw.core.session.MediaReference;
import meta.claw.core.session.MessageFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonlConversationStoreEnhancedTest {

    @TempDir
    Path tempDir;

    private JsonlConversationStore createStore() {
        return new JsonlConversationStore(tempDir);
    }

    private ChatMessage msg(String role, String content) {
        return ChatMessage.builder()
            .sessionKey("test")
            .role(role)
            .content(content)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Test
    void getHistory_withRoleFilter() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test", msg("user", "Hello"));
        store.appendMessage("test", msg("assistant", "Hi"));
        store.appendMessage("test", msg("user", "Bye"));

        MessageFilter filter = MessageFilter.builder().role("user").build();
        var history = store.getHistory("test", 0, filter);

        assertEquals(2, history.size());
        assertTrue(history.stream().allMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void getStats_shouldReturnCorrectCounts() {
        JsonlConversationStore store = createStore();
        store.appendMessage("test", msg("user", "Hello"));
        store.appendMessage("test", msg("assistant", "Hi"));

        ConversationStats stats = store.getStats("test");

        assertNotNull(stats);
        assertEquals(2, stats.getMessageCount());
        assertEquals(1, stats.getUserMessages());
        assertEquals(1, stats.getAssistantMessages());
        assertTrue(stats.getFileSizeBytes() > 0);
    }

    @Test
    void saveMedia_shouldWriteToMediaDir() {
        JsonlConversationStore store = createStore();
        byte[] data = "test image".getBytes();

        MediaReference ref = store.saveMedia("test", data, "photo.jpg", "image/jpeg");

        assertNotNull(ref);
        assertTrue(ref.getAbsolutePath().contains("media"));
        assertTrue(ref.getRelativePath().startsWith("media/"));
        assertTrue(java.nio.file.Path.of(ref.getAbsolutePath()).toFile().exists());
    }

    @Test
    void appendMessage_shouldStripBase64() {
        JsonlConversationStore store = createStore();
        String content = "data:image/png;base64," + "a".repeat(300);

        store.appendMessage("test", msg("user", content));
        var history = store.getHistory("test");

        assertEquals(1, history.size());
        assertTrue(history.get(0).getContent().contains("[media:image/png:base64:<stripped>]"));
        assertFalse(history.get(0).getContent().contains("aaaa"));
    }
}
```

- [ ] **Step 4: Compile and run tests**

```bash
cd /Users/kai/IdeaProjects/meta_claw

# Core tests
CP="meta-claw-core/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(junit-jupiter|junit-platform|opentest4j|apiguardian|jackson|lombok|slf4j|logback)" | head -30); do CP="$CP:$jar"; done

javac -cp "$CP" -d /tmp/test_core \
  meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java \
  meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java

java -cp "$CP:/tmp/test_core" org.junit.platform.console.ConsoleLauncher \
  --select-class=meta.claw.core.prompt.SystemPromptBuilderTest \
  --select-class=meta.claw.core.prompt.MemoryManagerTest

# Store tests
CP_STORE="meta-claw-core/target/classes:meta-claw-store/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(junit-jupiter|junit-platform|opentest4j|apiguardian|jackson|lombok|slf4j|logback)" | head -30); do CP_STORE="$CP_STORE:$jar"; done

javac -cp "$CP_STORE" -d /tmp/test_store \
  meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreEnhancedTest.java

java -cp "$CP_STORE:/tmp/test_store" org.junit.platform.console.ConsoleLauncher \
  --select-class=meta.claw.store.conversation.JsonlConversationStoreEnhancedTest
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git add meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java
git add meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java
git add meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreEnhancedTest.java
git commit -m "test(phase2): add SystemPromptBuilder, MemoryManager, Store enhanced tests"
```

---

## Task 14: Final Validation

- [ ] **Step 1: Compile all modified modules**

```bash
cd /Users/kai/IdeaProjects/meta_claw

# Compile core
CP_CORE="meta-claw-core/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(jackson|lombok|slf4j|logback|snakeyaml|guava|spring-ai)" | head -30); do CP_CORE="$CP_CORE:$jar"; done
find meta-claw-core/src/main/java -name "*.java" > /tmp/core_sources.txt
javac -cp "$CP_CORE" -d meta-claw-core/target/classes @/tmp/core_sources.txt
```

Expected: 0 errors.

```bash
# Compile store
CP_STORE="meta-claw-core/target/classes:meta-claw-store/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(jackson|lombok|slf4j|logback)" | head -20); do CP_STORE="$CP_STORE:$jar"; done
find meta-claw-store/src/main/java -name "*.java" > /tmp/store_sources.txt
javac -cp "$CP_STORE" -d meta-claw-store/target/classes @/tmp/store_sources.txt
```

Expected: 0 errors.

```bash
# Compile CLI
CP_CLI="meta-claw-core/target/classes:meta-claw-store/target/classes:meta-claw-vessel/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(jackson|lombok|slf4j|logback|picocli|spring-boot|jline)" | head -30); do CP_CLI="$CP_CLI:$jar"; done
find meta-claw-cli/src/main/java -name "*.java" > /tmp/cli_sources.txt
javac -cp "$CP_CLI" -d meta-claw-cli/target/classes @/tmp/cli_sources.txt
```

Expected: 0 errors.

- [ ] **Step 2: Run all tests**

```bash
cd /Users/kai/IdeaProjects/meta_claw
CP="meta-claw-core/target/classes:meta-claw-vessel/target/classes:meta-claw-store/target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(junit-jupiter|junit-platform|opentest4j|apiguardian|jackson|lombok|slf4j|logback|snakeyaml|guava)" | head -40); do CP="$CP:$jar"; done

find meta-claw-core/src/test/java -name "*.java" > /tmp/core_tests.txt
find meta-claw-vessel/src/test/java -name "*.java" > /tmp/vessel_tests.txt
find meta-claw-store/src/test/java -name "*.java" > /tmp/store_tests.txt

javac -cp "$CP" -d /tmp/test_all @/tmp/core_tests.txt @/tmp/vessel_tests.txt @/tmp/store_tests.txt

java -cp "$CP:/tmp/test_all" org.junit.platform.console.ConsoleLauncher \
  --scan-classpath=/tmp/test_all --packages=meta.claw
```

Expected: All tests pass (target: 50+ tests).

- [ ] **Step 3: Review commit history**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git log --oneline -20
```

Verify clean commit history.

- [ ] **Step 4: Tag completion**

```bash
cd /Users/kai/IdeaProjects/meta_claw
git tag -a phase2-complete -m "Phase 2 complete: Prompt Engineering + MemoryManager + Store enhancements"
```

---

## Self-Review Checklist

After completing all tasks:

- [ ] **Spec coverage**: Every section in the design doc has corresponding tasks
- [ ] **No placeholders**: No TBD, TODO, or vague steps remain
- [ ] **Type consistency**: Method signatures match across all files
- [ ] **File paths exact**: All paths verified against project structure
- [ ] **Tests pass**: All 50+ tests pass
- [ ] **Compilation clean**: 0 errors across core, store, cli modules
