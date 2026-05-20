# Meta-Claw Java 组件热进化系统设计文档

> 目标：让 Agent 运行时能够在不重启 JVM 的前提下，动态加载、替换、卸载业务组件（工具、技能、提示词模板、路由策略等），实现类似 Python `evo/` 系统的自进化能力。

---

## 1. 问题背景

### 1.1 什么是组件热进化

组件热进化（Hot Evolution）指运行时动态改进系统自身组件的能力：
- **热加载（Hot Load）**：运行中加载一个全新的组件（如新工具、新技能）
- **热替换（Hot Swap）**：用新的实现替换已有组件，不影响正在进行的对话
- **热卸载（Hot Unload）**：移除不再需要的组件，释放资源

### 1.2 为什么 Java 比 Python 难

| 维度 | Python | Java |
|------|--------|------|
| 类加载 | `import` 即执行，模块可重载 | 类加载器单向委托，类不可卸载除非 GC |
| 运行时编译 | 解释执行，可直接 `exec()` 字符串 | 需要 javac / Janino / Groovy 等编译器 |
| 对象替换 | 直接替换模块字典中的函数引用 | Spring Bean 有依赖图，需要上下文刷新 |
| 类型系统 | 动态类型， duck typing | 静态类型，替换需保证接口兼容 |

### 1.3 meta-claw 的具体场景

- **工具进化**：LLM 生成新的 `@Tool` 方法 → 编译 → 注册到 `ToolRegistry`
- **技能进化**：LLM 生成新的 `SKILL.md` 或提示词模板 → 热加载到 `TemplateLoader`
- **路由进化**：LLM 生成新的路由策略 → 替换 `AgentLoop.determineTargetVessel()`
- **Prompt 进化**：LLM 生成新的 system prompt 构建逻辑 → 替换 `SystemPromptBuilder`

---

## 2. 核心技术挑战

### 2.1 Java ClassLoader 的"单向加载"特性

```
Bootstrap ClassLoader
        ↑
Ext ClassLoader
        ↑
App ClassLoader  ←── 加载用户类（classpath）
        ↑
   自定义 CL    ←── 热进化组件应放在这里
```

- 同一个全限定类名在同一个 ClassLoader 中只能加载一次
- 要替换类，必须用**新的 ClassLoader** 重新加载
- 旧 ClassLoader 中的类实例如果还有引用，无法 GC，导致内存泄漏

### 2.2 Spring Bean 依赖图

Spring 容器启动后，Bean 的依赖关系是静态的：
- `VesselRuntime` → `ToolRegistry` → `CalculatorTool`
- 如果 `CalculatorTool` 被热替换，引用它的 `ToolRegistry` 必须同步更新
- 简单的 Bean 替换在原型（prototype）作用域下容易，单例（singleton）作用域下困难

### 2.3 接口契约约束

热替换必须保证**二进制兼容**：
- 新方法必须实现相同接口
- 方法签名必须匹配（或保持向后兼容）
- 序列化格式不能突变

---

## 3. 方案选型对比

| 方案 | 原理 | 热替换粒度 | Spring 集成 | 复杂度 | 推荐度 |
|------|------|-----------|------------|--------|--------|
| **OSGi** | 模块化 ClassLoader + 服务注册表 | Bundle 级别 | 需 Spring Dynamic Modules | 高 | ⭐⭐ |
| **JVM Agent + Instrumentation** | 通过 attach API 替换已加载类的字节码 | 方法级别 | 困难 | 高 | ⭐⭐ |
| **自定义 ClassLoader + 隔离目录** | 每个进化组件一个 URLClassLoader | 类级别 | 中等 | 中 | ⭐⭐⭐⭐ |
| **Groovy / Janino 脚本引擎** | 运行时编译脚本为 Class | 脚本级别 | 容易（Groovy 有 Spring 支持） | 低 | ⭐⭐⭐⭐⭐ |
| **Spring Boot DevTools** | 两个 ClassLoader + 重启 | 应用级别 | 原生支持 | 低 | ⭐⭐⭐ |

### 3.1 推荐组合方案

**首选：Groovy / Janino 脚本引擎 + 自定义注册表**

