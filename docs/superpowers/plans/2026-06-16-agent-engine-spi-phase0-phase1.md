# Agent Engine SPI + Native Agent Engine 实现计划（Phase 0+1）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `meta-claw-core` 中引入 `AgentEngine` SPI，让 `VesselRuntime` 通过 `AgentEngineFactory` 选择执行引擎；先以 `NativeAgentEngine` 无缝兜底现有 `AgentExecutor` / `StreamingAgentExecutor` 行为；同时验证 Spring AI Alibaba `agent-framework` / `graph-core` 与当前 Spring AI 1.1.8 的依赖兼容性。

**Architecture:** 在 `VesselRuntime` 与具体执行器之间插入一层最小契约 `AgentEngine`。`AgentEngineFactory` 自动收集 Spring 容器中所有实现并按 `name()` 索引。`NativeAgentEngine` 把既有同步/流式/HITL 恢复能力适配到契约上。`VesselConfig` 新增可选 `agentEngine` 字段，默认 `native`，`VesselConfigBundle` 暴露 `getAgentEngine()`。Phase 0 只新增 SAA 依赖和一个不联网的 `AlibabaEngineSmokeTest`；Phase 1 完成 SPI、Native 实现与 `VesselRuntime` 改造。

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AI 1.1.8, Spring AI Alibaba 1.1.2.3, Maven, JUnit 5, Mockito, Lombok.

---

## 文件结构总览

| 文件 | 操作 | 职责 |
|------|------|------|
| `meta-claw-core/pom.xml` | 修改 | 引入 `spring-ai-alibaba-agent-framework` / `spring-ai-alibaba-graph-core` |
| `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java` | 修改 | 新增 `agentEngine` 字段与 `AlibabaAgentConfig` 嵌套类 |
| `meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java` | 修改 | 新增 `getAgentEngine()` / `getAlibabaAgentConfig()` 便捷方法 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngine.java` | 创建 | Agent 执行引擎 SPI（同步/流式/恢复/name） |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngineFactory.java` | 创建 | 按名称收集并路由 `AgentEngine` |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/NativeAgentEngine.java` | 创建 | 复用现有 `AgentExecutor` / `StreamingAgentExecutor` 的兜底实现 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java` | 修改 | 移除直接依赖 `AgentExecutor` / `StreamingAgentExecutor`，改为 `AgentEngineFactory` |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AgentEngineFactoryTest.java` | 创建 | 验证工厂收集、重复名称检测、默认引擎、未知引擎报错 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/NativeAgentEngineTest.java` | 创建 | 验证 `NativeAgentEngine` 正确委托给 `AgentExecutor` / `StreamingAgentExecutor` |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AlibabaEngineSmokeTest.java` | 创建 | Phase 0 冒烟测试：SAA 依赖可加载、`ReactAgent` 可构建并调用一次无工具对话 |
| `init.sh` | 修改 | 将新增测试加入 P0 测试列表 |
| `feature_list.json` | 修改 | 更新 `agent-engine-001` 状态与证据 |
| `claude-progress.md` | 修改 | 记录 Phase 0+1 实施进度与验证结果 |

---

## Task 1: 在 `meta-claw-core` 引入 Spring AI Alibaba agent/graph 依赖

**Files:**
- Modify: `meta-claw-core/pom.xml:85-91`（在 `spring-ai-starter-mcp-client` 之后追加）

- [ ] **Step 1: 追加两个 SAA 依赖**

```xml
        <!-- Spring AI Alibaba Agent Framework & Graph Core：为后续 Alibaba 引擎提供基础类库 -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-agent-framework</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-graph-core</artifactId>
        </dependency>
```

- [ ] **Step 2: 确认根 POM 已导入 SAA BOM**

根 `pom.xml` 已包含 `spring-ai-alibaba-bom` 1.1.2.3，因此本模块不需要显式版本号。运行：

```bash
grep -n "spring-ai-alibaba-bom" /Users/kai/IdeaProjects/meta_claw/pom.xml
```

Expected: 显示 `spring-ai-alibaba-bom` 导入行，版本为 `${spring-ai-alibaba.version}` 或 `1.1.2.3`。

- [ ] **Step 3: 编译验证依赖可下载**

```bash
cd /Users/kai/IdeaProjects/meta_claw
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`；无 `Could not find artifact com.alibaba.cloud.ai:spring-ai-alibaba-agent-framework` 错误。

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/pom.xml
git commit -m "deps(meta-claw-core): add spring-ai-alibaba-agent-framework and graph-core"
```

