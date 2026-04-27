# LiteFlow Runtime Dependencies And Worker Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary per-context repository/config wiring with a shared LiteFlow runtime dependency object, and migrate `submitWorkerJob` plus `ingestWorkerResult` to LiteFlow without changing current worker stub behavior.

**Architecture:** Introduce `FlowRuntimeDependencies` as the only infrastructure dependency carrier for LiteFlow contexts. Refactor the existing register/resume contexts and nodes to read dependencies through that object, then add submit/ingest flow contexts, nodes, chains, and facade/controller wiring so all four main application chains run through `KnowledgeFlowFacade`.

**Tech Stack:** Java 21, LiteFlow 2.12.0, Lombok, Jackson JSONL persistence, SLF4J, Gradle.

---

### Task 1: Add Shared Runtime Dependency Carrier

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/FlowRuntimeDependencies.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/RegisterSourceFlowContext.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/ResumeSnapshotScanFlowContext.java`

- [x] **Step 1: Add `FlowRuntimeDependencies` DTO**

```java
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowRuntimeDependencies {
    private KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;
    private SourceRegistryRepository sourceRegistryRepository;
    private SnapshotStoreRepository snapshotStoreRepository;
    private KnowledgeStateRepository knowledgeStateRepository;
    private SourceIntakeConfig sourceIntakeConfig;
}
```

- [x] **Step 2: Replace flat dependency fields on register/resume contexts**

Keep only business state on each context and add:

```java
private FlowRuntimeDependencies runtimeDependencies;
```

- [x] **Step 3: Compile after context refactor**

Run: `./gradlew classes`

Expected: context changes compile, even though nodes still need follow-up adjustments.

Actual: passed. `registerSourceChain` and `resumeSnapshotScanChain` remained runnable after the refactor.

### Task 2: Refactor Existing Register And Resume Nodes To Use `runtimeDependencies`

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/ResolveKnowledgeSpaceNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/LoadLatestSnapshotNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/CreateSnapshotNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/PersistChangedSourceAndSnapshotNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/PersistUnchangedSourceNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/LoadSourceForResumeNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/LoadLatestSnapshotForResumeNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/CreateNextSnapshotNode.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/PersistResumedSnapshotNode.java`

- [x] **Step 1: Read repositories/config through `context.getRuntimeDependencies()`**

Example target style:

```java
FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
SourceRegistryRepository sourceRegistryRepository = runtimeDependencies.getSourceRegistryRepository();
```

- [x] **Step 2: Keep business semantics unchanged**

Do not alter:

- `registerSource` fingerprint comparison and unchanged branch
- `resumeSnapshotScan` one-batch resume behavior
- `latestSnapshotId` update semantics

- [x] **Step 3: Compile and run current POC**

Run:

```bash
./gradlew classes
./gradlew run
```

Expected: current register/resume LiteFlow demo still passes after dependency refactor.

Actual: passed.

### Task 3: Extend Flow Bootstrap And Facade

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`

- [x] **Step 1: Add worker chain node registrations**

Register these future node ids in `KnowledgeFlowExecutor`:

```java
registerNode("buildWorkerJobNode", BuildWorkerJobNode.class);
registerNode("submitWorkerJobNode", SubmitWorkerJobNode.class);
registerNode("buildSubmitWorkerJobResultNode", BuildSubmitWorkerJobResultNode.class);
registerNode("readWorkerResultEnvelopeNode", ReadWorkerResultEnvelopeNode.class);
registerNode("ingestKnowledgeStateNode", IngestKnowledgeStateNode.class);
registerNode("buildIngestWorkerResultNode", BuildIngestWorkerResultNode.class);
```

- [x] **Step 2: Build one shared `FlowRuntimeDependencies` in facade**

Create one helper inside `KnowledgeFlowFacade`:

```java
private FlowRuntimeDependencies buildRuntimeDependencies() {
    return FlowRuntimeDependencies.builder()
            .knowledgeSpaceBindingRepository(knowledgeSpaceBindingRepository)
            .sourceRegistryRepository(sourceRegistryRepository)
            .snapshotStoreRepository(snapshotStoreRepository)
            .knowledgeStateRepository(knowledgeStateRepository)
            .sourceIntakeConfig(sourceIntakeConfig)
            .build();
}
```

- [x] **Step 3: Add worker flow facade methods**

Add:

```java
public WorkerJob submitWorkerJob(SubmitWorkerJobFlowContext context)
public WorkerResult ingestWorkerResult(IngestWorkerResultFlowContext context)
```

Both methods should inject `runtimeDependencies` before executing their chains.

Actual: implemented. Final code uses one shared `FlowRuntimeDependencies`; no extra worker-only runtime carrier remains.

### Task 4: Add Worker Flow Contexts

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/SubmitWorkerJobFlowContext.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/IngestWorkerResultFlowContext.java`

- [x] **Step 1: Add submit context**

