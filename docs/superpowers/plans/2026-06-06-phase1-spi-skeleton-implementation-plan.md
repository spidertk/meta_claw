## Task 9: Refactor PromptRenderer to accept Map<String, String>

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptRenderer.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/prompt/PromptRendererTest.java`

- [ ] **Step 1: Modify PromptRenderer**

Replace `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptRenderer.java` with:

```java
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
        return render(loadTemplate(SYSTEM_TEMPLATE), vars);
    }

    public String renderContext(Map<String, String> vars) {
        return render(loadTemplate(CONTEXT_TEMPLATE), vars);
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
```

- [ ] **Step 2: Write test**

Create `meta-claw-core/src/test/java/meta/claw/core/prompt/PromptRendererTest.java`:

```java
package meta.claw.core.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptRendererTest {

    PromptRenderer renderer = new PromptRenderer();

    @Test
    void render_emptyVars_returnsEmpty() {
        assertEquals("", renderer.renderSystem(Map.of()));
    }

    @Test
    void render_basicVars() {
        Map<String, String> vars = Map.of(
                "vessel_name", "TestBot",
                "vessel_description", "A test vessel",
                "identity", "Test identity",
                "current_time", "2026-06-06 12:00:00 CST",
                "location", "Asia/Shanghai"
        );
        String result = renderer.renderSystem(vars);
        assertTrue(result.contains("# TestBot"));
        assertTrue(result.contains("A test vessel"));
        assertTrue(result.contains("## Identity"));
        assertTrue(result.contains("Test identity"));
        assertTrue(result.contains("2026-06-06 12:00:00 CST"));
    }

    @Test
    void render_skipsEmptySections() {
        Map<String, String> vars = Map.of(
                "vessel_name", "MinimalBot",
                "identity", ""
        );
        String result = renderer.renderSystem(vars);
        assertTrue(result.contains("# MinimalBot"));
        assertFalse(result.contains("## Identity"));
    }
}
```

- [ ] **Step 3: Run test**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn test -pl meta-claw-core -Dtest=PromptRendererTest -q
```

Expected: 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/prompt/PromptRenderer.java \
       meta-claw-core/src/test/java/meta/claw/core/prompt/PromptRendererTest.java
git commit -m "refactor: PromptRenderer now accepts Map<String,String> instead of PromptContext"
```

---

## Task 10: Modify SpiChatRequest (remove PromptContext, add vesselId)

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/llm/SpiChatRequest.java`

- [ ] **Step 1: Modify SpiChatRequest**

Replace `meta-claw-core/src/main/java/meta/claw/core/llm/SpiChatRequest.java` with:

```java
package meta.claw.core.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
public class SpiChatRequest {
   private String sessionId;
   private String vesselId;
   private List<SpiMessage> messages;
   private Map<String, Object> options;
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/llm/SpiChatRequest.java
git commit -m "refactor: SpiChatRequest drops PromptContext, adds vesselId field"
```

---

## Task 11: Adapt LlmClientManager to use vesselId from SpiChatRequest

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/LlmClientManager.java`

- [ ] **Step 1: Modify LlmClientManager**

The file has three methods that reference `request.getCtx()`: `chat()`, `chatStream()`, and the advisors in `chatStream()`. Replace the file with this adapted version.

Key changes:
1. Remove `import meta.claw.core.prompt.PromptContext;` (not present in current file, but check)
2. In `chat()` line ~82: change `request.getCtx().getBundle().getVesselName()` to `request.getVesselId()`
3. In `chat()` line ~91: change `request.getCtx().getBundle().getVesselName()` to `request.getVesselId()`
4. In `chatStream()` line ~130: change `request.getCtx().getBundle().getVesselName()` to `request.getVesselId()`
5. In `chatStream()` line ~136: remove `.param("memoryConfig", request.getCtx().getBundle().getMemoryConfig())` (or adapt if needed)

Current file has these references:
- Line 82: `request.getCtx().getBundle().getVesselName()`
- Line 91: `request.getCtx().getBundle().getVesselName()`  
- Line 130: `request.getCtx().getBundle().getVesselName()`
- Line 134-136: advisors params using `request.getCtx().getBundle()`

Apply these replacements to the current file:

```java
// Line 82
log.debug("LlmClientManager chat vessel={}, messages={}", request.getVesselId(), request.getMessages().size());

// Line 91
ChatResponse chatResponse = buildChatClient(request.getVesselId())

// Line 130
buildChatClient(request.getVesselId())

// Lines 133-136 (advisors in chatStream)
.advisors(spec -> spec
    .param("vesselName", request.getVesselId())
    .param("sessionId", request.getSessionId())
)
```

Use StrReplaceFile:

```bash
cd /Users/kai/IdeaProjects/meta_claw
```

Apply the edits:

```
// Replace the debug log line
old: log.debug("LlmClientManager chat vessel={}, messages={}", request.getCtx().getBundle().getVesselName(), request.getMessages().size());
new: log.debug("LlmClientManager chat vessel={}, messages={}", request.getVesselId(), request.getMessages().size());

// Replace buildChatClient call in chat()
old: ChatResponse chatResponse = buildChatClient(request.getCtx().getBundle().getVesselName())
new: ChatResponse chatResponse = buildChatClient(request.getVesselId())

// Replace buildChatClient call in chatStream()
old: buildChatClient(request.getCtx().getBundle().getVesselName())
new: buildChatClient(request.getVesselId())

// Replace advisors block
old: .advisors(spec -> spec
    .param("vesselName", request.getCtx().getBundle().getVesselName())
    .param("sessionId", request.getSessionId())
    .param("memoryConfig", request.getCtx().getBundle().getMemoryConfig()))
new: .advisors(spec -> spec
    .param("vesselName", request.getVesselId())
    .param("sessionId", request.getSessionId()))
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn compile -pl meta-claw-core -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/LlmClientManager.java
git commit -m "refactor: LlmClientManager uses request.getVesselId() instead of PromptContext"
```

---

## Task 12: Refactor VesselRuntime to orchestrator

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`

- [ ] **Step 1: Write the new VesselRuntime**

Replace the entire file `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`:

```java
package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.prompt.PromptComposer;
import meta.claw.core.prompt.PromptRenderer;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.subsystem.MemorySubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Vessel 核心运行时类 — 子系统编排器。
 * <p>不再直接持有 memory/prompt 依赖，而是通过 SubSystemRegistry 统一编排子系统生命周期。</p>
 */
@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptComposer promptComposer;
    @Autowired
    private PromptRenderer promptRenderer;
    @Autowired
    private LlmClientManager llmClient;

    /** 所有子系统，Spring 自动收集（含 VesselProfile） */
    @Autowired(required = false)
    private List<VesselSubSystem> subSystems = new ArrayList<>();

    private final SubSystemRegistry registry = new SubSystemRegistry();
    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;
    }

    @Override
    public void afterPropertiesSet() {
        // ① 按 priority 排序并注册所有子系统（含 VesselProfile）
        subSystems.stream()
                .sorted(Comparator.comparingInt(VesselSubSystem::priority))
                .forEach(sub -> {
                    registry.register(sub);
                    sub.configure(registry);
                    if (sub instanceof VesselProfile) {
                        ((VesselProfile) sub).loadForVessel(vesselId);
                    }
                });
    }

    // ========== 子系统查询 ==========

    public SubSystemRegistry getRegistry() {
        return registry;
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    /** 便捷查询：获取 Vessel 配置画像 */
    public VesselProfile getProfile() {
        return registry.get("profile");
    }

    // ========== Prompt 组装与渲染 ==========

    public String renderSystemPrompt() {
        PromptVars allVars = buildPromptVars();
        return promptRenderer.renderSystem(allVars.toMap());
    }

    public PromptVars buildPromptVars() {
        // 静态变量：由 PromptComposer 从 registry 收集所有子系统贡献
        PromptVars staticVars = promptComposer.compose(registry);

        // 动态变量：每次任务实时计算
        PromptVars dynamic = PromptVars.builder()
                .vars(java.util.Map.of(
                        "current_time", formatCurrentTime(),
                        "location", detectLocation()
                ))
                .build();

        return staticVars.merge(dynamic);
    }

    // ========== 对话入口 ==========

    public Reply chat(String sessionId, String userMessage) {
        return execute(newTask(sessionId, userMessage));
    }

    public Reply execute(VesselTask task) {
        // ① 渲染 system prompt
        String systemPrompt = renderSystemPrompt();

        // ② 构造任务上下文
        TaskContext ctx = new TaskContext(task, getProfile(), registry);

        // ③ 任务开始生命周期
        registry.listAll().forEach(sub -> sub.onTaskStart(ctx));

        try {
            // ④ 构建 LLM 请求并执行
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            SpiChatRequest request = SpiChatRequest.builder()
                    .vesselId(task.getVesselId())
                    .messages(messages)
                    .sessionId(task.getSessionId())
                    .build();

            SpiChatResponse response = llmClient.chat(request);

            String content = response != null && response.content() != null
                    ? response.content() : "";

            // 保存 assistant 消息到短期记忆
            saveAssistantMessage(task, content);

            return new Reply(ReplyType.TEXT, content);
        } finally {
            // ⑤ 任务结束生命周期（finally 中保证调用）
            registry.listAll().forEach(sub -> sub.onTaskEnd(ctx));
        }
    }

    public void chatStream(String sessionId, String userMessage, SpiStreamingCallback callback) {
        String systemPrompt = renderSystemPrompt();
        VesselTask task = newTask(sessionId, userMessage);
        List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
        SpiChatRequest request = SpiChatRequest.builder()
                .vesselId(task.getVesselId())
                .messages(messages)
                .sessionId(task.getSessionId())
                .build();
        llmClient.chatStream(request, callback);
    }

    // ========== 便捷方法（向后兼容）==========

    public ShortMemory getShortMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getShortMemory(getProfile().getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, short memory unavailable");
        return null;
    }

    public meta.claw.core.config.VesselConfig getConfig() {
        return getProfile().getBundle().getRuntimeVesselConfig();
    }

    // ========== 内部 ==========

    private VesselTask newTask(String sessionId, String userMessage) {
        return VesselTask.builder()
                .taskId(UUID.randomUUID().toString())
                .vesselId(this.vesselId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .createdAt(Instant.now())
                .build();
    }

    private List<SpiMessage> buildLlmRequest(VesselTask task, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }

        String sessionId = task.getSessionId();
        ShortMemory shortMem = getShortMemory();
        if (StringUtils.isNotBlank(sessionId) && shortMem != null) {
            int maxRounds = getProfile().getBundle().getMaxHistoryRounds();
            messages.addAll(toSpiMessages(shortMem.loadMessages(vesselId, sessionId, maxRounds)));
        }

        messages.add(SpiMessage.user(task.getUserMessage()));

        if (shortMem != null) {
            shortMem.appendMessage(vesselId, sessionId,
                    MemoryMessageConverter.fromSpiMessage(SpiMessage.user(task.getUserMessage())));
        }

        return messages;
    }

    private void saveAssistantMessage(VesselTask task, String content) {
        ShortMemory shortMem = getShortMemory();
        if (shortMem != null) {
            shortMem.appendMessage(vesselId, task.getSessionId(),
                    MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant(content, null, null)));
        }
    }

    private List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.getRole() == null) {
                continue;
            }
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(
                        SpiMessage.assistant(message.getContent(), message.getReasoningContent(), message.getToolCalls()));
                case "tool" -> restored.add(SpiMessage.tool(message.getContent()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }

    private static String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private static String detectLocation() {
        return ZoneId.systemDefault().getId();
    }

    public void shutdown() {
        log.info("VesselRuntime shutdown: {}", vesselId);
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn compile -pl meta-claw-core -q
```

Expected: BUILD SUCCESS. If there are errors about missing `getLongMemory()`, note that we removed it from the public API (not used by AgentLoop). If other modules depend on it, add it back.

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java
git commit -m "refactor: VesselRuntime becomes subsystem orchestrator with registry lifecycle"
```

---

## Task 13: Delete PromptContext and PromptContextFactory

**Files:**
- Delete: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`

- [ ] **Step 1: Delete files**

```bash
cd /Users/kai/IdeaProjects/meta_claw
rm meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java
rm meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java
```

- [ ] **Step 2: Verify no remaining references**

```bash
cd /Users/kai/IdeaProjects/meta_claw
grep -r "PromptContextFactory" --include="*.java" meta-claw-core/src/
grep -r "import meta.claw.core.prompt.PromptContext" --include="*.java" meta-claw-core/src/
```

Expected: No matches (or only matches in the deleted files if git hasn't tracked the deletion yet).

- [ ] **Step 3: Compile check**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn compile -pl meta-claw-core -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java \
       meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java
git commit -m "chore: delete PromptContext and PromptContextFactory - replaced by VesselProfile subsystem"
```

---

## Task 14: Full project compile

- [ ] **Step 1: Compile all modules**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn compile -q
```

Expected: BUILD SUCCESS. If any module references deleted classes, fix them.

- [ ] **Step 2: Run all existing tests**

```bash
cd /Users/kai/IdeaProjects/meta_claw && mvn test -q
```

Expected: All existing tests still PASS (VesselConfigLoaderTest, VesselProfileLoaderTest, plus new tests).

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: full compile + test pass after Phase 1 refactoring"
```

---

## Task 15: Run ./init.sh and verify CLI chat

- [ ] **Step 1: Run init.sh**

```bash
cd /Users/kai/IdeaProjects/meta_claw && ./init.sh
```

Expected: Script completes successfully, Vessel configurations are loaded, runtimes are registered.

- [ ] **Step 2: Verify CLI chat works**

If `init.sh` includes a smoke test or if you can manually test:

```bash
cd /Users/kai/IdeaProjects/meta_claw
# Run the CLI jar or spring boot app
java -jar meta-claw-cli/target/meta-claw-cli-*.jar chat default "Hello"
```

Expected: CLI responds with a text reply (not an error).

- [ ] **Step 3: Update progress files**

Update `claude-progress.md` to record Phase 1 completion.
Update `feature_list.json` to mark Phase 1 tasks as done.

- [ ] **Step 4: Final commit**

```bash
git add claude-progress.md feature_list.json
git commit -m "docs: update progress - Phase 1 SPI skeleton + memory subsystem complete"
```

---

## Self-Review Checklist

**1. Spec coverage:**

| Design Doc Requirement | Implementing Task |
|----------------------|-------------------|
| `PromptVars` immutable Map wrapper with merge | Task 1 |
| `MessageThread` encapsulates `List<SpiMessage>` | Task 2 |
| `StepLog` encapsulates `List<StepRecord>` | Task 2 |
| `VesselTask` DTO | Task 3 |
| `VesselSubSystem` SPI (`configure` + `promptVars` + `onTaskStart/End` + `priority`) | Task 3 |
| `SubSystemRegistry` with priority sorting | Task 4 |
| `TaskContext` (task + profile + registry + messages + steps) | Task 5 |
| `MemorySubSystem` wrapping factories | Task 6 |
| `VesselProfile` built-in subsystem (priority=0) | Task 7 |
| `PromptComposer` collects and merges promptVars | Task 8 |
| `PromptRenderer` accepts `Map<String,String>` | Task 9 |
| `SpiChatRequest` removes PromptContext | Task 10 |
| `LlmClientManager` adapted for vesselId | Task 11 |
| `VesselRuntime` orchestrates subsystems | Task 12 |
| Delete `PromptContext` + `PromptContextFactory` | Task 13 |
| `./init.sh` passes, CLI chat works | Task 15 |

**2. Placeholder scan:** No TBD, TODO, "implement later", or "similar to Task N" found.

**3. Type consistency:**
- `PromptVars.merge()` returns `PromptVars` ✓
- `VesselSubSystem.promptVars()` returns `PromptVars` ✓
- `PromptComposer.compose()` returns `PromptVars` ✓
- `TaskContext.getSubSystem()` returns typed `VesselSubSystem` ✓
- `VesselProfile` (new) is in `meta.claw.core.runtime` package, distinct from `meta.claw.core.vessel.VesselProfile` ✓

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-06-phase1-spi-skeleton-implementation-plan.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Each subagent gets the full plan + current task context.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints for review.

**Which approach?**