---

## Task 2: 在 Vessel 配置模型中增加 `agentEngine` 字段

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java:56-62`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java:106-109`

- [ ] **Step 1: 在 `VesselConfig` 中新增 `agentEngine` 与 `AlibabaAgentConfig`**

在 `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java` 的 `ToolConfig tools` 字段之后、`maxHistoryRounds` 之前插入：

```java
    /** Agent 执行引擎类型：native（默认）或 alibaba */
    private String agentEngine = "native";

    /** Spring AI Alibaba 引擎专属配置（仅 agentEngine=alibaba 时生效） */
    private AlibabaAgentConfig alibabaAgent = new AlibabaAgentConfig();
```

在同一文件末尾、闭类大括号之前新增嵌套静态类：

```java
    /**
     * Spring AI Alibaba Agent 引擎配置。
     */
    @Getter
    @Setter
    public static class AlibabaAgentConfig {
        /** 是否启用并行工具执行 */
        private boolean parallelToolExecution = true;
        /** 最大并行工具数 */
        private int maxParallelTools = 5;
        /** 是否返回 reasoning_content */
        private boolean returnReasoningContents = true;
    }
```

- [ ] **Step 2: 在 `VesselConfigBundle` 暴露便捷访问方法**

在 `meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java` 的 `getRuntimeVesselConfig()` 方法之后追加：

```java
    public String getAgentEngine() {
        VesselConfig config = getRuntimeVesselConfig();
        if (config != null && config.getAgentEngine() != null && !config.getAgentEngine().isBlank()) {
            return config.getAgentEngine();
        }
        return "native";
    }

    public VesselConfig.AlibabaAgentConfig getAlibabaAgentConfig() {
        VesselConfig config = getRuntimeVesselConfig();
        return config != null && config.getAlibabaAgent() != null
                ? config.getAlibabaAgent()
                : new VesselConfig.AlibabaAgentConfig();
    }
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java \
        meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java
git commit -m "feat(config): add agentEngine and AlibabaAgentConfig to VesselConfig"
```

---

## Task 3: 定义 `AgentEngine` SPI

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngine.java`

- [ ] **Step 1: 创建 SPI 接口**

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;

/**
 * Agent 执行引擎 SPI。
 *
 * <p>实现类负责把 {@link SpiChatRequest} 转换为一次 Agent 任务执行，
 * 并返回最终 {@link Reply}。同步、流式、HITL 恢复三种入口必须同时提供。</p>
 *
 * <p>该接口刻意保持最小化：只接收 TaskContext 和 SpiChatRequest，
 * 不暴露任何 Spring AI 或 SAA 专有类型，保证上层 VesselRuntime 与引擎实现解耦。</p>
 */
public interface AgentEngine {

    /** 同步执行一次对话任务。 */
    Reply execute(TaskContext ctx, SpiChatRequest request);

    /** 流式执行一次对话任务。 */
    void executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback);

    /** 从 HITL 挂起状态恢复并继续执行。 */
    Reply resume(TaskContext ctx, SpiChatRequest request,
                 ApprovalTicket ticket, ApprovalResolution resolution);

    /** 引擎名称，用于配置选择，如 {@code native} 或 {@code alibaba}。 */
    String name();
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngine.java
git commit -m "feat(engine): define AgentEngine SPI"
```

---

## Task 4: 创建 `AgentEngineFactory`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngineFactory.java`

- [ ] **Step 1: 创建工厂类**

```java
package meta.claw.core.runtime.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 Vessel 配置选择 AgentEngine 实现。
 *
 * <p>所有 {@link AgentEngine} 实现由 Spring 自动收集，按 {@code name()} 注册。
 * 默认引擎为 {@code native}，可通过 {@code agentEngine} 切换。</p>
 */
@Component
public class AgentEngineFactory {

    private final Map<String, AgentEngine> engines = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public void setEngines(List<AgentEngine> engineList) {
        engines.clear();
        if (engineList == null) {
            return;
        }
        for (AgentEngine engine : engineList) {
            AgentEngine previous = engines.put(engine.name(), engine);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate AgentEngine name: " + engine.name());
            }
        }
    }

    public AgentEngine getEngine(String name) {
        AgentEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException(
                    "No AgentEngine for name: " + name + ". Available: " + engines.keySet());
        }
        return engine;
    }

    public AgentEngine getDefaultEngine() {
        return getEngine("native");
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngineFactory.java
git commit -m "feat(engine): add AgentEngineFactory to route engines by name"
```

