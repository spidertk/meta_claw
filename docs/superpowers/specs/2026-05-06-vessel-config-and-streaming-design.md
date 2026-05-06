# Vessel 级模型配置覆盖 + CLI 流式输出 设计文档

## 1. 背景与目标

阶段 1 的 CLI 聊天功能已基本跑通，但还缺两个能力：

1. **Vessel 级模型配置覆盖**：目前全局 `~/.meta-claw/config.yaml` 管理所有 provider，每个 vessel 的 `vessel.md` 只有一个 `model` 字段，无法为不同 Expert（Vessel）独立配置完整的模型参数（如 api_key、base_url、temperature 等）。
2. **CLI 流式输出**：`SpiLlmClient.chatStream()` 当前是伪实现（一次性返回完整响应），CLI 用户看不到逐字输出效果。

本设计补齐这两个能力，使阶段 1 的 CLI 交互体验完整。

## 2. 设计决策

- **配置覆盖策略（B 方案）**：Vessel 级 `config.yaml` 只放需要覆盖的字段，缺失字段回退到全局同 provider 的配置。
- **流式输出范围（B 方案）**：`SpringAiLlmClient.chatStream()` 实现真正的流式，同时 CLI `ChatCommand` 改成逐字打印流式输出。

## 3. Vessel 级模型配置覆盖

### 3.1 配置优先级（从高到低）

1. `~/.meta-claw/vessels/<name>/config.yaml` 中的非空字段
2. `~/.meta-claw/vessels/<name>/vessel.md` frontmatter 中的 `model`
3. 全局 `~/.meta-claw/config.yaml` 中对应 provider 的 `ProviderConfig`

### 3.2 Vessel 级 config.yaml 结构

```yaml
# Vessel 级模型配置（可选）
# 此文件中非空字段会覆盖全局 config.yaml 中对应 provider 的配置
# 留空或删除则表示完全使用全局配置
provider: ""       # 指定使用全局 providers 下的哪个 provider，空则使用全局 default_provider
model: ""          # 覆盖模型名称
api_key: ""        # 覆盖 API Key
base_url: ""       # 覆盖 Base URL
temperature: ""    # 覆盖温度参数
timeout: ""        # 覆盖超时（秒）
```

### 3.3 新增模型

#### `VesselProfileConfig`

映射 vessel 目录下 `config.yaml`，字段与 `ProviderConfig` 对齐并增加 `provider`：

| 字段 | 类型 | 说明 |
|------|------|------|
| provider | String | 指向全局 providers 的 key，空则回退到全局 `default_provider` |
| apiKey | String | 覆盖 API Key |
| baseUrl | String | 覆盖 Base URL |
| model | String | 覆盖模型名称 |
| temperature | Double | 覆盖温度 |
| timeout | Double | 覆盖超时（秒） |

所有字段均为可选。

#### `ResolvedVesselConfig`

`VesselConfigResolver` 的返回对象，包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| providerName | String | 最终使用的 provider 名称 |
| providerConfig | ProviderConfig | 合并后的完整 provider 配置 |
| vesselConfig | VesselConfig | 加载的 vessel.md 配置（供后续使用 systemPrompt 等） |

### 3.4 新增组件

#### `VesselProfileLoader`

- 职责：加载单个 vessel 目录下的 `config.yaml`
- 输入：`Path vesselDir`
- 输出：`VesselProfileConfig`（文件不存在时返回 null）

#### `VesselConfigResolver`

- 职责：解析某个 vessel 的最终运行时配置
- 流程：
  1. 调用 `GlobalConfigLoader` 加载全局配置
  2. 调用 `VesselProfileLoader` 加载 Vessel 级覆盖配置
  3. 调用 `VesselConfigLoader` 加载 `vessel.md`
  4. 确定 providerName：`vesselProfile.provider` > 全局 `defaultProvider`
  5. 从全局 `providers` 中取出基础 `ProviderConfig`
  6. 用 `VesselProfileConfig` 非空字段覆盖基础配置
  7. 若 `vessel.md` 的 `model` 有值且第 6 步未覆盖 model，则用 `vessel.md` 的 model
  8. 组装并返回 `ResolvedVesselConfig`

