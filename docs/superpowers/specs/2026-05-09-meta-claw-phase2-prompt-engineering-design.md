# Meta-Claw Phase 2: Prompt Engineering + Memory Domain 设计文档

> 日期：2026-05-09
> 目标：实现模板驱动的系统提示组装、上下文窗口管理，以及 Store 层的增强
> 依赖前置文档：`2026-05-09-meta-claw-phase1-cleanup-design.md`
> 参考实现：`.rwa/expert_project/expert/prompts.py`, `.rwa/expert_project/expert/storage/file_storage.py`

---

## 一、设计概览与范围

### 1.1 目标

将 Vessel 的系统提示从静态 `config.systemPrompt` 升级为**模板驱动、上下文感知的动态组装**。同时引入 `ConversationHistoryManager` 管理短期记忆中的对话历史截断，并增强 `JsonlConversationStore` 以支持过滤、统计和媒体存储。

### 1.2 架构决策

- **SystemPromptBuilder**：采用"手动 section 构建 + `String.replace("<SECTION/>", content)`"模式（参考 `prompts.py`），不引入通用模板引擎
- **TemplateLoader**：仅负责从 classpath 加载模板文件内容
- **PromptContext**：承载所有构建系统提示所需的数据
- **ConversationHistoryManager**：提供最近 N 轮 / Token 估算两种截断策略
- **JsonlConversationStore**：参考 `file_storage.py` 增强过滤查询、统计信息、媒体存储、base64 剥离

### 1.3 范围边界

**包含**：
- 重写 `system.tmpl.md` + `context.tmpl.md`（Vessel 语义）
- `TemplateLoader`：classpath 模板加载
- `SystemPromptBuilder`：section 构建 + 模板替换
- `PromptContext` + `PromptContextFactory`：数据载体 + 构建工厂
- `ConversationHistoryManager`：短期记忆截断策略（最近 N 轮 + Token 估算）
- `JsonlConversationStore` 增强：过滤、统计、媒体、base64 剥离
- `VesselRuntime` / `ChatCommand` 集成
- `VesselConfig` 新增 `maxHistoryRounds`、`maxTokens`

**不包含**：
- 工具 JSON Schema 生成（Batch 6）
- 技能懒加载 XML（Batch 7）
- 知识库检索（Batch 5+）
- LLM 驱动的对话摘要（Phase 2 预留接口，Phase 3+ 实现）

---

## 二、模板文件设计

### 2.1 system.tmpl.md

**路径**：`meta-claw-core/src/main/resources/templates/system.tmpl.md`

```markdown
# {vessel_name}

{vessel_description}

<IDENTITY_SECTION/>

<TOOLS_SECTION/>

<SKILLS_SECTION/>

<KNOWLEDGE_SECTION/>
```

### 2.2 context.tmpl.md

**路径**：`meta-claw-core/src/main/resources/templates/context.tmpl.md`

```markdown
<WORKSPACE_SECTION/>

<RUNTIME_SECTION/>

<PREFERENCES_SECTION/>

<CONVERSATION_HISTORY_SECTION/>
```

### 2.3 占位符标记说明

| 标记 | Section 构建器 | Phase 2 状态 |
|------|---------------|-------------|
| `<IDENTITY_SECTION/>` | `buildIdentitySection()` | ✅ |
| `<TOOLS_SECTION/>` | `buildToolsSection()` | ⏳ 返回空字符串 |
| `<SKILLS_SECTION/>` | `buildSkillsSection()` | ⏳ 返回空字符串 |
| `<KNOWLEDGE_SECTION/>` | `buildKnowledgeSection()` | ⏳ 返回空字符串 |
| `<WORKSPACE_SECTION/>` | `buildWorkspaceSection()` | ✅ |
| `<RUNTIME_SECTION/>` | `buildRuntimeSection()` | ✅ |
| `<PREFERENCES_SECTION/>` | `buildPreferencesSection()` | ✅ |
| `<CONVERSATION_HISTORY_SECTION/>` | `buildConversationHistorySection()` | ✅ |

**清理规则**：section 构建器返回空字符串时，`SystemPromptBuilder` 会连同该 section 的 Markdown 标题一起移除，避免空 section 污染 prompt。

---

## 三、TemplateLoader

**路径**：`meta-claw-core/src/main/java/meta/claw/core/prompt/TemplateLoader.java`

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

---

## 四、PromptContext + PromptContextFactory

### 4.1 PromptContext

**路径**：`meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`