---

## Task 5: 实现 `NativeAgentEngine`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/NativeAgentEngine.java`

- [ ] **Step 1: 创建 Native 实现**

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.AgentExecutor;
import meta.claw.core.runtime.StreamingAgentExecutor;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 复用现有 {@link AgentExecutor} / {@link StreamingAgentExecutor} 的本地引擎。
 */
@Component
public class NativeAgentEngine implements AgentEngine {

    @Autowired
    private AgentExecutor agentExecutor;
    @Autowired
    private StreamingAgentExecutor streamingAgentExecutor;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        return agentExecutor.execute(ctx, request);
    }

    @Override
    public void executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        streamingAgentExecutor.execute(ctx, request, callback);
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        return agentExecutor.resume(ctx, request, ticket, resolution);
    }

    @Override
    public String name() {
        return "native";
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/NativeAgentEngine.java
git commit -m "feat(engine): implement NativeAgentEngine as fallback"
```

---

## Task 6: 改造 `VesselRuntime` 使用 `AgentEngineFactory`

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`

- [ ] **Step 1: 替换直接注入的执行器为工厂**

把下面两行：

```java
    @Autowired
    private AgentExecutor agentExecutor;
    @Autowired
    private StreamingAgentExecutor streamingAgentExecutor;
```

替换为：

```java
    @Autowired
    private AgentEngineFactory agentEngineFactory;
```

- [ ] **Step 2: 新增 `currentEngine()` 私有方法**

在 `VesselRuntime` 的 `getProfile()` 方法之后、`renderSystemPrompt()` 之前插入：

```java
    private AgentEngine currentEngine() {
        return agentEngineFactory.getEngine(getProfile().getBundle().getAgentEngine());
    }
```

需要新增 import：

```java
import meta.claw.core.runtime.engine.AgentEngine;
import meta.claw.core.runtime.engine.AgentEngineFactory;
```

- [ ] **Step 3: 修改 `resume()` 调用点**

把：

```java
            Reply reply = agentExecutor.resume(ctx, request, ticket, resolution);
```

改为：

```java
            Reply reply = currentEngine().resume(ctx, request, ticket, resolution);
```

- [ ] **Step 4: 修改 `execute()` 调用点**

把：

```java
            // Phase 2: 使用 AgentExecutor 执行，支持多轮 tool-call
            Reply reply = agentExecutor.execute(ctx, request);
```

改为：

```java
            Reply reply = currentEngine().execute(ctx, request);
```

- [ ] **Step 5: 修改 `chatStream()` 调用点**

把：

```java
            Reply reply = streamingAgentExecutor.execute(ctx, request, callback);
```

改为：

```java
            Reply reply = currentEngine().executeStream(ctx, request, callback);
```

注意：`AgentEngine.executeStream` 返回 `void`，但 `VesselRuntime.chatStream` 需要 `Reply` 来保存 assistant 消息。这里有两种处理方式：

**推荐方式 A（保持当前行为）：** 让 `AgentEngine.executeStream` 返回 `Reply`，同步得到最终内容后落盘。需要同步修改 SPI 接口。

**方式 B（由引擎内部回调保存）：** 让 `AgentEngine.executeStream` 返回 `void`，`VesselRuntime` 在调用前把保存逻辑包装进 callback。这样更自然，但改动更大。

本计划采用 **方式 A** 以最小化本轮改动：把 `AgentEngine.executeStream` 的返回类型从 `void` 改为 `Reply`。同步修改 Task 3 中的 SPI 接口和 Task 5 中的 `NativeAgentEngine`。

- [ ] **Step 6: 把 `AgentEngine.executeStream` 返回类型改为 `Reply`**

在 `AgentEngine.java` 中：

```java
    /** 流式执行一次对话任务。 */
    Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback);
```

在 `NativeAgentEngine.java` 中：

```java
    @Override
    public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        return streamingAgentExecutor.execute(ctx, request, callback);
    }
```

- [ ] **Step 7: 编译验证**

```bash
mvn clean compile -pl meta-claw-core -am -q
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 8: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java \
        meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngine.java \
        meta-claw-core/src/main/java/meta/claw/core/runtime/engine/NativeAgentEngine.java
git commit -m "feat(runtime): route VesselRuntime execution through AgentEngineFactory"
```

---

## Task 7: 新增 `AgentEngineFactoryTest`

**Files:**
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AgentEngineFactoryTest.java`

- [ ] **Step 1: 创建测试类**

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentEngineFactoryTest {

    @Test
    void collectsEnginesByName() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine nativeEngine = new NamedEngine("native");
        AgentEngine alibabaEngine = new NamedEngine("alibaba");
        factory.setEngines(List.of(nativeEngine, alibabaEngine));

        assertSame(nativeEngine, factory.getEngine("native"));
        assertSame(alibabaEngine, factory.getEngine("alibaba"));
    }

    @Test
    void defaultEngineIsNative() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine nativeEngine = new NamedEngine("native");
        factory.setEngines(List.of(nativeEngine));

        assertSame(nativeEngine, factory.getDefaultEngine());
    }

    @Test
    void throwsOnDuplicateName() {
        AgentEngineFactory factory = new AgentEngineFactory();
        AgentEngine a = new NamedEngine("native");
        AgentEngine b = new NamedEngine("native");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.setEngines(List.of(a, b)));
        assertTrue(ex.getMessage().contains("Duplicate AgentEngine name"));
    }

    @Test
    void throwsOnUnknownEngine() {
        AgentEngineFactory factory = new AgentEngineFactory();
        factory.setEngines(List.of(new NamedEngine("native")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.getEngine("missing"));
        assertTrue(ex.getMessage().contains("No AgentEngine for name"));
    }

    private static class NamedEngine implements AgentEngine {
        private final String name;

        NamedEngine(String name) {
            this.name = name;
        }

        @Override
        public Reply execute(TaskContext ctx, SpiChatRequest request) {
            return null;
        }

        @Override
        public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
            return null;
        }

        @Override
        public Reply resume(TaskContext ctx, SpiChatRequest request,
                            ApprovalTicket ticket, ApprovalResolution resolution) {
            return null;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl meta-claw-core -am -Dtest=AgentEngineFactoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AgentEngineFactoryTest.java
git commit -m "test(engine): add AgentEngineFactoryTest"
```

---

## Task 8: 新增 `NativeAgentEngineTest`

**Files:**
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/NativeAgentEngineTest.java`

- [ ] **Step 1: 创建测试类**

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.AgentExecutor;
import meta.claw.core.runtime.StreamingAgentExecutor;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NativeAgentEngineTest {

    @Test
    void nameIsNative() {
        NativeAgentEngine engine = new NativeAgentEngine();
        assertEquals("native", engine.name());
    }

    @Test
    void executeDelegatesToAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        StreamingAgentExecutor streamingAgentExecutor = mock(StreamingAgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);
        ReflectionTestUtils.setField(engine, "streamingAgentExecutor", streamingAgentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        Reply expected = new Reply(ReplyType.TEXT, "hello");
        when(agentExecutor.execute(ctx, request)).thenReturn(expected);

        Reply actual = engine.execute(ctx, request);

        assertEquals(expected, actual);
        verify(agentExecutor).execute(ctx, request);
        verifyNoInteractions(streamingAgentExecutor);
    }

    @Test
    void executeStreamDelegatesToStreamingAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        StreamingAgentExecutor streamingAgentExecutor = mock(StreamingAgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);
        ReflectionTestUtils.setField(engine, "streamingAgentExecutor", streamingAgentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        SpiStreamingCallback callback = mock(SpiStreamingCallback.class);
        Reply expected = new Reply(ReplyType.TEXT, "streamed");
        when(streamingAgentExecutor.execute(ctx, request, callback)).thenReturn(expected);

        Reply actual = engine.executeStream(ctx, request, callback);

        assertEquals(expected, actual);
        verify(streamingAgentExecutor).execute(ctx, request, callback);
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void resumeDelegatesToAgentExecutor() {
        NativeAgentEngine engine = new NativeAgentEngine();
        AgentExecutor agentExecutor = mock(AgentExecutor.class);
        ReflectionTestUtils.setField(engine, "agentExecutor", agentExecutor);

        TaskContext ctx = mock(TaskContext.class);
        SpiChatRequest request = SpiChatRequest.builder().vesselId("v1").build();
        ApprovalTicket ticket = mock(ApprovalTicket.class);
        ApprovalResolution resolution = mock(ApprovalResolution.class);
        Reply expected = new Reply(ReplyType.TEXT, "resumed");
        when(agentExecutor.resume(ctx, request, ticket, resolution)).thenReturn(expected);

        Reply actual = engine.resume(ctx, request, ticket, resolution);

        assertEquals(expected, actual);
        verify(agentExecutor).resume(ctx, request, ticket, resolution);
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl meta-claw-core -am -Dtest=NativeAgentEngineTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/test/java/meta/claw/core/runtime/engine/NativeAgentEngineTest.java
git commit -m "test(engine): add NativeAgentEngineTest"
```

---

## Task 9: 新增 `AlibabaEngineSmokeTest`（Phase 0 兼容性验证）

**Files:**
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AlibabaEngineSmokeTest.java`

- [ ] **Step 1: 创建冒烟测试**

```java
package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 0 冒烟测试：验证 Spring AI Alibaba 依赖与 Spring AI 1.1.8 的兼容性。
 *
 * <p>本测试不发起真实网络请求，仅验证：</p>
 * <ol>
 *   <li>{@link ReactAgent} 类可加载</li>
 *   <li>{@link ReactAgent} 可使用 mock {@link ChatModel} 构建</li>
 *   <li>一次无工具对话可返回预期结果</li>
 * </ol>
 */
class AlibabaEngineSmokeTest {

    @Test
    void canBuildReactAgentAndCallWithMockModel() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("hello from alibaba")))));

        ReactAgent agent = ReactAgent.builder()
                .name("smoke")
                .description("smoke test agent")
                .model(chatModel)
                .systemPrompt("You are a smoke tester.")
                .build();

        assertNotNull(agent);

        List<Message> messages = List.of(new UserMessage("hi"));
        AssistantMessage result = agent.call(messages);

        assertNotNull(result);
        assertEquals("hello from alibaba", result.getText());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl meta-claw-core -am -Dtest=AlibabaEngineSmokeTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`。

如果 SAA `ReactAgent` API 与示例不一致（例如 `call(...)` 返回类型不同、builder 方法名不同），根据实际编译错误调整测试代码。该测试的核心目的是暴露版本/API 不兼容问题。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AlibabaEngineSmokeTest.java
git commit -m "test(engine): add AlibabaEngineSmokeTest for SAA dependency compatibility"
```

