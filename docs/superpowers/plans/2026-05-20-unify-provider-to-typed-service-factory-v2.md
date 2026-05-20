# 设计文档 v2：领域工厂自维护 Map 模式（去 TypedService / 去全局注册表）

> 目标：去掉 `TypedService` 全局接口和 `ServiceRegistry` 全局注册表，每个领域工厂自己维护自己的 `Map<type, 实现>`，Spring 启动时自动收集。
> 原则：`PromptContextFactory.create(VesselConfig)` 保持单参数。

---

## 一、核心变更（相比 v1）

| v1 方案 | v2 方案 | 原因 |
|---|---|---|
| 全局 `TypedService` 接口 | **删除** | 用户要求不需要统一类型标识接口 |
| 全局 `ServiceRegistry` | **删除** | 用户要求每个工厂自己维护 Map，不搞全局注册表 |
| 工厂通过 `getServiceType()` 查类型 | 工厂通过 **bean name / 领域注解** 查类型 | 直接利用 Spring 已有机制 |
| 一个 `ServiceRegistry` 管所有领域 | **每个领域一个 Factory**，各自扫描、各自管理 | 领域隔离，互不干扰 |

---

## 二、核心机制

### 2.1 Bean 命名即类型标识

每个 Store 实现通过 `@Component("type")` 声明自己的领域类型：

```java
@Component("jsonl")
@Scope("prototype")
public class JsonlShortMemoryStore implements ShortMemoryStore { }

@Component("file")
@Scope("prototype")
public class FileLongMemoryStore implements LongMemoryStore { }
```

> **LongMemoryManager 不再 `implements LongMemoryStore`**（编排器不是 backend，去掉后容器中只剩 `file` 一个 `LongMemoryStore`，注入冲突根因消除）。

### 2.2 领域工厂自维护 Map

每个领域（short-term / long-term）有自己的工厂，**自己扫描、自己管理 Map**。

```java
@Component
public class ShortMemoryStoreFactory {
    private final Map<String, String> storeMap = new HashMap<>();  // type -> beanName
    private final ApplicationContext applicationContext;

    public ShortMemoryStoreFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        // 自己扫描自己领域的所有实现，按 bean name（即 type）注册
        for (String beanName : applicationContext.getBeanNamesForType(ShortMemoryStore.class)) {
            storeMap.put(beanName, beanName);
        }
    }

    public ShortMemoryStore create(String type, Path baseDir, String vesselId) {
        String beanName = storeMap.get(type);
        if (beanName == null) {
            throw new IllegalArgumentException(
                "No ShortMemoryStore for type: " + type + ". Available: " + storeMap.keySet());
        }
        // prototype + 运行时构造参数
        return (ShortMemoryStore) applicationContext.getBean(beanName, baseDir, vesselId);
    }
}
```

```java
@Component
public class LongMemoryStoreFactory {
    private final Map<String, String> storeMap = new HashMap<>();
    private final ApplicationContext applicationContext;

    public LongMemoryStoreFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        for (String beanName : applicationContext.getBeanNamesForType(LongMemoryStore.class)) {
            storeMap.put(beanName, beanName);
        }
    }

    public LongMemoryStore create(String type, Path baseDir) {
        String beanName = storeMap.get(type);
        if (beanName == null) {
            throw new IllegalArgumentException(
                "No LongMemoryStore for type: " + type + ". Available: " + storeMap.keySet());
        }
        return (LongMemoryStore) applicationContext.getBean(beanName, baseDir);
    }
}
```

**关键点**：
- **没有全局接口、没有全局注册表**，每个工厂独立。
- **Map 中存的是 bean name**，不是实例。查询时才通过 `getBean(beanName, args...)` 创建 prototype 实例，支持运行时构造参数。
- **新增 backend = 新增一个 `@Component("type")` + `implements ShortMemoryStore`**，工厂零改动自动发现。

### 2.3 为什么不用 `Map<String, Store>` 注入？

Spring 支持 `Map<String, ShortMemoryStore>` 构造注入：

```java
public ShortMemoryStoreFactory(Map<String, ShortMemoryStore> stores) { ... }
```

但当前 Store 实现是 **prototype + 需要运行时构造参数**（`Path baseDir`, `String vesselId`）。Spring 在注入 `Map<String, T>` 时会**立即实例化所有 bean**，而 `Path` 不是 Spring 管理的 bean，注入会直接报错。

所以 v2 方案改用 `ApplicationContext.getBeanNamesForType()` 扫描 bean name，需要实例时再 `getBean(beanName, args...)` 创建。

### 2.4 Manager 简化

Manager 不再接收 `Map<String, Store>`，直接接收 Store 实例：

```java
@Component
@Scope("prototype")
public class ShortMemoryManager {
    private final ShortMemoryStore store;

    public ShortMemoryManager(ShortMemoryStore store) {
        this.store = store;
    }
    // delegate 方法不变
}
```

```java
@Component
@Scope("prototype")
public class LongMemoryManager {
    private final LongMemoryStore store;

    public LongMemoryManager(LongMemoryStore store) {
        this.store = store;
    }
    // delegate 方法不变
}
```

### 2.5 调用方（以 ChatCommand 为例）

```java
// 1. 用 Factory 创建 Store（传入运行时参数）
ShortMemoryStore store = shortMemoryStoreFactory.create("jsonl", vesselsDir, vesselName);
// 2. 用 ObjectProvider 创建 Manager（传入 Store 实例）
this.shortMemoryManager = shortMemoryManagers.getObject(store);
```

