# Memory Aggregate Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the overloaded `MemoryEntry` model with a `Memory` aggregate root plus focused child models for sessions, messages, and preferences.

**Architecture:** `Memory` owns `SessionMemory` and `PreferenceMemory` collections. Short-term stores persist `MemoryMessage` lines and per-session `summary.json`, while long-term stores persist `PreferenceMemory` records.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5, Maven

---

### Task 1: Introduce the new memory domain model

**Files:**
- Create: `Memory.java`
- Create: `SessionMemory.java`
- Create: `MemoryMessage.java`
- Create: `PreferenceMemory.java`
- Create: `MemoryMessageConverter.java`

- [ ] Add focused tests for message conversion and model serialization expectations
- [ ] Implement the new Lombok-backed DTOs with builders

### Task 2: Refactor short-term memory contracts

**Files:**
- Modify: `ShortMemoryStore.java`
- Modify: `ShortMemoryManager.java`
- Modify: `JsonlShortMemoryStore.java`
- Modify: `JsonlShortMemoryStoreTest.java`

- [ ] Replace `MemoryEntry` with `MemoryMessage` for history operations
- [ ] Replace `listSessions` return type with `SessionMemory`
- [ ] Add `loadSummary` / `saveSummary`
- [ ] Persist `history.jsonl` as `MemoryMessage`
- [ ] Persist `summary.json` as `SessionMemory`

### Task 3: Refactor long-term memory contracts

**Files:**
- Modify: `LongMemoryStore.java`
- Modify: `LongMemoryManager.java`
- Modify: `FileLongMemoryStore.java`
- Modify: `FileLongMemoryStoreTest.java`

- [ ] Replace `MemoryEntry` with `PreferenceMemory`
- [ ] Keep existing preference lookup behavior

### Task 4: Update CLI boundaries

**Files:**
- Modify: `ChatCommand.java`
- Modify: `SessionsCommand.java`
- Modify related tests

- [ ] Convert CLI transport messages through `MemoryMessageConverter`
- [ ] Render session list from `SessionMemory`

### Task 5: Remove obsolete model usage and verify

**Files:**
- Delete or retire: `MemoryEntry.java`, `MemoryEntryConverter.java`
- Modify: `claude-progress.md`
- Modify: `feature_list.json`

- [ ] Remove stale references to `MemoryEntry`
- [ ] Run focused tests
- [ ] Run `./init.sh`
- [ ] Record evidence and commit
