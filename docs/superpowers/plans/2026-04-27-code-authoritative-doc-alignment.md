# Code-Authoritative Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align all knowledge superpower docs with the current LiteFlow code path so outdated `*Process` descriptions cannot be mistaken for current executable architecture.

**Architecture:** Treat current Java code as the source of truth, especially `CoreController`, `KnowledgeFlowFacade`, `KnowledgeFlowExecutor`, flow contexts, chain XML, and flow nodes. Scan every `docs/superpowers/specs/*.md` and `docs/superpowers/plans/*.md` hit for outdated process/path wording, then update each hit according to its semantic role: current state, historical background, or obsolete implementation plan.

**Tech Stack:** Markdown documentation, ripgrep, Git, Java 21, Gradle, LiteFlow 2.12.0.

---

## File Structure

- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-04-21-private-knowledge-base-sprint1-design.md`
- Modify: `docs/superpowers/specs/2026-04-24-source-registry-snapshot-boundary-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-application-orchestration-poc-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-runtime-dependencies-and-worker-flow-design.md`
- Modify: `docs/superpowers/plans/2026-04-25-snapshot-scan-cursor.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-intake-config-request.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-registry-contract-alignment.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-intake-snapshot-units.md`
- Modify: `docs/superpowers/plans/2026-04-25-resume-snapshot-scan-process.md`
- Modify: `docs/superpowers/plans/2026-04-25-liteflow-application-orchestration-poc.md`
- Modify: `docs/superpowers/plans/2026-04-25-liteflow-runtime-dependencies-and-worker-flow.md`
- Inspect: `knowledge/service/core/README.md`
- Inspect: `knowledge/README.md`
- Verify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- Verify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`
- Verify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- Verify: `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`

## Task 1: Establish Current Code Facts