理由：
1. **低门槛**：LLM 生成的是文本代码，脚本引擎直接编译执行，无需管理 .class 文件
2. **易回滚**：脚本作为字符串存储，回滚即恢复旧版本字符串
3. **Spring 友好**：Groovy 有 `groovy-spring-boot-starter`，Janino 可编译实现标准接口的类
4. **安全可控**：可在沙箱 SecurityManager / 自定义 ClassLoader 中执行

**备选：自定义 URLClassLoader**

用于需要完整 Java 语法、性能敏感、或需要访问大量现有类的场景。

---

## 4. 推荐架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│  Evo Engine（热进化引擎）                                     │
│  ├─ EvoCodeCompiler      ← Janino / GroovyShell            │
│  ├─ EvoSandboxClassLoader ← 隔离编译产物                   │
│  ├─ EvoRegistry          ← 管理进化组件的生命周期          │
│  └─ EvoValidator         ← 语法校验 + 接口兼容检查         │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ ToolEvo      │    │ SkillEvo     │    │ PromptEvo    │
│ Registry     │    │ Registry     │    │ Registry     │
│              │    │              │    │              │
│ 热替换工具    │    │ 热加载技能   │    │ 热替换模板   │
│ 实现          │    │ 文件         │    │ 构建器       │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
   ToolRegistry          TemplateLoader       SystemPromptBuilder
```

### 4.2 关键抽象：EvolvableComponent

```java
package meta.claw.evo;

/**
 * 可进化组件标记接口。
 * 所有支持热替换的组件必须实现此接口，以便 EvoRegistry 统一管理生命周期。
 */
public interface EvolvableComponent {
    /** 组件唯一标识 */
    String getComponentId();
    /** 组件类型：tool / skill / prompt / router */
    String getComponentType();
    /** 组件版本号，用于回滚 */
    String getVersion();
    /** 卸载前清理资源 */
    default void onUnload() {}
    /** 加载后初始化 */
    default void onLoad() {}
}
```

### 4.3 EvoRegistry：进化组件注册表

```java
@Component
public class EvoRegistry {
    private final Map<String, EvolvableComponent> components = new ConcurrentHashMap<>();
    private final Map<String, List<EvolvableComponent>> history = new ConcurrentHashMap<>();

    /**
     * 注册进化组件。如果已存在同 ID 组件，旧组件被保留到历史栈。
     */
    public void register(EvolvableComponent component) {
        EvolvableComponent old = components.put(component.getComponentId(), component);
        if (old != null) {
            history.computeIfAbsent(component.getComponentId(), k -> new ArrayList<>()).add(old);
            old.onUnload();
        }
        component.onLoad();
    }

    /**
     * 回滚到上一个版本。
     */
    public boolean rollback(String componentId) {
        List<EvolvableComponent> stack = history.get(componentId);
        if (stack == null || stack.isEmpty()) return false;
        EvolvableComponent current = components.get(componentId);
        if (current != null) current.onUnload();
        EvolvableComponent previous = stack.remove(stack.size() - 1);
        components.put(componentId, previous);
        previous.onLoad();
        return true;
    }

    /**
     * 卸载组件。
     */
    public boolean unregister(String componentId) {
        EvolvableComponent removed = components.remove(componentId);
        if (removed != null) {
            removed.onUnload();
            return true;
        }
        return false;
    }
}
```

---

## 5. 关键实现细节

### 5.1 工具热进化（Tool Evolution）

**场景**：LLM 生成了一个更好的 `calculator` 实现，需要替换现有工具。

**实现**：

```java
@Component
public class ToolEvoRegistry {

    private final ToolRegistry toolRegistry;
    private final JaninoCompiler compiler;

