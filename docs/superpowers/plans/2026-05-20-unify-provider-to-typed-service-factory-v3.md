# 设计文档 v3：Store 单例化 + 接口参数收敛 + Spring Map 注入

> 目标：Store 实现改为无参构造 Spring 单例，运行时参数通过接口方法传入（vesselId 收敛），工厂通过 Spring `Map<String, Store>` 直接持有单例实例。

---

## 一、核心变更（相比 v2）

| v2 | v3 |
|---|---|
| Store prototype + 运行时构造参数 | **Store 无参构造 + Spring 单例** |
| 工厂 Map 存 beanName，用时 `getBean(...)` 创建 | **工厂 Map 直接存单例实例**（Spring `Map<String, Store>` 注入） |
| 接口方法签名不变 | **ShortMemoryStore 所有方法增加 `vesselId` 参数**（LongMemoryStore 已有） |

---

## 二、Store 改造

### 2.1 Bean 声明（无参构造 + 单例）

```java
@Component("jsonl")
public class JsonlShortMemoryStore implements ShortMemoryStore {
    // 无参构造，Spring 单例
}

@Component("file")
public class FileLongMemoryStore implements LongMemoryStore {
    // 无参构造，Spring 单例
}
```

### 2.2 内部路径计算

运行时参数（baseDir）不再通过构造传入，Store 内部通过 `vesselId` + `ProjectRootFinder` 计算：

```java
private Path resolveVesselDir(String vesselId) {
    return ProjectRootFinder.getMetaClawDir()
            .resolve("vessels")
            .resolve(vesselId);
}
```

### 2.3 ShortMemoryStore 接口增加 vesselId 参数

```java
public interface ShortMemoryStore {
    void appendMessage(String vesselId, String sessionKey, MemoryMessage message);
    void initializeConversation(String vesselId, String sessionKey);
    List<MemoryMessage> getHistory(String vesselId, String sessionKey);
    List<MemoryMessage> getHistory(String vesselId, String sessionKey, int limit);
    List<SessionMemory> listSessions(String vesselId);
    boolean clearHistory(String vesselId, String sessionKey);
    boolean conversationExists(String vesselId, String sessionKey);
    List<MemoryMessage> getHistoryByToken(String vesselId, String sessionKey, int maxTokens);
    SessionMemory loadSummary(String vesselId, String sessionKey);
    void saveSummary(String vesselId, String sessionKey, SessionMemory summary);
    String summarizeConversation(String vesselId, List<MemoryMessage> history);
}
```

> `LongMemoryStore` 接口所有方法已包含 `vesselId`，无需修改。

### 2.4 JsonlShortMemoryStore 状态调整

当前 `JsonlShortMemoryStore` 的字段：
- `Path baseDir` → **删除**，方法内按 vesselId 计算
- `String vesselId` → **删除**，从方法参数获取
- `Path sessionDir` → **删除**，方法内按 vesselId + sessionKey 计算
- `ObjectMapper objectMapper` → 保留（单例共用）
- `Map<String, ReentrantReadWriteLock> locks` → 保留（key 为 sessionKey，全局锁）

### 2.5 FileLongMemoryStore 状态调整

- `Path baseDir` → **删除**
- `Path preferencesFile` → **删除**，方法内按 vesselId 计算

---

## 三、领域工厂（完整实现）

Spring 直接注入 `Map<String, Store>`，key 为 bean name（即 type），value 为单例实例。

工厂内部封装"按配置选实现"和"按类型选实现"两种入口。

```java
@Component
public class ShortMemoryStoreFactory {
    private final Map<String, ShortMemoryStore> stores;

    public ShortMemoryStoreFactory(Map<String, ShortMemoryStore> stores) {
        this.stores = stores;
    }

    /**
     * 按配置自动选择实现（默认 "jsonl"）。
     */
    public ShortMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getShortTermStore() != null
                ? config.getShortTermStore() : "jsonl";
        return getStore(type);
    }

    /**
     * 按类型标识获取实现。
     */
    public ShortMemoryStore getStore(String type) {
        ShortMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No ShortMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
```

```java
@Component
public class LongMemoryStoreFactory {
    private final Map<String, LongMemoryStore> stores;

    public LongMemoryStoreFactory(Map<String, LongMemoryStore> stores) {
        this.stores = stores;
    }

    /**
     * 按配置自动选择实现（默认 "file"）。
     */
    public LongMemoryStore getStore(MemoryConfig config) {
        String type = config != null && config.getLongTermStore() != null
                ? config.getLongTermStore() : "file";
        return getStore(type);
    }

    /**
     * 按类型标识获取实现。
     */
    public LongMemoryStore getStore(String type) {
        LongMemoryStore store = stores.get(type);
        if (store == null) {
            throw new IllegalArgumentException(
                "No LongMemoryStore for type: " + type + ". Available: " + stores.keySet());
        }
        return store;
    }
}
```

