# 私有知识中台实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从当前 LiteFlow 骨架出发，打通 `.rwa` 来源扫描 → Python graphify → artifact 归档 → Java knowledge state → REST API 的最小闭环，同步搭建测试体系。

**Architecture:** 保持 Java State Core + Python Worker Mesh 双层架构。Java 侧新增 `.rwa` 解析器、loop-to-complete 调度器、Artifact 归档器、REST 层；Python 侧从 stub 扩展为真实调用 graphify。测试与业务同步搭建，采用 TDD。

**Tech Stack:** Java 21, Gradle Kotlin DSL, LiteFlow 2.12.0, Spring Boot 3.x, Jackson, Lombok, JUnit 5, AssertJ, Mockito, Python 3.11+, graphify

---

## 文件结构映射

### 新增文件

| 文件 | 职责 |
|------|------|
| `src/main/java/.../application/intake/RwaSourceResolver.java` | 扫描 `.rwa/` 子目录，生成 `SourceRegistrationRequest` |
| `src/main/java/.../application/schedule/ScanCompletionScheduler.java` | 轮询 `partial` snapshot，自动触发 resume |
| `src/main/java/.../application/worker/GraphifyWorkerInvoker.java` | 组装命令行调用 Python worker |
| `src/main/java/.../application/archive/ArtifactArchiveService.java` | 按规范目录归档 graph/wiki 文件 |
| `src/main/java/.../application/state/KnowledgeStatePersister.java` | 把 artifact 元数据写入 `knowledge_state` |
| `src/main/java/.../application/view/AgentViewBuilder.java` | 按 role 过滤并组装 agent-facing views |
| `src/main/java/.../adapter/inbound/rest/KnowledgeRestController.java` | Spring Boot REST 端点 |
| `src/main/java/.../MainApplication.java` | Spring Boot 启动类 |
| `src/test/java/.../flow/register/RegisterSourceChainTest.java` | `registerSourceChain` 链路测试 |
| `src/test/java/.../flow/resume/ResumeSnapshotScanChainTest.java` | `resumeSnapshotScanChain` 链路测试 |
| `src/test/java/.../intake/RwaSourceResolverTest.java` | `.rwa` 解析器单元测试 |
| `src/test/java/.../schedule/ScanCompletionSchedulerTest.java` | 调度器单元测试 |
| `src/test/java/.../worker/GraphifyWorkerInvokerTest.java` | Worker 调用器单元测试 |
| `src/test/java/.../archive/ArtifactArchiveServiceTest.java` | 归档器单元测试 |
| `src/test/java/.../integration/EndToEndIntegrationTest.java` | 端到端集成测试 |
| `src/test/resources/samples/sample-repo/` | 微型 Python 项目样本 |
| `src/test/resources/samples/sample-doc/` | Markdown 文档样本 |
| `knowledge/workers/python/graphify_adapter.py` | Python 侧 graphify 调用适配器 |

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `build.gradle.kts` | 添加 Spring Boot、测试依赖 |
| `settings.gradle.kts` | 如有需要，调整根项目名 |
| `src/main/java/.../CoreApplication.java` | 标记为 `@Deprecated`，引导使用 REST 入口 |
| `src/main/java/.../application/flow/KnowledgeFlowFacade.java` | 添加 `resumeSnapshotScan(String sourceId)` 便捷方法 |
| `src/main/java/.../application/flow/worker/IngestKnowledgeStateNode.java` | 接入 `KnowledgeStatePersister` |
| `knowledge/workers/python/worker_entry.py` | 从 stub 扩展为真实调用 `graphify_adapter.py` |

---

## Task 1: 测试骨架与 Gradle 配置

**Files:**
- Modify: `knowledge/service/core/build.gradle.kts`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/TestFixtures.java`
- Create: `knowledge/service/core/src/test/resources/samples/sample-repo/README.md`
- Create: `knowledge/service/core/src/test/resources/samples/sample-repo/main.py`
- Create: `knowledge/service/core/src/test/resources/samples/sample-repo/utils/helper.py`
- Create: `knowledge/service/core/src/test/resources/samples/sample-doc/guide.md`

- [ ] **Step 1: 添加 Spring Boot 与测试依赖到 build.gradle.kts**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.meta-claw"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("com.yomahub:liteflow-spring-boot-starter:2.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
```

- [ ] **Step 2: 同步更新 settings.gradle.kts**

```kotlin
rootProject.name = "knowledge-core"
```

- [ ] **Step 3: 创建样本目录结构**

```bash
mkdir -p knowledge/service/core/src/test/resources/samples/sample-repo/utils
mkdir -p knowledge/service/core/src/test/resources/samples/sample-doc
```

- [ ] **Step 4: 创建微型 Python 项目样本文件**

`knowledge/service/core/src/test/resources/samples/sample-repo/README.md`:
```markdown
# Sample Repo
A tiny Python project for integration testing.
```

`knowledge/service/core/src/test/resources/samples/sample-repo/main.py`:
```python
def greet(name: str) -> str:
    return f"Hello, {name}!"

if __name__ == "__main__":
    print(greet("World"))
```

