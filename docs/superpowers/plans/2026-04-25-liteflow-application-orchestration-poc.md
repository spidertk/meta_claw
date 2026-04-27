# LiteFlow Application Orchestration POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the handwritten `registerSource` and `resumeSnapshotScan` application orchestration with LiteFlow chains while preserving current Sprint 2 behavior.

**Architecture:** Introduce a LiteFlow-based `application/flow` layer with strong-typed flow contexts, explicit nodes, and a single `KnowledgeFlowFacade`. Keep domain models, repositories, JSONL persistence, and `SourceSnapshotScanner` unchanged; only move orchestration responsibilities out of `RegisterSourceProcess` and `ResumeSnapshotScanProcess`.

**Tech Stack:** Java 21, LiteFlow, Lombok, Jackson JSON Lines persistence, SLF4J, Gradle.

---

### Task 1: Add LiteFlow Runtime And Chain Resources

**Files:**
- Modify: `knowledge/service/core/build.gradle.kts`
- Create: `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`

- [x] **Step 1: Add LiteFlow dependencies**

Update `knowledge/service/core/build.gradle.kts` dependencies to include LiteFlow runtime for non-Spring usage. Keep existing Lombok, Jackson, and SLF4J dependencies intact.

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    implementation("com.yomahub:liteflow-core:2.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
```

- [x] **Step 2: Add LiteFlow chain definition resource**

Create `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml` with two chains only: one for source registration, one for one-batch resume. The register chain should keep both persistence nodes in sequence and rely on each node's `isAccess()` guard for mutual exclusion.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow>
    <chain name="registerSourceChain">
        THEN(
            resolveKnowledgeSpaceNode,
            buildSourceRecordNode,
            loadLatestSnapshotNode,
            createSnapshotNode,
            decideUnchangedNode,
            persistChangedSourceAndSnapshotNode,
            persistUnchangedSourceNode,
            buildSourceRegistrationResultNode
        );
    </chain>

    <chain name="resumeSnapshotScanChain">
        THEN(
            loadSourceForResumeNode,
            loadLatestSnapshotForResumeNode,
            decideResumeNeededNode,
            createNextSnapshotNode,
            persistResumedSnapshotNode,
            buildResumeResultNode
        );
    </chain>
</flow>
```

- [x] **Step 3: Run dependency resolution build**

Run: `./gradlew classes`  
Expected: Gradle resolves LiteFlow and compilation still reaches Java compile phase without resource errors.

Actual: passed.

### Task 2: Add Flow Context Models

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/RegisterSourceFlowContext.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/ResumeSnapshotScanFlowContext.java`

- [x] **Step 1: Add register flow context**

Create `RegisterSourceFlowContext` using the project DTO style.

```java
package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSourceFlowContext {
    private SourceRegistrationRequest request;
    private AgentRoleBinding binding;
    private SourceRecord incomingSourceRecord;
    private SourceRecord resolvedSourceRecord;
    private SnapshotRecord latestSnapshot;
    private SnapshotRecord candidateSnapshot;
    private SourceRegistrationResult result;
    private Boolean unchanged;
    private SourceIntakeConfig sourceIntakeConfig;
}
```

- [x] **Step 2: Add resume flow context**

Create `ResumeSnapshotScanFlowContext` using the same DTO style.

```java
package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSnapshotScanFlowContext {
    private String sourceId;
    private SourceRecord sourceRecord;
    private SnapshotRecord latestSnapshot;
    private SnapshotRecord nextSnapshot;
    private SnapshotRecord resultSnapshot;
    private SourceIntakeConfig sourceIntakeConfig;
    private Boolean resumeNeeded;
}
```

- [x] **Step 3: Compile after adding contexts**

Run: `./gradlew classes`  
Expected: New DTO classes compile cleanly with Lombok.

Actual: passed. Runtime fix later extended both contexts with repository/config fields so non-Spring LiteFlow nodes can read dependencies from context.

### Task 3: Add Shared Flow Bootstrap And Facade

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`

- [x] **Step 1: Add LiteFlow bootstrap wrapper**

Create a focused executor that initializes LiteFlow once and executes named chains with strong-typed context objects.

```java
package com.meta_claw.knowledge.core.application.flow;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.property.LiteflowConfig;

public class KnowledgeFlowExecutor {
    private final FlowExecutor flowExecutor;

    public KnowledgeFlowExecutor() {
        LiteflowConfig config = new LiteflowConfig();
        config.setRuleSource("liteflow/register-resume-el.xml");
        config.setParseOnStart(true);
        this.flowExecutor = new FlowExecutor(config);
        this.flowExecutor.init(true);
    }

    public FlowExecutor getFlowExecutor() {
        return flowExecutor;
    }
}
```

- [x] **Step 2: Add facade over register and resume chains**

Create `KnowledgeFlowFacade` to hide LiteFlow APIs from `CoreController`.

```java
package com.meta_claw.knowledge.core.application.flow;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KnowledgeFlowFacade {
    private final FlowExecutor flowExecutor;

    public SourceRegistrationResult registerSource(RegisterSourceFlowContext context) {
        LiteflowResponse response = flowExecutor.execute2Resp("registerSourceChain", null, context);
        if (!response.isSuccess()) {
            throw new IllegalStateException("registerSourceChain failed", response.getCause());
        }
        return context.getResult();
    }

    public SnapshotRecord resumeSnapshotScan(ResumeSnapshotScanFlowContext context) {
        LiteflowResponse response = flowExecutor.execute2Resp("resumeSnapshotScanChain", null, context);
        if (!response.isSuccess()) {
            throw new IllegalStateException("resumeSnapshotScanChain failed", response.getCause());
        }
        return context.getResultSnapshot();
    }
}
```

- [x] **Step 3: Compile bootstrap and facade**

Run: `./gradlew classes`  
Expected: LiteFlow bootstrap APIs resolve and no missing imports remain.

Actual: passed, but runtime validation later showed that non-Spring mode also requires explicit node registration before `FlowExecutor.init()`. Final implementation uses `LiteFlowNodeBuilder.createCommonNode()` in `KnowledgeFlowExecutor`.

### Task 4: Implement Register Source Flow Nodes

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/ResolveKnowledgeSpaceNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/BuildSourceRecordNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/LoadLatestSnapshotNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/CreateSnapshotNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/DecideUnchangedNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/PersistChangedSourceAndSnapshotNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/PersistUnchangedSourceNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/BuildSourceRegistrationResultNode.java`

- [x] **Step 1: Implement resolve and source-building nodes**

Create nodes that read and update `RegisterSourceFlowContext`, not raw parameters.

```java
public class ResolveKnowledgeSpaceNode extends NodeComponent {
    private final KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        AgentRoleBinding binding = knowledgeSpaceBindingRepository.findByRoleName(context.getRequest().getRoleName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + context.getRequest().getRoleName()));
        context.setBinding(binding);
    }
}
```

```java
public class BuildSourceRecordNode extends NodeComponent {
    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SourceRecord incoming = context.getRequest().toDomain(context.getBinding().getSpaceId());
        String sourceId = SourceSnapshotScanner.ensureSourceId(incoming);
        SourceRecord resolved = incoming.toBuilder().sourceId(sourceId).build();
        context.setIncomingSourceRecord(incoming);
        context.setResolvedSourceRecord(resolved);
    }
}
```

- [x] **Step 2: Implement load/create/compare nodes**

Port the orchestration logic now held in `RegisterSourceProcess` into dedicated nodes.

```java
public class LoadLatestSnapshotNode extends NodeComponent {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        String sourceId = context.getResolvedSourceRecord().getSourceId();
        sourceRegistryRepository.findById(sourceId).ifPresent(existing -> {
            SourceRecord resolved = context.getResolvedSourceRecord().toBuilder()
                    .status("partial_update")
                    .createdAt(existing.getCreatedAt())
                    .latestSnapshotId(existing.getLatestSnapshotId())
                    .build();
            context.setResolvedSourceRecord(resolved);
            snapshotStoreRepository.findLatestBySourceId(sourceId).ifPresent(context::setLatestSnapshot);
        });
    }
}
```

```java
public class CreateSnapshotNode extends NodeComponent {
    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SnapshotRecord snapshot = SourceSnapshotScanner.createSnapshot(SourceIntakeRequest.builder()
                .sourceRecord(context.getResolvedSourceRecord())
                .scanCursor(null)
                .config(context.getSourceIntakeConfig())
                .build());
        context.setCandidateSnapshot(snapshot);
    }
}
```

```java
public class DecideUnchangedNode extends NodeComponent {
    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        boolean unchanged = context.getLatestSnapshot() != null
                && context.getLatestSnapshot().getContentFingerprint().equals(context.getCandidateSnapshot().getContentFingerprint());
        context.setUnchanged(unchanged);
    }

    @Override
    public boolean isAccess() {
        return true;
    }
}
```

- [x] **Step 3: Implement persist and result nodes**

Split changed and unchanged persistence clearly, then build the result object once at the end.

```java
public class PersistChangedSourceAndSnapshotNode extends NodeComponent {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    @Override
    public boolean isAccess() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        return !Boolean.TRUE.equals(context.getUnchanged());
    }

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SourceRecord resolved = context.getResolvedSourceRecord().toBuilder()
                .latestSnapshotId(context.getCandidateSnapshot().getSnapshotId())
                .build();
        sourceRegistryRepository.save(resolved);
        snapshotStoreRepository.save(context.getCandidateSnapshot());
        context.setResolvedSourceRecord(resolved);
    }
}
```

```java
public class PersistUnchangedSourceNode extends NodeComponent {
    private final SourceRegistryRepository sourceRegistryRepository;

