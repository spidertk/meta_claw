# Memory Model Clarification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clarify Meta-Claw's memory model by separating conversation history from long-term preferences in both code and prompt templates.

**Architecture:** Keep `Memory` as the conceptual umbrella, but make implementation units explicit: `ConversationHistoryManager` owns short-term conversation history behavior, `UserPreferenceStore` remains the current long-term-memory-backed preference source, and prompt templates render preferences and conversation history through separate context sections. Preserve current behavior while improving semantic boundaries and regression coverage.

**Tech Stack:** Java 21, Maven, JUnit 5, Lombok, Markdown templates.

---

## File Map

- Rename `meta-claw-core/src/main/java/meta/claw/core/prompt/MemoryManager.java` → `ConversationHistoryManager.java`: short-term memory behavior for truncation and summary placeholder.
- Rename `meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java` → `ConversationHistoryManagerTest.java`: regression coverage for the renamed component.
- Modify `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`: split preference rendering from conversation history rendering.
- Modify `meta-claw-core/src/main/resources/templates/context.tmpl.md`: replace the generic memory placeholder with explicit context placeholders.
- Modify `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`: assert the new section placement and naming.
- Modify CLI/runtime consumers of the renamed manager where applicable.
- Update state docs after verification: `claude-progress.md`, `clean-state-checklist.md`, `evaluator-rubric.md`, `feature_list.json`.

### Task 1: Lock the new prompt semantics with failing tests

**Files:**
- Modify: `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`

- [ ] **Step 1: Write failing assertions for separate context sections**

Add coverage that builds a `PromptContext` with `knowledge`, `preferences`, `conversationSummary`, and `recentMessages`, then asserts:

```java
assertTrue(prompt.contains("## Domain Knowledge"));
assertTrue(prompt.contains("## User Preferences"));
assertTrue(prompt.contains("## Conversation History"));
assertFalse(prompt.contains("## Memory"));
assertTrue(prompt.indexOf("## User Preferences") > prompt.indexOf("## Runtime"));
```

- [ ] **Step 2: Run the targeted test to verify failure**

Run: `mvn test -pl meta-claw-core -Dtest=SystemPromptBuilderTest`
Expected: FAIL because the current builder still emits `## Memory` and keeps preferences under knowledge.

- [ ] **Step 3: Commit the red test**

```bash
git add meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java
git commit -m "test: define explicit memory prompt sections"
```

### Task 2: Split the context template and prompt builder sections

**Files:**
- Modify: `meta-claw-core/src/main/resources/templates/context.tmpl.md`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`

- [ ] **Step 1: Replace the generic context placeholder**

Update `context.tmpl.md` to:

```markdown
<WORKSPACE_SECTION/>

<RUNTIME_SECTION/>

<PREFERENCES_SECTION/>

<CONVERSATION_HISTORY_SECTION/>
```

- [ ] **Step 2: Refactor the builder into explicit sections**

In `SystemPromptBuilder`:

```java
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

private String buildConversationHistorySection(PromptContext context) {
    if (isBlank(context.getConversationSummary())
            && (context.getRecentMessages() == null || context.getRecentMessages().isEmpty())) {
        return "";
    }
    StringBuilder sb = new StringBuilder("## Conversation History\n\n");
    if (!isBlank(context.getConversationSummary())) {
        sb.append("### Conversation Summary\n\n")
                .append(context.getConversationSummary())
                .append("\n\n");
    }
    if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
        sb.append("### Recent Messages\n\n");
        context.getRecentMessages().forEach(m -> sb.append("**")
                .append(m.getRole())
                .append(":** ")
                .append(orDefault(m.getContent(), ""))
                .append("\n\n"));
    }
    return sb.toString().trim();
}
```

And replace context template substitutions with:

```java
template = template.replace("<PREFERENCES_SECTION/>", buildPreferencesSection(context));
template = template.replace("<CONVERSATION_HISTORY_SECTION/>", buildConversationHistorySection(context));
```

- [ ] **Step 3: Run the targeted tests**

Run: `mvn test -pl meta-claw-core -Dtest=SystemPromptBuilderTest`
Expected: PASS.

- [ ] **Step 4: Commit the semantic split**

```bash
git add meta-claw-core/src/main/resources/templates/context.tmpl.md \
        meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java \
        meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java
