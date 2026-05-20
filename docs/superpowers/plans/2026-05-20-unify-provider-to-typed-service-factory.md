# 设计文档：统一 Provider 为 TypedService + 自动注册工厂模式

> 目标：去掉项目里所有 `*Provider` 接口，统一为"类型标识 + Spring 启动自动扫描注册 Map + 工厂按配置获取"的清晰架构。
> 原则：`PromptContextFactory.create(VesselConfig)` 保持单参数。

---

## 一、现状问题分析

### 1.1 Provider 泛滥，接口碎片化

当前仓库存在 3 个 Provider 接口，每个都是"一对一"（1 接口 → 1 实现），没有带来抽象价值，反而增加注入点：

| Provider 接口 | 实现类 | 所在模块 | 注入点 |
|---|---|---|---|
| `WorkspaceProvider` | `MetaClawWorkspaceProvider` | cli | `PromptContextFactory` |
| `ToolDefinitionProvider` | `ToolRegistry` | tool | `PromptContextFactory`, `VesselRuntime`, `ChatCommand` |
| `PreferenceProvider` | `LongMemoryPreferenceProvider` | core | `PromptContextFactory` |

这些 Provider 的问题：
- **没有多态价值**：每个接口只有 1 个实现，接口层是多余的间接。
- **增加依赖图复杂度**：`PromptContextFactory` 构造函数需要 3 个 Provider，调用链长。
- **跨模块耦合**：`ToolDefinitionProvider` 放在 `core` 模块，`ToolRegistry` 在 `tool` 模块，看似解耦实则增加接口维护成本。

### 1.2 Store 多实现无统一管理，导致 Spring 注入冲突

| Store 接口 | 实现类 | 问题 |
|---|---|---|
| `ShortMemoryStore` | `JsonlShortMemoryStore` | 目前 1 个实现，但未来可能扩展 redis/db |
| `LongMemoryStore` | `FileLongMemoryStore`, `LongMemoryManager` | **2 个实现都标了 `@Component`**，Spring 按接口注入时直接抛 `NoUniqueBeanDefinitionException` |

上一次的修复方案（`LongMemoryPreferenceProvider` 改为 POJO、本地 `new`）是**绕过问题**，没有解决根本：Spring 容器里同时存在多个同接口的 `@Component`，注入时必然冲突。

### 1.3 MemoryManagerProvider 手写 Map，扩展不封闭

```java
// MemoryManagerProvider.java（当前代码）
public ShortMemoryManager createShortTerm(...) {
    Map<String, ShortMemoryStore> stores = Map.of(
        "jsonl", jsonlShortMemoryStores.getObject(vesselsDir, vesselId)  // 硬编码
    );
    return shortMemoryManagers.getObject(config, stores);
}
```

新增一个 backend（如 `redis`）需要：
1. 新增 `RedisShortMemoryStore implements ShortMemoryStore`
2. 修改 `MemoryManagerProvider` 的 `Map.of()` 加一行
3. 修改 `MemoryConfig` 的默认值（如有）

违反开闭原则。

### 1.4 Manager 构造函数需要调用方手动构建 Map

```java
public ShortMemoryManager(MemoryConfig config, Map<String, ShortMemoryStore> stores)
public LongMemoryManager(MemoryConfig config, Map<String, LongMemoryStore> stores)
```

调用方（`MemoryManagerProvider`）需要知道所有实现类并手动组装 Map，Manager 本身应该只关心"按配置选 backend"。

---

## 二、目标架构

核心模式：**TypedService → ServiceRegistry（启动自动扫描）→ TypedServiceFactory（按配置获取）**

### 2.1 类型标识接口

```java
package meta.claw.core.spi;

public interface TypedService {
    String getServiceType();
}
```

### 2.2 Store 接口改为继承 TypedService

```java
public interface ShortMemoryStore extends TypedService {
    // 原有方法不变
}
```

```java
public interface LongMemoryStore extends TypedService {
    // 原有方法不变
}
```

### 2.3 每个实现类提供类型标识

```java
@Component
@Scope("prototype")
public class JsonlShortMemoryStore implements ShortMemoryStore {
    @Override
    public String getServiceType() { return "jsonl"; }
}
```

```java
@Component
@Scope("prototype")
public class FileLongMemoryStore implements LongMemoryStore {
    @Override
    public String getServiceType() { return "file"; }
}
```

