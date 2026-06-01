# 模板与配置层命名收敛方案

## 核心思路

按**用户定义域**和**运行时域**重新切分，命名即领域，目录即边界。

---

## 一、模板资源层（resources/templates/）

```
templates/
├── user/                          # 用户定义模板：初始化时生成给用户编辑的
│   ├── vessel.meta.tmpl.yaml      # Vessel 元数据（原 vessel-config.tmpl.yaml）
│   └── vessel.profile.tmpl.md     # Vessel 人格档案（原 vessel.tmpl.md）
└── runtime/                       # 运行时模板：系统渲染 Prompt 用的骨架
    ├── system.tmpl.md             # System Prompt 骨架
    └── context.tmpl.md            # Context Prompt 骨架
```

**边界**：
- `user/` 下的模板只在 **创建 Vessel** 时渲染一次，产物是用户的持久化文件
- `runtime/` 下的模板在 **每次对话** 时渲染，产物是发给 LLM 的 prompt

---

## 二、Java 包层（按域切分）

### 2.1 用户定义域（user）

用户创建、编辑、持久化的内容。

```
meta.claw.core.user/
├── VesselMeta.java                # 模型：映射 vessel.meta.yaml
├── VesselMetaLoader.java          # 加载：SnakeYAML loadAs 直接反序列化
├── VesselProfile.java             # 模型：映射 vessel.profile.md 的 Section 集合
├── VesselProfileLoader.java       # 加载：提取 ## Section 内容
└── VesselInitializer.java         # 初始化：基于 user/ 模板创建用户文件
```

### 2.2 运行时域（runtime）

系统运行时的配置解析、Prompt 渲染。

```
meta.claw.core.runtime/
├── RuntimeConfig.java             # 模型：运行时聚合配置（Provider + Memory + Meta）
├── RuntimeConfigResolver.java     # 解析：合并全局 + Vessel 级配置
├── PromptAssembler.java           # 组装：统一渲染 runtime/ 模板
├── SectionRegistry.java           # 注册表：声明 Prompt 包含哪些 Section、来源、去向
└── resolver/
    ├── MetaSectionResolver.java       # 解析 VesselMeta → Section 内容
    ├── ProfileSectionResolver.java    # 解析 VesselProfile → Section 内容
    ├── MemorySectionResolver.java     # 解析记忆库 → Section 内容
    └── WorkspaceSectionResolver.java  # 解析运行时环境 → Section 内容
```

### 2.3 基础设施域（infra）

与业务无关的通用能力。

```
meta.claw.core.infra/
├── GlobalConfig.java              # 全局配置模型
├── GlobalConfigLoader.java        # 全局配置加载
├── ProviderConfig.java            # Provider 配置模型
├── MemoryConfig.java              # Memory 后端配置模型
└── ProjectRootFinder.java         # 项目根目录查找
```

---

## 三、旧命名 → 新命名对照表

| 旧文件/类 | 归属域 | 新文件/类 | 理由 |
|-----------|--------|----------|------|
| `templates/vessel-config.tmpl.yaml` | 用户 | `templates/user/vessel.meta.tmpl.yaml` | `meta` 表示元数据，`.meta.` 让用户知道这是配置表 |
| `templates/vessel.tmpl.md` | 用户 | `templates/user/vessel.profile.tmpl.md` | `profile` 表示人格档案，区别于配置 |
| `templates/system.tmpl.md` | 运行时 | `templates/runtime/system.tmpl.md` | 运行时域一目了然 |
| `templates/context.tmpl.md` | 运行时 | `templates/runtime/context.tmpl.md` | 同上 |
| `config/VesselConfig.java` | 用户 | `user/VesselMeta.java` | 拆分为 Meta（YAML）+ Profile（MD），不再混合 |
| `config/VesselConfigLoader.java` | 用户 | `user/VesselMetaLoader.java` + `user/VesselProfileLoader.java` | 一 loader 一模型，职责清晰 |
| `vessel/VesselTemplate.java` | 用户 | `user/VesselInitializer.java` | `Initializer` 表示初始化，不是模板引擎 |
| `vessel/VesselConfigResolver.java` | 运行时 | `runtime/RuntimeConfigResolver.java` | 明确这是运行时配置解析，不是用户配置解析 |
| `vessel/ResolvedVesselConfig.java` | 运行时 | `runtime/RuntimeConfig.java` | 去掉 `Resolved` 冗余词，运行时配置就是运行时配置 |
| `config/GlobalConfig.java` | 基础设施 | `infra/GlobalConfig.java` | 全局配置属于基础设施 |
| `vessel/ProjectRootFinder.java` | 基础设施 | `infra/ProjectRootFinder.java` | 路径查找与 vessel 业务无关 |