---

## Task 10: 更新 `init.sh` 把新增测试纳入 P0 基线

**Files:**
- Modify: `init.sh:13`

- [ ] **Step 1: 在 P0 测试列表追加新测试**

把 `-Dtest=...GitToolTest` 的列表改为追加 `,AgentEngineFactoryTest,NativeAgentEngineTest,AlibabaEngineSmokeTest`：

```bash
  -Dtest=VesselConfigLoaderTest,VesselManagerTest,SystemPromptBuilderTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest,MessageFlowIntegrationTest,ConfigurableHitlPolicyTest,HitlSubSystemTest,AgentExecutorHitlTest,StreamingAgentExecutorTest,SkillRegistryTest,SkillSubSystemTest,ReadSkillToolTest,MetricsSubSystemTest,MetricsRecorderTest,ToolRegistryTest,ToolSubSystemTest,ShellToolTest,FileToolTest,GitToolTest,AgentEngineFactoryTest,NativeAgentEngineTest,AlibabaEngineSmokeTest
```

注意：`init.sh` 第 13 行和第 51 行有两份相同的列表（第 51 行是实际执行时重新组装的数组），两处都要更新。

- [ ] **Step 2: 运行 `./init.sh` 全量验证**

```bash
cd /Users/kai/IdeaProjects/meta_claw
./init.sh
```