### 2.4 关键决策：LongMemoryManager 不再实现 LongMemoryStore

`LongMemoryManager` 是编排器（delegate 模式），不是真正的存储后端。改造后：
- **去掉 `implements LongMemoryStore`**
- 构造函数简化为 `LongMemoryManager(LongMemoryStore store)`

这样 Spring 容器中只剩 `FileLongMemoryStore` 一个 `LongMemoryStore` bean，彻底消除注入冲突。

### 2.5 启动自动注册机制（ServiceRegistry）

利用 Spring 的 `@PostConstruct` + `ApplicationContext` 扫描，在应用启动时自动将所有 `TypedService` 实现按【接口类型 + 类型标识】注册到内部 Map。

```java
@Component
public class ServiceRegistry implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    // Map<接口类型, Map<类型标识, Bean名称>>
    private final Map<Class<?>, Map<String, String>> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, TypedService> beans = applicationContext.getBeansOfType(TypedService.class);
        for (Map.Entry<String, TypedService> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            TypedService service = entry.getValue();
            Class<?> serviceInterface = findTypedServiceInterface(service.getClass());
            if (serviceInterface != null) {
                registry.computeIfAbsent(serviceInterface, k -> new ConcurrentHashMap<>())
                        .put(service.getServiceType(), beanName);
            }
        }
    }

    public String resolveBeanName(Class<?> serviceType, String typeKey) {
        Map<String, String> map = registry.get(serviceType);
        if (map == null || !map.containsKey(typeKey)) {
            throw new IllegalArgumentException(
                "No " + serviceType.getSimpleName() + " for type: " + typeKey
                + ". Available: " + (map != null ? map.keySet() : "[]"));
        }
        return map.get(typeKey);
    }

    @SuppressWarnings("unchecked")
    public <T> T createBean(String beanName, Object... args) {
        return (T) applicationContext.getBean(beanName, args);
    }

    private static Class<?> findTypedServiceInterface(Class<?> clazz) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (TypedService.class.isAssignableFrom(iface) && iface != TypedService.class) {
                return iface;
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findTypedServiceInterface(clazz.getSuperclass());
        }
        return null;
    }
}
```

**关键点**：
- 不依赖 bean name，依赖的是实现类自己声明的 `getServiceType()`。
- 新增 backend 只需新增一个 `implements TypedService` + `@Component` 的类，**零配置自动注册**。
- `createBean(beanName, args...)` 利用 Spring 的 `getBean(String, Object...)`，支持 prototype bean 带运行时构造参数。

### 2.6 具体工厂（按配置 + 运行时参数获取实现）

```java
@Component
public class ShortMemoryStoreFactory {
    private final ServiceRegistry registry;
    private final ApplicationContext applicationContext;

    public ShortMemoryStore create(MemoryConfig config, Path baseDir, String vesselId) {
        String type = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        String beanName = registry.resolveBeanName(ShortMemoryStore.class, type);
        return (ShortMemoryStore) applicationContext.getBean(beanName, baseDir, vesselId);
    }
}
```

```java
@Component
public class LongMemoryStoreFactory {
    private final ServiceRegistry registry;
    private final ApplicationContext applicationContext;

    public LongMemoryStore create(MemoryConfig config, Path baseDir) {
        String type = config != null && config.getLongTermStore() != null
                ? config.getLongTermStore() : "file";
        String beanName = registry.resolveBeanName(LongMemoryStore.class, type);
        return (LongMemoryStore) applicationContext.getBean(beanName, baseDir);
    }
}
```

### 2.7 Manager 简化

Manager 不再需要调用方传入 `Map<String, Store>`，直接接收 Store 实例：

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

### 2.8 去掉 MemoryManagerProvider

`MemoryManagerProvider` 的职责（按配置组装 Manager）已经被 Factory + `ObjectProvider<Manager>` 取代，直接删除。

调用方改为：
```java
// 先用 Factory 创建 Store（含运行时参数）
ShortMemoryStore store = shortMemoryStoreFactory.create(config, vesselsDir, vesselId);
// 再用 ObjectProvider 创建 Manager
ShortMemoryManager manager = shortMemoryManagers.getObject(store);
```

---

## 三、Provider 去除方案