```java
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubmitWorkerJobFlowContext {
    private SubmitWorkerJobRequest request;
    private AgentRoleBinding binding;
    private WorkerJob workerJob;
    private WorkerJob result;
    private FlowRuntimeDependencies runtimeDependencies;
}
```

- [x] **Step 2: Add ingest context**

```java
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IngestWorkerResultFlowContext {
    private WorkerResultEnvelope envelope;
    private WorkerResult workerResult;
    private WorkerResult result;
    private FlowRuntimeDependencies runtimeDependencies;
}
```

Actual: implemented with the shared `runtimeDependencies` field.

### Task 5: Add Submit Worker Job Chain And Nodes

**Files:**
- Modify: `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/BuildWorkerJobNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/SubmitWorkerJobNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/BuildSubmitWorkerJobResultNode.java`

- [x] **Step 1: Add `submitWorkerJobChain`**

```xml
<chain name="submitWorkerJobChain">
    THEN(
        resolveWorkerKnowledgeSpaceNode,
        buildWorkerJobNode,
        submitWorkerJobNode,
        buildSubmitWorkerJobResultNode
    );
</chain>
```

- [x] **Step 2: Build worker job from request plus resolved binding**

```java
public void process() {
    SubmitWorkerJobFlowContext context = this.getContextBean(SubmitWorkerJobFlowContext.class);
    context.setWorkerJob(context.getRequest().toDomain(context.getBinding().getSpaceId()));
}
```

- [x] **Step 3: Keep submit semantics as current stub**

`SubmitWorkerJobNode` should only log and return the current `WorkerJob`, matching `SubmitWorkerJobProcess`.

Actual: implemented. A dedicated `ResolveWorkerKnowledgeSpaceNode` is used because the worker submit context differs from the register context.

### Task 6: Add Ingest Worker Result Chain And Nodes

**Files:**
- Modify: `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/ReadWorkerResultEnvelopeNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/IngestKnowledgeStateNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/BuildIngestWorkerResultNode.java`

- [x] **Step 1: Add `ingestWorkerResultChain`**

```xml
<chain name="ingestWorkerResultChain">
    THEN(
        readWorkerResultEnvelopeNode,
        ingestKnowledgeStateNode,
        buildIngestWorkerResultNode
    );
</chain>
```

- [x] **Step 2: Convert envelope to domain worker result**

```java
public void process() {
    IngestWorkerResultFlowContext context = this.getContextBean(IngestWorkerResultFlowContext.class);
    context.setWorkerResult(context.getEnvelope().toDomain());
}
```

- [x] **Step 3: Mirror current ingest semantics**

`IngestKnowledgeStateNode` should iterate artifacts and write:

- `knowledgeStateRepository.saveAsset(asset)`
- `knowledgeStateRepository.saveControlState(...)`

matching current `IngestWorkerResultProcess`.

Actual: implemented.

### Task 7: Rewire Controller And Demo

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`

- [x] **Step 1: Remove old process dependencies from controller**

`CoreController` should keep only:

- `KnowledgeFlowFacade`

and route all four application operations through it.

- [x] **Step 2: Rewire demo bootstrap**

`CoreApplication` should stop injecting:

- `ResolveKnowledgeSpaceBindingProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`

and instead build one `KnowledgeFlowFacade` with all required repositories/config.

- [x] **Step 3: Add minimal demo calls for worker chains**

After source registration/resume demo, add:

```java
var workerJob = controller.submitWorkerJob(buildSampleWorkerJobRequest());
log.info("Submit worker job -> jobId={}, spaceId={}, jobType={}", ...);

var workerResult = controller.ingestWorkerResult(buildSampleWorkerResultEnvelope(workerJob));
log.info("Ingest worker result -> jobId={}, artifacts={}, issues={}", ...);
```

Actual: implemented. Demo worker ingest now reuses the `spaceId`, `jobId`, and `snapshotId` produced by the submit/register flow outputs.

### Task 8: Verify End-To-End Behavior And Sync Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-runtime-dependencies-and-worker-flow-design.md`
- Modify: `docs/superpowers/specs/2026-04-20-private-knowledge-base-implementation-plan.md`

- [x] **Step 1: Run full verification**

Run:

```bash
./gradlew classes
./gradlew run
git diff --check
```

Expected:

- all commands pass
- register/resume still work
- submit/ingest worker chains run through LiteFlow

Actual:

- `./gradlew classes` passed
- `./gradlew run` passed
- `git diff --check` passed
- demo 实际跑通四条链：`registerSourceChain`、`resumeSnapshotScanChain`、`submitWorkerJobChain`、`ingestWorkerResultChain`

- [x] **Step 2: Update docs to reflect validated state**

Record that:

- `FlowRuntimeDependencies` replaced flat context dependency wiring
- `submitWorkerJob` and `ingestWorkerResult` now run through LiteFlow
- controller main path is fully facade-based for the four chains
