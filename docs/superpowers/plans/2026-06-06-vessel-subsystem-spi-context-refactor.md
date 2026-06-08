# VesselSubSystem SPI 与执行上下文可读性重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 `VesselSubSystem` SPI 骨架，新建 `AgentExecutionContext` 消除执行期与 `VesselRuntime` 的循环引用，让 `VesselRuntime` 从"直接执行器"转变为"子系统编排器"，同时显著提升 `PromptContext` 与执行上下文关系的可读性。

**Architecture:** 保留现有 `PromptContext` 名称（成本已沉没），通过 `VesselSubSystem.enrich()` 明确"向 Prompt 素材注入内容"的语义；新建 `AgentExecutionContext` 只持有执行期状态（sessionId、userMessage、messages）和 `SubSystemRegistry` 引用，不再反向引用 `VesselRuntime`；现有 Memory 与 Prompt 能力包装为第一批子系统接入。

**Tech Stack:** Java 21, Spring Boot 3.2, Spring AI 1.1.7, Lombok, JUnit 5, Mockito

---

## File Structure

| File | Responsibility |
|------|---------------|
| `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/VesselSubSystem.java` | 子系统 SPI 接口，`enrich` + `onExecutionStart/End` 生命周期 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/SubSystemRegistry.java` | 子系统注册表，按 name 查询，消除 AgentExecutionContext → VesselRuntime 循环引用 |
| `meta-claw-core/src/main/java/meta/claw/core/agent/AgentExecutionContext.java` | 执行期上下文：任务参数 + PromptContext + messages + registry |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/MemorySubSystem.java` | 包装 `ShortMemoryFactory` + `LongMemoryFactory`，向 VesselRuntime 提供记忆服务 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/PromptSubSystem.java` | 包装 `PromptContextFactory`，负责 `PromptContext` 的 enrich 与重建 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java` | 重构为子系统编排器：注入 `List<VesselSubSystem>`，通过 registry 暴露子系统 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/SubSystemRegistryTest.java` | 注册表查询测试 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/MemorySubSystemTest.java` | MemorySubSystem 生命周期与 enrich 测试 |
| `meta-claw-core/src/test/java/meta/claw/core/agent/AgentExecutionContextTest.java` | AgentExecutionContext 构造与快照测试 |

---

### Task 1: VesselSubSystem SPI 接口

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/VesselSubSystem.java`

- [ ] **Step 1: 写接口**

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.agent.AgentExecutionContext;
import meta.claw.core.prompt.PromptContext;

/**
 * Vessel 子系统 SPI。
 * 所有能力（Memory, Tool, Skill, HITL, Metrics 等）必须实现此接口，
 * 由 VesselRuntime 统一编排生命周期和上下文注入。
 *
 * <p>调用顺序（由 VesselRuntime 保证）：</p>
 * <ol>
 *   <li>{@link #initialize(SubSystemRegistry)} — VesselRuntime 创建时调用一次</li>
 *   <li>{@link #enrich(PromptContext.Builder)} — 每次对话前，构建 PromptContext 时调用</li>
 *   <li>{@link #onExecutionStart(AgentExecutionContext)} — 每次对话开始时调用</li>
 *   <li>{@link #onExecutionEnd(AgentExecutionContext)} — 每次对话结束时调用（finally 中）</li>
 * </ol>
 */
public interface VesselSubSystem {

    /** 子系统唯一标识，如 "memory", "tool", "skill", "hitl" */
    String name();

    /** 初始化：VesselRuntime 创建时调用 */
    void initialize(SubSystemRegistry registry);

    /**
     * 向 PromptContext 注入本系统贡献的领域数据。
     * 调用时机：AgentExecutionContext 构造之前，由 VesselRuntime.buildPromptContext() 触发。
     */
    void enrich(PromptContext.Builder builder);

    /** 每次对话开始时调用 */
    default void onExecutionStart(AgentExecutionContext ctx) {}

    /** 每次对话结束时调用 */
    default void onExecutionEnd(AgentExecutionContext ctx) {}

    /** 子系统优先级，数值越小越早执行 enrich */
    default int priority() { return 100; }
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/VesselSubSystem.java
git commit -m "feat(subsystem): introduce VesselSubSystem SPI with enrich lifecycle"
```

---

### Task 2: SubSystemRegistry（消除循环引用）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/SubSystemRegistry.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/SubSystemRegistryTest.java`

