# Short Memory Realtime Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist short-term chat messages immediately as timestamped `MemoryEntry` records while preserving the existing `SpiMessage` API for callers.

**Architecture:** `JsonlShortMemoryStore` becomes the conversion boundary between transport messages and persisted memory entries. It writes `MemoryEntry` JSON lines with a human-readable timestamp format, then maps them back to `SpiMessage` when loading history.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5, Maven

---

### Task 1: Lock the short-memory file contract

**Files:**
- Modify: `meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java`

- [ ] **Step 1: Add a failing file-format test**

Add a test that appends one user message, reads `history.jsonl`, and asserts the JSON contains `category`, `content`, `sessionId`, `metadata.role`, and a `timestamp` matching `\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}`.

- [ ] **Step 2: Run the focused test**

Run: `mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL because the current JSONL payload is still serialized `SpiMessage`.

### Task 2: Move JSONL persistence onto `MemoryEntry`

**Files:**
- Modify: `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`

- [ ] **Step 1: Configure Jackson time serialization**

Configure the store mapper so `LocalDateTime` values serialize as `yyyy-MM-dd HH:mm:ss`.

- [ ] **Step 2: Add conversion helpers**

Add helpers that:
- build a `MemoryEntry` from `sessionKey` and `SpiMessage`
- rebuild a `SpiMessage` from a persisted `MemoryEntry`

- [ ] **Step 3: Update append/read paths**

Write `MemoryEntry` lines in `appendMessage()` and read `MemoryEntry` lines in `getHistory()` / vessel-scoped history reads.

- [ ] **Step 4: Re-run the focused store test**

Run: `mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS.

### Task 3: Verify realtime behavior and repository baseline

**Files:**
- Modify if needed: `claude-progress.md`
- Modify if needed: `feature_list.json`

- [ ] **Step 1: Confirm immediate file append in test coverage**

Review the focused test to ensure it reads the file immediately after `appendMessage()` returns.

- [ ] **Step 2: Run the standard repo verification**

Run: `./init.sh`

Expected: full compile and P0 tests pass.

- [ ] **Step 3: Record evidence and commit**

Update progress and feature evidence, then commit the change set with a message describing realtime timestamped short-memory persistence.