Expected: 全仓编译 SUCCESS，P0 测试全部通过（包括新增的 3 个 engine 测试）。

- [ ] **Step 3: Commit**

```bash
git add init.sh
git commit -m "chore(init): include new engine tests in P0 baseline"
```

---

## Task 11: 更新长期状态文件

**Files:**
- Modify: `feature_list.json`
- Modify: `claude-progress.md`

- [ ] **Step 1: 更新 `feature_list.json` 中 `agent-engine-001` 的证据**

在 `agent-engine-001` 的 `evidence` 数组末尾追加：

```json
        {
          "date": "2026-06-16",
          "result": "in_progress",
          "detail": "Phase 0+1 实施计划已输出到 docs/superpowers/plans/2026-06-16-agent-engine-spi-phase0-phase1.md；开始按 Task 1~11 逐步实现。"
        }
```

实施完成后，将 `status` 从 `"passing"` 改为 `"passing"`（仍为 passing），并追加一条证据：

```json
        {
          "date": "2026-06-16",
          "result": "passing",
          "detail": "AgentEngine SPI、AgentEngineFactory、NativeAgentEngine 已实现；VesselRuntime 通过工厂路由；新增 AgentEngineFactoryTest、NativeAgentEngineTest、AlibabaEngineSmokeTest；./init.sh 全量通过。"
        }
```