    /**
     * 热替换工具。
     * @param toolName   工具名（如 "calculator"）
     * @param sourceCode 完整的 Java 类源码字符串
     */
    public boolean evolveTool(String toolName, String sourceCode) {
        // 1. 语法校验
        if (!compiler.validate(sourceCode)) {
            return false;
        }

        // 2. 编译为新 Class（使用隔离的 ClassLoader）
        Class<?> clazz = compiler.compile(sourceCode);
        if (clazz == null) {
            return false;
        }

        // 3. 检查是否包含目标 @Tool 方法
        boolean hasToolMethod = Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Tool.class)
                        && m.getAnnotation(Tool.class).name().equals(toolName));
        if (!hasToolMethod) {
            return false;
        }

        // 4. 实例化并注册（先卸载旧工具）
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            toolRegistry.unregister(toolName);
            toolRegistry.register(instance);
            return true;
        } catch (Exception e) {
            // 失败时回退：旧工具已经被 unregister，需要恢复
            // 实际实现应使用两阶段提交：先注册新工具，再卸载旧工具
            return false;
        }
    }
}
```

**JaninoCompiler 伪代码**：

```java
@Component
public class JaninoCompiler {

    private final Map<String, SimpleCompiler> classLoaders = new ConcurrentHashMap<>();

    public Class<?> compile(String sourceCode) {
        String className = extractClassName(sourceCode);
        SimpleCompiler compiler = new SimpleCompiler();
        compiler.setParentClassLoader(this.getClass().getClassLoader());
        try {
            compiler.cook(sourceCode);
            return compiler.getClassLoader().loadClass(className);
        } catch (Exception e) {
            log.error("Compilation failed", e);
            return null;
        }
    }

    public boolean validate(String sourceCode) {
        // 基本安全检查：禁止 System.exit、Runtime.exec、反射访问敏感类等
        return SecurityScanner.scan(sourceCode).isSafe();
    }
}
```

### 5.2 技能热进化（Skill Evolution）

技能是提示词文件，不需要编译：

```java
@Component
public class SkillEvoRegistry {

    private final TemplateLoader templateLoader;

