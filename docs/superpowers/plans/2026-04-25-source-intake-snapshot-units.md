# Source Intake Snapshot Units Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Java source intake skeleton generate deterministic content fingerprints and useful root/file `UnitRef` entries for local file and directory sources.

**Architecture:** Keep all source intake helper logic inside `SourceSnapshotScanner` so `RegisterSourceProcess` remains orchestration-only. For files, hash file bytes; for directories, hash a bounded sorted list of relative paths plus file byte hashes and create one root unit plus file units for the same bounded scan.

**Tech Stack:** Java 21, `java.nio.file`, SHA-256, existing Lombok domain models.

---

### Task 1: Replace Metadata Fingerprint With Content Fingerprint

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Add constants and byte hash helper**

Add a maximum directory unit count constant and hash file bytes with `MessageDigest`.

- [x] **Step 2: Hash regular files by bytes**

For `Files.isRegularFile(path)`, compute `sha256:<hex>` from the file content instead of size and modified time.

- [x] **Step 3: Hash directories by sorted relative file content**

For `Files.isDirectory(path)`, walk up to the same bounded limit, sort by path, and hash each relative path plus either directory marker or file byte hash.

### Task 2: Generate File Units For Directory Snapshots

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Keep root unit**

Create one root `UnitRef` for the source path, preserving the current root behavior.

- [x] **Step 2: Add bounded file units**

For directory snapshots, add one `UnitRef` for each regular file found in sorted order up to the bounded limit.

- [x] **Step 3: Link file units to root**

Set each file unit `parentUnitId` to the root unit id and set root `neighbors` to the file unit ids.

### Task 3: Verify Build

**Files:**
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java`

- [x] **Step 1: Compile Java core**

Run: `./gradlew classes` from `knowledge/service/core`.

Expected: Gradle completes successfully.