---

## 三、Provider 去除方案（同 v1）

| Provider | 替代方案 |
|---|---|
| `WorkspaceProvider` | 删除，逻辑内联到 `PromptContextFactory`（`ProjectRootFinder` 移入 core） |
| `ToolDefinitionProvider` | 删除，`ChatCommand` 直接注入 `ToolRegistry`，`VesselRuntime` 不渲染 tools |
| `PreferenceProvider` | 删除，逻辑内联到 `PromptContextFactory`（内部用 `LongMemoryStoreFactory`） |
| `MemoryManagerProvider` | 删除，调用方直接用 `Factory + ObjectProvider<Manager>` |

---

## 四、PromptContextFactory 最终形态

```java
@Component
public class PromptContextFactory {
    private final LongMemoryStoreFactory longMemoryStoreFactory;

    public PromptContextFactory(LongMemoryStoreFactory longMemoryStoreFactory) {
        this.longMemoryStoreFactory = longMemoryStoreFactory;
    }

    public PromptContext create(VesselConfig config) {
        Path workspaceDir = resolveWorkspaceDir(config);

        return PromptContext.builder()
                .vesselName(orDefault(config.getName(), "Vessel"))
                .vesselDescription(orDefault(config.getDescription(), ""))
                .identity(orDefault(config.getIdentity(), ""))
                .soul(orDefault(config.getSoul(), ""))
                .capabilities(orDefault(config.getCapabilities(), ""))
                .guidelines(orDefault(config.getGuidelines(), ""))
                .knowledge(orDefault(config.getDomainKnowledge(), ""))
                .preferences(loadPreferences(config))
                .workspaceDir(workspaceDir)
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .runtimeInfo(Collections.emptyMap())
                .tools(Collections.emptyList())
                .build();
    }

    private Path resolveWorkspaceDir(VesselConfig config) {
        if (config.getId() == null) return Path.of(".");
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(config.getId())
                .resolve("workspace");
    }

    private String loadPreferences(VesselConfig config) {
        if (!config.isPreferencesEnabled() || config.getId() == null) return "";
        Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");
        LongMemoryStore store = longMemoryStoreFactory.create(config.getMemory().getLongTermStore(), vesselsDir);
        List<PreferenceMemory> prefs = store.listRecentPreferences(config.getId(), 10);
        // 格式化为字符串...
    }
}
```

---

## 五、改动清单

### 新增（4 个文件）

| # | 文件 | 说明 |
|---|---|---|
| 1 | `meta/claw/core/memory/shortterm/ShortMemoryStoreFactory.java` | 领域工厂，自维护 Map |
| 2 | `meta/claw/core/memory/longterm/LongMemoryStoreFactory.java` | 领域工厂，自维护 Map |
| 3 | `meta/claw/core/util/ProjectRootFinder.java` | 从 vessel 模块移入 |

### 修改（10 个文件）

| # | 文件 | 改动 |
|---|---|---|
| 4 | `JsonlShortMemoryStore` | 确认 bean name 为 `"jsonl"`（`@Component("jsonl")`） |
| 5 | `FileLongMemoryStore` | 确认 bean name 为 `"file"`（`@Component("file")`） |
| 6 | `ShortMemoryManager` | 构造函数改为 `(ShortMemoryStore store)` |
| 7 | `LongMemoryManager` | 去掉 `implements LongMemoryStore`，构造函数改为 `(LongMemoryStore store)` |
| 8 | `PromptContextFactory` | 构造函数只留 `LongMemoryStoreFactory`，内联 workspace/preferences |
| 9 | `PromptContext` | `@Builder(toBuilder = true)` |
| 10 | `VesselRuntime` | 去掉 `ToolDefinitionProvider` |
| 11 | `ToolRegistry` | 去掉 `implements ToolDefinitionProvider` |
| 12 | `ChatCommand` | 注入链改为 Factory + ObjectProvider + ToolRegistry |
| 13 | `SessionsCommand` | 适配 Manager 创建方式 |

### 删除（7 个文件）

| # | 文件 | 原因 |
|---|---|---|
| 14 | `TypedService`（v1 新增，v2 不需要） | 无全局类型接口 |
| 15 | `ServiceRegistry`（v1 新增，v2 不需要） | 无全局注册表 |
| 16 | `WorkspaceProvider` | 内联 |
| 17 | `MetaClawWorkspaceProvider` | 内联 |
| 18 | `ToolDefinitionProvider` | 直接依赖 ToolRegistry |
| 19 | `PreferenceProvider` | 内联 |
| 20 | `LongMemoryPreferenceProvider` | 内联 |
| 21 | `MemoryManagerProvider` | 被 Factory + ObjectProvider 取代 |

### 测试适配（4 个文件）

| # | 文件 | 说明 |
|---|---|---|
| 22 | `VesselManagerTest` | 适配 PromptContextFactory 构造函数 |
| 23 | `LongMemoryPreferenceProviderTest` | 删除 |
| 24 | `ChatCommandTest` | 适配新注入链 |
| 25 | `Store 相关测试` | 适配 Manager 构造函数变化 |

---

## 六、验证计划

1. `./init.sh` 编译全仓库通过
2. 全量测试通过（core / store / cli / bootstrap / tool）
3. 新增 mock Store 实现（`@Component("mock")`），验证工厂自动发现无需改注册代码
