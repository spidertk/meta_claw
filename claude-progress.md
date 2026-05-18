# 进度日志

## 当前已验证状态

- 仓库根目录：`/Users/kai/IdeaProjects/meta_claw`
- 当前架构基线：Java 21 + Maven 多模块仓库；已存在 `meta-claw-core`、`meta-claw-vessel`、`meta-claw-store`、`meta-claw-cli`、`meta-claw-gateway*`、`meta-claw-bootstrap`
- 标准启动路径：`./init.sh`
- 标准验证路径：`./init.sh` 先执行全仓编译，再运行初始化阶段 P0 测试集
- 最近已通过证据：2026-05-18 在真实 Maven 环境中执行新版 `./init.sh`，完成全仓编译并通过 7 个 P0 测试类
- 当前最高优先级未完成功能：暂无新的已选定功能
- 当前 blocker：
  1. 当前无 blocker

## 与设计文档对齐后的真实现状

### 已经落地

- `Vessel` 语义主干已完成：`VesselManager`、`VesselRuntime`、`VesselResponseReady` 已替代核心 `Expert*` 代码
- `VesselManager.loadVessels()` 已改为复用 `VesselConfigLoader`
- Vessel 模板中的 `provider` 字段已正确存在
- CLI 基础命令已存在：`init`、`create`、`list`、`delete`、`config`、`chat`
- Prompt Engineering Phase 2 的主件已存在：`PromptContext`、`TemplateLoader`、`SystemPromptBuilder`、`PromptContextFactory`
- `Memory` 已成为独立领域：短期记忆位于 `core.memory.shortterm`，长期记忆位于 `core.memory.longterm`
- `core.session` 与 `core.model` 已移除；配置模型归入 `core.config`，消息流模型归入 `core.message`
- `ShortMemoryManager` / `LongMemoryManager` 已成为配置驱动的编排入口；short-term 窗口策略已下沉到 `ShortMemoryStore`
- `meta-claw-store` 已按 Memory 边界拆为 `store.memory.shortterm.JsonlShortMemoryStore` 与 `store.memory.longterm.FileLongMemoryStore`
- `VesselConfigLoader` 已支持读取 `memory.short_term_store` / `memory.long_term_store`
- `ChatCommand` 已通过 `MemoryManagerFactory` 追加短期消息，并通过 `LongMemoryManager` 把长期偏好接回 prompt context

### 仍未完成或不能算完成

- `./init.sh` 已迁移到当前 Java/Maven 实际启动路径，并已于 2026-05-16 完整跑通
- `ChatCommand` 默认仍会创建新 `sessionKey`，但已支持 `sessions <vessel>` 与 `chat <vessel> --resume <session-id>` 的显式恢复
- `serve/start/stop/restart/status/logs`、工具引擎、MCP、Skill 系统仍未实现

## 会话记录

### Session 001

- 日期：2026-05-15
- 本轮目标：在继续开发前，把长期状态文件从模板态校准为仓库真实现状
- 已完成：
  - 按 `AGENTS.md` 流程读取 `claude-progress.md`、`feature_list.json`、最近提交，并执行 `./init.sh`
  - 对照 `2026-04-30-meta-claw-agent-platform-design-v2.md` 与 `2026-05-08-meta-claw-vessel-migration-and-roadmap.md`
  - 审计当前代码、最近提交、测试报告与关键模块实现
  - 更新 `claude-progress.md`、`clean-state-checklist.md`、`evaluator-rubric.md`、`feature_list.json`
- 运行过的验证：
  - `./init.sh` → 失败：脚本执行 `npm install`，但根目录不存在 `package.json`
  - `mvn test` → 失败：当前环境 `mvn: command not found`
  - 静态核查：`git log --oneline -12`、关键源码审阅、`rg "Expert|专家"`、Surefire 报告审阅
- 已记录证据：
  - 2026-05-15 的失败输出已写入本文件与 `feature_list.json`
  - 已引用当前代码事实：`VesselManager` 使用 `VesselConfigLoader`、`ChatCommand` 已接入 `SystemPromptBuilder`/`MemoryManager`/`JsonlConversationStore`