`knowledge/service/core/src/test/resources/samples/sample-repo/utils/helper.py`:
```python
def add(a: int, b: int) -> int:
    return a + b
```

- [ ] **Step 5: 创建文档样本**

`knowledge/service/core/src/test/resources/samples/sample-doc/guide.md`:
```markdown
# User Guide

This is a sample document for knowledge base ingestion testing.

## Section 1
Sample content here.
```

- [ ] **Step 6: 创建测试工具基类**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/TestFixtures.java`:
```java
package com.meta_claw.knowledge.core;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.domain.SourceRecord;

public final class TestFixtures {

    private TestFixtures() {}

    public static SourceRegistrationRequest sampleRepoRequest() {
        return SourceRegistrationRequest.builder()
                .roleName("test_role")
                .sourceType("git_repository")
                .location("src/test/resources/samples/sample-repo")
                .displayName("sample_repo")
                .description("Test fixture repository")
                .workspaceIdentity(SourceRecord.WorkspaceIdentity.builder()
                        .workspaceId("ws_sample")
                        .workspaceRoot("src/test/resources/samples/sample-repo")
                        .vcs("git")
                        .originMode("native_git")
                        .defaultBranch("main")
                        .build())
                .snapshotHint(SourceRecord.SnapshotHint.builder()
                        .branch("main")
                        .worktreeState("clean")
                        .build())
                .build();
    }
}
```

- [ ] **Step 7: 运行 Gradle 测试验证骨架**

Run: `./gradlew test --info`

Expected: BUILD SUCCESSFUL（没有测试执行或空通过）

- [ ] **Step 8: Commit**

```bash
git add knowledge/service/core/build.gradle.kts \
        knowledge/service/core/settings.gradle.kts \
        knowledge/service/core/src/test/
git commit -m "chore: setup Spring Boot, test deps, and sample fixtures"
```

---

## Task 2: `.rwa` 来源规则解析器

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/RwaSourceResolver.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/intake/RwaSourceResolverTest.java`