### 3.5 初始化模板更新

`VesselTemplate.createDefaultVessel()` 在生成 `vessel.md` 的同时，生成上述带注释的 `config.yaml` 模板，降低用户首次使用的理解成本。

### 3.6 修改点

- **`ChatCommand.java`**：把原来手动加载全局配置 + `vessel.md` + 手动合并 `model` 的三段式逻辑，替换为 `VesselConfigResolver.resolve(expertName)` 的一行调用。

## 4. CLI 流式输出

### 4.1 `SpringAiLlmClient.chatStream()` 改造

当前实现：
```java
callback.onStart();
try {
    SpiChatResponse response = chat(request);
    callback.onChunk(response.content());
    callback.onComplete(response);
} catch (Exception e) {
    callback.onError(e);
}
```

目标实现：
- 使用 `chatClient.prompt(prompt).stream().content()` 获取 `Flux<String>`
- 订阅 Flux，每个 token 通过 `callback.onChunk(token)` 下发
- 内部用 `StringBuilder` 累积完整内容
- 流结束时通过 `callback.onComplete(SpiChatResponse)` 下发完整响应
- 异常通过 `callback.onError(Throwable)` 下发

由于 CLI 是同步交互，使用 `blockLast()` 阻塞消费整个流。

### 4.2 `ChatCommand` 输出改造

把原来的阻塞调用：
```java
SpiChatResponse response = llmClient.chat(request);
System.out.println(response.content());
```

改为流式 Callback：
```java
llmClient.chatStream(request, new SpiStreamingCallback() {
    @Override public void onStart() {
        System.out.print("AI: ");
    }
    @Override public void onChunk(String chunk) {
        System.out.print(chunk);
        System.out.flush();
    }
    @Override public void onToolCall(SpiToolCall toolCall) {
        // 阶段 1 暂不实现工具调用流式下发
    }
    @Override public void onComplete(SpiChatResponse response) {
        System.out.println();
    }
    @Override public void onError(Throwable error) {
        System.err.println("\nError: " + error.getMessage());
    }
});
```

### 4.3 依赖说明

`ChatClient.stream()` 返回 `Flux<String>`（Project Reactor）。`meta-claw-cli` 已依赖 `spring-ai-starter-model-openai`，其传递依赖包含 `reactor-core`。若编译时出现 `ClassNotFoundException`，在 `meta-claw-core/pom.xml` 中显式补 `io.projectreactor:reactor-core`。

## 5. 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `meta-claw-vessel/.../VesselProfileConfig.java` | 新增 | Vessel 级配置模型 |
| `meta-claw-vessel/.../VesselProfileLoader.java` | 新增 | 加载 Vessel config.yaml |
| `meta-claw-vessel/.../VesselConfigResolver.java` | 新增 | 配置合并与解析 |
| `meta-claw-vessel/.../VesselTemplate.java` | 修改 | init 时生成 vessel config.yaml 模板 |
| `meta-claw-core/.../SpringAiLlmClient.java` | 修改 | 实现真正流式输出 |
| `meta-claw-cli/.../ChatCommand.java` | 修改 | 使用 Resolver + 流式 Callback |

## 6. 边界与限制

- 阶段 1 仅覆盖 CLI 场景的流式输出；Gateway（微信等）通道的流式事件下发留到后续阶段。
- `SpiStreamingCallback.onToolCall()` 在阶段 1 暂不实现，因为工具调用循环尚未接入。
- Vessel 级 `config.yaml` 若指定了全局 `providers` 中不存在的 `provider`，`VesselConfigResolver` 将抛出 `IllegalArgumentException`，提示用户检查配置。