### 3.1 WorkspaceProvider → 直接内联

`WorkspaceProvider` 只有 `MetaClawWorkspaceProvider` 一个实现，且路径规则固定。

方案：删除接口和实现类，逻辑直接内联到 `PromptContextFactory`。

```java
private Path resolveWorkspaceDir(VesselConfig config) {
    if (config.getId() == null) {
        return Path.of(".");
    }
    return ProjectRootFinder.getMetaClawDir()
            .resolve("vessels")
            .resolve(config.getId())
            .resolve("workspace");
}
```

> 依赖处理：`ProjectRootFinder` 需要从 `meta-claw-vessel` 模块移到 `meta-claw-core`（纯工具类，无 vessel 特有逻辑）。

### 3.2 ToolDefinitionProvider → 调用方直接处理

`ToolDefinitionProvider` 只有一个实现 `ToolRegistry`。`PromptContextFactory` 和 `VesselRuntime`（core 模块）不能依赖 `ToolRegistry`（tool 模块）。

方案：
1. `PromptContextFactory` 不再处理 tools，`PromptContext.tools` 留空列表
2. `PromptContext` 添加 `@Builder(toBuilder = true)`，支持调用方补充字段
3. `ChatCommand`（cli 模块，可依赖 tool）直接注入 `ToolRegistry`，构建后补充 tools：

```java
PromptContext baseCtx = contextFactory.create(vesselConfig);
PromptContext promptContext = baseCtx.toBuilder()
        .tools(toolRegistry.getToolDefinitions())
        .build();
```

4. `VesselRuntime` 暂时不包含 tools（当前也是可选渲染，不影响功能）

### 3.3 PreferenceProvider → 内联到 PromptContextFactory

`PreferenceProvider` 只有 `LongMemoryPreferenceProvider` 一个实现。逻辑是"通过 LongMemoryStore 查询偏好并格式化"。

方案：删除接口和实现类，逻辑直接内联到 `PromptContextFactory`：

```java
private String loadPreferences(VesselConfig config) {
    if (!config.isPreferencesEnabled() || config.getId() == null) {
        return "";
    }
    Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");
    LongMemoryStore store = longMemoryStoreFactory.create(config.getMemory(), vesselsDir);
    List<PreferenceMemory> prefs = store.listRecentPreferences(config.getId(), 10);
    if (prefs.isEmpty()) {
        return "";
    }
    StringBuilder sb = new StringBuilder();
    for (PreferenceMemory p : prefs) {
        sb.append("- ").append(p.getContent()).append("\n");
    }
    return sb.toString().trim();
}
```

---