- 提交记录：待提交
- 更新过的文件或工件：
  - `claude-progress.md`
  - `clean-state-checklist.md`
  - `evaluator-rubric.md`
  - `feature_list.json`
- 已知风险或未解决问题：
  - 标准入口失效会让下一轮 agent 从错误起点开始
  - 旧文档仍含已过时结论，后续若继续依赖，应先看本状态文件与最新代码
  - 测试报告只能证明“此前通过过”，不能替代本轮重新执行
- 下一步最佳动作：
  1. 修正 `init.sh` 为 Java/Maven 仓库真实入口
  2. 补齐可执行的 Maven 环境或仓库内 wrapper
  3. 重新跑标准验证，再继续功能开发

### Session 002

- 日期：2026-05-15
- 本轮目标：修复 `./init.sh` 的错误 npm 工作流
- 已完成：
  - 新增 `docs/superpowers/specs/2026-05-15-init-script-repair-design.md`
  - 新增 `docs/superpowers/plans/2026-05-15-init-script-repair.md`
  - 将 `init.sh` 从 npm 模板命令改为 Maven 工作流
- 运行过的验证：
  - `./init.sh` → 失败但边界已修正：当前环境明确报错 `未找到 mvn`，且不再调用 npm
- 已记录证据：
  - `init.sh` 当前使用 `mvn clean test`
  - `RUN_START_COMMAND=1` 时的启动命令已改为 `mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests`
- 提交记录：
  - `1cefabf docs: add init script repair design`
  - `85d3742 docs: add init script repair plan`
- 更新过的文件或工件：
  - `init.sh`
  - `claude-progress.md`
  - `clean-state-checklist.md`
  - `evaluator-rubric.md`
  - `feature_list.json`
- 已知风险或未解决问题：
  - 尚未在真实 Maven 环境中执行 `mvn clean test`
  - `repo-001` 还不能升级为 passing
- 下一步最佳动作：
  1. 提供可执行的 Maven（系统安装或仓库内 wrapper）
  2. 重新运行 `./init.sh`
  3. 仅在验证真实通过后，把 `repo-001` 更新为 passing

### Session 003

- 日期：2026-05-16
- 本轮目标：补齐 Maven 可执行路径，并让标准入口真实跑通
- 已完成：
  - 尝试通过 Homebrew 安装 Maven，确认当前 Homebrew 版本与 macOS 15 不兼容
  - 按 Apache 官方方式手动安装 Maven 3.9.15 到 `~/.local/tools/apache-maven-3.9.15`
  - 将 Maven 加入 `~/.bash_profile`
  - 在沙箱外运行 `./init.sh`，完成真实全量验证
  - 还原由验证生成的 tracked `target/` 构建产物变动，保持工作树干净
- 运行过的验证：
  - `mvn -version` → 成功，确认 Apache Maven 3.9.15 + Java 21
  - `./init.sh` → 成功，内部执行 `mvn clean test`
  - 全量结果：`openilink-sdk-java`、`meta-claw-core`、`meta-claw-gateway`、`meta-claw-gateway-weixin`、`meta-claw-store`、`meta-claw-vessel`、`meta-claw-bootstrap`、`meta-claw-cli` 全部 `SUCCESS`
- 已记录证据：
  - 2026-05-16 `./init.sh` 成功输出已用于更新长期状态文件
- 提交记录：待提交
- 更新过的文件或工件：
  - `claude-progress.md`
  - `clean-state-checklist.md`
  - `evaluator-rubric.md`
  - `feature_list.json`
- 已知风险或未解决问题：
  - `SpringAiLlmClientIntegrationTest` 会访问真实外部 provider，标准验证依赖网络与有效配置
  - `Expert/专家` 残留尚未清理
- 下一步最佳动作：
  1. 处理 `semantic-001`
  2. 之后再决定是否把 Maven Wrapper 作为工程化增强单独立项

### Session 004

