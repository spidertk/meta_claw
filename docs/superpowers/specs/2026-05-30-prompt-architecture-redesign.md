# Prompt 架构重构设计：配置即 Prompt

> 目标：解决 `VesselMeta` / `PromptContext` 不内聚、`prompt` 包概念过载、渲染链路冗长的问题。

## 一、现状诊断

### 1.1 PromptContext 持有大量死字段

```java
public class PromptContext {
    private String vesselName;          // ✅ 来自 VesselMeta
    private String vesselDescription;   // ✅ 来自 VesselMeta
    private String identity;            // ❌ 从未被填充
    private String soul;                // ❌ 从未被填充
    private String capabilities;        // ❌ 从未被填充
    private String guidelines;          // ❌ 从未被填充
    private String knowledge;           // ❌ 从未被填充（Builder.Default=""）
    private VesselMeta vesselMeta;      // ✅ 整体持有
}
```

`identity/soul/capabilities/guidelines` 的**真实来源**是 `VesselProfile`（Markdown `## Identity` / `## Soul` 等段落），但 `PromptContextFactory` 从未加载 `VesselProfile`，这些字段永远是 `null`。

### 1.2 渲染链路概念过载

当前生成一条 prompt 需要经过 **6 个核心概念 + 4 个实现类**：

```
VesselMeta + GlobalConfig
    ↓ RuntimeConfigResolver.resolve()
RuntimeConfig
    ↓ PromptContextFactory.create()
PromptContext（DTO，5 个死字段）
    ↓ PromptRuntimeBuilder.build()
ResolutionContext（又一个 DTO，字段和 PromptContext 高度重叠）
    ↓ PromptAssembler.assembleSystem()/assembleContext()
加载模板文件 → 遍历 SectionRegistry 枚举
    ↓ 逐个匹配 SectionResolver.supports()
4 个 SectionResolver 实现
    ↓
字符串替换 <SECTION id="xxx"/>
```

**新增一个 section 的改动成本**：需要同时修改 `SectionRegistry` 枚举、`ProfileSectionResolver.SECTION_HEADING` Map、模板文件。三个地方必须对齐，否则静默失败。

### 1.3 SectionRegistry 的 Source/Target 是"注释型代码"

```java
META("meta", Source.VESSEL_META, Target.SYSTEM, true),
IDENTITY("identity", Source.VESSEL_PROFILE, Target.SYSTEM, true),
```

`Source`（VESSEL_META / VESSEL_PROFILE / RUNTIME / MEMORY）和 `Target`（SYSTEM / CONTEXT）虽然在枚举中声明，但**实际分发逻辑不依赖它们**。`PromptAssembler` 按 `Target` 过滤，`SectionResolver` 按 `supports()` 匹配，`Source` 枚举只是文档，不参与任何路由决策。

### 1.4 VesselMeta 和 PromptContext 的关系断裂

- `VesselMeta` 描述了"我是谁"（MetaInfo）、"我用什么模型"（LlmConfig）、"我有什么行为"（RuntimeConfig）
- `VesselProfile` 描述了"我怎么做"（Identity/Soul/Capabilities/Guidelines）
- `PromptContext` 只接了前者的一小部分，后者完全绕过它直接进 `ResolutionContext`

两个配置源 + 两个 Context 对象 = **读者无法一眼看出 prompt 到底由什么构成**。

---

## 二、设计目标

1. **单一事实来源**：Prompt 的所有内容只从 `VesselMeta` + `VesselProfile` + `RuntimeConfig` 推导，不引入第三份数据副本。
2. **配置自描述**：Vessel 配置模型知道自己如何渲染成 prompt，不需要外部注册表翻译。
3. **最少概念**：Prompt 渲染链路不超过 3 个核心类，新增 section 只改 1 个地方。
4. **消除死字段**：Context 对象中的每个字段都必须有真实数据源和实际消费方。

---

## 三、新架构设计

