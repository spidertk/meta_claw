# 初始化阶段 P0 测试基线收敛设计

## 目标

在项目仍处于开发初始化阶段时，把标准验证收敛到少量真正保护主链路的 P0 测试，降低每轮开发的测试维护成本，并移除当前阶段尚未承诺的旁支测试。

## 背景

仓库当前已经拥有一批分散在 core、store、cli、bootstrap、vessel 以及第三方模块中的测试。它们覆盖面较广，但其中不少测试保护的是尚未稳定的内部拆分、辅助命令或未来能力。

这会带来三个问题：

1. 每次重构都需要同步维护大量尚未进入当前阶段核心承诺的测试
2. `./init.sh` 的验证成本被旁支能力放大
3. 测试集合表达的是“未来完整系统”，而不是“当前初始化阶段真正必须守住的能力”

当前阶段需要把测试面压缩到主链路能力本身，而不是继续维持一套过早膨胀的测试网。

## 选定方案

直接保留少量 P0 测试类，删除当前阶段不需要维护的旁支测试，并把 `./init.sh` 收敛为只执行这组 P0 测试。

不采用以下两种方案：

- 保留全部测试文件，仅通过 profile 或 tag 选择执行
- 不删测试文件，只从 `./init.sh` 中排除

原因是这两种做法都会继续保留无效维护成本，且让仓库中的“存在测试”和“实际承诺验证”发生分裂。

## P0 测试定义

初始化阶段只保留能够保护主链路能力的测试：

1. 能读取 Vessel 配置
2. 能加载 Vessel
3. 能生成核心系统提示
4. 能持久化并读取短期记忆
5. 能持久化并读取长期记忆
6. CLI chat 主路径能执行
7. 启动级消息流能够串通

## 保留的测试

- `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`
- `meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java`
- `meta-claw-store/src/test/java/meta/claw/store/memory/longterm/FileLongMemoryStoreTest.java`
- `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
- `meta-claw-bootstrap/src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java`

## 删除的测试

### CLI 辅助命令

- `meta-claw-cli/src/test/java/meta/claw/cli/ConfigCommandTest.java`
- `meta-claw-cli/src/test/java/meta/claw/cli/SessionsCommandTest.java`

### Prompt 内部拆分

- `meta-claw-core/src/test/java/meta/claw/core/prompt/PromptContextFactoryTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/TemplateLoaderTest.java`

### Manager 级重复委托

- `meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ShortMemoryManagerTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/memory/longterm/LongMemoryManagerTest.java`

### 即将被删除的旧抽象

- `meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ConversationHistoryManagerTest.java`

### 运行时与 SPI 旁支

- `meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientIntegrationTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/spi/llm/MessageTest.java`

### Vessel 辅助解析

- `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java`

### 第三方模块

当前阶段移除 `third-party/openilink-sdk-java` 下的全部测试。该模块暂不纳入初始化阶段的 P0 验证；后续当相关能力真正进入主链路后，再补回与实际集成边界对应的新测试。

## 标准验证入口

`./init.sh` 的语义调整为：

- 保证仓库可编译
- 只执行初始化阶段定义的 P0 测试集合
- 不再默认承诺全仓库所有测试都执行

第三方模块仍可参与编译，但不参与当前阶段的标准测试集合。

## 文档同步

以下长期状态文件需要同步更新：

- `claude-progress.md`
- `feature_list.json`
- 必要时补充 `evaluator-rubric.md`

它们需要明确记录：

- 当前仓库标准验证已从“全量测试”收敛为“P0 测试集”
- 这是开发初始化阶段的主动策略，不是测试缺失或验证退化
- 被移除的旁支能力后续若重新进入主链，需要重新补测试

## 错误处理

- 如果某个保留测试暴露出真实主链路问题，应优先修复主链路，而不是继续删测试
- 如果某个准备删除的测试当前唯一覆盖了一个已经进入 P0 的行为，需要先把那条断言迁移到保留测试，再删除原测试
- 如果删测后 `./init.sh` 仍被非 P0 测试拖慢，说明验证入口没有真正收敛，需要继续修正入口

## 验证策略

实施完成后需要验证：

1. 仓库中只剩下设计列出的 P0 测试
2. `./init.sh` 只运行 P0 测试集合
3. `./init.sh` 在当前环境中成功通过
4. 长期状态文件准确记录新的验证口径

## 非目标

- 不在这一轮补充新的业务能力测试
- 不在这一轮继续推进 Memory 重构实现
- 不把所有模块都改造成零测试
- 不把当前阶段的 P0 测试定义成永久标准

## 预期结果

完成后：

- 每轮初始化验证更短、更聚焦
- 后续开发只需要维护真正保护当前主链路的测试
- 仓库中的测试集合与当前阶段目标一致
- 后续能力扩展时，可以围绕新进入主链的能力重新补充测试，而不是提前维护一圈尚未稳定的旁支测试