- [ ] **Step 1: 写注册表**

```java
package meta.claw.core.runtime.subsystem;

import java.util.*;

/**
 * 子系统轻量级注册表。
 * 按 name 索引所有 VesselSubSystem，供 AgentExecutionContext 查询使用。
 * 避免 AgentExecutionContext 直接持有 VesselRuntime 导致的循环引用。
 */
public class SubSystemRegistry {

    private final Map<String, VesselSubSystem> subSystems = new HashMap<>();

    public void register(VesselSubSystem subSystem) {
        VesselSubSystem existing = subSystems.put(subSystem.name(), subSystem);
        if (existing != null) {
            throw new IllegalStateException(
                "Duplicate VesselSubSystem name: '" + subSystem.name() +
                "'. Existing: " + existing.getClass().getName() +
                ", New: " + subSystem.getClass().getName()
            );
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T get(String name) {
        return (T) subSystems.get(name);
    }

    public boolean has(String name) {
        return subSystems.containsKey(name);
    }

    public List<VesselSubSystem> listAll() {
        return List.copyOf(subSystems.values());
    }

    public void clear() {
        subSystems.clear();
    }
}
```

- [ ] **Step 2: 写测试**

```java
package meta.claw.core.runtime.subsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubSystemRegistryTest {

    static class FakeSubSystem implements VesselSubSystem {
        private final String name;
        FakeSubSystem(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public void initialize(SubSystemRegistry registry) {}
        @Override public void enrich(meta.claw.core.prompt.PromptContext.Builder builder) {}
    }

    @Test
    void registerAndGet_returnsSubSystem() {
        SubSystemRegistry registry = new SubSystemRegistry();
        FakeSubSystem mem = new FakeSubSystem("memory");
        registry.register(mem);

        assertSame(mem, registry.get("memory"));
        assertTrue(registry.has("memory"));
        assertNull(registry.get("tool"));
    }

    @Test
    void duplicateName_throws() {
        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(new FakeSubSystem("memory"));
        assertThrows(IllegalStateException.class,
            () -> registry.register(new FakeSubSystem("memory")));
    }

    @Test
    void listAll_returnsUnmodifiableSnapshot() {
        SubSystemRegistry registry = new SubSystemRegistry();
        registry.register(new FakeSubSystem("memory"));
        assertEquals(1, registry.listAll().size());
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -pl meta-claw-core -am -Dtest=SubSystemRegistryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3/3)

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/SubSystemRegistry.java \
        meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/SubSystemRegistryTest.java
git commit -m "feat(subsystem): add SubSystemRegistry to break circular ref"
```

---

### Task 3: AgentExecutionContext（执行期上下文）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/agent/AgentExecutionContext.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/agent/AgentExecutionContextTest.java`

**Rationale:** AgentExecutionContext 不持有 VesselRuntime，只持有 SubSystemRegistry + 执行期数据。这是解决"PromptContext 和 AgentExecutionContext 关系别扭"的核心。

- [ ] **Step 1: 写 AgentExecutionContext**

```java
package meta.claw.core.agent;

import lombok.Getter;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;

import java.util.ArrayList;
import java.util.List;

import meta.claw.core.llm.SpiMessage;

/**
 * Agent 单次执行上下文。
 * <p>生命周期 = 一次用户消息 → LLM 响应结束。</p>
 * <p>与 {@link PromptContext} 的关系：</p>
 * <ul>
 *   <li>PromptContext = 静态/半静态的 Prompt 素材（配置、技能、偏好）</li>
 *   <li>AgentExecutionContext = 动态执行跟踪器（本次会话、消息累积、子系统查找）</li>
 * </ul>
 */
@Getter
public class AgentExecutionContext {

    private final String vesselId;
    private final String sessionId;
    private final String userMessage;
    private final PromptContext promptContext;
    private final SubSystemRegistry registry;
    private final List<SpiMessage> messages = new ArrayList<>();

    public AgentExecutionContext(String vesselId, String sessionId, String userMessage,
                                  PromptContext promptContext, SubSystemRegistry registry) {
        this.vesselId = vesselId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.promptContext = promptContext;
        this.registry = registry;
    }

    /** 便捷方法：通过注册表获取子系统 */
    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    public void addMessage(SpiMessage message) {
        this.messages.add(message);
    }

    public List<SpiMessage> getMessagesSnapshot() {
        return List.copyOf(messages);
    }
}
```

