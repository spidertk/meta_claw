# Source Registry Contract Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the language-neutral source registry contract and example with the Java `SourceRecord.latestSnapshotId` field already present in code.

**Architecture:** Keep Java as the current implementation source of truth and expose the same pointer through snake_case JSON as `latest_snapshot_id`. This is a contract/example-only correction; it does not add persistence, real source scanning, or graph/wiki processing.

**Tech Stack:** JSON Schema draft 2020-12, shared JSON examples, Java Lombok domain model.

---

### Task 1: Add `latest_snapshot_id` To Contract

**Files:**
- Modify: `knowledge/contracts/source-registry.schema.json`

- [x] **Step 1: Add the optional property**

Add this property after `updated_at`:

```json
"latest_snapshot_id": {
  "type": [
    "string",
    "null"
  ]
}
```

- [x] **Step 2: Keep the field optional**

Do not add `latest_snapshot_id` to the top-level `required` array because newly registered sources can exist before a snapshot pointer is assigned.

- [x] **Step 3: Validate JSON syntax**

Run: `python3 -m json.tool knowledge/contracts/source-registry.schema.json >/tmp/source-registry.schema.validated.json`

Expected: command exits successfully and writes formatted JSON to `/tmp/source-registry.schema.validated.json`.

### Task 2: Update Shared Example

**Files:**
- Modify: `knowledge/examples/source-registry.example.json`

- [x] **Step 1: Add the example pointer**

Add this field after `updated_at`:

```json
"latest_snapshot_id": "snapshot_meta_claw_001"
```

- [x] **Step 2: Validate JSON syntax**
 
Run: `python3 -m json.tool knowledge/examples/source-registry.example.json >/tmp/source-registry.example.validated.json`

Expected: command exits successfully and writes formatted JSON to `/tmp/source-registry.example.validated.json`.

### Task 3: Verify Java Naming Boundary

**Files:**
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/domain/SourceRecord.java`
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java`

- [x] **Step 1: Confirm Java field**

Verify `SourceRecord` contains:

```java
private String latestSnapshotId;
```

- [x] **Step 2: Confirm registration process**

Verify `RegisterSourceProcess` sets `latestSnapshotId` when creating a new snapshot and when reusing an unchanged snapshot.

- [x] **Step 3: Compile Java core if available**

Run: `./gradlew classes` from `knowledge/service/core`

Expected: Gradle completes successfully or reports an environment/dependency issue unrelated to the JSON contract edit.