- [ ] **Step 2: 在 `claude-progress.md` 追加 Session 记录**

在文件最上方 `## 当前已验证状态` 之后，或 `## 会话记录` 末尾新增一个 Session：

```markdown
### Session <next>

- 日期：2026-06-16
- 本轮目标：实现 AgentEngine SPI + NativeAgentEngine，验证 SAA 依赖兼容性
- 已完成：
  - 按 2026-06-15 设计文档 Phase 0+1 完成代码实现
  - 新增 `AgentEngine` SPI、`AgentEngineFactory`、`NativeAgentEngine`
  - 改造 `VesselRuntime` 通过工厂选择引擎
  - 在 `VesselConfig` / `VesselConfigBundle` 中新增 `agentEngine` 配置
  - 新增 `AgentEngineFactoryTest`、`NativeAgentEngineTest`、`AlibabaEngineSmokeTest`
  - 更新 `init.sh` P0 测试列表
- 运行过的验证：
  - `./init.sh` → 成功；新增测试全部通过
- 更新过的文件或工件：
  - `meta-claw-core/pom.xml`
  - `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java`
  - `meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngine.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/AgentEngineFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/NativeAgentEngine.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AgentEngineFactoryTest.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/NativeAgentEngineTest.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/AlibabaEngineSmokeTest.java`
  - `init.sh`
  - `feature_list.json`
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前 `AgentEngine.executeStream` 返回 `Reply` 以保持 VesselRuntime 落盘逻辑简单；后续若接入 SAA 流式需要重新评估返回类型。
  - `AlibabaEngineSmokeTest` 依赖 SAA `ReactAgent` API 签名；若 SAA 1.1.2.3 与 Spring AI 1.1.8 存在二进制不兼容，该测试会首先暴露。
- 下一步最佳动作：
  1. 提交本轮修改
  2. 进入 Phase 2：实现 `SpringAiAlibabaAgentEngine` 同步调用
```

- [ ] **Step 3: Commit**

```bash
git add feature_list.json claude-progress.md
git commit -m "docs: update feature_list and progress for agent-engine phase 0+1"
```

---

## Self-Review Checklist

实施前由执行者自检：

- [ ] **Spec coverage:** 设计文档 Phase 0（SAA 依赖 + 冒烟测试）与 Phase 1（SPI + Native + VesselRuntime 改造）均有对应 Task。
- [ ] **Placeholder scan:** 计划中没有 TBD/TODO/"implement later"；所有代码片段、命令、期望输出均已给出。
- [ ] **Type consistency:** `AgentEngine.executeStream` 在 SPI、Native 实现、VesselRuntime 调用点、测试类中均返回 `Reply`。
- [ ] **File paths:** 所有路径与当前仓库结构一致（`meta-claw-core/src/main/java/meta/claw/core/runtime/engine/` 为新包）。
- [ ] **Validation gate:** 每个 Task 都有明确的编译/测试命令与期望输出；最终 `./init.sh` 为全量验证。

---

## 执行入口

计划保存后，建议按 Task 1→11 顺序执行。每个 Task 的 Step 1 为实际代码改动，后续为验证与提交。如果 `./init.sh` 在沙箱内失败（Mockito/ByteBuddy 自附加限制），请在允许 JVM agent 附加的真实环境中重新运行。