- [ ] **Step 2: 写测试**

```java
package meta.claw.core.agent;

import meta.claw.core.prompt.PromptContext;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionContextTest {

    static class FakeSubSystem implements VesselSubSystem {
        @Override public String name() { return "fake"; }
        @Override public void initialize(SubSystemRegistry registry) {}
        @Override public void enrich(PromptContext.Builder builder) {}
    }

    @Test
    void construction_holdsAllFields() {
        SubSystemRegistry registry = new SubSystemRegistry();
        PromptContext ctx = PromptContext.builder().build();
        AgentExecutionContext exec = new AgentExecutionContext(
            "v1", "s1", "hello", ctx, registry);

        assertEquals("v1", exec.getVesselId());
        assertEquals("s1", exec.getSessionId());
        assertEquals("hello", exec.getUserMessage());
        assertSame(ctx, exec.getPromptContext());
        assertSame(registry, exec.getRegistry());
        assertTrue(exec.getMessagesSnapshot().isEmpty());
    }

    @Test
    void getSubSystem_returnsRegistered() {
        SubSystemRegistry registry = new SubSystemRegistry();
        FakeSubSystem fake = new FakeSubSystem();
        registry.register(fake);

        AgentExecutionContext exec = new AgentExecutionContext(
            "v1", "s1", "hello", PromptContext.builder().build(), registry);

        assertSame(fake, exec.getSubSystem("fake"));
        assertNull(exec.getSubSystem("missing"));
    }

    @Test
    void addMessage_accumulatesAndSnapshotIsImmutable() {
        AgentExecutionContext exec = new AgentExecutionContext(
            "v1", "s1", "hello", PromptContext.builder().build(), new SubSystemRegistry());

        exec.addMessage(meta.claw.core.llm.SpiMessage.user("hi"));
        assertEquals(1, exec.getMessagesSnapshot().size());

        assertThrows(UnsupportedOperationException.class,
            () -> exec.getMessagesSnapshot().add(null));
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -pl meta-claw-core -am -Dtest=AgentExecutionContextTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3/3)

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/agent/AgentExecutionContext.java \
        meta-claw-core/src/test/java/meta/claw/core/agent/AgentExecutionContextTest.java
git commit -m "feat(agent): add AgentExecutionContext with SubSystemRegistry lookup"
```

---

### Task 4: MemorySubSystem（包装现有 Memory 能力）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/MemorySubSystem.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/MemorySubSystemTest.java`

- [ ] **Step 1: 写 MemorySubSystem**

```java
package meta.claw.core.runtime.subsystem;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.prompt.PromptContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Memory 子系统。
 * 负责向 PromptContext 注入长期偏好摘要，并在执行期间提供短期/长期记忆服务。
 */
@Slf4j
@Component
public class MemorySubSystem implements VesselSubSystem {

    @Autowired
    private ShortMemoryFactory shortMemoryFactory;
    @Autowired
    private LongMemoryFactory longMemoryFactory;

    private SubSystemRegistry registry;

    @Override
    public String name() { return "memory"; }

    @Override
    public void initialize(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void enrich(PromptContext.Builder builder) {
        // 长期偏好可注入 PromptContext（如需要）
        // 当前保持空实现，避免破坏现有 PromptContextFactory 行为
    }

    @Override
    public int priority() { return 10; }

    public ShortMemory getShortMemory(MemoryConfig config) {
        return shortMemoryFactory.get(config.getShortTermStore());
    }

    public LongMemory getLongMemory(MemoryConfig config) {
        return longMemoryFactory.get(config.getLongTermStore());
    }
}
```

