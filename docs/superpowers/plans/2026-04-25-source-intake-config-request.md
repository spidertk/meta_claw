# Source Intake Config Request Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hard-coded source intake limit with an objectized configuration and request model so intake behavior can grow without repeated method signature changes.

**Architecture:** Introduce `SourceIntakeConfig` for reusable intake settings and `SourceIntakeRequest` for per-call state. `SourceSnapshotScanner` will accept the request object and derive scan behavior from it, while `RegisterSourceProcess` and `ResumeSnapshotScanProcess` construct the request from their dependencies.

**Tech Stack:** Java 21, Lombok, existing domain models, Gradle.

---

### Task 1: Add Intake Config And Request Types

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceIntakeConfig.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceIntakeRequest.java`

- [x] **Step 1: Add config object**

`SourceIntakeConfig` should carry:

```java
private int unitLimit;
```

- [x] **Step 2: Add request object**

`SourceIntakeRequest` should carry:

```java
private SourceRecord sourceRecord;
private String scanCursor;
private SourceIntakeConfig config;
```

### Task 2: Refactor Source Intake Support

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Replace static limit with request config**

Read the unit limit from `SourceIntakeRequest.config`.

- [x] **Step 2: Keep the scan logic intact**

Preserve content hashing, root/file unit creation, cursor filtering, and scan status calculation.

- [x] **Step 3: Keep a default helper if needed**

If convenient for callers, provide a single-object factory path rather than separate primitive parameters.

### Task 3: Update Processes And Demo Wiring

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`

- [x] **Step 1: Inject intake config**

Pass `SourceIntakeConfig` into both processes.

- [x] **Step 2: Build request objects**

Construct `SourceIntakeRequest` in each process before calling `SourceSnapshotScanner`.

- [x] **Step 3: Wire demo config**

Create the config in `CoreApplication` and pass it through process constructors.

### Task 4: Sync Docs And Verify

**Files:**
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`

- [x] **Step 1: Update docs**

Mark `unitLimit` as coming from `SourceIntakeConfig` rather than a hard-coded constant.

- [x] **Step 2: Build and run**

Run:

```bash
./gradlew classes
./gradlew run
git diff --check
```

Expected: build succeeds, demo still writes JSONL files under `knowledge/demo-store`, and diff check passes.