    @Override
    public boolean isAccess() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        return Boolean.TRUE.equals(context.getUnchanged());
    }

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SnapshotRecord latestSnapshot = context.getLatestSnapshot();
        SourceRecord resolved = context.getResolvedSourceRecord().toBuilder()
                .status("unchanged")
                .latestSnapshotId(latestSnapshot.getSnapshotId())
                .build();
        sourceRegistryRepository.save(resolved);
        context.setResolvedSourceRecord(resolved);
    }
}
```

```java
public class BuildSourceRegistrationResultNode extends NodeComponent {
    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SnapshotRecord snapshot = Boolean.TRUE.equals(context.getUnchanged())
                ? context.getLatestSnapshot()
                : context.getCandidateSnapshot();
        context.setResult(SourceRegistrationResult.builder()
                .sourceRecord(context.getResolvedSourceRecord())
                .snapshotRecord(snapshot)
                .unchanged(Boolean.TRUE.equals(context.getUnchanged()))
                .build());
    }
}
```

- [x] **Step 4: Compile register flow nodes**

Run: `./gradlew classes`  
Expected: Register flow compiles and node names match the chain resource.

Actual: passed. Final implementation removed constructor injection from nodes and reads repository/config dependencies from `RegisterSourceFlowContext` for non-Spring LiteFlow runtime compatibility.

### Task 5: Implement Resume Snapshot Scan Flow Nodes

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/LoadSourceForResumeNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/LoadLatestSnapshotForResumeNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/DecideResumeNeededNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/CreateNextSnapshotNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/PersistResumedSnapshotNode.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/BuildResumeResultNode.java`