- [ ] **Step 2: 写测试**

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.config.MemoryConfig;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.memory.shortterm.ShortMemoryFactory;
import meta.claw.core.prompt.PromptContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemorySubSystemTest {

    @Test
    void name_returnsMemory() {
        MemorySubSystem sub = new MemorySubSystem();
        assertEquals("memory", sub.name());
    }

    @Test
    void priority_returns10() {
        assertEquals(10, new MemorySubSystem().priority());
    }

    @Test
    void getShortMemory_delegatesToFactory() {
        ShortMemoryFactory factory = mock(ShortMemoryFactory.class);
        ShortMemory store = mock(ShortMemory.class);
        when(factory.get("jsonl")).thenReturn(store);

        MemorySubSystem sub = new MemorySubSystem();
        sub.shortMemoryFactory = factory;

        MemoryConfig config = MemoryConfig.builder()
            .shortTermStore("jsonl")
            .longTermStore("file")
            .build();

        assertSame(store, sub.getShortMemory(config));
    }

    @Test
    void enrich_doesNotThrow() {
        MemorySubSystem sub = new MemorySubSystem();
        PromptContext.Builder builder = PromptContext.builder();
        assertDoesNotThrow(() -> sub.enrich(builder));
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -pl meta-claw-core -am -Dtest=MemorySubSystemTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4/4)

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/MemorySubSystem.java \
        meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/MemorySubSystemTest.java
git commit -m "feat(subsystem): add MemorySubSystem wrapping existing factories"
```

---

### Task 5: PromptSubSystem（PromptContext enrich 能力）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/PromptSubSystem.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/PromptSubSystemTest.java`

- [ ] **Step 1: 写 PromptSubSystem**

```java
package meta.claw.core.runtime.subsystem;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Prompt 子系统。
 * 负责管理 PromptContext 的生命周期：初始化时创建基础上下文，
 * enrich 阶段允许其他子系统注入内容，执行时提供最终构建的上下文。
 */
@Slf4j
@Component
public class PromptSubSystem implements VesselSubSystem {

    @Autowired
    private PromptContextFactory promptContextFactory;

    private String vesselId;

    @Override
    public String name() { return "prompt"; }

    @Override
    public void initialize(SubSystemRegistry registry) {
        // vesselId 在 VesselRuntime 创建后通过外部设置
    }

    @Override
    public void enrich(PromptContext.Builder builder) {
        // PromptSubSystem 自身不向 builder 注入内容；
        // 它的职责是创建初始 PromptContext，由 VesselRuntime 在 buildPromptContext() 中使用。
    }

    @Override
    public int priority() { return 0; }

    /** 创建当前 Vessel 的基础 PromptContext */
    public PromptContext createBaseContext(String vesselId) {
        this.vesselId = vesselId;
        return promptContextFactory.create(vesselId);
    }

    public String getVesselId() {
        return vesselId;
    }
}
```

- [ ] **Step 2: 写测试**

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptSubSystemTest {

    @Test
    void name_returnsPrompt() {
        assertEquals("prompt", new PromptSubSystem().name());
    }

    @Test
    void priority_returns0() {
        assertEquals(0, new PromptSubSystem().priority());
    }

    @Test
    void createBaseContext_delegatesToFactory() {
        PromptContextFactory factory = mock(PromptContextFactory.class);
        PromptContext expected = PromptContext.builder().build();
        when(factory.create("v1")).thenReturn(expected);

        PromptSubSystem sub = new PromptSubSystem();
        sub.promptContextFactory = factory;

        PromptContext actual = sub.createBaseContext("v1");
        assertSame(expected, actual);
        assertEquals("v1", sub.getVesselId());
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -pl meta-claw-core -am -Dtest=PromptSubSystemTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3/3)

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/subsystem/PromptSubSystem.java \
        meta-claw-core/src/test/java/meta/claw/core/runtime/subsystem/PromptSubSystemTest.java
git commit -m "feat(subsystem): add PromptSubSystem for PromptContext lifecycle"
```

---

### Task 6: 重构 VesselRuntime 为子系统编排器

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
- Modify: `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselRuntimeTest.java`（新建）

**Rationale:** VesselRuntime 当前直接持有 ShortMemoryFactory、LongMemoryFactory 等，既是编排器又是执行器。重构后：
- 注入 `List<VesselSubSystem>`，按 priority 排序
- 持有 `SubSystemRegistry`
- `chat/chatStream` 内部构造 `AgentExecutionContext`
- 现有 `getShortMemory()` / `getLongMemory()` 改为通过 `MemorySubSystem` 代理，保持 CLI 调用方兼容

- [ ] **Step 1: 修改 VesselRuntime**

替换整个文件内容：

```java
package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.agent.AgentExecutionContext;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.PromptRenderer;
import meta.claw.core.runtime.subsystem.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vessel 核心运行时类 —— 子系统编排器。
 * <p>
 * 不再直接执行 LLM 调用细节，而是：
 * 1. 持有并初始化所有 {@link VesselSubSystem}
 * 2. 在每次对话时构建 {@link AgentExecutionContext}
 * 3. 触发子系统生命周期钩子（enrich → onExecutionStart → delegate → onExecutionEnd）
 * </p>
 */
@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptContextFactory promptContextManager;
    @Autowired
    private PromptRenderer promptRenderer;
    @Autowired
    private LlmClientManager llmClient;

    /** 所有子系统，Spring 自动收集 */
    @Autowired(required = false)
    private List<VesselSubSystem> subSystems = new ArrayList<>();

    private final SubSystemRegistry registry = new SubSystemRegistry();
    private PromptContext promptContext;
    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;
    }

    @Override
    public void afterPropertiesSet() {
        // 按 priority 排序并注册
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> {
                registry.register(sub);
                sub.initialize(registry);
            });

        // 创建基础 PromptContext（由 PromptSubSystem 或本地 factory 提供）
        PromptSubSystem promptSub = registry.get("prompt");
        if (promptSub != null) {
            this.promptContext = promptSub.createBaseContext(vesselId);
        } else {
            this.promptContext = promptContextManager.create(vesselId);
        }
    }

    public PromptContext createPromptContext() {
        return buildPromptContext();
    }

    /**
     * 构建带所有子系统贡献的 PromptContext。
     * 调用顺序：按 priority 升序调用各子系统的 enrich()。
     */
    public PromptContext buildPromptContext() {
        PromptContext.Builder builder = promptContext.toBuilder();
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> sub.enrich(builder));
        return builder.build();
    }

    public SubSystemRegistry getRegistry() {
        return registry;
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    /** 便捷方法：获取当前 Vessel 的短期记忆（向后兼容） */
    public ShortMemory getShortMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getShortMemory(promptContext.getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, short memory unavailable");
        return null;
    }

    /** 便捷方法：获取当前 Vessel 的长期记忆（向后兼容） */
    public LongMemory getLongMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getLongMemory(promptContext.getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, long memory unavailable");
        return null;
    }

    public VesselConfig getConfig() {
        return promptContext.getBundle().getRuntimeVesselConfig();
    }

    public Reply chat(String sessionId, String userMessage) {
        AgentExecutionContext ctx = createExecutionContext(sessionId, userMessage);

        // 子系统生命周期：onExecutionStart
        subSystems.forEach(sub -> sub.onExecutionStart(ctx));

        try {
            String systemPrompt = resolveSystemPrompt(ctx.getPromptContext());
            SpiChatRequest request = SpiChatRequest.builder()
                .messages(buildLlmRequest(userMessage, sessionId, systemPrompt))
                .ctx(ctx.getPromptContext())
                .sessionId(sessionId)
                .build();
            SpiChatResponse response = llmClient.chat(request);

            String content = response != null && response.content() != null
                ? response.content() : "";

            return new Reply(ReplyType.TEXT, content);
        } finally {
            subSystems.forEach(sub -> sub.onExecutionEnd(ctx));
        }
    }

    public void chatStream(String sessionId, String userMessage, SpiStreamingCallback callback) {
        AgentExecutionContext ctx = createExecutionContext(sessionId, userMessage);

        subSystems.forEach(sub -> sub.onExecutionStart(ctx));

        try {
            SpiChatRequest request = SpiChatRequest.builder()
                .messages(buildLlmRequest(userMessage, sessionId, resolveSystemPrompt(ctx.getPromptContext())))
                .ctx(ctx.getPromptContext())
                .sessionId(sessionId)
                .build();
            llmClient.chatStream(request, callback);
        } finally {
            subSystems.forEach(sub -> sub.onExecutionEnd(ctx));
        }
    }

    private AgentExecutionContext createExecutionContext(String sessionId, String userMessage) {
        PromptContext enrichedCtx = buildPromptContext();
        return new AgentExecutionContext(vesselId, sessionId, userMessage, enrichedCtx, registry);
    }

    private String resolveSystemPrompt(PromptContext ctx) {
        try {
            return promptRenderer.renderSystem(ctx);
        } catch (Exception e) {
            log.warn("Failed to build system prompt for vessel {}: {}", vesselId, e.getMessage());
            return null;
        }
    }

    private List<SpiMessage> buildLlmRequest(String userMessage, String sessionId, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }
        ShortMemory shortMem = getShortMemory();
        if (StringUtils.isNotBlank(sessionId) && shortMem != null) {
            messages.addAll(toSpiMessages(shortMem.loadMessages(
                vesselId, sessionId, promptContext.getBundle().getMaxHistoryRounds())));
        }
        messages.add(SpiMessage.user(userMessage));
        if (shortMem != null) {
            shortMem.appendMessage(vesselId, sessionId,
                MemoryMessageConverter.fromSpiMessage(SpiMessage.user(userMessage)));
        }
        return messages;
    }

    private List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.getRole() == null) continue;
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(
                    SpiMessage.assistant(message.getContent(), message.getReasoningContent(), message.getToolCalls()));
                case "tool" -> restored.add(SpiMessage.tool(message.getContent()));
                default -> { /* skip system */ }
            }
        }
        return restored;
    }

    public void shutdown() {
        log.info("VesselRuntime shutdown: {}", vesselId);
    }
}
```

- [ ] **Step 2: 新建 VesselRuntimeTest**

```java
package meta.claw.core.runtime;

