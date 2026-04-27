# Snapshot Scan Cursor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a stable scan cursor so partial directory snapshots can describe where the next batch should resume.

**Architecture:** Extend snapshot contract/domain with `nextScanCursor`, represented as `next_scan_cursor` in JSON. Keep `RegisterSourceProcess` on the first batch for now, and add a cursor-aware `SourceSnapshotScanner.createSnapshot(request)` entry for later resume orchestration.

**Tech Stack:** Java 21, Lombok domain models, JSON Schema draft 2020-12, Gradle.

---

### Task 1: Extend Snapshot Contract With Cursor

**Files:**
- Modify: `knowledge/contracts/snapshot-store.schema.json`
- Modify: `knowledge/examples/snapshot-store.example.json`

- [x] **Step 1: Add `next_scan_cursor` to required fields**

Add `next_scan_cursor` to the `required` array so every snapshot explicitly states whether another batch can continue.

- [x] **Step 2: Add nullable cursor property**

Add:

```json
"next_scan_cursor": {
  "type": [
    "string",
    "null"
  ]
}
```

- [x] **Step 3: Update complete example**

Set:

```json
"next_scan_cursor": null
```

### Task 2: Extend Java Snapshot Domain

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/domain/SnapshotRecord.java`

- [x] **Step 1: Add field**

Add:

```java
/** 下一批扫描的稳定游标；没有后续批次时为 null。 */
private String nextScanCursor;
```

### Task 3: Add Cursor-Aware Intake Helper

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Add overload**

Keep existing callers working:

```java
public static SnapshotRecord createSnapshot(SourceRecord sourceRecord) {
    return createSnapshot(sourceRecord, null);
}
```

- [x] **Step 2: Use cursor in directory unit scan**

For directory file paths sorted by relative path, skip files whose relative path is less than or equal to `scanCursor`.

- [x] **Step 3: Set `nextScanCursor`**

When another batch exists, set cursor to the last included relative file path. When complete, set it to `null`.

- [x] **Step 4: Keep first batch behavior compatible**

`RegisterSourceProcess` still calls `createSnapshot(sourceRecord)` and receives the first batch.

### Task 4: Verify And Sync Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`

- [x] **Step 1: Update docs**

State that `nextScanCursor` is now present and resume orchestration is still future work.

- [x] **Step 2: Validate and build**

Run:

```bash
python3 -m json.tool knowledge/contracts/snapshot-store.schema.json >/tmp/snapshot-store.schema.validated.json
python3 -m json.tool knowledge/examples/snapshot-store.example.json >/tmp/snapshot-store.example.validated.json
./gradlew classes
./gradlew run
git diff --check
```

Expected: JSON validation, Java compilation, demo run, and diff check all pass.