- 日期：2026-05-16
- 本轮目标：清理仍会误导后来者的活跃工件遗留项
- 已完成：
  - 新增活跃工件清理设计与计划
  - 更新 README、根 POM、模块 POM、运行配置注释与 `SessionManagerTest` 文案
  - 将当前活跃工件统一到 `Vessel` / `数字员工` 语义
- 运行过的验证：
  - 活跃工件扫描：`rg -n "Expert|专家|meta-claw-session|ExpertRuntime|targetExpert|expertName" README.md pom.xml meta-claw-* --glob '!**/target/**'` → 无结果
  - `./init.sh`（沙箱内）→ 因 Mockito/ByteBuddy 自附加限制失败
  - `./init.sh`（沙箱外）→ 成功，9 个 reactor 模块全部 `SUCCESS`
- 已记录证据：
  - 活跃工件扫描为空
  - 2026-05-16 标准入口再次真实通过
- 提交记录：待提交
- 更新过的文件或工件：
  - `README.md`
  - `pom.xml`
  - `meta-claw-core/pom.xml`
  - `meta-claw-cli/pom.xml`
  - `meta-claw-bootstrap/pom.xml`
  - `meta-claw-bootstrap/src/main/resources/application.yml`
  - `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java`
  - `claude-progress.md`
  - `clean-state-checklist.md`
  - `evaluator-rubric.md`
  - `feature_list.json`
- 已知风险或未解决问题：
  - 历史设计文档仍保留 `Expert → Vessel` 迁移语境，这是有意保留的历史证据
  - 标准验证在受限沙箱内可能因 Mockito 自附加受限而失败，需要允许真实进程附加的环境
- 下一步最佳动作：
  1. 处理 `chat-001`

### Session 005

- 日期：2026-05-16
- 本轮目标：实现显式列出并恢复 Vessel 内已有 CLI 会话
- 已完成：
  - 将 `chat-001` 重新定义为显式会话发现与恢复
  - 为 `JsonlConversationStore` 增加 Vessel 绑定能力与 `listConversations(vesselId)`
  - 新增 `sessions <vessel>` 命令
  - 为 `chat <vessel>` 增加 `--resume <session-id>`
  - 恢复时重建当前 system prompt，并仅回放已有对话消息
- 运行过的验证：
  - `mvn test -pl meta-claw-store,meta-claw-cli -am` → 成功
  - `./init.sh` → 成功，9 个 reactor 模块全部 `SUCCESS`
- 已记录证据：
  - `JsonlConversationStoreTest` 新增 Vessel 作用域测试
  - `SessionsCommandTest` 与 `ChatCommandTest` 通过
  - `sessions` / `--resume` 的产品边界已落到数据层，不再依赖目录顺序猜测 Vessel
- 提交记录：待提交
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java`
  - `meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java`
  - `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/MetaClawCommand.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/SessionsCommandTest.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
- 已知风险或未解决问题：
  - 目前仍未做手动 CLI 交互验收；当前证据来自自动化测试与全量构建
- 下一步最佳动作：
  1. 由用户决定下一项功能优先级

### Session 006

- 日期：2026-05-16
- 本轮目标：把 Memory 从混合命名升级为独立领域边界
- 已完成：
  - 明确 `Memory = Short-term Memory + Long-term Memory`
  - 将会话历史相关模型与管理器迁入 `core.memory.shortterm`
  - 将偏好接口迁入 `core.memory.longterm`
  - 将存储实现迁入 `store.memory.shortterm` / `store.memory.longterm`
  - 将 prompt context 拆为显式 `User Preferences` 与 `Conversation History`
- 运行过的验证：
  - `mvn test -pl meta-claw-store,meta-claw-cli -am` → 成功
  - `./init.sh` → 成功，9 个 reactor 模块全部 `SUCCESS`
- 已知风险或未解决问题：
  - 当前无新增 blocker；后续长期记忆扩展仍待单独设计
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 011