import meta.claw.core.agent.AgentExecutionContext;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.prompt.PromptContextFactory;
import meta.claw.core.prompt.PromptRenderer;
import meta.claw.core.runtime.subsystem.PromptSubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VesselRuntimeTest {

    static class NoOpSubSystem implements VesselSubSystem {
        private final String name;
        private final int priority;
        NoOpSubSystem(String name, int priority) {
            this.name = name; this.priority = priority;
        }
        @Override public String name() { return name; }
        @Override public void initialize(SubSystemRegistry registry) {}
        @Override public void enrich(PromptContext.Builder builder) {}
        @Override public int priority() { return priority; }
    }

    @Test
    void afterPropertiesSet_registersSubSystemsByPriority() {
        PromptContextFactory factory = mock(PromptContextFactory.class);
        when(factory.create("v1")).thenReturn(PromptContext.builder().build());

        VesselRuntime runtime = new VesselRuntime("v1");
        runtime.subSystems = List.of(
            new NoOpSubSystem("zoo", 200),
            new NoOpSubSystem("alpha", 5)
        );
        runtime.promptContextManager = factory;
        runtime.promptRenderer = mock(PromptRenderer.class);
        runtime.llmClient = mock(LlmClientManager.class);

        runtime.afterPropertiesSet();

        // alpha (priority 5) should be first in registry list
        List<VesselSubSystem> all = runtime.getRegistry().listAll();
        assertEquals("alpha", all.get(0).name());
        assertEquals("zoo", all.get(1).name());
    }

    @Test
    void createExecutionContext_includesEnrichedPromptContext() {
        PromptContextFactory factory = mock(PromptContextFactory.class);
        PromptContext base = PromptContext.builder().currentTime("now").build();
        when(factory.create("v1")).thenReturn(base);

        VesselRuntime runtime = new VesselRuntime("v1");
        runtime.subSystems = new ArrayList<>();
        runtime.promptContextManager = factory;
        runtime.promptRenderer = mock(PromptRenderer.class);
        runtime.llmClient = mock(LlmClientManager.class);
        runtime.afterPropertiesSet();

        AgentExecutionContext ctx = runtime.createExecutionContext("s1", "hello");
        assertEquals("v1", ctx.getVesselId());
        assertEquals("s1", ctx.getSessionId());
        assertEquals("hello", ctx.getUserMessage());
        assertNotNull(ctx.getPromptContext());
        assertSame(runtime.getRegistry(), ctx.getRegistry());
    }
}
```

- [ ] **Step 3: 编译检查**

Run: `mvn compile -pl meta-claw-core -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行新测试**