## 四、最终 PromptContextFactory

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

    private Path resolveWorkspaceDir(VesselConfig config) { ... }
    private String loadPreferences(VesselConfig config) { ... }
    private String formatCurrentTime() { ... }
    private String detectLocation() { ... }
    private static String orDefault(String value, String defaultValue) { ... }
}
```

---

## 五、具体改动清单

### meta-claw-core

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `meta/claw/core/spi/TypedService.java` | 新增 | 类型标识接口 |
| 2 | `meta/claw/core/registry/ServiceRegistry.java` | 新增 | 启动自动扫描注册 |
| 3 | `meta/claw/core/memory/shortterm/ShortMemoryStore.java` | 修改 | `extends TypedService` |
| 4 | `meta/claw/core/memory/longterm/LongMemoryStore.java` | 修改 | `extends TypedService` |
| 5 | `meta/claw/core/memory/shortterm/ShortMemoryManager.java` | 修改 | 构造函数改为 `(ShortMemoryStore store)` |
| 6 | `meta/claw/core/memory/longterm/LongMemoryManager.java` | 修改 | 去掉 `implements LongMemoryStore`，构造函数改为 `(LongMemoryStore store)` |
| 7 | `meta/claw/core/memory/shortterm/ShortMemoryStoreFactory.java` | 新增 | 工厂 |
| 8 | `meta/claw/core/memory/longterm/LongMemoryStoreFactory.java` | 新增 | 工厂 |
| 9 | `meta/claw/core/prompt/PreferenceProvider.java` | 删除 | 接口废弃 |
| 10 | `meta/claw/core/prompt/LongMemoryPreferenceProvider.java` | 删除 | 逻辑内联到 PromptContextFactory |
| 11 | `meta/claw/core/spi/workspace/WorkspaceProvider.java` | 删除 | 接口废弃 |
| 12 | `meta/claw/core/spi/tool/ToolDefinitionProvider.java` | 删除 | 接口废弃 |
| 13 | `meta/claw/core/prompt/PromptContextFactory.java` | 修改 | 构造函数只留 `LongMemoryStoreFactory`，内联 workspace 和 preferences |
| 14 | `meta/claw/core/prompt/PromptContext.java` | 修改 | `@Builder(toBuilder = true)` |
| 15 | `meta/claw/core/runtime/VesselRuntime.java` | 修改 | 去掉 `ToolDefinitionProvider` 注入和字段 |
| 16 | `meta/claw/core/util/ProjectRootFinder.java` | 移动 | 从 vessel 模块移入 core |

### meta-claw-store

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 17 | `meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java` | 修改 | 添加 `getServiceType() { return "jsonl"; }` |
| 18 | `meta/claw/store/memory/longterm/FileLongMemoryStore.java` | 修改 | 添加 `getServiceType() { return "file"; }` |
| 19 | `meta/claw/store/memory/MemoryManagerProvider.java` | 删除 | 被取代 |

### meta-claw-cli

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 20 | `meta/claw/cli/workspace/MetaClawWorkspaceProvider.java` | 删除 | 实现废弃 |
| 21 | `meta/claw/cli/ChatCommand.java` | 修改 | 注入链改为 Factory + ObjectProvider + ToolRegistry |
| 22 | `meta/claw/cli/SessionsCommand.java` | 修改 | 适配 Manager 创建方式 |

### meta-claw-tool

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 23 | `meta/claw/tool/registry/ToolRegistry.java` | 修改 | 去掉 `implements ToolDefinitionProvider` |

### 测试文件

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 24 | `meta/claw/core/runtime/VesselManagerTest.java` | 修改 | 适配 PromptContextFactory 新构造函数 |
| 25 | `meta/claw/core/prompt/LongMemoryPreferenceProviderTest.java` | 删除 | 对应类已删除 |
| 26 | `meta/claw/cli/ChatCommandTest.java` | 修改 | 适配新注入链 |
| 27 | `meta/claw/store/memory/...`（相关测试） | 修改 | 适配 Manager 构造函数变化 |

---

## 六、模块依赖调整

### 6.1 ProjectRootFinder 移动

`ProjectRootFinder` 从 `meta-claw-vessel` 移到 `meta-claw-core` 的 `meta.claw.core.util` 包下。`meta-claw-vessel` 中引用它的地方改为从 core 引入（vessel 已依赖 core，POM 无需改动）。

### 6.2 core 不新增对 tool 的依赖

`PromptContextFactory` 不再处理 tools，`VesselRuntime` 也不直接引用 `ToolRegistry`。tools 由上层模块（cli、bootstrap）处理，core 保持对 tool 的零依赖。

---

## 七、风险与注意事项

1. **Spring prototype bean + 运行时参数**：`ServiceRegistry.createBean(beanName, args...)` 依赖 Spring 的 `BeanFactory.getBean(String, Object...)`。需要验证在 `prototype` scope 下是否能正确传入构造参数。`FileLongMemoryStore(Path)` 和 `JsonlShortMemoryStore(Path, String)` 的构造参数需要被 Spring 正确解析。

2. **Manager 构造函数变化影响所有调用点**：`ChatCommand`、`SessionsCommand`、`VesselManagerTest` 等都需要更新。

3. **`ToolRegistry` 去掉 `ToolDefinitionProvider` 后跨模块引用**：`ChatCommand` 在 cli 模块可以直接注入 `ToolRegistry`，但 `VesselRuntime` 在 core 模块不能。当前 `VesselRuntime` 的 system prompt 将暂时不包含 tools（不影响现有功能，因为当前 tools 也是可选渲染）。

4. **测试影响面大**：涉及 core、store、cli、tool 四个模块的测试，需要全量回归。

---

## 八、验证计划

1. 编译全仓库：`./init.sh` 编译阶段通过
2. 全量测试：core / store / cli / bootstrap / tool 全部通过
3. 手动验证 `ChatCommand` 的会话初始化流程正常
4. 验证新增 mock backend 能否被 ServiceRegistry 自动发现（无需改注册代码）