    public boolean evolveSkill(String skillName, String markdownContent) {
        Path skillPath = workspace.resolve("skills").resolve(skillName).resolve("SKILL.md");
        try {
            Files.createDirectories(skillPath.getParent());
            Files.writeString(skillPath, markdownContent);
            templateLoader.invalidateCache(skillName); // 让 TemplateLoader 重新加载
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
```

### 5.3 Prompt 模板热进化

```java
@Component
public class PromptEvoRegistry {

    private final SystemPromptBuilder promptBuilder;

    public boolean evolvePromptTemplate(String templateName, String newTemplate) {
        // 替换模板加载器中的模板内容
        // SystemPromptBuilder 的下次 build() 调用会自动使用新模板
        return promptBuilder.updateTemplate(templateName, newTemplate);
    }
}
```

---

## 6. 安全与回滚机制

### 6.1 安全扫描（SecurityScanner）

热进化最大的风险是 LLM 生成恶意代码。必须实施多层安全：

```java
public class SecurityScanner {

    private static final List<String> BLACKLIST = List.of(
        "System.exit", "Runtime.getRuntime().exec", "ProcessBuilder",
        "java.io.FileDelete", "java.net.URLClassLoader", "sun.misc.Unsafe"
    );

    public ScanResult scan(String sourceCode) {
        // 1. 黑名单字符串匹配
        for (String banned : BLACKLIST) {
            if (sourceCode.contains(banned)) {
                return ScanResult.fail("Contains forbidden keyword: " + banned);
            }
        }

        // 2. 语法树分析（AST）：禁止反射修改私有字段、禁止加载外部类
        // 可使用 JavaParser 库进行 AST 遍历

        // 3. 类加载隔离：进化代码只能访问白名单包
        return ScanResult.pass();
    }
}
```

### 6.2 两阶段提交 + 回滚

```
Phase 1: 验证
  ├── 语法校验
  ├── 接口兼容检查
  ├── 安全扫描
  └── 在沙箱中试运行（dry-run）

Phase 2: 原子替换
  ├── 备份当前组件到 history
  ├── 注册新组件
  ├── 运行冒烟测试（如工具执行一次）
  └── 失败则自动 rollback()
```

### 6.3 版本与历史

```java
public record EvoVersion(
    String componentId,
    String version,
    String sourceCode,
    Instant createdAt,
    String createdBy // LLM model name or user id
) {}
```

历史栈保存在 `workspace/evo/history/<component-id>/` 目录下，每个版本一个文件。

---

## 7. 与 meta-claw 的集成路径

### 7.1 当前状态 → 目标状态

| 组件 | 当前状态 | 目标状态 | 优先级 |
|------|---------|---------|--------|
| `ToolRegistry` | Spring `@Component`，支持 `register/unregister/reregister` | 接入 `ToolEvoRegistry`，支持 Janino 编译 | P0 |
| `TemplateLoader` | 文件系统加载 | 支持运行时模板内容热替换 | P1 |
| `SystemPromptBuilder` | 静态构建逻辑 | 支持模板热替换 | P1 |
| `AgentLoop` | 固定路由策略 | 支持路由策略热替换 | P2 |
| `VesselRuntime` | 初始化时绑定配置 | 支持配置热更新 | P2 |

### 7.2 新模块建议：`meta-claw-evo`

建议新增 `meta-claw-evo` 模块，职责边界：
- **不负责** LLM 代码生成（由 `meta-claw-core` 的 LLM Client 负责）
- **负责** 代码编译、安全扫描、注册表管理、历史回滚
- **依赖** `meta-claw-tool`、`meta-claw-core`
- **被依赖** `meta-claw-bootstrap`（作为可选扩展）

### 7.3 CLI 入口

```bash
# 触发指定组件的进化（由 LLM 生成代码并自动应用）
meta-claw evolve <vessel> --component tool:calculator

# 查看进化历史
meta-claw evo-history <vessel> --component tool:calculator

# 手动回滚
meta-claw evo-rollback <vessel> --component tool:calculator --version 1

# 查看当前进化组件列表
meta-claw evo-list <vessel>
```

---

## 8. 演进路线

### Phase 1：工具热替换（已实现基础，待接入编译器）
- [x] `ToolRegistry` 支持 `register/unregister/reregister`
- [ ] 接入 Janino 编译器
- [ ] 实现 `SecurityScanner`
- [ ] 实现 `ToolEvoRegistry`

### Phase 2：技能与 Prompt 热加载
- [ ] `TemplateLoader` 支持运行时缓存失效
- [ ] `SystemPromptBuilder` 支持模板热替换
- [ ] `SkillEvoRegistry` 实现

### Phase 3：路由与策略热进化
- [ ] `AgentLoop` 路由策略可插拔
- [ ] `PromptEvoRegistry` 支持提示词构建逻辑热替换

### Phase 4：全自动进化闭环
- [ ] LLM 生成代码 → 自动编译 → 自动测试 → 自动部署
- [ ] A/B 测试：新旧组件并行运行，对比效果
- [ ] 自动回滚：监控异常率，触发自动 rollback

---

## 9. 关键代码参考

### 9.1 Janino 依赖

```xml
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.11</version>
</dependency>
```

### 9.2 Groovy 依赖（备选）

```xml
<dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.21</version>
</dependency>
```

### 9.3 现有基础（已落地）

- `meta-claw-tool/src/main/java/meta/claw/tool/registry/ToolRegistry.java`
  - 已实现：Spring `@Component`、自动扫描 `@Tool`、动态 `register/unregister/reregister`
- `meta-claw-core/src/main/java/meta/claw/core/spi/tool/ToolDefinitionProvider.java`
  - 已实现：解耦接口，避免 core 模块反向依赖 tool 模块

---

## 10. 总结

Java 组件热进化的核心矛盾是**静态类型 + ClassLoader 单向加载**与**运行时动态替换**之间的矛盾。推荐方案是：

1. **脚本引擎优先**：用 Janino / Groovy 处理 LLM 生成的代码，避免直接管理 .class 文件和 ClassLoader 生命周期
2. **接口隔离**：所有可进化组件通过窄接口暴露，替换时只需保证接口兼容
3. **注册表模式**：用 `EvoRegistry` + `ToolRegistry` 等注册表管理组件生命周期，而非直接替换 Spring Bean
4. **安全第一**：多层安全扫描 + 沙箱执行 + 自动回滚，防止 LLM 生成恶意代码

这个设计让 meta-claw 在保持 Spring 生态稳定性的同时，获得了接近 Python 生态的灵活进化能力。