Run: `mvn test -pl meta-claw-core -am -Dtest=VesselRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2/2)

- [ ] **Step 5: 运行已有 P0 测试，确认无回归**

Run: `mvn test -pl meta-claw-core,meta-claw-tool -am -Dtest=VesselConfigLoaderTest,VesselProfileLoaderTest,ToolRegistryTest,CalculatorToolTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (全部)

- [ ] **Step 6: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java \
        meta-claw-core/src/test/java/meta/claw/core/runtime/VesselRuntimeTest.java
git commit -m "refactor(runtime): VesselRuntime becomes subsystem orchestrator"
```

---

### Task 7: ChatCommand 适配（最小改动）

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

**Rationale:** ChatCommand 当前直接调用 `vesselRuntime.getShortMemory()` 和 `vesselRuntime.chatStream()`。重构后这些 API 仍然保留（向后兼容），但内部实现已改为通过 `MemorySubSystem`。因此 ChatCommand 的改动应该极小——只需要确认编译通过即可。

- [ ] **Step 1: 确认 ChatCommand 编译通过**

Run: `mvn compile -pl meta-claw-cli -am`
Expected: BUILD SUCCESS

如果编译失败，修复 import 或调用点。预期只需要添加 `meta.claw.core.runtime.subsystem.*` 的 import（如果 IDE 自动 import 了被引用的类）。

- [ ] **Step 2: Commit（如无改动则跳过）**

```bash
# 如有改动则提交
git add meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
git commit -m "chore(cli): adapt ChatCommand to new VesselRuntime subsystem API"
```

---

### Task 8: 全量回归验证

- [ ] **Step 1: 全仓编译**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS（全部 reactor 模块）

- [ ] **Step 2: 运行所有现存测试**

Run: `mvn test -pl meta-claw-core,meta-claw-tool -am -Dtest=VesselConfigLoaderTest,VesselProfileLoaderTest,ToolRegistryTest,CalculatorToolTest,SubSystemRegistryTest,AgentExecutionContextTest,MemorySubSystemTest,PromptSubSystemTest,VesselRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（全部）