---

## 四、Manager 层调整（完整实现）

Manager 改为单例，注入 Factory，方法调用时传入 `MemoryConfig` 由 Factory 选择对应 Store。

```java
@Component
public class ShortMemoryManager {
    private final ShortMemoryStoreFactory storeFactory;

    public ShortMemoryManager(ShortMemoryStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public void appendMessage(MemoryConfig config, String vesselId, String sessionKey, MemoryMessage message) {
        storeFactory.getStore(config).appendMessage(vesselId, sessionKey, message);
    }

    public void initializeConversation(MemoryConfig config, String vesselId, String sessionKey) {
        storeFactory.getStore(config).initializeConversation(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistory(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).getHistory(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistory(MemoryConfig config, String vesselId, String sessionKey, int limit) {
        return storeFactory.getStore(config).getHistory(vesselId, sessionKey, limit);
    }

    public List<SessionMemory> listSessions(String vesselId) {
        return storeFactory.getStore("jsonl").listSessions(vesselId);
    }

    public boolean clearHistory(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).clearHistory(vesselId, sessionKey);
    }

    public boolean conversationExists(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).conversationExists(vesselId, sessionKey);
    }

    public List<MemoryMessage> getHistoryByToken(MemoryConfig config, String vesselId, String sessionKey, int maxTokens) {
        return storeFactory.getStore(config).getHistoryByToken(vesselId, sessionKey, maxTokens);
    }

    public SessionMemory loadSummary(MemoryConfig config, String vesselId, String sessionKey) {
        return storeFactory.getStore(config).loadSummary(vesselId, sessionKey);
    }

    public void saveSummary(MemoryConfig config, String vesselId, String sessionKey, SessionMemory summary) {
        storeFactory.getStore(config).saveSummary(vesselId, sessionKey, summary);
    }

    public String summarizeConversation(MemoryConfig config, String vesselId, List<MemoryMessage> history) {
        return storeFactory.getStore(config).summarizeConversation(vesselId, history);
    }
}
```

```java
@Component
public class LongMemoryManager {
    private final LongMemoryStoreFactory storeFactory;

    public LongMemoryManager(LongMemoryStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
    }

    public void addPreference(MemoryConfig config, String vesselId, PreferenceMemory entry) {
        storeFactory.getStore(config).addPreference(vesselId, entry);
    }

    public List<PreferenceMemory> lookupPreference(MemoryConfig config, String vesselId, String query) {
        return storeFactory.getStore(config).lookupPreference(vesselId, query);
    }

    public List<PreferenceMemory> listRecentPreferences(MemoryConfig config, String vesselId, int limit) {
        return storeFactory.getStore(config).listRecentPreferences(vesselId, limit);
    }

    public boolean deletePreference(MemoryConfig config, String vesselId, String preferenceId) {
        return storeFactory.getStore(config).deletePreference(vesselId, preferenceId);
    }

    public boolean clearPreferences(MemoryConfig config, String vesselId) {
        return storeFactory.getStore(config).clearPreferences(vesselId);
    }
}
```

---

## 五、调用方使用方式（ChatCommand）

```java
@Component
@Command(name = "chat")
public class ChatCommand implements Runnable {
    private final ShortMemoryManager shortMemoryManager;
    private final LongMemoryManager longMemoryManager;
    private final PromptContextFactory contextFactory;
    private final ToolRegistry toolRegistry;
    // ...

    @Override
    public void run() {
        // ... 解析 vesselConfig ...

        // 单例 Manager 直接使用，每次调用传入 MemoryConfig
        shortMemoryManager.initializeConversation(vesselConfig.getMemory(), vesselName, sessionKey);

        // PromptContext 构建后补充 tools
        PromptContext baseCtx = contextFactory.create(vesselConfig);
        PromptContext promptContext = baseCtx.toBuilder()
                .tools(toolRegistry.getToolDefinitions())
                .build();
        String systemPrompt = promptBuilder.build(promptContext);

        // 后续对话循环
        shortMemoryManager.appendMessage(vesselConfig.getMemory(), vesselName, sessionKey, message);
        List<MemoryMessage> history = shortMemoryManager.getHistory(vesselConfig.getMemory(), vesselName, sessionKey);
    }
}
```

