# Snapshot Scan Coverage Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add scan coverage metadata to snapshots so a bounded batch is never mistaken for complete codebase analysis.

**Architecture:** Extend `SnapshotRecord` and the snapshot contract with coverage fields, then have `SourceSnapshotScanner` populate them from the current single-batch scan. The batch size is supplied through `SourceIntakeConfig`; multi-batch cursor/resume is documented as the next slice.

**Tech Stack:** Java 21, Lombok domain models, JSON Schema draft 2020-12, shared JSON examples, Gradle.

---

### Task 1: Extend Snapshot Contract

**Files:**
- Modify: `knowledge/contracts/snapshot-store.schema.json`
- Modify: `knowledge/examples/snapshot-store.example.json`

- [x] **Step 1: Add required coverage fields to schema**

Add these required fields after `units`:

```json
"scan_status",
"unit_limit",
"included_unit_count",
"scan_batch_count"
```

- [x] **Step 2: Add schema properties**

Add these properties:

```json
"scan_status": {
  "type": "string",
  "enum": ["complete", "partial", "truncated", "failed"]
},
"unit_limit": {
  "type": "integer",
  "minimum": 0
},
"included_unit_count": {
  "type": "integer",
  "minimum": 0
},
"scan_batch_count": {
  "type": "integer",
  "minimum": 0
}
```

- [x] **Step 3: Update example**

For the current complete sample, add:

```json
"scan_status": "complete",
"unit_limit": 512,
"included_unit_count": 2,
"scan_batch_count": 1
```

- [x] **Step 4: Validate JSON**

Run:

```bash
python3 -m json.tool knowledge/contracts/snapshot-store.schema.json >/tmp/snapshot-store.schema.validated.json
python3 -m json.tool knowledge/examples/snapshot-store.example.json >/tmp/snapshot-store.example.validated.json
```

Expected: both commands exit successfully.

### Task 2: Extend Java Snapshot Domain

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/domain/SnapshotRecord.java`

- [x] **Step 1: Add fields with Chinese comments**

Add:

```java
/** 当前快照扫描覆盖状态，例如 complete、partial、truncated 或 failed。 */
private String scanStatus;
/** 单批扫描最多纳入的子单元数量。 */
private int unitLimit;
/** 当前快照实际写入的 UnitRef 数量，包含根单元。 */
private int includedUnitCount;
/** 当前快照已经完成的扫描批次数。 */
private int scanBatchCount;
```

- [x] **Step 2: Compile Java**

Run: `./gradlew classes` from `knowledge/service/core`

Expected: build succeeds.

### Task 3: Populate Metadata In Source Intake

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Compute scan status**

After `List<UnitRef> units = buildUnits(...)`, compute:

```java
boolean hasNextBatch = filePaths.size() > unitLimit;
```

If a non-directory source has one root unit, status is `complete`.

- [x] **Step 2: Populate snapshot fields**

Set:

```java
.scanStatus(scanStatus)
.unitLimit(unitLimit)
.includedUnitCount(units.size())
.scanBatchCount(1)
```

- [x] **Step 3: Compile and run demo**

Run:

```bash
./gradlew classes
./gradlew run
```

Expected: both commands succeed; second registration still reports `unchanged=true`.

### Task 4: Sync Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`

- [x] **Step 1: Mark metadata slice as implemented**

Update the current implementation baseline to say snapshot coverage fields are now present in contract/example/domain and populated by `SourceSnapshotScanner`.

- [x] **Step 2: Keep batch cursor/resume as future work**

Leave multi-batch cursor/resume explicitly in the next slice, not in this implementation.

- [x] **Step 3: Run diff check**

Run: `git diff --check`

Expected: no whitespace errors.