```java
package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import meta.claw.core.memory.shortterm.ChatMessage;

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
    private List<ToolInfo> tools;
    private List<SkillInfo> skills;
    private String knowledge;
    private String preferences;
    private Path workspaceDir;
    private String currentTime;
    private String location;
    private Map<String, String> runtimeInfo;
    private List<ChatMessage> recentMessages;
    private String conversationSummary;
}
```

### 4.2 辅助数据类

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

### 4.3 PromptContextFactory

**路径**：`meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`

```java
package meta.claw.core.prompt;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.VesselConfig;
import meta.claw.core.memory.longterm.PreferenceEntry;
import meta.claw.core.memory.longterm.UserPreferenceStore;

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

---

## 五、SystemPromptBuilder

**路径**：`meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`

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
        result = replaceSection(result, "<PREFERENCES_SECTION/>

<CONVERSATION_HISTORY_SECTION/>", buildPreferencesSection());
        return result;
    }
    
    private String replaceSection(String template, String marker, String section) {
        if (section == null || section.isBlank()) {
            // 移除该 section 的整行（包括前后的空行）
            return template.replaceAll("\\n?\\s*" + java.util.regex.Pattern.quote(marker) + "\\s*\\n?", "\n");
        }
        return template.replace(marker, section);
    }
    
    // === Section Builders ===
    
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
        // Phase 6: 生成 JSON Schema 数组
        return "";
    }
    
    private String buildSkillsSection() {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            return "";
        }
        // Phase 7: 生成 XML 懒加载列表
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
    
    private String buildPreferencesSection() {
        if (context.getPreferences() == null || context.getPreferences().isBlank()) {
            return "";
        }
        return "## User Preferences\n\n" + context.getPreferences();
    }

    private String buildConversationHistorySection() {
        if ((context.getConversationSummary() == null || context.getConversationSummary().isBlank())
                && (context.getRecentMessages() == null || context.getRecentMessages().isEmpty())) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## Conversation History\n\n");
        if (context.getConversationSummary() != null && !context.getConversationSummary().isBlank()) {
            sb.append("### Conversation Summary\n\n")
                    .append(context.getConversationSummary())
                    .append("\n\n");
        }
        if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
            sb.append("### Recent Messages\n\n");
        }
        return sb.toString().trim();
    }
}
```

---

## 六、ConversationHistoryManager

**路径**：`meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ConversationHistoryManager.java`

```java
package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.shortterm.ChatMessage;
import meta.claw.core.spi.llm.SpiMessage;

import java.util.ArrayList;
import java.util.List;

public class ConversationHistoryManager {
    
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

---

## 七、JsonlConversationStore 增强

### 7.1 接口增强

```java
package meta.claw.core.session;

import java.nio.file.Path;
import java.util.List;