- 日期：2026-05-18
- 本轮目标：修复 CLI 会话目录延迟创建，并把 prompt workspace 收敛到当前 Vessel 目录
- 已完成：
  - 为 `ShortMemoryStore` / `ShortMemoryManager` 增加 `initializeConversation(sessionKey)`
  - 新会话创建时立即生成 `<vessel>/conversations/<session-id>/history.jsonl`
  - `ChatCommand` 构建 prompt context 时，将 workspace 从 `.meta-claw` 根目录改为 `.meta-claw/vessels/<vessel>`
- 运行过的验证：
  - `mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
  - `./init.sh` → 成功；完成全仓编译并通过 P0 测试集
  - `./init.sh`（沙箱外真实环境）→ 成功；完成全仓编译并通过 P0 测试集
- 已记录证据：
  - `JsonlShortMemoryStoreTest` 新增“初始化会话即创建空 history 文件”覆盖
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 012

- 日期：2026-05-18
- 本轮目标：让短期会话消息实时以统一记忆实体落盘，并记录逐条消息时间
- 已完成：
  - `JsonlShortMemoryStore` 的 JSONL 磁盘格式从直接序列化 `SpiMessage` 改为 `MemoryEntry`
  - 短期消息写入时记录 `timestamp`，格式固定为 `yyyy-MM-dd HH:mm:ss`
  - 读取路径兼容旧版 `SpiMessage` JSONL，已有历史文件仍可恢复
  - 新增核心测试，直接在 `appendMessage()` 返回后读取文件，验证记录已经即时写入
- 运行过的验证：
  - `mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
- 已记录证据：
  - 新增“追加后立即可从 history 文件读到带时间 MemoryEntry”覆盖
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 013

- 日期：2026-05-18
- 本轮目标：把短期记忆接口层彻底统一为 `MemoryEntry`
- 已完成：
  - 新增 `MemoryEntryConverter`，集中处理 `SpiMessage` 与 `MemoryEntry` 转换
  - `ShortMemoryStore` / `ShortMemoryManager` 出入参统一切换为 `MemoryEntry`
  - `truncateByRound` 更名为重载 `getHistory`
  - `truncateByToken` 更名为 `getHistoryByToken`
  - `JsonlShortMemoryStore` 不再直接暴露 `SpiMessage`
  - `ChatCommand` 在 CLI 边界显式做模型转换
- 运行过的验证：
  - `mvn test -pl meta-claw-core,meta-claw-store,meta-claw-cli -am -Dtest=MemoryEntryConverterTest,JsonlShortMemoryStoreTest,ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
  - `./init.sh` → 成功；完成全仓编译并通过 P0 测试集
  - `./init.sh` → 成功；完成全仓编译并通过 P0 测试集
- 已记录证据：
  - `MemoryEntryConverterTest` 覆盖双向转换
  - `JsonlShortMemoryStoreTest` 全部切到 `MemoryEntry`
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 014

- 日期：2026-05-18
- 本轮目标：把短期历史窗口查询进一步收敛到基于 `sessionKey` 的接口
- 已完成：
  - 删除 `getHistory(List<MemoryEntry>, int)`
  - `getHistory(String sessionKey, int limit)` 直接承担按轮数读取历史
  - `getHistoryByToken(...)` 改为 `getHistoryByToken(String sessionKey, int maxTokens)`
  - `ChatCommand` 不再把外部历史列表重新传回 store，而是直接按 `sessionKey` 查询窗口
  - LLM 请求前单独补回 system prompt，避免持久化历史不含 system 消息时丢上下文
- 运行过的验证：
  - `mvn test -pl meta-claw-core,meta-claw-store,meta-claw-cli -am -Dtest=MemoryEntryConverterTest,JsonlShortMemoryStoreTest,ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
- 已记录证据：
  - `JsonlShortMemoryStoreTest` 已按“最近 N 轮”语义验证 `getHistory(sessionKey, limit)`
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 015

- 日期：2026-05-18
- 本轮目标：把记忆领域模型从 `MemoryEntry` 重构为 `Memory` 聚合模型
- 已完成：
  - 新增 `Memory`、`SessionMemory`、`MemoryMessage`、`PreferenceMemory`
  - 删除旧 `MemoryEntry` / `MemoryEntryConverter`
  - 短期记忆历史改为持久化 `MemoryMessage`
  - 每个会话目录新增 `summary.json` 的读写能力
  - 会话列表改为返回 `SessionMemory`
  - 长期偏好改为使用 `PreferenceMemory`
  - CLI 边界改用 `MemoryMessageConverter`
