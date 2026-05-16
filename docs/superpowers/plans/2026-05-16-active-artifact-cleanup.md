# Active Artifact Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove stale Expert-era language from live repository artifacts while preserving historical migration documents.

**Architecture:** Keep the cleanup purely textual in active files that describe the present system: README, current POM comments/descriptions, runtime config comments, and current test messages. Validate with a targeted residue scan plus the standard repository entrypoint.

**Tech Stack:** Markdown, XML, YAML, Java, Bash

---

### Task 1: Update active documentation and metadata

**Files:**
- Modify: `README.md`
- Modify: `pom.xml`
- Modify: `meta-claw-core/pom.xml`
- Modify: `meta-claw-cli/pom.xml`
- Modify: `meta-claw-bootstrap/pom.xml`
- Modify: `meta-claw-bootstrap/src/main/resources/application.yml`

- [ ] Replace current-module descriptions so they reflect `VesselRuntime` and the actual merged module layout.
- [ ] Replace active comments that still mention `meta-claw-session`, `ExpertRuntime`, `Expert`, `专家`, or `expert.yaml`.
- [ ] Run the targeted scan and confirm remaining matches are outside the active-file scope.

### Task 2: Update current test language

**Files:**
- Modify: `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java`

- [ ] Replace `专家` wording in comments and assertion messages with `Vessel` / `数字员工`.
- [ ] Re-run the targeted scan and confirm the test file is clean.

### Task 3: Verify and sync persistent state

**Files:**
- Modify: `claude-progress.md`
- Modify: `clean-state-checklist.md`
- Modify: `evaluator-rubric.md`
- Modify: `feature_list.json`

- [ ] Run `./init.sh`
- [ ] Update state files with exact evidence from the residue scan and verification run.
- [ ] Mark `semantic-001` as `passing` only if the active-artifact scan is clean and `./init.sh` passes.
- [ ] Validate JSON with `python3 -m json.tool feature_list.json >/dev/null`
- [ ] Commit implementation and state sync with clear messages.
