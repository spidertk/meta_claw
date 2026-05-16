# Memory Domain Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote Memory into an explicit domain by moving short-term and long-term memory code into dedicated core/store packages while preserving current behavior.

**Architecture:** Keep session lifecycle separate from memory content. Move conversation-history abstractions into `core.memory.shortterm`, preference abstractions into `core.memory.longterm`, and mirror those boundaries in `store.memory.shortterm` / `store.memory.longterm`; update prompt rendering to consume explicit preference and conversation-history sections.

**Tech Stack:** Java 21, Maven, JUnit 5, Lombok, Markdown templates.

---

## File Map

### Move into `core.memory.shortterm`
- `ChatMessage`
- `ConversationHistoryManager`
- `ConversationInfo`
- `ConversationStats`
- `ConversationStore`
- `MediaReference`
- `MessageFilter`

### Move into `core.memory.longterm`
- `PreferenceEntry`
- `UserPreferenceStore`

### Keep in `core.session`
- `ChatMode`
- `InMemorySessionStorage`
- `SessionManager`
- `SessionStorage`
- `UserSession`

### Move into `store.memory.shortterm`
- `JsonlConversationStore`

### Move into `store.memory.longterm`
- `FilePreferenceStore`

### Modify in place
- `PromptContext`
- `PromptContextFactory`
- `SystemPromptBuilder`
- `context.tmpl.md`
- CLI consumers and all affected tests/docs.

### Task 1: Lock prompt semantics
- [ ] Keep the already-added `SystemPromptBuilderTest` assertions that require explicit `User Preferences` and `Conversation History` sections and reject `## Memory`.
- [ ] Verify the targeted test fails before implementation and passes after template/builder updates.

### Task 2: Split prompt placeholders
- [ ] Replace `<MEMORY_SECTION/>` with `<PREFERENCES_SECTION/>` and `<CONVERSATION_HISTORY_SECTION/>` in `context.tmpl.md`.
- [ ] Split `SystemPromptBuilder` into `buildPreferencesSection()` and `buildConversationHistorySection()` while keeping `buildKnowledgeSection()` knowledge-only.
- [ ] Run `mvn test -pl meta-claw-core -Dtest=SystemPromptBuilderTest`.

### Task 3: Move short-term memory abstractions
- [ ] Move the short-term memory types from `core.session` / `core.prompt` into `core.memory.shortterm`.
- [ ] Update package declarations and imports across CLI, prompt, store, and tests.
- [ ] Keep session lifecycle classes in `core.session`.
- [ ] Run focused tests for `ConversationHistoryManagerTest`, `SystemPromptBuilderTest`, `JsonlConversationStoreTest`, `ChatCommandTest`.

### Task 4: Move long-term memory abstractions
- [ ] Move `PreferenceEntry` and `UserPreferenceStore` into `core.memory.longterm`.
- [ ] Update `PromptContextFactory`, `FilePreferenceStore`, and related tests/imports.
- [ ] Run focused tests for `PromptContextFactoryTest` and `FilePreferenceStoreTest`.

### Task 5: Mirror the memory boundary in store
- [ ] Move `JsonlConversationStore` to `meta.claw.store.memory.shortterm`.
- [ ] Move `FilePreferenceStore` to `meta.claw.store.memory.longterm`.
- [ ] Update CLI/test imports and leave on-disk storage layout unchanged.
- [ ] Run focused store and CLI tests.

### Task 6: Align docs and repository state
- [ ] Update the Phase 2 design doc, `claude-progress.md`, `clean-state-checklist.md`, `evaluator-rubric.md`, and `feature_list.json` to reflect the Memory domain and verification evidence.
- [ ] Add or update a single tracked feature entry for the Memory domain refactor.

### Task 7: Verify the full path
- [ ] Run `mvn test -pl meta-claw-core,meta-claw-store,meta-claw-cli -am`.
- [ ] Run `./init.sh`.
- [ ] Restore generated `target/` changes if needed, then commit final state updates.