- [x] **Step 1: Implement load and decide nodes**

Port the current resume logic into nodes with explicit state handoff through `ResumeSnapshotScanFlowContext`.

```java
public class LoadSourceForResumeNode extends NodeComponent {
    private final SourceRegistryRepository sourceRegistryRepository;

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        SourceRecord sourceRecord = sourceRegistryRepository.findById(context.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + context.getSourceId()));
        context.setSourceRecord(sourceRecord);
    }
}
```

```java
public class LoadLatestSnapshotForResumeNode extends NodeComponent {
    private final SnapshotStoreRepository snapshotStoreRepository;

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        if (context.getSourceRecord().getLatestSnapshotId() == null || context.getSourceRecord().getLatestSnapshotId().isBlank()) {
            context.setResumeNeeded(true);
            return;
        }
        SnapshotRecord latestSnapshot = snapshotStoreRepository.findById(context.getSourceRecord().getLatestSnapshotId())
                .orElseThrow(() -> new IllegalStateException("Missing latest snapshot: " + context.getSourceRecord().getLatestSnapshotId()));
        context.setLatestSnapshot(latestSnapshot);
    }
}
```

```java
public class DecideResumeNeededNode extends NodeComponent {
    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        boolean resumeNeeded = context.getLatestSnapshot() == null
                || ("partial".equals(context.getLatestSnapshot().getScanStatus())
                && context.getLatestSnapshot().getNextScanCursor() != null
                && !context.getLatestSnapshot().getNextScanCursor().isBlank());
        context.setResumeNeeded(resumeNeeded);
    }
}
```

- [x] **Step 2: Implement create/persist/result nodes**

Keep the current “one call, one next batch” behavior.

```java
public class CreateNextSnapshotNode extends NodeComponent {
    @Override
    public boolean isAccess() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        return Boolean.TRUE.equals(context.getResumeNeeded());
    }

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        String cursor = context.getLatestSnapshot() == null ? null : context.getLatestSnapshot().getNextScanCursor();
        SnapshotRecord nextSnapshot = SourceSnapshotScanner.createSnapshot(SourceIntakeRequest.builder()
                .sourceRecord(context.getSourceRecord())
                .scanCursor(cursor)
                .config(context.getSourceIntakeConfig())
                .build());
        context.setNextSnapshot(nextSnapshot);
    }
}
```