**Files:**
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- Inspect: `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`
- Inspect: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/FlowRuntimeDependencies.java`

- [ ] **Step 1: Confirm controller-to-facade main path**

Run:

```bash
sed -n '1,180p' knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java
```

Expected: `CoreController` has a single `KnowledgeFlowFacade` dependency and delegates `registerSource`, `resumeSnapshotScan`, `submitWorkerJob`, and `ingestWorkerResult` to that facade.

- [ ] **Step 2: Confirm facade exposes four LiteFlow-backed methods**

Run:

```bash
rg "public .* registerSource|public .* resumeSnapshotScan|public .* submitWorkerJob|public .* ingestWorkerResult|execute\\(\".*Chain\"" knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java
```

Expected: four public methods exist, and each calls a named LiteFlow chain: `registerSourceChain`, `resumeSnapshotScanChain`, `submitWorkerJobChain`, or `ingestWorkerResultChain`.

- [ ] **Step 3: Confirm node registration and chain resource**

Run:

```bash
sed -n '1,220p' knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java
sed -n '1,220p' knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml
```

Expected: `KnowledgeFlowExecutor` registers register/resume/worker nodes before `FlowExecutor.init(true)`, and the XML defines all four chains.

- [ ] **Step 4: Confirm legacy process classes are absent from Java code**

Run:

```bash
rg "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport" knowledge/service/core/src/main/java
```

Expected: no matches.

## Task 2: Classify Documentation Hits

**Files:**
- Inspect: `docs/superpowers/specs/*.md`
- Inspect: `docs/superpowers/plans/*.md`
- Inspect: `knowledge/service/core/README.md`
- Inspect: `knowledge/README.md`

- [ ] **Step 1: Generate the outdated-doc hit list**

Run:

```bash
rg -n "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport|adapter/inbound|sample repository|minimal use case|不迁 SubmitWorkerJobProcess|不迁 IngestWorkerResultProcess|只覆盖两条|仍停留在旧的 \\*Process" docs/superpowers knowledge/service/core/README.md knowledge/README.md
```

Expected: hits may remain in historical docs, but every hit must be classified before editing.

- [ ] **Step 2: Classify every hit into one of three categories**

Use these exact categories while reviewing each hit:

```text
CURRENT_STATE: document claims or implies current executable architecture.
HISTORICAL_CONTEXT: document describes a previous design stage or migration motivation.
OBSOLETE_PLAN_STEP: document contains a task step or path that was once planned but is no longer executable.
```

Expected: each hit is assigned a category before modification. Do not do mechanical global replacement.

- [ ] **Step 3: Record the classification in working notes**

Use a temporary note outside the repository or local scratch text. The note must include:

```text
file:line
category
planned treatment
```

Expected: the classification guides edits and is not committed unless it becomes useful documentation.

## Task 3: Align Current-State Specs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-04-21-private-knowledge-base-sprint1-design.md`
- Modify: `docs/superpowers/specs/2026-04-24-source-registry-snapshot-boundary-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-snapshot-scan-batch-coverage-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-application-orchestration-poc-design.md`
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-runtime-dependencies-and-worker-flow-design.md`

- [ ] **Step 1: Update current-state mentions of old process classes**

Replace current-state wording that says old `*Process` classes are current implementation with wording that names the current path:

```markdown
当前实现已由后续 LiteFlow 重构收敛到 `CoreController -> KnowledgeFlowFacade -> LiteFlow chain/node` 主路径；旧 `*Process` 类只属于迁移前历史背景，不再是当前可执行路径。
```

Expected: current-state sections no longer present old process classes as active code.

- [ ] **Step 2: Preserve historical background with explicit labels**

Where a document explains why LiteFlow was introduced, keep the historical context but prefix or suffix it with:

```markdown
> 历史背景：以下 `*Process` 描述反映 LiteFlow 迁移前的设计状态；当前代码已由 `KnowledgeFlowFacade` 和 LiteFlow chain/node 承接。
```

Expected: old process names may remain only when clearly labeled as historical.

- [ ] **Step 3: Fix old adapter and repository wording**

Update outdated wording as follows:

```text
sample repository implementations -> demo and JSONL file repository implementations
adapter/inbound/demo -> adapter/outbound/demo
内存 sample adapter -> demo repository and JSONL file adapter
```

Expected: current-state docs use `adapter/outbound/demo` and `adapter/outbound/file` for adapter location.

- [ ] **Step 4: Re-scan specs**

Run:

```bash
rg -n "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport|adapter/inbound|sample repository|minimal use case|不迁 SubmitWorkerJobProcess|不迁 IngestWorkerResultProcess|只覆盖两条|仍停留在旧的 \\*Process" docs/superpowers/specs
```

Expected: remaining hits are either in `2026-04-27-liteflow-main-path-cleanup-regression-design.md`, `2026-04-27-code-authoritative-doc-alignment-design.md`, or are explicitly marked as historical or replaced by current LiteFlow wording.

## Task 4: Align Obsolete Implementation Plans

**Files:**
- Modify: `docs/superpowers/plans/2026-04-25-snapshot-scan-cursor.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-intake-config-request.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-registry-contract-alignment.md`
- Modify: `docs/superpowers/plans/2026-04-25-source-intake-snapshot-units.md`
- Modify: `docs/superpowers/plans/2026-04-25-resume-snapshot-scan-process.md`
- Modify: `docs/superpowers/plans/2026-04-25-liteflow-application-orchestration-poc.md`
- Modify: `docs/superpowers/plans/2026-04-25-liteflow-runtime-dependencies-and-worker-flow.md`

- [ ] **Step 1: Mark obsolete process-plan steps as superseded**

For plan steps that instruct creation or modification of old `*Process` files, add a line immediately near the step:

```markdown
> Superseded: 当前代码已迁移到 `KnowledgeFlowFacade` + LiteFlow chain/node；该旧 `*Process` 步骤仅保留为历史执行记录，不再作为可执行计划。
```

Expected: no old process path appears as an unqualified executable instruction.

- [ ] **Step 2: Convert active plan wording to current path**

Where the plan describes the desired final architecture, use:

```markdown
当前主路径为 `CoreController -> KnowledgeFlowFacade -> LiteFlow chain/node`，运行时依赖通过 `FlowRuntimeDependencies` 注入。
```

Expected: active plan summaries align with current code.

- [ ] **Step 3: Correct plan file paths where they are current references**

Use current paths for current references:

```text
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/SourceSnapshotScanner.java
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/demo/
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/outbound/file/
```

Expected: old paths remain only inside superseded historical instructions.

- [ ] **Step 4: Re-scan plans**

Run:

```bash
rg -n "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport|adapter/inbound|sample repository|minimal use case|不迁 SubmitWorkerJobProcess|不迁 IngestWorkerResultProcess|只覆盖两条|仍停留在旧的 \\*Process" docs/superpowers/plans
```

Expected: remaining hits are explicitly marked as superseded, historical, or part of the completed cleanup plan.

## Task 5: Verify README Files Need No Further Alignment

**Files:**
- Inspect: `knowledge/service/core/README.md`
- Inspect: `knowledge/README.md`

- [ ] **Step 1: Check Java core README**

Run:

```bash
sed -n '1,160p' knowledge/service/core/README.md
rg -n "minimal use case|Process|process skeleton|sample repository|adapter/inbound" knowledge/service/core/README.md
```

Expected: README describes `LiteFlow facade + chain/node application orchestration`; the `rg` command returns no matches.

- [ ] **Step 2: Check knowledge README**

Run:

```bash
sed -n '1,220p' knowledge/README.md
rg -n "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport|adapter/inbound|minimal use case" knowledge/README.md
```

Expected: no current-state mismatch. If mismatches appear, update `knowledge/README.md` using the same code-authoritative wording.

## Task 6: Documentation Consistency Review

**Files:**
- Verify: `docs/superpowers/specs/*.md`
- Verify: `docs/superpowers/plans/*.md`

- [ ] **Step 1: Run full outdated keyword scan**

Run:

```bash
rg -n "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess|SourceIntakeSupport|adapter/inbound|sample repository|minimal use case|不迁 SubmitWorkerJobProcess|不迁 IngestWorkerResultProcess|只覆盖两条|仍停留在旧的 \\*Process" docs/superpowers knowledge/service/core/README.md knowledge/README.md
```

Expected: every remaining hit has one of these nearby markers or equivalent wording:

```text
历史背景
迁移前
已被后续 LiteFlow 重构取代
不再适用
Superseded
```

- [ ] **Step 2: Inspect unmatched current-state risks**

For each remaining hit without an explicit marker, edit the surrounding paragraph to either remove the outdated wording or add an explicit historical/superseded marker.

Expected: no unqualified old process path remains.

- [ ] **Step 3: Check the final diff**

Run:

```bash
git diff -- docs/superpowers knowledge/service/core/README.md knowledge/README.md
```

Expected: diff contains documentation-only changes. No Java, contract, schema, or demo-store files are modified.

## Task 7: Regression And Commit

**Files:**
- Verify: `knowledge/service/core/build.gradle.kts`
- Commit: `docs/superpowers/specs/*.md`
- Commit: `docs/superpowers/plans/*.md`
- Commit if modified: `knowledge/service/core/README.md`
- Commit if modified: `knowledge/README.md`

- [ ] **Step 1: Run compile regression**

Run from `knowledge/service/core`:

```bash
./gradlew classes
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run demo regression if demo behavior wording changed**

If any edited doc describes demo output or demo application behavior, run from `knowledge/service/core`:

```bash
./gradlew run
```

Expected: `BUILD SUCCESSFUL` and logs cover register, optional resume, submit worker job, and ingest worker result.

- [ ] **Step 3: Confirm no non-doc files are staged**

Run:

```bash
git diff --cached --name-only
git status --short
```

Expected before staging: only intended documentation files are modified, plus any unrelated pre-existing untracked files outside this task. Do not stage unrelated files such as `install.sh`.

- [ ] **Step 4: Stage documentation files**

Run:

```bash
git add docs/superpowers/specs docs/superpowers/plans knowledge/service/core/README.md knowledge/README.md
```

Expected: only documentation files are staged.

- [ ] **Step 5: Verify staged files**

Run:

```bash
git diff --cached --name-only
```

Expected: staged files are Markdown documentation only.

- [ ] **Step 6: Commit documentation alignment**

Run:

```bash
git commit -m "docs: align knowledge docs with liteflow code path"
```

Expected: commit succeeds.

## Self-Review

- Spec coverage: Task 1 covers code facts; Task 2 covers full hit classification; Task 3 and Task 4 cover specs and plans; Task 5 covers README scope; Task 6 covers consistency scan; Task 7 covers regression and commit.
- Placeholder scan: no unresolved placeholder text or vague implementation-only step is left.
- Type consistency: all current architecture terms match the committed spec: `CoreController`, `KnowledgeFlowFacade`, `KnowledgeFlowExecutor`, `FlowRuntimeDependencies`, LiteFlow chain/node, `SourceSnapshotScanner`, `adapter/outbound/demo`, and `adapter/outbound/file`.
