# Short Memory Interface Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make short-memory APIs use `MemoryEntry` end-to-end and move `SpiMessage` conversion into a dedicated converter.

**Architecture:** `MemoryEntryConverter` becomes the only bridge between chat transport models and memory models. `ShortMemoryStore` and `ShortMemoryManager` expose only `MemoryEntry`, while `ChatCommand` converts at the CLI boundary.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5, Maven

---

### Task 1: Add the conversion boundary

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/memory/MemoryEntryConverter.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/memory/MemoryEntryConverterTest.java`

- [ ] Add focused tests for `SpiMessage -> MemoryEntry` and `MemoryEntry -> SpiMessage`
- [ ] Implement the converter with `message` category and metadata-based role/toolCalls mapping
- [ ] Run the focused converter test

### Task 2: Unify short-memory contracts

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStore.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryManager.java`

- [ ] Rename `appendMessage` to `appendEntry`
- [ ] Change history/query/summarize APIs to use `MemoryEntry`
- [ ] Rename `truncateByRound` to overloaded `getHistory`
- [ ] Rename `truncateByToken` to `getHistoryByToken`

### Task 3: Move store implementation to `MemoryEntry`

**Files:**
- Modify: `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
- Modify: `meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java`

- [ ] Update append/read paths to use `MemoryEntry`
- [ ] Keep backward-compatible parsing for older direct-`SpiMessage` lines
- [ ] Update truncation tests to assert on `MemoryEntry`
- [ ] Run focused store tests

### Task 4: Update CLI boundary

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
- Modify: `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`

- [ ] Convert writes with `MemoryEntryConverter.fromSpiMessage`
- [ ] Convert loaded memory history back to `SpiMessage`
- [ ] Use `MemoryEntry` history windowing before LLM requests
- [ ] Run focused CLI tests

### Task 5: Verify and record

**Files:**
- Modify: `claude-progress.md`
- Modify: `feature_list.json`

- [ ] Run `./init.sh`
- [ ] Record evidence
- [ ] Commit the completed refactor