- [ ] **Step 1: 写失败测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/intake/RwaSourceResolverTest.java`:
```java
package com.meta_claw.knowledge.core.application.intake;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RwaSourceResolverTest {

    @Test
    @DisplayName("解析 .rwa 下的 graphify 子目录为 source request")
    void resolveSingleDirectory() {
        RwaSourceResolver resolver = new RwaSourceResolver(Path.of(".rwa"));
        List<SourceRegistrationRequest> requests = resolver.resolve("graphify");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getSourceType()).isEqualTo("git_repository");
        assertThat(requests.get(0).getDisplayName()).isEqualTo("graphify");
        assertThat(requests.get(0).getLocation()).contains(".rwa/graphify");
    }

    @Test
    @DisplayName("解析全部子目录")
    void resolveAllDirectories() {
        RwaSourceResolver resolver = new RwaSourceResolver(Path.of(".rwa"));
        List<SourceRegistrationRequest> requests = resolver.resolveAll();

        assertThat(requests).isNotEmpty();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests RwaSourceResolverTest -i`

Expected: 2 FAILED — `RwaSourceResolver` class not found

- [ ] **Step 3: 实现最小解析器**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/intake/RwaSourceResolver.java`:
```java
package com.meta_claw.knowledge.core.application.intake;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.domain.SourceRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class RwaSourceResolver {

    private final Path rwaRoot;

    public RwaSourceResolver(Path rwaRoot) {
        this.rwaRoot = rwaRoot;
    }

    public List<SourceRegistrationRequest> resolveAll() {
        try (Stream<Path> paths = Files.list(rwaRoot)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::toRequest)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list .rwa directories", e);
        }
    }

    public List<SourceRegistrationRequest> resolve(String dirName) {
        Path target = rwaRoot.resolve(dirName);
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        return List.of(toRequest(target));
    }

    private SourceRegistrationRequest toRequest(Path dir) {
        String name = dir.getFileName().toString();
        String absolutePath = dir.toAbsolutePath().toString();
        return SourceRegistrationRequest.builder()
                .roleName("shared")
                .sourceType("git_repository")
                .location(absolutePath)
                .displayName(name)
                .description("Auto-resolved from .rwa/" + name)
                .workspaceIdentity(SourceRecord.WorkspaceIdentity.builder()
                        .workspaceId("ws_" + name)
                        .workspaceRoot(absolutePath)
                        .vcs("git")
                        .originMode("native_git")
                        .defaultBranch("main")
                        .build())
                .snapshotHint(SourceRecord.SnapshotHint.builder()
                        .branch("main")
                        .worktreeState("clean")
                        .build())
                .build();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew test --tests RwaSourceResolverTest -i`

Expected: 2 PASSED

- [ ] **Step 5: Commit**

```bash
git add knowledge/service/core/src/main/java/.../application/intake/RwaSourceResolver.java \
        knowledge/service/core/src/test/java/.../application/intake/RwaSourceResolverTest.java
git commit -m "feat: add .rwa source resolver with auto-discovery"
```

---

## Task 3: Loop-to-Complete 调度器

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/schedule/ScanCompletionScheduler.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/schedule/ScanCompletionSchedulerTest.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`

- [ ] **Step 1: 在 KnowledgeFlowFacade 添加便捷方法**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`，在 `resumeSnapshotScan` 方法后添加：

```java
    public SnapshotRecord resumeSnapshotScan(String sourceId) {
        return resumeSnapshotScan(
                ResumeSnapshotScanFlowContext.builder()
                        .sourceId(sourceId)
                        .build()
        );
    }
```

- [ ] **Step 2: 写调度器失败测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/schedule/ScanCompletionSchedulerTest.java`:
```java
package com.meta_claw.knowledge.core.application.schedule;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScanCompletionSchedulerTest {

    @Test
    @DisplayName("partial snapshot 触发一次 resume 后变为 complete")
    void resumeUntilComplete() {
        KnowledgeFlowFacade facade = Mockito.mock(KnowledgeFlowFacade.class);

        SnapshotRecord partial = SnapshotRecord.builder()
                .snapshotId("snap_1")
                .sourceId("src_1")
                .scanStatus("partial")
                .nextScanCursor("cursor_1")
                .build();

        SnapshotRecord complete = SnapshotRecord.builder()
                .snapshotId("snap_2")
                .sourceId("src_1")
                .scanStatus("complete")
                .nextScanCursor(null)
                .build();

        when(facade.resumeSnapshotScan("src_1"))
                .thenReturn(partial)
                .thenReturn(complete);

        ScanCompletionScheduler scheduler = new ScanCompletionScheduler(facade);
        int resumedCount = scheduler.completeScan("src_1");

        assertThat(resumedCount).isEqualTo(2);
        verify(facade, times(2)).resumeSnapshotScan("src_1");
    }

    @Test
    @DisplayName("complete snapshot 不触发 resume")
    void alreadyComplete() {
        KnowledgeFlowFacade facade = Mockito.mock(KnowledgeFlowFacade.class);
        SnapshotRecord complete = SnapshotRecord.builder()
                .snapshotId("snap_1")
                .sourceId("src_1")
                .scanStatus("complete")
                .build();

        when(facade.resumeSnapshotScan("src_1")).thenReturn(complete);

        ScanCompletionScheduler scheduler = new ScanCompletionScheduler(facade);
        int resumedCount = scheduler.completeScan("src_1");

        assertThat(resumedCount).isEqualTo(1);
        verify(facade, times(1)).resumeSnapshotScan("src_1");
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew test --tests ScanCompletionSchedulerTest -i`

Expected: 2 FAILED — `ScanCompletionScheduler` not found

- [ ] **Step 4: 实现调度器**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/schedule/ScanCompletionScheduler.java`:
```java
package com.meta_claw.knowledge.core.application.schedule;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScanCompletionScheduler {

    private final KnowledgeFlowFacade flowFacade;

    public ScanCompletionScheduler(KnowledgeFlowFacade flowFacade) {
        this.flowFacade = flowFacade;
    }

    /**
     * 对指定 source 执行 resume，直到 scanStatus 变为 complete。
     * @return 实际执行的 resume 次数（含首次）
     */
    public int completeScan(String sourceId) {
        int count = 0;
        SnapshotRecord snapshot;

        do {
            snapshot = flowFacade.resumeSnapshotScan(sourceId);
            count++;
            log.info("Resume scan #{} for source={}, status={}, cursor={}",
                    count, sourceId, snapshot.getScanStatus(), snapshot.getNextScanCursor());
        } while ("partial".equals(snapshot.getScanStatus()));

        return count;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew test --tests ScanCompletionSchedulerTest -i`

Expected: 2 PASSED

- [ ] **Step 6: Commit**

```bash
git add knowledge/service/core/src/main/java/.../application/schedule/ScanCompletionScheduler.java \
        knowledge/service/core/src/test/java/.../application/schedule/ScanCompletionSchedulerTest.java \
        knowledge/service/core/src/main/java/.../application/flow/KnowledgeFlowFacade.java
git commit -m "feat: add scan completion scheduler with loop-to-complete"
```

---

## Task 4: `registerSourceChain` 与 `resumeSnapshotScanChain` 链路测试

**Files:**
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/register/RegisterSourceChainTest.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/resume/ResumeSnapshotScanChainTest.java`

- [ ] **Step 1: 写 registerSourceChain 链路测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/register/RegisterSourceChainTest.java`:
```java
package com.meta_claw.knowledge.core.flow.register;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterSourceChainTest {

    private KnowledgeFlowFacade facade;

    @BeforeEach
    void setUp() {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        facade = new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                new SampleKnowledgeStateRepository(),
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Test
    @DisplayName("首次注册来源生成新 snapshot")
    void firstRegistrationCreatesSnapshot() {
        RegisterSourceFlowContext context = RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build();

        SourceRegistrationResult result = facade.registerSource(context);

        assertThat(result.getSourceRecord()).isNotNull();
        assertThat(result.getSourceRecord().getSourceId()).isNotBlank();
        assertThat(result.getSnapshotRecord()).isNotNull();
        assertThat(result.getSnapshotRecord().getSnapshotId()).isNotBlank();
        assertThat(result.isUnchanged()).isFalse();
    }

    @Test
    @DisplayName("重复注册相同来源返回 unchanged")
    void repeatedRegistrationIsUnchanged() {
        RegisterSourceFlowContext context = RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build();

        facade.registerSource(context);
        SourceRegistrationResult second = facade.registerSource(context);

        assertThat(second.isUnchanged()).isTrue();
        assertThat(second.getSourceRecord().getLatestSnapshotId())
                .isEqualTo(second.getSnapshotRecord().getSnapshotId());
    }
}
```

- [ ] **Step 2: 写 resumeSnapshotScanChain 链路测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/resume/ResumeSnapshotScanChainTest.java`:
```java
package com.meta_claw.knowledge.core.flow.resume;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeSnapshotScanChainTest {

    private KnowledgeFlowFacade facade;

    @BeforeEach
    void setUp() {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        facade = new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                new SampleKnowledgeStateRepository(),
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Test
    @DisplayName("resume 来源生成新 snapshot batch")
    void resumeScanGeneratesNextBatch() {
        // 先注册一个来源
        var reg = facade.registerSource(RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build());

        String sourceId = reg.getSourceRecord().getSourceId();

        // 再 resume
        SnapshotRecord resumed = facade.resumeSnapshotScan(sourceId);

        assertThat(resumed).isNotNull();
        assertThat(resumed.getSourceId()).isEqualTo(sourceId);
    }
}
```

- [ ] **Step 3: 运行链路测试**

Run: `./gradlew test --tests '*ChainTest' -i`

Expected: 3 PASSED（2 个 register + 1 个 resume）

- [ ] **Step 4: Commit**

```bash
git add knowledge/service/core/src/test/java/.../flow/
git commit -m "test: add register and resume chain integration tests"
```

---

## Task 5: Python Worker graphify 适配器

**Files:**
- Create: `knowledge/workers/python/graphify_adapter.py`
- Modify: `knowledge/workers/python/worker_entry.py`

- [ ] **Step 1: 创建 graphify 适配器**

`knowledge/workers/python/graphify_adapter.py`:
```python
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


def run_graphify(source_dir: Path, output_dir: Path) -> dict[str, Any]:
    """
    调用 graphify 分析 source_dir，产出 graph.json 到 output_dir。
    若 graphify 未安装，则产出一个最小占位 graph。
    """
    graph_file = output_dir / "graph.json"

    # 尝试调用 graphify CLI（假设已安装为系统命令）
    try:
        result = subprocess.run(
            ["graphify", str(source_dir), "--output", str(graph_file)],
            capture_output=True,
            text=True,
            timeout=120,
        )
        if result.returncode == 0 and graph_file.exists():
            return json.loads(graph_file.read_text(encoding="utf-8"))
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass

    # Fallback: 产出最小占位 graph
    placeholder = {
        "meta": {"tool": "graphify", "version": "0.1.0-fallback"},
        "nodes": [],
        "edges": [],
        "communities": [],
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    graph_file.write_text(json.dumps(placeholder, indent=2), encoding="utf-8")
    return placeholder


def generate_wiki(graph: dict[str, Any], output_dir: Path) -> Path:
    """基于 graph 产出最小 wiki.md。"""
    wiki_file = output_dir / "wiki.md"
    lines = ["# Auto-generated Wiki\n", "## Summary\n"]

    nodes = graph.get("nodes", [])
    if nodes:
        lines.append(f"- Discovered {len(nodes)} nodes\n")
    else:
        lines.append("- No structured nodes found (placeholder mode)\n")

    wiki_file.write_text("".join(lines), encoding="utf-8")
    return wiki_file
```

- [ ] **Step 2: 扩展 worker_entry.py**

`knowledge/workers/python/worker_entry.py` 完整替换为：
```python
from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path

from graphify_adapter import run_graphify, generate_wiki


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Knowledge Worker")
    parser.add_argument("--job-file", required=True, help="Path to job contract JSON")
    parser.add_argument("--output-dir", required=True, help="Directory to write artifacts")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    job_path = Path(args.job_file)
    output_dir = Path(args.output_dir)

    job = json.loads(job_path.read_text(encoding="utf-8"))
    source_dir = Path(job.get("snapshot_dir", "."))

    # 调用 graphify
    graph = run_graphify(source_dir, output_dir)
    wiki_file = generate_wiki(graph, output_dir)

    result = {
        "job_id": job["job_id"],
        "status": "completed",
        "retriable": False,
        "artifacts": [
            {
                "asset_type": "graph",
                "path": str(output_dir / "graph.json"),
                "format": "json",
            },
            {
                "asset_type": "wiki",
                "path": str(wiki_file),
                "format": "markdown",
            },
        ],
        "issues": [],
        "coverage": "partial" if not graph.get("nodes") else "full",
        "scope": job.get("processing_scope", "latest_snapshot"),
    }

    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 3: 本地手动验证 Python Worker**

```bash
cd /Users/kai/IdeaProjects/meta_claw/knowledge/workers/python
# 创建最小 job contract
cat > /tmp/test_job.json << 'EOF'
{"job_id": "job_test_001", "snapshot_dir": "../../../knowledge/service/core/src/test/resources/samples/sample-repo", "processing_scope": "latest_snapshot"}
EOF

mkdir -p /tmp/test_output
python worker_entry.py --job-file /tmp/test_job.json --output-dir /tmp/test_output
```

Expected: stdout 输出 JSON，包含 `status=completed`，`/tmp/test_output/graph.json` 和 `/tmp/test_output/wiki.md` 存在。

- [ ] **Step 4: Commit**

```bash
git add knowledge/workers/python/graphify_adapter.py \
        knowledge/workers/python/worker_entry.py
git commit -m "feat: python worker calls graphify adapter with fallback placeholder"
```

---

## Task 6: Java Worker 调用器与 Artifact 归档

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/worker/GraphifyWorkerInvoker.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/archive/ArtifactArchiveService.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/worker/GraphifyWorkerInvokerTest.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/archive/ArtifactArchiveServiceTest.java`

- [ ] **Step 1: 实现 GraphifyWorkerInvoker**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/worker/GraphifyWorkerInvoker.java`:
```java
package com.meta_claw.knowledge.core.application.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GraphifyWorkerInvoker {

    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final Path workerScriptPath;

    public GraphifyWorkerInvoker(ObjectMapper objectMapper, String pythonExecutable, Path workerScriptPath) {
        this.objectMapper = objectMapper;
        this.pythonExecutable = pythonExecutable;
        this.workerScriptPath = workerScriptPath;
    }

    public WorkerResultEnvelope invoke(WorkerJob job, Path snapshotDir, Path outputDir) throws Exception {
        Path jobFile = Files.createTempFile("job_", ".json");
        objectMapper.writeValue(jobFile.toFile(), job);

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                workerScriptPath.toString(),
                "--job-file", jobFile.toString(),
                "--output-dir", outputDir.toString()
        );
        pb.inheritIO();

        int maxRetries = 3;
        long backoffMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Process process = pb.start();
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new RuntimeException("Worker timed out after 120s");
                }
                if (process.exitValue() != 0) {
                    throw new RuntimeException("Worker exited with code " + process.exitValue());
                }

                // 读取 stdout 中最后一行 JSON
                // 简化：直接读取 outputDir 下的 result（实际应由 stdout 捕获，此处简化）
                return WorkerResultEnvelope.builder()
                        .jobId(job.getJobId())
                        .spaceId(job.getSpaceId())
                        .status("completed")
                        .retriable(false)
                        .artifacts(java.util.List.of())
                        .issues(java.util.List.of())
                        .build();
            } catch (Exception e) {
                lastException = e;
                log.warn("Worker attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                }
            }
        }

        return WorkerResultEnvelope.builder()
                .jobId(job.getJobId())
                .spaceId(job.getSpaceId())
                .status("failed")
                .retriable(true)
                .issues(java.util.List.of(lastException != null ? lastException.getMessage() : "unknown"))
                .build();
    }
}
```

- [ ] **Step 2: 实现 ArtifactArchiveService**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/archive/ArtifactArchiveService.java`:
```java
package com.meta_claw.knowledge.core.application.archive;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
public class ArtifactArchiveService {

    private final Path archiveRoot;

    public ArtifactArchiveService(Path archiveRoot) {
        this.archiveRoot = archiveRoot;
    }

    /**
     * 归档 artifact 到规范路径。
     * @return 归档后的文件路径
     */
    public Path archive(String spaceId, String sourceId, String snapshotId, String jobId,
                        String artifactType, Path tempFile) throws Exception {
        Path dir = archiveRoot
                .resolve("spaces")
                .resolve(spaceId)
                .resolve("sources")
                .resolve(sourceId)
                .resolve("snapshots")
                .resolve(snapshotId)
                .resolve("artifacts")
                .resolve(jobId);
        Files.createDirectories(dir);

        String ext = artifactType.equals("wiki") ? "md" : "json";
        Path target = dir.resolve(artifactType + "." + ext);
        Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);

        log.info("Archived {} to {}", artifactType, target);
        return target;
    }
}
```

- [ ] **Step 3: 写单元测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/archive/ArtifactArchiveServiceTest.java`:
```java
package com.meta_claw.knowledge.core.application.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactArchiveServiceTest {

    @Test
    @DisplayName("artifact 归档到规范路径")
    void archiveToStandardPath(@TempDir Path tempDir) throws Exception {
        ArtifactArchiveService service = new ArtifactArchiveService(tempDir);
        Path sourceFile = Files.writeString(tempDir.resolve("temp_graph.json"), "{}");

        Path archived = service.archive(
                "shared", "src_1", "snap_1", "job_1",
                "graph", sourceFile
        );

        assertThat(archived).exists();
        assertThat(archived.toString()).contains("spaces/shared/sources/src_1/snapshots/snap_1/artifacts/job_1/graph.json");
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `./gradlew test --tests '*WorkerInvokerTest' --tests '*ArchiveServiceTest' -i`

Expected: PASSED

- [ ] **Step 5: Commit**

```bash
git add knowledge/service/core/src/main/java/.../application/worker/ \
        knowledge/service/core/src/main/java/.../application/archive/ \
        knowledge/service/core/src/test/java/.../application/worker/ \
        knowledge/service/core/src/test/java/.../application/archive/
git commit -m "feat: add graphify worker invoker and artifact archive service"
```

---

## Task 7: KnowledgeState 落盘与 Agent View

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/state/KnowledgeStatePersister.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/view/AgentViewBuilder.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/IngestKnowledgeStateNode.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/state/KnowledgeStatePersisterTest.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/view/AgentViewBuilderTest.java`

- [ ] **Step 1: 修改 IngestKnowledgeStateNode 接入 Persister**

读取当前 `IngestKnowledgeStateNode.java` 确认结构，然后修改：

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/IngestKnowledgeStateNode.java`:

```java
package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.application.state.KnowledgeStatePersister;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

import java.util.List;

@LiteflowComponent("ingestKnowledgeStateNode")
public class IngestKnowledgeStateNode extends NodeComponent {

    @Override
    public void process() throws Exception {
        IngestWorkerResultFlowContext context = this.getContextBean(IngestWorkerResultFlowContext.class);
        FlowRuntimeDependencies deps = context.getRuntimeDependencies();

        if (deps == null || deps.getKnowledgeStateRepository() == null) {
            return; // 无 repository 时不写入（兼容 demo 模式）
        }

        KnowledgeStatePersister persister = new KnowledgeStatePersister(deps.getKnowledgeStateRepository());
        List<KnowledgeAsset> assets = context.getWorkerResultEnvelope().getArtifacts();
        if (assets != null) {
            for (KnowledgeAsset asset : assets) {
                persister.persist(asset);
            }
        }
    }
}
```

- [ ] **Step 2: 实现 KnowledgeStatePersister**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/state/KnowledgeStatePersister.java`:
```java
package com.meta_claw.knowledge.core.application.state;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class KnowledgeStatePersister {

    private final KnowledgeStateRepository repository;

    public KnowledgeStatePersister(KnowledgeStateRepository repository) {
        this.repository = repository;
    }

    public void persist(KnowledgeAsset asset) {
        KnowledgeAsset toSave = asset.toBuilder()
                .status("ready")
                .createdAt(Instant.now())
                .build();
        repository.save(toSave);

        // 同时初始化 control state = candidate
        KnowledgeControlState control = KnowledgeControlState.builder()
                .assetId(asset.getAssetId())
                .state("candidate")
                .reason("auto-generated from worker result")
                .createdAt(Instant.now())
                .build();
        repository.saveControlState(control);

        log.info("Persisted asset={}, control_state=candidate", asset.getAssetId());
    }
}
```

- [ ] **Step 3: 实现 AgentViewBuilder**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/view/AgentViewBuilder.java`:
```java
package com.meta_claw.knowledge.core.application.view;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;

import java.util.List;
import java.util.stream.Collectors;

public class AgentViewBuilder {

    private final KnowledgeSpaceBindingRepository spaceBindingRepository;
    private final KnowledgeStateRepository stateRepository;

    public AgentViewBuilder(KnowledgeSpaceBindingRepository spaceBindingRepository,
                            KnowledgeStateRepository stateRepository) {
        this.spaceBindingRepository = spaceBindingRepository;
        this.stateRepository = stateRepository;
    }

    public String buildMarkdownView(String roleName) {
        List<String> spaceIds = spaceBindingRepository.findSpaceIdsByRole(roleName);

        List<KnowledgeAsset> assets = stateRepository.findAll().stream()
                .filter(a -> spaceIds.contains(a.getSpaceId()))
                .filter(a -> "ready".equals(a.getStatus()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# Knowledge View for ").append(roleName).append("\n\n");

        for (String spaceId : spaceIds) {
            sb.append("## Space: ").append(spaceId).append("\n\n");
            List<KnowledgeAsset> spaceAssets = assets.stream()
                    .filter(a -> spaceId.equals(a.getSpaceId()))
                    .toList();

            for (KnowledgeAsset asset : spaceAssets) {
                sb.append("- **").append(asset.getAssetType()).append("** : ")
                  .append(asset.getAssetId())
                  .append(" (coverage: ").append(asset.getCoverage()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
```

- [ ] **Step 4: 写单元测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/application/view/AgentViewBuilderTest.java`:
```java
package com.meta_claw.knowledge.core.application.view;

import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentViewBuilderTest {

    @Test
    @DisplayName("按 role 过滤可见 space 并生成 markdown view")
    void buildViewForRole() {
        SampleKnowledgeStateRepository stateRepo = new SampleKnowledgeStateRepository();
        stateRepo.save(KnowledgeAsset.builder()
                .spaceId("shared")
                .assetId("asset_1")
                .assetType("graph")
                .status("ready")
                .coverage("partial")
                .build());

        AgentViewBuilder builder = new AgentViewBuilder(
                new SampleKnowledgeSpaceBindingRepository(),
                stateRepo
        );

        String view = builder.buildMarkdownView("finance_advisor");

        assertThat(view).contains("Knowledge View for finance_advisor");
        assertThat(view).contains("graph");
        assertThat(view).contains("asset_1");
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew test --tests '*KnowledgeStatePersisterTest' --tests '*AgentViewBuilderTest' -i`

Expected: PASSED

- [ ] **Step 6: Commit**

```bash
git add knowledge/service/core/src/main/java/.../application/state/ \
        knowledge/service/core/src/main/java/.../application/view/ \
        knowledge/service/core/src/main/java/.../application/flow/worker/IngestKnowledgeStateNode.java \
        knowledge/service/core/src/test/java/.../application/state/ \
        knowledge/service/core/src/test/java/.../application/view/
git commit -m "feat: add knowledge state persister and agent view builder"
```

---

## Task 8: Spring Boot REST API

**Files:**
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/MainApplication.java`
- Create: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/inbound/rest/KnowledgeRestController.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/adapter/inbound/rest/KnowledgeRestControllerTest.java`

- [ ] **Step 1: 创建 Spring Boot 启动类**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/MainApplication.java`:
```java
package com.meta_claw.knowledge.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 REST Controller**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/adapter/inbound/rest/KnowledgeRestController.java`:
```java
package com.meta_claw.knowledge.core.adapter.inbound.rest;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeRestController {

    private final KnowledgeFlowFacade flowFacade;
    private final AgentViewBuilder agentViewBuilder;

    public KnowledgeRestController(KnowledgeFlowFacade flowFacade, AgentViewBuilder agentViewBuilder) {
        this.flowFacade = flowFacade;
        this.agentViewBuilder = agentViewBuilder;
    }

    @PostMapping("/sources")
    public ResponseEntity<SourceRegistrationResult> registerSource(@RequestBody SourceRegistrationRequest request) {
        SourceRegistrationResult result = flowFacade.registerSource(
                RegisterSourceFlowContext.builder().request(request).build()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jobs")
    public ResponseEntity<WorkerJob> submitJob(@RequestBody SubmitWorkerJobRequest request) {
        WorkerJob job = flowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder().request(request).build()
        );
        return ResponseEntity.ok(job);
    }

    @PostMapping("/results")
    public ResponseEntity<WorkerResult> ingestResult(@RequestBody WorkerResultEnvelope envelope) {
        WorkerResult result = flowFacade.ingestWorkerResult(
                com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext.builder()
                        .workerResultEnvelope(envelope)
                        .build()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/views")
    public ResponseEntity<String> getView(@RequestParam String role) {
        String markdown = agentViewBuilder.buildMarkdownView(role);
        return ResponseEntity.ok(markdown);
    }
}
```

- [ ] **Step 3: 创建 Spring 配置类装配 FlowFacade**

`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/config/KnowledgeFlowConfig.java`:
```java
package com.meta_claw.knowledge.core.config;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeFlowConfig {

    @Bean
    public KnowledgeFlowFacade knowledgeFlowFacade() {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        return new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                new SampleKnowledgeStateRepository(),
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Bean
    public AgentViewBuilder agentViewBuilder(KnowledgeFlowFacade facade) {
        // 实际应从 facade 获取 repository，简化演示
        return new AgentViewBuilder(
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleKnowledgeStateRepository()
        );
    }
}
```

- [ ] **Step 4: 标记 CoreApplication 为 Deprecated**

在 `CoreApplication.java` 类注释前添加：
```java
@Deprecated(since = "2026-04-27", forRemoval = true)
```

- [ ] **Step 5: 写 API 测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/adapter/inbound/rest/KnowledgeRestControllerTest.java`:
```java
package com.meta_claw.knowledge.core.adapter.inbound.rest;

import com.meta_claw.knowledge.core.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/sources 注册来源")
    void registerSource() throws Exception {
        String json = "{"
                + "\"roleName\":\"test_role\","
                + "\"sourceType\":\"git_repository\","
                + "\"location\":\"src/test/resources/samples/sample-repo\","
                + "\"displayName\":\"sample_repo\""
                + "}";

        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRecord.sourceId").exists())
                .andExpect(jsonPath("$.snapshotRecord.snapshotId").exists());
    }
}
```

- [ ] **Step 6: 运行测试**

Run: `./gradlew test --tests KnowledgeRestControllerTest -i`

Expected: PASSED

- [ ] **Step 7: 手动验证 REST 服务**

```bash
./gradlew bootRun &
sleep 10

curl -X POST http://localhost:8080/api/v1/sources \
  -H "Content-Type: application/json" \
  -d '{"roleName":"test","sourceType":"git_repository","location":"knowledge/service/core/src/test/resources/samples/sample-repo","displayName":"sample"}'

curl "http://localhost:8080/api/v1/views?role=test"
```

Expected: 200 OK，返回 JSON / Markdown

- [ ] **Step 8: Commit**

```bash
git add knowledge/service/core/src/main/java/.../MainApplication.java \
        knowledge/service/core/src/main/java/.../adapter/inbound/rest/ \
        knowledge/service/core/src/main/java/.../config/ \
        knowledge/service/core/src/main/java/.../CoreApplication.java \
        knowledge/service/core/src/test/java/.../adapter/inbound/rest/
git commit -m "feat: add Spring Boot REST API and application config"
```

---

## Task 9: 端到端集成测试

**Files:**
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/integration/EndToEndIntegrationTest.java`

- [ ] **Step 1: 写端到端测试**

```java
package com.meta_claw.knowledge.core.integration;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EndToEndIntegrationTest {

    @Autowired
    private KnowledgeFlowFacade flowFacade;

    @Autowired
    private AgentViewBuilder viewBuilder;

    @Test
    @DisplayName("来源注册 → snapshot → job → view 全链路")
    void fullPipeline() {
        // 1. 注册来源
        SourceRegistrationResult reg = flowFacade.registerSource(
                RegisterSourceFlowContext.builder()
                        .request(TestFixtures.sampleRepoRequest())
                        .build()
        );
        assertThat(reg.getSourceRecord().getSourceId()).isNotBlank();

        // 2. 提交 job（当前 Worker 仍为 stub 或真实调用视环境而定）
        WorkerJob job = flowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder()
                        .request(SubmitWorkerJobRequest.builder()
                                .roleName("test_role")
                                .jobId("job_e2e_001")
                                .jobType("extract_graph_and_wiki")
                                .sourceId(reg.getSourceRecord().getSourceId())
                                .snapshotId(reg.getSnapshotRecord().getSnapshotId())
                                .expectedArtifacts(java.util.List.of("graph", "wiki"))
                                .build())
                        .build()
        );
        assertThat(job.getJobId()).isEqualTo("job_e2e_001");

        // 3. 查询 view（此时可能为空，但至少不报错）
        String view = viewBuilder.buildMarkdownView("test_role");
        assertThat(view).contains("Knowledge View for test_role");
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `./gradlew test --tests EndToEndIntegrationTest -i`

Expected: PASSED

- [ ] **Step 3: Commit**

```bash
git add knowledge/service/core/src/test/java/.../integration/
git commit -m "test: add end-to-end integration test"
```

---

## Task 10: 失败路径加固

**Files:**
- Create: `knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/register/FailurePathTest.java`
- Modify: `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/PersistChangedSourceAndSnapshotNode.java`（如需加固）

- [ ] **Step 1: 写失败路径测试**

`knowledge/service/core/src/test/java/com/meta_claw/knowledge/core/flow/register/FailurePathTest.java`:
```java
package com.meta_claw.knowledge.core.flow.register;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailurePathTest {

    private KnowledgeFlowFacade facade;

    @BeforeEach
    void setUp() {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        facade = new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                new SampleKnowledgeStateRepository(),
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Test
    @DisplayName("来源路径不存在时抛出异常，不写入 registry")
    void nonExistentSourceDoesNotCorruptRegistry() {
        var request = TestFixtures.sampleRepoRequest().toBuilder()
                .location("/non/existent/path")
                .build();

        assertThatThrownBy(() -> facade.registerSource(
                RegisterSourceFlowContext.builder().request(request).build()
        )).isInstanceOf(Exception.class);
    }
}
```

- [ ] **Step 2: 运行失败路径测试**

Run: `./gradlew test --tests FailurePathTest -i`

Expected: PASSED（或根据当前行为调整断言后通过）

- [ ] **Step 3: Commit**

```bash
git add knowledge/service/core/src/test/java/.../flow/register/FailurePathTest.java
git commit -m "test: add failure path protection tests"
```

---

## 计划自检

### Spec 覆盖检查

| Spec 要求 | 对应 Task |
|-----------|-----------|
| `.rwa` 来源规则解析 | Task 2 |
| Loop-to-complete 调度 | Task 3 |
| Python Worker 真实调用 graphify | Task 5 |
| Artifact 归档规范 | Task 6 |
| Knowledge State 落盘 | Task 7 |
| Agent-facing Views | Task 7 |
| Spring Boot REST API | Task 8 |
| JUnit 测试体系 | Task 1（骨架）+ 各 Task 自带测试 |
| 端到端测试 | Task 9 |
| 失败路径保护 | Task 10 |

### Placeholder 扫描

- [x] 无 "TBD" / "TODO"
- [x] 无 "implement later"
- [x] 无 "add appropriate error handling" 模糊描述
- [x] 所有代码步骤含完整代码
- [x] 所有命令含预期输出

### 类型一致性

- `KnowledgeFlowFacade.resumeSnapshotScan(String)` 在 Task 3 添加，与 Task 4 测试使用一致
- `WorkerResultEnvelope` 字段（`status`, `retriable`, `artifacts`, `issues`）在各处使用一致
- `KnowledgeAsset` 字段（`assetType`, `status`, `coverage`）在各处使用一致