### 3.1 分层模型

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 0: 配置事实来源（Config Layer）                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  VesselMeta  │  │VesselProfile │  │ RuntimeConfig│      │
│  │  (YAML)      │  │  (Markdown)  │  │ (merged)     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                             │
│  VesselMeta    → 名称、描述、模型、记忆、工具等结构化元信息    │
│  VesselProfile → identity, soul, capabilities, guidelines   │
│  RuntimeConfig → 合并后的 provider + memory（运行时生效）    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 统一配置视图（Bundle Layer）                        │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              VesselConfigBundle                      │    │
│  │  ├─ vesselMeta: VesselMeta                          │    │
│  │  ├─ vesselProfile: VesselProfile                    │    │
│  │  ├─ runtimeConfig: RuntimeConfig                    │    │
│  │  └─ workspaceDir: Path                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  职责：把三个异构配置源封装为一个统一访问入口。               │
│  不复制数据，只提供便捷访问方法。                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Prompt 上下文（Context Layer）                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              PromptContext                           │    │
│  │  ├─ bundle: VesselConfigBundle                      │    │
│  │  ├─ currentTime: String                             │    │
│  │  └─ location: String                                │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  职责：Prompt 渲染的只读参数集合。                            │
│  删除所有死字段，所有访问都委托给 bundle。                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: 渲染引擎（Renderer Layer）                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              PromptRenderer                          │    │
│  │                                                     │    │
│  │  renderSystem(PromptContext)   → String             │    │
│  │  renderContext(PromptContext)  → String             │    │
│  │                                                     │    │
│  │  内部：按结构化模板拼接，不依赖外部注册表               │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心类设计

#### VesselConfigBundle（新增）

```java
@Getter
@Builder
public class VesselConfigBundle {
    private final VesselMeta vesselMeta;
    private final VesselProfile vesselProfile;
    private final RuntimeConfig runtimeConfig;
    private final Path workspaceDir;

    // 便捷访问方法：把分散在三处的配置聚合为 prompt 所需视图
    public String getVesselName() {
        return vesselMeta != null && vesselMeta.getMeta() != null
                ? vesselMeta.getMeta().getName()
                : "Vessel";
    }

    public String getVesselDescription() {
        return vesselMeta != null && vesselMeta.getMeta() != null
                ? vesselMeta.getMeta().getDescription()
                : "";
    }

    public String getIdentity() {
        return vesselProfile != null ? vesselProfile.getIdentity() : "";
    }

    public String getSoul() {
        return vesselProfile != null ? vesselProfile.getSoul() : "";
    }

    public String getCapabilities() {
        return vesselProfile != null ? vesselProfile.getCapabilities() : "";
    }

    public String getGuidelines() {
        return vesselProfile != null ? vesselProfile.getGuidelines() : "";
    }

    public String getDomainKnowledge() {
        return vesselProfile != null ? vesselProfile.getDomainKnowledge() : "";
    }

    public ProviderConfig getProviderConfig() {
        return runtimeConfig != null ? runtimeConfig.getProviderConfig() : null;
    }

    public MemoryConfig getMemoryConfig() {
        return runtimeConfig != null ? runtimeConfig.getMemoryConfig() : null;
    }
}
```

**设计意图**：
- `VesselMeta` 和 `VesselProfile` 是**物理存储模型**（YAML / Markdown 的结构映射）
- `VesselConfigBundle` 是**逻辑视图模型**（按 prompt 需要的语义聚合）
- 两者解耦：存储格式变化不影响 prompt 渲染逻辑

#### PromptContext（精简）

```java
@Getter
@Builder
public class PromptContext {
    private final VesselConfigBundle bundle;
    private final String currentTime;
    private final String location;

    // 委托方法，保持调用方代码简洁
    public String getVesselName() { return bundle.getVesselName(); }
    public String getIdentity()   { return bundle.getIdentity(); }
    public String getSoul()       { return bundle.getSoul(); }
    // ... 其他委托方法
}
```

**删除的字段**：`identity`, `soul`, `capabilities`, `guidelines`, `knowledge`, `vesselDescription`, `memoryConfig`, `providerConfig`, `vesselMeta`, `workspaceDir`。这些全部委托给 `bundle`。

#### PromptRenderer（合并 PromptAssembler + PromptRuntimeBuilder）