- 运行过的验证：
  - `mvn test -pl meta-claw-core,meta-claw-store,meta-claw-cli -am -Dtest=MemoryMessageConverterTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
  - `./init.sh` → 成功；完成全仓编译并通过 P0 测试集
- 已记录证据：
  - `JsonlShortMemoryStoreTest` 现覆盖 `summary.json` 读写
  - 新写入的消息文件不再带 `messageCount` 这类会话级字段
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 010

- 日期：2026-05-18
- 本轮目标：把主链业务对象改为 Spring 托管装配，移除业务调用链上的手工 `new`
- 已完成：
  - 将 prompt、vessel template、config loader、weixin converter 改为 Spring 组件
  - 新增 `MemoryManagerProvider`，通过 prototype bean 统一获取 short-term / long-term manager 与 store
  - 删除 `MemoryManagerFactory`
  - `ChatCommand`、`SessionsCommand`、`VesselRuntime`、`VesselManager`、`InitCommand`、`CreateCommand`、`WeixinChannel` 改为注入式依赖
  - `SpringAiLlmClient` 与 `VesselRuntime` 改为 prototype bean，由 Spring 根据运行时参数创建
  - 清理生产代码中原先那批手工业务装配 `new`
- 运行过的验证：
  - 生产代码扫描：`rg -n "new (MemoryManagerFactory|PromptContextFactory|TemplateLoader|SystemPromptBuilder|VesselTemplate|WeixinMessageConverter|VesselRuntime|VesselConfigLoader)\\(" ...` → 无结果
  - `./init.sh`（沙箱外真实环境）→ 成功；完成全仓编译并通过 P0 测试集
- 已知风险或未解决问题：
  - 当前 prototype provider 仍属于运行时装配边界；若后续 backend 类型继续增加，最好把 backend 名称与 provider 映射进一步抽象成注册表
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 007

- 日期：2026-05-17
- 本轮目标：删除旧 session/model 杂质，并把 Memory 扶正为“Manager 编排 + Store Backend”架构
- 已完成：
  - 删除未进入主链路的 `core.session` 与对应单测
  - 将配置对象迁入 `core.config`，将消息流对象迁入 `core.message`
  - 统一 short-term memory 的公开消息模型为 `SpiMessage`
  - 新增 `MemoryConfig`、`ShortMemoryManager`、`LongMemoryManager`
  - 将 store concrete implementation 改为 `JsonlShortMemoryStore` / `FileLongMemoryStore`
  - 新增 `MemoryManagerFactory`，让 CLI 调用层不再直接依赖具体文件后端
  - 为 Vessel 配置与模板增加 `memory.short_term_store` / `memory.long_term_store`
  - 将长期偏好重新接回 `ChatCommand` 的 prompt context
- 运行过的验证：
  - `mvn test -pl meta-claw-core,meta-claw-store,meta-claw-cli -am -Dtest=VesselConfigLoaderTest,ConversationHistoryManagerTest,ShortMemoryManagerTest,LongMemoryManagerTest,PromptContextFactoryTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest,SessionsCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
  - `./init.sh` → 成功，9 个 reactor 模块全部 `SUCCESS`