---

## 四、运行时模板语法统一

当前 `system.tmpl.md` 混合了 `{vessel_name}` 和 `<IDENTITY_SECTION/>`。

**统一为单一语法**：

```markdown
<!-- runtime/system.tmpl.md -->
<SECTION id="meta"/>
<SECTION id="identity"/>
<SECTION id="soul"/>
<SECTION id="capabilities"/>
<SECTION id="guidelines"/>
<SECTION id="knowledge"/>
```

```markdown
<!-- runtime/context.tmpl.md -->
## Runtime Context

<SECTION id="workspace"/>
<SECTION id="runtime"/>
<SECTION id="preferences"/>
```

**规则**：
- 运行时模板**只允许** `<SECTION id="xxx"/>` 这一种动态语法
- 每个 `id` 必须在 `SectionRegistry` 中有注册
- Section 的 Markdown 格式由来源文件决定，模板只负责**插入位置**

---

## 五、用户模板语法统一

当前 `vessel.tmpl.md` 有 `{name}`、`{description}` 占位符。

**收敛后**：

```yaml
# user/vessel.meta.tmpl.yaml
meta:
  id: {name}
  name: {name}
  description: {description}
  created_at: {created_at}
  display_name: ~
  emoji: 🤖

llm:
  provider: openapi
  model: ~
  overrides:
    api_key: ~
    base_url: ~
    temperature: ~
    timeout: ~

runtime:
  role: member
  auto_serve: false

memory:
  preferences_enabled: true
  short_term_store: jsonl
  long_term_store: file

tools:
  exclude: []
```

```markdown
<!-- user/vessel.profile.tmpl.md -->
## Identity

## Soul

## Capabilities

## Guidelines

## Domain Knowledge
```

**规则**：
- `vessel.meta.tmpl.yaml`：**唯一**包含 `{var}` 占位符的文件（初始化时替换）
- `vessel.profile.tmpl.md`：**零占位符**，用户直接写内容
- `description` 只存在于 `meta` 中，`profile` 不再重复

---

## 六、SectionRegistry 契约（核心桥接）

`SectionRegistry` 统一声明"用户写的 Section 最终去哪"：

```java
public enum SectionRegistry {
    META        ("meta",        Source.VESSEL_META,     Target.SYSTEM),
    IDENTITY    ("identity",    Source.VESSEL_PROFILE,  Target.SYSTEM),
    SOUL        ("soul",        Source.VESSEL_PROFILE,  Target.SYSTEM),
    CAPABILITIES("capabilities",Source.VESSEL_PROFILE,  Target.SYSTEM),
    GUIDELINES  ("guidelines",  Source.VESSEL_PROFILE,  Target.SYSTEM),
    KNOWLEDGE   ("knowledge",   Source.VESSEL_PROFILE,  Target.SYSTEM),
    WORKSPACE   ("workspace",   Source.RUNTIME,         Target.CONTEXT),
    RUNTIME     ("runtime",     Source.RUNTIME,         Target.CONTEXT),
    PREFERENCES ("preferences", Source.MEMORY,          Target.CONTEXT);

    // id, source, target, required...
}
```

**收益**：
- 用户在 `vessel.profile.md` 里写 `## CustomSection`，查 Registry 发现不支持 → 静默忽略，不报错不困惑
- 调整 Section 去向（如 preferences 从 context 改到 system）只改 Registry，不动模板

---

## 七、迁移优先级

1. **先改目录和命名**（零逻辑改动，纯移动 + 重命名）
2. **再统一运行时模板语法**（system/context 改为纯 `<SECTION/>`）
3. **最后抽 SectionRegistry**（把 PromptRuntimeBuilder 中的硬编码 section 映射提取出来）