---

## 六、Provider 去除方案

| Provider | 替代方案 |
|---|---|
| `WorkspaceProvider` | 删除，逻辑内联到 `PromptContextFactory`（`ProjectRootFinder` 移入 core） |
| `ToolDefinitionProvider` | 删除，`ChatCommand` 直接注入 `ToolRegistry` |
| `PreferenceProvider` | 删除，逻辑内联到 `PromptContextFactory`（内部用 `LongMemoryStoreFactory`） |
| `MemoryManagerProvider` | 删除，`ChatCommand` / `SessionsCommand` 直接注入 `ShortMemoryManager` / `LongMemoryManager` 单例 |

---

## 七、PromptContextFactory

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
        LongMemoryStore store = longMemoryStoreFactory.getStore(config.getMemory());
        List<PreferenceMemory> prefs = store.listRecentPreferences(config.getId(), 10);
        if (prefs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PreferenceMemory p : prefs) {
            sb.append("- ").append(p.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private String detectLocation() {
        return ZoneId.systemDefault().getId();
    }

    private static String orDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
```

---

## 八、改动清单

### 接口变更

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `ShortMemoryStore` | 修改 | 所有方法增加 `vesselId` 参数 |
| 2 | `LongMemoryStore` | 不变 | 已有 `vesselId`，无需修改 |

### Store 实现改造

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 3 | `JsonlShortMemoryStore` | 修改 | 无参构造、单例、内部按 vesselId 算路径 |
| 4 | `FileLongMemoryStore` | 修改 | 无参构造、单例、内部按 vesselId 算路径 |

### 新增工厂

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 5 | `ShortMemoryStoreFactory` | 新增 | `Map<String, ShortMemoryStore>` 注入，含 `getStore(MemoryConfig)` 和 `getStore(String)` |
| 6 | `LongMemoryStoreFactory` | 新增 | `Map<String, LongMemoryStore>` 注入，含 `getStore(MemoryConfig)` 和 `getStore(String)` |

### Manager 改造

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 7 | `ShortMemoryManager` | 修改 | 去掉 `@Scope("prototype")`，注入 `ShortMemoryStoreFactory`，方法传入 `MemoryConfig` |
| 8 | `LongMemoryManager` | 修改 | 去掉 `implements LongMemoryStore`，去掉 `@Scope("prototype")`，注入 `LongMemoryStoreFactory`，方法传入 `MemoryConfig` |

### Provider 删除

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 9 | `WorkspaceProvider` | 删除 | 内联 |
| 10 | `MetaClawWorkspaceProvider` | 删除 | 内联 |
| 11 | `ToolDefinitionProvider` | 删除 | 直接依赖 ToolRegistry |
| 12 | `PreferenceProvider` | 删除 | 内联 |
| 13 | `LongMemoryPreferenceProvider` | 删除 | 内联 |
| 14 | `MemoryManagerProvider` | 删除 | 被取代 |

### PromptContextFactory & 周边

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 15 | `PromptContextFactory` | 修改 | 构造函数只留 `LongMemoryStoreFactory` |
| 16 | `PromptContext` | 修改 | `@Builder(toBuilder = true)` |
| 17 | `ProjectRootFinder` | 移动 | 从 vessel 移入 core |
| 18 | `VesselRuntime` | 修改 | 去掉 `ToolDefinitionProvider` |
| 19 | `ToolRegistry` | 修改 | 去掉 `implements ToolDefinitionProvider` |
| 20 | `ChatCommand` | 修改 | 直接注入 `ToolRegistry` + `ShortMemoryManager` / `LongMemoryManager` 单例 |
| 21 | `SessionsCommand` | 修改 | 适配 Manager 单例 |

### 测试适配

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 22 | `VesselManagerTest` | 修改 | 适配 PromptContextFactory |
| 23 | `LongMemoryPreferenceProviderTest` | 删除 | 对应类已删 |
| 24 | `ChatCommandTest` | 修改 | 适配新注入链 |
| 25 | `JsonlShortMemoryStoreTest` | 修改 | 适配 vesselId 参数 + 无参构造 |
| 26 | `FileLongMemoryStoreTest` | 修改 | 适配 vesselId 参数 + 无参构造 |
| 27 | `SystemPromptBuilderTest` 等 | 修改 | 如有影响 |

---

## 九、验证计划

1. `./init.sh` 编译全仓库通过
2. 全量测试通过
3. 新增 mock Store（`@Component("mock")`），验证工厂自动发现