```java
@Component
public class PromptRenderer {

    private static final String SYSTEM_TEMPLATE = loadTemplate("/templates/runtime/system.tmpl.md");
    private static final String CONTEXT_TEMPLATE = loadTemplate("/templates/runtime/context.tmpl.md");

    public String renderSystem(PromptContext ctx) {
        return render(SYSTEM_TEMPLATE, ctx);
    }

    public String renderContext(PromptContext ctx) {
        return render(CONTEXT_TEMPLATE, ctx);
    }

    private String render(String template, PromptContext ctx) {
        // 直接占位符替换，无需注册表 / resolver 中间层
        return template
                .replace("{vessel_name}",        ctx.getVesselName())
                .replace("{vessel_description}", ctx.getVesselDescription())
                .replace("{identity}",           sectionOrEmpty(ctx.getIdentity(), "Identity"))
                .replace("{soul}",               sectionOrEmpty(ctx.getSoul(), "Soul"))
                .replace("{capabilities}",       sectionOrEmpty(ctx.getCapabilities(), "Capabilities"))
                .replace("{guidelines}",         sectionOrEmpty(ctx.getGuidelines(), "Guidelines"))
                .replace("{domain_knowledge}",   sectionOrEmpty(ctx.getDomainKnowledge(), "Domain Knowledge"))
                .replace("{workspace}",          workspaceSection(ctx))
                .replace("{current_time}",       ctx.getCurrentTime())
                .replace("{location}",           ctx.getLocation())
                .replace("{preferences}",        "")  // Phase 1: placeholder
                .trim();
    }

    private String sectionOrEmpty(String content, String heading) {
        if (content == null || content.isBlank()) return "";
        return "## " + heading + "\n\n" + content;
    }

    private String workspaceSection(PromptContext ctx) {
        Path dir = ctx.getBundle().getWorkspaceDir();
        if (dir == null) return "";
        return "## Workspace\n\nCurrent working directory: `" + dir + "`";
    }
}
```

**关键变化**：
1. **无注册表**：不再遍历 `SectionRegistry` 枚举，模板中的 `{xxx}` 直接对应 `VesselConfigBundle` 的方法
2. **无 resolver 链**：4 个 `SectionResolver` 的逻辑全部内联到 `render()` 方法中
3. **模板语法统一**：从 `<SECTION id="xxx"/>` 改为 `{xxx}`，与 `vessel.meta.tmpl.yaml` 的 `{name}` `{description}` 语法一致

#### 模板文件同步修改

`templates/runtime/system.tmpl.md`：
```markdown
# {vessel_name}

{vessel_description}

{identity}
{soul}
{capabilities}
{guidelines}
{domain_knowledge}
```

`templates/runtime/context.tmpl.md`：
```markdown
{workspace}

## Runtime Context

- Current Time: {current_time}
- Location: {location}

{preferences}
```

### 3.3 调用链路对比

**当前链路**（6 个核心概念）：
```
VesselRuntime
  → PromptContextFactory.create(vesselId)
    → RuntimeConfigResolver.resolve(vesselId)     // 合并全局+Vessel配置
    → PromptContext.builder()...build()            // 复制字段，5 个死字段
  → PromptRuntimeBuilder.build(ctx)
    → ResolutionContext.builder()...build()        // 再包一层 DTO
    → PromptAssembler.assembleSystem(resCtx)
      → loadTemplate(SYSTEM_TEMPLATE)
      → SectionRegistry.forTarget(SYSTEM)          // 遍历枚举
      → 对每个 section：遍历所有 resolver.supports()
      → 字符串替换 <SECTION id="xxx"/>
```

**新链路**（3 个核心概念）：
```
VesselRuntime
  → PromptContextFactory.create(vesselId)
    → RuntimeConfigResolver.resolve(vesselId)
    → VesselProfileLoader.load(vesselDir)          // 新增：加载 profile
    → VesselConfigBundle.builder()...build()       // 统一视图
    → PromptContext.builder()...build()            // 只读投影
  → PromptRenderer.renderSystem(ctx)
    → loadTemplate(SYSTEM_TEMPLATE)
    → 直接占位符替换 {vessel_name}, {identity}...
```

---

## 四、被删除的类清单