- 已记录证据：
  - `ShortMemoryManagerTest` 与 `LongMemoryManagerTest` 覆盖配置驱动 backend 选择
  - `VesselConfigLoaderTest` 覆盖 memory 配置读取
  - `PromptContextFactory` 继续只依赖 `UserPreferenceStore` 语义，`ChatCommand` 当前传入 `LongMemoryManager`
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/config/*`
  - `meta-claw-core/src/main/java/meta/claw/core/message/*`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/*`
  - `meta-claw-store/src/main/java/meta/claw/store/memory/*`
  - `meta-claw-cli/src/main/java/meta/claw/cli/{ChatCommand,SessionsCommand}.java`
  - `meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`
  - `.meta-claw/vessels/default/config.yaml`
  - 长期状态文件
- 已知风险或未解决问题：
  - `SpringAiLlmClientIntegrationTest` 仍会访问真实外部 provider，标准验证依赖网络与有效配置
  - `MemoryManagerFactory` 当前只注册 JSONL / file 两个默认 backend；后续若增加新 backend，需要同时补充装配与配置文档
- 下一步最佳动作：
  1. 由用户决定下一项优先级

### Session 008

- 日期：2026-05-18
- 本轮目标：把开发初始化阶段的标准验证收敛为只维护主链路 P0 测试
- 已完成：
  - 新增初始化阶段 P0 测试基线收敛设计与实施计划
  - 将 `./init.sh` 从 `mvn clean test` 改为“全仓编译 + P0 测试集”
  - 删除当前阶段不再维护的旁支测试与第三方 `openilink-sdk-java` 测试
  - 当前仓库只保留 7 个 P0 测试类：
    - `VesselConfigLoaderTest`
    - `VesselManagerTest`
    - `SystemPromptBuilderTest`
    - `JsonlShortMemoryStoreTest`
    - `FileLongMemoryStoreTest`
    - `ChatCommandTest`
    - `MessageFlowIntegrationTest`
- 运行过的验证：
  - `find . -path '*/src/test/java/*Test.java' | sort` → 确认仓库只剩 7 个 P0 测试类
  - `./init.sh`（沙箱内）→ 编译阶段成功；P0 测试阶段在 `MessageFlowIntegrationTest` 因 Mockito/ByteBuddy 自附加限制失败
  - `./init.sh`（沙箱外真实环境）→ 成功；全仓编译通过，7 个 P0 测试类全部通过
- 已记录证据：
  - 新标准入口已能把全仓编译与 P0 测试分开执行
  - 第三方 `openilink-sdk-java` 当前只参与编译，不再参与初始化阶段测试基线
- 更新过的文件或工件：
  - `init.sh`
  - `docs/superpowers/specs/2026-05-18-p0-test-baseline-reduction-design.md`
  - `docs/superpowers/plans/2026-05-18-p0-test-baseline-reduction.md`
  - 多个已删除测试文件
  - 长期状态文件
- 已知风险或未解决问题：
  - `MessageFlowIntegrationTest` 仍依赖 Mockito 自附加；在受限沙箱里仍需允许真实进程附加，才能完成标准入口验证
  - 当前 P0 测试集是初始化阶段策略，不代表未来长期最终测试面
- 下一步最佳动作：
  1. 回到 Memory 重构主线

### Session 009

- 日期：2026-05-18
- 本轮目标：统一 Memory 领域实体，并移除多余的 short-term / long-term 抽象
- 已完成：
  - 新增统一实体 `MemoryEntry`
  - 删除 `PreferenceEntry` 与 `SessionSummary`
  - 删除 `ConversationHistoryManager`，把轮数裁剪、token 裁剪与摘要接口下沉到 `ShortMemoryStore`
  - 删除 `UserPreferenceStore`，让 `PromptContextFactory` 直接依赖 `LongMemoryStore`
  - `SessionsCommand`、`FileLongMemoryStore`、`JsonlShortMemoryStore` 已全部切到新实体
- 运行过的验证：
  - `mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest,FileLongMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功
  - `./init.sh`（沙箱外真实环境）→ 成功；完成全仓编译并通过 P0 测试集
- 已记录证据：
  - `JsonlShortMemoryStoreTest` 现覆盖会话列表、按轮数裁剪、按 token 裁剪
  - `FileLongMemoryStoreTest` 已全部改用 `MemoryEntry`
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/memory/*`
  - `meta-claw-store/src/main/java/meta/claw/store/memory/*`
  - `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
  - 长期状态文件
- 已知风险或未解决问题：
  - `getHistory(sessionKey, limit)` 仍保留为历史读取 API，未来若需要更强的窗口查询，可以再单独设计更明确的 query 形态
- 下一步最佳动作：
  1. 由用户决定下一项优先级