```java
public class PersistResumedSnapshotNode extends NodeComponent {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    @Override
    public boolean isAccess() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        return Boolean.TRUE.equals(context.getResumeNeeded()) && context.getNextSnapshot() != null;
    }

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        snapshotStoreRepository.save(context.getNextSnapshot());
        context.getSourceRecord().setLatestSnapshotId(context.getNextSnapshot().getSnapshotId());
        sourceRegistryRepository.save(context.getSourceRecord());
    }
}
```

```java
public class BuildResumeResultNode extends NodeComponent {
    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        SnapshotRecord resultSnapshot = Boolean.TRUE.equals(context.getResumeNeeded()) && context.getNextSnapshot() != null
                ? context.getNextSnapshot()
                : context.getLatestSnapshot();
        context.setResultSnapshot(resultSnapshot);
    }
}
```

- [x] **Step 3: Compile resume flow nodes**

Run: `./gradlew classes`  
Expected: Resume flow compiles and chain node names match the resource file.

Actual: passed. Final implementation removed constructor injection from nodes and reads repository/config dependencies from `ResumeSnapshotScanFlowContext`.

### Task 6: Rewire Controller And Demo To Use LiteFlow Facade

**Files:**
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`

- [x] **Step 1: Replace controller dependencies**

Refactor `CoreController` so only register/resume go through `KnowledgeFlowFacade`, while submit/ingest stay on existing process classes.

```java
@RequiredArgsConstructor
public class CoreController {
    private final KnowledgeFlowFacade knowledgeFlowFacade;
    private final SubmitWorkerJobProcess submitWorkerJobProcess;
    private final IngestWorkerResultProcess ingestWorkerResultProcess;

    public SourceRegistrationResult registerSource(SourceRegistrationRequest request) {
        return knowledgeFlowFacade.registerSource(RegisterSourceFlowContext.builder()
                .request(request)
                .build());
    }

    public SnapshotRecord resumeSnapshotScan(String sourceId) {
        return knowledgeFlowFacade.resumeSnapshotScan(ResumeSnapshotScanFlowContext.builder()
                .sourceId(sourceId)
                .build());
    }
}
```

- [x] **Step 2: Rewire demo application bootstrap**

Instantiate LiteFlow executor and facade in `CoreApplication`, then inject facade into `CoreController`.

```java
KnowledgeFlowExecutor knowledgeFlowExecutor = new KnowledgeFlowExecutor();
KnowledgeFlowFacade knowledgeFlowFacade = new KnowledgeFlowFacade(knowledgeFlowExecutor.getFlowExecutor());

CoreController controller = new CoreController(
        knowledgeFlowFacade,
        new SubmitWorkerJobProcess(),
        new IngestWorkerResultProcess(knowledgeStateRepository)
);
```

- [x] **Step 3: Keep old processes out of the main path**

Remove direct `RegisterSourceProcess` and `ResumeSnapshotScanProcess` wiring from `CoreApplication` and `CoreController`, but do not delete their files yet.

- [x] **Step 4: Compile after wiring changes**

Run: `./gradlew classes`  
Expected: Controller and demo compile against the new flow facade.

Actual: passed. Final controller path resolves binding inside LiteFlow chain rather than pre-resolving it in controller.

### Task 7: Verify End-To-End Demo Behavior

**Files:**
- Inspect: `knowledge/demo-store/source-registry.jsonl`
- Inspect: `knowledge/demo-store/snapshot-store.jsonl`
- Modify: `docs/superpowers/specs/2026-04-25-liteflow-application-orchestration-poc-design.md`

- [x] **Step 1: Run the demo**

Run: `./gradlew run`  
Expected:

- first registration creates a snapshot
- resume creates one additional batch when `scanStatus=partial`
- second registration returns `unchanged=true`

- [x] **Step 2: Confirm persisted output semantics**

Inspect `knowledge/demo-store/source-registry.jsonl` and `knowledge/demo-store/snapshot-store.jsonl`.

Expected:

- `latestSnapshotId` points at the newest persisted snapshot
- `scanStatus` and `nextScanCursor` still progress as before
- no duplicate source identity is introduced

- [x] **Step 3: Mark spec validated**

Update `docs/superpowers/specs/2026-04-25-liteflow-application-orchestration-poc-design.md` to record that the POC has been validated in code and that controller entry now routes through LiteFlow for the two Sprint 2 chains.

- [x] **Step 4: Run final checks**

Run:

```bash
./gradlew classes
./gradlew run
git diff --check
```

Expected: all commands succeed, and the LiteFlow POC matches the current demo behavior without regressions.

Actual:

- `./gradlew classes` passed
- `./gradlew run` passed
- `git diff --check` passed
- demo 实际跑通 `registerSourceChain` 和 `resumeSnapshotScanChain`
- 当前 demo store 非空，首次注册日志表现为 `unchanged=true`，属现有持久化数据影响