| 类 | 替代方案 | 删除理由 |
|---|---|---|
| `SectionRegistry` 枚举 | 无 | 注册表是中间层，配置模型自描述后不需要翻译表 |
| `SectionResolver` 接口 | 无 | 4 个实现全部内联到 `PromptRenderer` |
| `MetaSectionResolver` | `PromptRenderer` 内联 | 逻辑只有 10 行（取 meta.name + description） |
| `ProfileSectionResolver` | `PromptRenderer` 内联 | 逻辑只有 15 行（Map 映射 + 取 profile section） |
| `WorkspaceSectionResolver` | `PromptRenderer` 内联 | 逻辑只有 5 行（输出 workspace 路径） |
| `MemorySectionResolver` | `PromptRenderer` 内联 | 当前是空实现 placeholder |
| `ResolutionContext` | 合并到 `PromptContext` | 字段和 PromptContext 高度重叠，多包一层无意义 |
| `PromptAssembler` | `PromptRenderer` | 职责合并：模板加载 + 占位符替换 |
| `PromptRuntimeBuilder` | `PromptRenderer` | 职责合并：组装 ResolutionContext + 调用 Assembler |

---

## 五、保留的类清单

| 类 | 保留理由 | 变更 |
|---|---|---|
| `VesselMeta` | 配置事实来源 | 无 |
| `VesselMetaLoader` | 配置加载 | 无 |
| `VesselProfile` | 配置事实来源 | 无 |
| `VesselProfileLoader` | 配置加载 | 无 |
| `RuntimeConfig` | 运行时合并配置 | 无 |
| `RuntimeConfigResolver` | 配置解析 | 无 |
| `PromptContext` | Prompt 渲染参数 | **删除死字段，委托给 VesselConfigBundle** |
| `PromptContextFactory` | 工厂 | **新增 VesselProfileLoader 注入，构建 VesselConfigBundle** |

---

## 六、迁移路径

### Phase 1：新增 VesselConfigBundle（不破坏现有代码）

1. 新增 `VesselConfigBundle` 类
2. 在 `PromptContextFactory` 中注入 `VesselProfileLoader`，构建 bundle
3. 在 `PromptContext` 中新增 `bundle` 字段，保留旧字段（兼容期）
4. 编译通过，测试通过

### Phase 2：替换渲染引擎

1. 新增 `PromptRenderer`，复制 `PromptAssembler` + `PromptRuntimeBuilder` 的逻辑
2. 修改模板文件为 `{xxx}` 语法
3. `VesselRuntime` 改为注入 `PromptRenderer`
4. 编译通过，测试通过

### Phase 3：删除废弃类

1. 删除 `ResolutionContext`, `SectionRegistry`, `SectionResolver`
2. 删除 4 个 `SectionResolver` 实现
3. 删除 `PromptAssembler`, `PromptRuntimeBuilder`
4. 清理 `PromptContext` 死字段
5. 编译通过，测试通过

---

## 七、边界与权衡

### 为什么不用更复杂的模板引擎（Freemarker/Thymeleaf）？

当前 prompt 模板非常简单（纯文本替换），引入外部引擎会增加依赖和学习成本。`String.replace()` 足够且零依赖。如果未来模板复杂度显著增加（条件分支、循环），再评估引入轻量引擎。

### 为什么删除 SectionResolver 扩展点？

当前 4 个 resolver 全部是**内联级别**的逻辑（10-20 行），没有需要外部扩展的真实场景。如果未来需要插件化渲染（如第三方 skill 注入自定义 section），可以重新引入一个极简的 `SectionRenderer` 接口，但当前属于过度设计。

### PromptContext 为什么不直接删除，让 VesselRuntime 传 VesselConfigBundle？

保留 `PromptContext` 作为**渲染参数集合**是有价值的：
- 它承载运行时动态数据（`currentTime`, `location`），这些不属于配置
- 它是渲染引擎的**单一入口契约**，未来如果增加 `sessionId`、`userName` 等动态参数，只需扩展 PromptContext，不影响配置层
- 测试时可以构造 mock PromptContext，无需构造完整的 VesselMeta + VesselProfile

---

## 八、代码行数对比（预估）

| 层级 | 当前（行数） | 新设计（行数） | 减少 |
|---|---|---|---|
| 核心概念数 | 6 + 4 实现 = 10 个类 | 3 + 1 新增 = 4 个类 | -60% |
| Prompt 渲染链路 | ~350 行 | ~120 行 | -65% |
| 新增一个 section | 改 3 个文件 | 改 1 个文件（PromptRenderer） | -67% |