public interface ConversationStore {
    void appendMessage(String sessionKey, ChatMessage message);
    List<ChatMessage> getHistory(String sessionKey, int limit);
    default List<ChatMessage> getHistory(String sessionKey) { return getHistory(sessionKey, 0); }
    List<ChatMessage> getHistory(String sessionKey, int limit, MessageFilter filter);
    List<ConversationInfo> listConversations();
    boolean clearHistory(String sessionKey);
    boolean deleteConversation(String sessionKey);
    boolean conversationExists(String sessionKey);
    ConversationStats getStats(String sessionKey);
    MediaReference saveMedia(String sessionKey, byte[] data, String filename, String mediaType);
    Path getMediaPath(String sessionKey, String relativePath);
}
```

### 7.2 新增数据类

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

### 7.3 增强实现

```java
package meta.claw.store.memory.shortterm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.session.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class JsonlConversationStore implements ConversationStore {

    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "data:([^;]+);base64,[A-Za-z0-9+/=]{200,}"
    );

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();

    public JsonlConversationStore(Path baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private ReentrantReadWriteLock getLock(String sessionKey) {
        return lockMap.computeIfAbsent(sessionKey, k -> new ReentrantReadWriteLock());
    }

    private Path getSessionDir(String sessionKey) {
        return baseDir.resolve(sessionKey);
    }

    private Path getHistoryFilePath(String sessionKey) {
        return getSessionDir(sessionKey).resolve("history.jsonl");
    }

    private Path getMediaDirPath(String sessionKey) {
        return getSessionDir(sessionKey).resolve("media");
    }

    @Override
    public void appendMessage(String sessionKey, ChatMessage message) {
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path filePath = getHistoryFilePath(sessionKey);
            Files.createDirectories(filePath.getParent());
            
            String safeContent = stripBase64(message.getContent());
            message.setContent(safeContent);
            
            String jsonLine = objectMapper.writeValueAsString(message) + "\n";
            Files.writeString(filePath, jsonLine,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            log.error("Serialize failed: {}", e.getMessage());
            throw new RuntimeException("Message serialization failed", e);
        } catch (IOException e) {
            log.error("Append failed: {}", e.getMessage());
            throw new RuntimeException("Message append failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ChatMessage> getHistory(String sessionKey, int limit) {
        return getHistory(sessionKey, limit, null);
    }

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
                .filter(Objects::nonNull)
                .filter(msg -> matchesFilter(msg, filter))
                .collect(Collectors.toList());

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

        Map<String, Long> roleCounts = history.stream()
            .collect(Collectors.groupingBy(
                msg -> msg.getRole() != null ? msg.getRole().toLowerCase() : "unknown",
                Collectors.counting()
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

    @Override
    public MediaReference saveMedia(String sessionKey, byte[] data, String filename, String mediaType) {
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path mediaDir = getMediaDirPath(sessionKey);
            Files.createDirectories(mediaDir);

            String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + filename;
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

    private String stripBase64(String content) {
        if (content == null || !content.contains("data:") || !content.contains(";base64,")) {
            return content;
        }
        return BASE64_PATTERN.matcher(content).replaceAll(match -> {
            String mime = match.group(1);
            return "[media:" + mime + ":base64:<stripped>]";
        });
    }

    private ChatMessage parseMessage(String jsonLine) {
        try { return objectMapper.readValue(jsonLine, ChatMessage.class); }
        catch (JsonProcessingException e) { log.warn("Parse failed: {}", e.getMessage()); return null; }
    }

    // ... 保留 Phase 1 的 listConversations, clearHistory, deleteConversation, conversationExists
}
```

---

## 八、VesselConfig 新增字段

```java
// VesselConfig.java 新增
private Integer maxHistoryRounds = 20;
private Integer maxTokens = 4096;
```

---

## 九、验收标准

| 组件 | 验收项 | 验证方式 |
|------|--------|----------|
| TemplateLoader | 成功加载 system.tmpl.md 和 context.tmpl.md | 单元测试 |
| SystemPromptBuilder | 给定 VesselConfig 生成包含 Identity/Workspace/Runtime 的系统提示 | 单元测试 + 手动查看输出 |
| SystemPromptBuilder | 空 section 自动移除（不渲染空标题） | 单元测试 |
| PromptContextFactory | current_time 和 location 正确生成 | 单元测试 |
| ConversationHistoryManager | truncateByRound(20) 正确保留最近 20 轮 | 单元测试 |
| ConversationHistoryManager | truncateByToken(4096) 正确截断 | 单元测试 |
| JsonlConversationStore | getHistory with MessageFilter 按 role 过滤 | 单元测试 |
| JsonlConversationStore | getStats 返回正确计数和文件大小 | 单元测试 |
| JsonlConversationStore | saveMedia 文件写入 media/ 子目录 | 单元测试 |
| JsonlConversationStore | stripBase64 正确替换 data URL | 单元测试 |
| VesselRuntime | 使用 SystemPromptBuilder 替代硬编码 systemPrompt | 集成测试 |
| ChatCommand | 对话前调用 ConversationHistoryManager.truncateByRound | 手动验证 |
| 全量 | `mvn clean test` 全量通过 | 全量构建 |

---

## 十、新增/修改文件清单

### 新增文件
```
meta-claw-core/src/main/java/meta/claw/core/prompt/TemplateLoader.java
meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java
meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java
meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java
meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ConversationHistoryManager.java
meta-claw-core/src/main/java/meta/claw/core/prompt/ToolInfo.java
meta-claw-core/src/main/java/meta/claw/core/prompt/SkillInfo.java
meta-claw-core/src/main/java/meta/claw/core/session/MessageFilter.java
meta-claw-core/src/main/java/meta/claw/core/session/ConversationStats.java
meta-claw-core/src/main/java/meta/claw/core/session/MediaReference.java
meta-claw-core/src/main/resources/templates/system.tmpl.md
meta-claw-core/src/main/resources/templates/context.tmpl.md
meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java
meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ConversationHistoryManagerTest.java
meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreEnhancedTest.java
```

### 修改文件
```
meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java
meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java
meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java
meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java
```

---

*文档版本：v1.0*  
*日期：2026-05-09*
