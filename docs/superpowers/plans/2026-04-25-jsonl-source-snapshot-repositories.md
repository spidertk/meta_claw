# JSONL Source Snapshot Repositories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist source registry and snapshot store records to local JSON Lines files so Sprint 2 state survives process restart without introducing a database.

**Architecture:** Add outbound file adapters that implement the existing repository interfaces. Each adapter keeps an in-memory index for fast reads and rewrites its JSONL file on save, using Jackson for JSON encoding/decoding while keeping repository contracts unchanged.

**Tech Stack:** Java 21, `java.nio.file`, Jackson databind, Jackson JSR310 module, existing domain models, JSON Lines, Gradle.

---

### Task 1: Add Jackson JSONL Codec

**Files:**
- Modify: `knowledge/service/core/build.gradle.kts`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/file/JacksonJsonLineSupport.java`
- Delete: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/file/JsonLineSupport.java`

- [x] **Step 1: Add Jackson dependencies**

Add `jackson-databind` and `jackson-datatype-jsr310`.

- [x] **Step 2: Add shared ObjectMapper**

Register `JavaTimeModule` and disable timestamp date serialization.

- [x] **Step 3: Remove custom parser**

Delete the earlier hand-written JSON parser and use Jackson for JSONL encoding/decoding.

### Task 2: Implement Source Registry File Adapter

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/file/FileSourceRegistryRepository.java`

- [x] **Step 1: Load JSONL file on construction**

If the file exists, parse each non-blank line and index by `source_id`.

- [x] **Step 2: Save by source id**

Update the index and rewrite the whole JSONL file so source current view remains one record per source.

- [x] **Step 3: Preserve nested source metadata**

Persist `workspace_identity`, `snapshot_hint`, and `latest_snapshot_id`.

### Task 3: Implement Snapshot Store File Adapter

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/file/FileSnapshotStoreRepository.java`

- [x] **Step 1: Load JSONL file on construction**

If the file exists, parse each non-blank line and index by `snapshot_id`.

- [x] **Step 2: Save immutable snapshots**

Update the index by `snapshot_id` and rewrite the JSONL file.

- [x] **Step 3: Preserve units and scan metadata**

Persist `units`, `scan_status`, `unit_limit`, `included_unit_count`, `scan_batch_count`, and `next_scan_cursor`.

### Task 4: Wire Demo Application

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`

- [x] **Step 1: Replace source/snapshot sample repositories**

Use file adapters for source registry and snapshot store, writing to `knowledge/demo-store` under the local repo checkout.

- [x] **Step 2: Keep in-memory binding and knowledge state repositories**

Only source/snapshot persistence is in scope for this slice.

### Task 5: Verify And Sync Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`

- [x] **Step 1: Mark local JSONL persistence implemented**

Update Sprint 2 baseline to mention local JSONL adapters.

- [x] **Step 2: Build and run**

Run:

```bash
./gradlew classes
./gradlew run
git diff --check
```

Expected: build succeeds, demo writes JSONL files under `knowledge/demo-store`, and diff check passes.