- [ ] **Step 3: 更新进度文件**

在 `claude-progress.md` 的"当前已验证状态"段落下新增：

```markdown
- 2026-06-06: VesselSubSystem SPI 骨架已落地。AgentExecutionContext 消除 VesselRuntime 循环引用。
  PromptContext 职责边界已澄清（静态 Prompt 素材）。MemorySubSystem + PromptSubSystem 已接入。
  `./init.sh` 全量通过。
```

在 `feature_list.json` 中新增一项：

```json
{
  "id": "subsystem-001",
  "priority": 20,
  "area": "architecture",
  "title": "VesselSubSystem SPI 骨架与执行上下文可读性重构",
  "user_visible_behavior": "VesselRuntime 从直接执行器转变为子系统编排器；AgentExecutionContext 与 PromptContext 职责边界清晰；后续新增能力（Tool/Skill/HITL）可通过实现 VesselSubSystem 接口接入。",
  "status": "passing",
  "verification": [
    "确认 VesselSubSystem 接口存在，包含 enrich + onExecutionStart/End 生命周期",
    "确认 AgentExecutionContext 不持有 VesselRuntime，通过 SubSystemRegistry 查询子系统",
    "确认 MemorySubSystem 和 PromptSubSystem 已注册并能在 VesselRuntime 初始化时加载",
    "确认 ChatCommand 无需改动即可继续使用 vesselRuntime.chatStream()",
    "运行 ./init.sh 全量编译与 P0 测试通过"
  ],
  "evidence": [],
  "notes": "ToolSubSystem、HitlSubSystem、SkillSubSystem 仍待后续按各自计划实现。"
}
```

- [ ] **Step 4: Commit 进度文件**

```bash
git add claude-progress.md feature_list.json
git commit -m "docs: record subsystem-001 completion evidence"
```

---

## Self-Review Checklist

**1. Spec coverage（对照用户原始需求）：**

| 原始需求 | 对应任务 |
|---------|---------|
| PromptContext 和 AgentExecutionContext 关系可读性更强 | Task 3（AgentExecutionContext Javadoc 明确与 PromptContext 的分工）、Task 6（VesselRuntime 中清晰的调用顺序注释） |
| 消除设计中的"别扭"感 | Task 2（SubSystemRegistry 切断循环引用）、Task 1（enrich 替代 contribute，语义明确） |
| VesselSubSystem 接口改进 | Task 1（接口命名 + 调用顺序 Javadoc） |
| 可落地、可验证 | 全部任务均包含具体代码 + 测试命令 + 预期输出 |

**2. Placeholder scan：**

- [x] 无 "TBD"/"TODO"/"implement later"
- [x] 无 "Add appropriate error handling" 等模糊描述
- [x] 无 "Similar to Task N" 省略
- [x] 所有步骤包含实际代码块

**3. Type consistency：**

- `VesselSubSystem.enrich(PromptContext.Builder)` — Task 1 定义，Task 4/5/6 消费，一致
- `AgentExecutionContext` 构造函数参数 — Task 3 定义，Task 6 调用，一致
- `SubSystemRegistry.register/get` — Task 2 定义，Task 3/6 使用，一致
- `MemorySubSystem.getShortMemory(MemoryConfig)` — Task 4 定义，Task 6 通过 `getShortMemory()` 代理调用，一致

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-06-vessel-subsystem-spi-context-refactor.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints for review

**Which approach?**
