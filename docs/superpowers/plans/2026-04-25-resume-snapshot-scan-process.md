# Resume Snapshot Scan Process Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an application process that resumes one additional scan batch from a partial snapshot cursor.

**Architecture:** Keep snapshots immutable by creating a new batch snapshot for each resume. The process reads the current source, reads its latest snapshot, returns it unchanged when no resume is needed, or creates/saves the next batch and advances `SourceRecord.latestSnapshotId`.

**Tech Stack:** Java 21, Lombok, existing in-memory repositories, Gradle.

---

### Task 1: Add Source Registry Lookup Support

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/repository/SourceRegistryRepository.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/demo/SampleSourceRegistryRepository.java`

- [x] **Step 1: Confirm existing lookup**

Use existing `findById(String sourceId)` to load the current source view. No new repository method is needed.

### Task 2: Implement Resume Process

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java`

- [x] **Step 1: Add process class**

Create a class with constructor dependencies:

```java
private final SourceRegistryRepository sourceRegistryRepository;
private final SnapshotStoreRepository snapshotStoreRepository;
```

- [x] **Step 2: Add execute method**

`execute(String sourceId)`:

- load source by id or throw `IllegalArgumentException`
- if `latestSnapshotId` is blank, register a first batch with `SourceSnapshotScanner.createSnapshot(request)`
- load latest snapshot by id or throw `IllegalStateException`
- if `scanStatus` is not `partial` or `nextScanCursor` is blank, return latest snapshot
- create next batch via `SourceSnapshotScanner.createSnapshot(request)`
- save snapshot
- update and save source `latestSnapshotId`
- return next snapshot

### Task 3: Expose Controller And Demo Entry

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`

- [x] **Step 1: Add controller dependency and method**

Add `ResumeSnapshotScanProcess` to `CoreController` and expose:

```java
public SnapshotRecord resumeSnapshotScan(String sourceId)
```

- [x] **Step 2: Wire demo application**

Instantiate `ResumeSnapshotScanProcess` and pass it into `CoreController`.

- [x] **Step 3: Demo one resume**

After first registration, if `scanStatus=partial`, call `resumeSnapshotScan(sourceId)` and log returned `snapshotId`, `scanStatus`, `nextScanCursor`.

### Task 4: Verify And Sync Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`

- [x] **Step 1: Mark one-batch resume implemented**

Update docs to say resume process exists for one additional batch per call; loop-to-complete and persistence remain future work.

- [x] **Step 2: Build and run**

Run:

```bash
./gradlew classes
./gradlew run
git diff --check
```

Expected: build succeeds, demo logs registration and one resume batch, diff check passes.