git commit -m "refactor: split preference and history prompt sections"
```

### Task 3: Rename the short-term-memory implementation unit

**Files:**
- Rename: `meta-claw-core/src/main/java/meta/claw/core/prompt/MemoryManager.java` → `meta-claw-core/src/main/java/meta/claw/core/prompt/ConversationHistoryManager.java`
- Rename: `meta-claw-core/src/test/java/meta/claw/core/prompt/MemoryManagerTest.java` → `meta-claw-core/src/test/java/meta/claw/core/prompt/ConversationHistoryManagerTest.java`
- Modify: all current consumers/imports that instantiate `MemoryManager`

- [ ] **Step 1: Rename the tests first**

Rename the test class and file to `ConversationHistoryManagerTest`, and replace constructor uses:

```java
private final ConversationHistoryManager manager = new ConversationHistoryManager();
```

- [ ] **Step 2: Run the targeted renamed test to verify compilation failure**

Run: `mvn test -pl meta-claw-core -Dtest=ConversationHistoryManagerTest`
Expected: FAIL because `ConversationHistoryManager` does not exist yet.

- [ ] **Step 3: Rename the implementation and update consumers**

Create/rename the implementation class as:

```java
public class ConversationHistoryManager {
    // existing truncateByRound, truncateByToken, summarizeConversation behavior unchanged
}
```

Update imports/usages in CLI/runtime classes from `MemoryManager` to `ConversationHistoryManager`.

- [ ] **Step 4: Run focused tests**

Run: `mvn test -pl meta-claw-core,meta-claw-cli -am -Dtest=ConversationHistoryManagerTest,ChatCommandTest`
Expected: PASS.

- [ ] **Step 5: Commit the rename**

```bash
git add meta-claw-core meta-claw-cli
git commit -m "refactor: rename memory manager to conversation history manager"
```

### Task 4: Align documentation and repository state

**Files:**
- Modify: `docs/superpowers/specs/2026-05-09-meta-claw-phase2-prompt-engineering-design.md`
- Modify: `claude-progress.md`
- Modify: `clean-state-checklist.md`
- Modify: `evaluator-rubric.md`
- Modify: `feature_list.json`

- [ ] **Step 1: Update active documentation language**

Revise active docs so they describe:

```text
Memory
├─ Short-term Memory = Conversation History
└─ Long-term Memory = Preferences (current implementation)
```

Also update the older Phase 2 design references that currently describe preferences as `buildMemorySection()` output.

- [ ] **Step 2: Add a tracked feature entry and evidence**

Add a single active feature entry such as `memory-001` with verification criteria for:

```text
- Conversation history manager naming
- Explicit prompt placeholders for preferences and conversation history
- Prompt rendering tests
- Full ./init.sh verification
```

- [ ] **Step 3: Commit the documentation alignment**

```bash
git add docs/superpowers/specs/2026-05-09-meta-claw-phase2-prompt-engineering-design.md \
        claude-progress.md clean-state-checklist.md evaluator-rubric.md feature_list.json
git commit -m "docs: align active artifacts with memory model"
```

### Task 5: Verify the whole path

**Files:**
- No source changes expected unless verification exposes regressions.

- [ ] **Step 1: Run focused validation**

Run: `mvn test -pl meta-claw-core,meta-claw-cli -am`
Expected: PASS.

- [ ] **Step 2: Run the standard repository entrypoint**

Run: `./init.sh`
Expected: PASS with all reactor modules successful.

- [ ] **Step 3: Record final evidence and commit if verification required doc updates**

If the final verification changes state files, commit them with:

```bash
git add claude-progress.md clean-state-checklist.md evaluator-rubric.md feature_list.json
git commit -m "docs: mark memory model clarification complete"
```
