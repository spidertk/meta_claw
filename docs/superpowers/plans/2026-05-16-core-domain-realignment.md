# Core Domain Realignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the unused session domain, split config/message concerns, and refactor Memory into a configuration-driven manager/backend architecture with `SpiMessage` as the only short-term public message model.

**Architecture:** `ShortMemoryManager` and `LongMemoryManager` become orchestration services. They read `MemoryConfig`, choose pluggable backend stores, and expose stable memory behavior to callers. Concrete JSON/file persistence moves behind `ShortMemoryStore` / `LongMemoryStore`; `ConversationHistoryManager` becomes a short-term windowing strategy rather than the whole short-memory abstraction.

**Tech Stack:** Java 21, Maven, JUnit 5, Spring Boot.

---

## File Map

### Create / reshape in core
- `config/MemoryConfig.java`
- `memory/shortterm/ShortMemoryManager.java`
- `memory/shortterm/ShortMemoryStore.java`
- `memory/longterm/LongMemoryManager.java`
- `memory/longterm/LongMemoryStore.java`

### Rename / reshape in store
- `JsonlConversationStore` → `JsonlShortMemoryStore`
- `FilePreferenceStore` → `FileLongMemoryStore`

### Move by domain
- config models → `core.config`
- message-flow models → `core.message`

### Delete
- `core.session.*`
- `SessionManagerTest`
- `SessionConfig`
- `ChatMessage`

### Task 1: Normalize the public memory model
- [ ] Replace short-term store APIs from `ChatMessage` to `SpiMessage`.
- [ ] Remove `ChatMessage` usages from prompt/CLI/store tests.
- [ ] Keep only the minimal short-term projection APIs still needed by CLI session listing.

### Task 2: Build orchestration managers and config
- [ ] Add `MemoryConfig` with configurable short-/long-term backend names.
- [ ] Add real `ShortMemoryManager` and `LongMemoryManager` classes that own backend selection.
- [ ] Keep `ConversationHistoryManager` as a strategy used by `ShortMemoryManager`.
- [ ] Move callers away from direct `new Jsonl...Store` / `new File...Store` where current code path can use managers.

### Task 3: Convert concrete stores into backends
- [ ] Rename `ConversationStore` to `ShortMemoryStore`.
- [ ] Rename `JsonlConversationStore` to `JsonlShortMemoryStore`.
- [ ] Add `LongMemoryStore` and rename `FilePreferenceStore` to `FileLongMemoryStore`.
- [ ] Preserve current on-disk JSONL/file layout.

### Task 4: Finish the core domain cleanup
- [ ] Delete detached `session` sources/tests and remove their beans from `AppConfig`.
- [ ] Finish moving config models into `core.config`.
- [ ] Finish moving message models into `core.message`.
- [ ] Confirm `core.model` and `core.session` no longer exist.

### Task 5: Update docs and status
- [ ] Update active specs/docs/state files to the manager/backend architecture.
- [ ] Add feature evidence for config-driven memory orchestration and domain cleanup.

### Task 6: Verify the whole path
- [ ] Run targeted memory/store/CLI tests.
- [ ] Run `./init.sh`.
- [ ] Restore build noise and commit the implementation/state changes separately.
