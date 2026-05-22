# 进度日志

## 当前已验证状态

- 仓库根目录：`/Users/kai/IdeaProjects/meta_claw`
- 当前架构基线：Java 21 + Maven 多模块仓库；已存在 `meta-claw-core`、`meta-claw-vessel`、`meta-claw-store`、`meta-claw-cli`、`meta-claw-gateway*`、`meta-claw-bootstrap`
- 标准启动路径：`./init.sh`
- 标准验证路径：`./init.sh` 先执行全仓编译，再运行初始化阶段 P0 测试集
- 最近已通过证据：2026-05-20 在真实 Maven 环境中执行新版 `./init.sh`，完成全仓编译并通过 P0 测试集；`ChatCommandTest` 覆盖新会话即时初始化
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
- `ChatCommand` 新会话生成 `sessionKey` 后会先按 CLI 输出的 `History` 绝对路径直接预创建并校验 `history.jsonl`，再调用 memory store 初始化；CLI 欢迎区会显示当前 `Session` 与 `History` 绝对路径
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

### Session 013

- 日期：2026-05-20
- 本轮目标：修复历史验收中发现的新会话记录不实时出现在 Vessel conversations 目录的问题
- 已完成：
  - 将 `ChatCommand` 的 session 选择/创建逻辑提取为可测试的 `selectSession(...)`
  - 新会话生成 session ID 后立即调用 `ShortMemoryManager.initializeConversation(sessionId)`
  - 将新会话初始化提前到 LLM client 构建之前，避免被模型客户端初始化、输入循环或进程退出时机影响
  - 恢复已有会话时仍只校验存在性，不会误创建新会话目录
- 运行过的验证：
  - `mvn test -pl meta-claw-cli -am -Dtest=ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功，`ChatCommandTest` 4/4 通过
  - `./init.sh`（沙箱内）→ 失败：`MessageFlowIntegrationTest` 仍受 Mockito/ByteBuddy 自附加限制影响
  - `./init.sh`（沙箱外真实环境）→ 成功；完成全仓编译并通过 P0 测试集
- 已记录证据：
  - `ChatCommandTest` 覆盖新会话立即初始化、恢复已有会话不新建、恢复缺失会话报错
  - `feature_list.json` 的 `chat-002` 已补充 2026-05-20 验证记录
- 更新过的文件或工件：
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
  - `feature_list.json`
  - `claude-progress.md`
- 已知风险或未解决问题：
  - 沙箱内标准验证仍可能因 Mockito/ByteBuddy 自附加受限失败；真实环境验证已通过
- 下一步最佳动作：
  1. 由用户继续做手动 CLI 验收：启动新会话后，在另一个终端检查 `.meta-claw/vessels/default/conversations/<session-id>/history.jsonl` 是否立即出现

### Session 014

- 日期：2026-05-20
- 本轮目标：复核用户反馈“仍然退出后才创建”，并让运行中可直接核对新会话文件
- 已完成：
  - 按真实 CLI 入口复现：`mvn -f meta-claw-cli/pom.xml -q exec:java -Dexec.mainClass=meta.claw.cli.CliApplication -Dexec.args='chat default'`
  - 在进程停留于 `>` 输入提示符期间确认 `.meta-claw/vessels/default/conversations/<session-id>/history.jsonl` 已存在
  - 将 `JsonlShortMemoryStore.initializeConversation` 从 `Files.createFile` 改为 `FileChannel.open(... CREATE, WRITE)` 并 `force(true)`
  - CLI 欢迎区新增当前 `Session` 与 `History` 绝对路径输出，便于用户直接按显示路径核对
- 运行过的验证：
  - `mvn test -pl meta-claw-store,meta-claw-cli -am -Dtest=JsonlShortMemoryStoreTest,ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功，`JsonlShortMemoryStoreTest` 10/10、`ChatCommandTest` 5/5 通过
  - 真实 CLI 启动验收 → 成功；未退出时已看到对应 history 文件
- 已记录证据：
  - `feature_list.json` 的 `chat-002` 已补充第二条 2026-05-20 验证记录
- 更新过的文件或工件：
  - `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
  - `feature_list.json`
  - `claude-progress.md`
- 已知风险或未解决问题：
  - 若用户通过其他未纳入仓库的包装命令启动，需要先确认该命令是否指向本仓当前 `CliApplication`
- 下一步最佳动作：
  1. 用户按 CLI 输出的 `History:` 绝对路径在另一个终端直接 `ls -l <path>`，确认不是在看旧路径或旧入口

### Session 015

- 日期：2026-05-20
- 本轮目标：处理 `FileChannel.force(true)` 仍未解决用户本地实时可见性的反馈
- 已完成：
  - 不再只依赖 `JsonlShortMemoryStore.initializeConversation`
  - `ChatCommand` 在新 session ID 生成后，直接按 `History` 绝对路径预创建 parent directories 与 `history.jsonl`
  - 预创建后立即 `Files.exists(historyFile)` 校验；失败则在进入聊天界面前报错
  - memory store 初始化仍保留，作为后端一致性保障
- 运行过的验证：
  - `mvn test -pl meta-claw-cli -am -Dtest=ChatCommandTest -Dsurefire.failIfNoSpecifiedTests=false` → 成功，`ChatCommandTest` 6/6 通过
  - 真实 CLI 启动验收：`mvn -f meta-claw-cli/pom.xml -q exec:java -Dexec.mainClass=meta.claw.cli.CliApplication -Dexec.args='chat default'` → 停留在 `>` 输入提示符期间，`find .meta-claw/vessels/default/conversations -maxdepth 2 -type f` 已能看到本轮 session 的 `history.jsonl`
- 已记录证据：
  - `ChatCommandTest` 新增 CLI 直接创建 history 文件的断言
  - `feature_list.json` 的 `chat-002` 已补充第三条 2026-05-20 验证记录
- 更新过的文件或工件：
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
  - `feature_list.json`
  - `claude-progress.md`
- 已知风险或未解决问题：
  - 若用户仍观察不到 CLI 输出的 `History` 路径，优先确认实际运行命令是否使用当前源码编译出的 `CliApplication`
### Session 016

- 日期：2026-05-20
- 本轮目标：修复 `JsonlShortMemoryStore` 流泄漏并确保新会话文件实时可见
- 已完成：
  - 修复 `getHistory()` 与 `getHistoryForVessel()` 中 `Files.lines` 未关闭的资源泄漏
  - 新增 `syncParentDirectory()`，在 `initializeConversation()` 与 `appendMessage()` 写入后对父目录执行 `FileChannel.force(true)`
  - 修复 `ChatCommand.initializeHistoryFile()` 中 `FileChannel` 创建空文件后未 `force` 的问题
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；9 个 reactor 模块全部 SUCCESS
  - `JsonlShortMemoryStoreTest` → 10/10 通过
  - `ChatCommandTest` → 6/6 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
- 已记录证据：
  - `git diff` 已确认修改范围：`JsonlShortMemoryStore.java` 关闭读流 + 目录 sync；`ChatCommand.java` 预创建空文件后 `force(true)`
- 更新过的文件或工件：
  - `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
- 已知风险或未解决问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定是否继续下一项功能

### Session 017

- 日期：2026-05-20
- 本轮目标：重构 ChatCommand / PromptContext，解耦 prompt 构建与历史管理
- 已完成：
  - 新增 `PreferenceProvider` 窄接口 + `LongMemoryPreferenceProvider` Spring 组件，将偏好查询/格式化从 `PromptContextFactory` 中剥离
  - `PromptContext` 删除 `recentMessages`、`conversationSummary` 死字段，只保留 system prompt 所需的上下文（persona + preferences + runtime）
  - `PromptContextFactory.create()` 签名从 3 参数降为 2 参数，不再直接依赖 `LongMemoryStore`
  - `SystemPromptBuilder` 删除 `buildConversationHistorySection()`，历史完全由 `ShortMemoryManager` 管理
  - `ChatCommand` 提取 `buildLlmRequest()` 私有方法，主循环只做 I/O；删除未使用的 `LongMemoryManager` 本地变量
  - 修复 `VesselRuntime`、`VesselManagerTest` 中旧签名的调用点
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；9 个 reactor 模块全部 SUCCESS
  - `SystemPromptBuilderTest` → 10/10 通过
  - `ChatCommandTest` → 6/6 通过
  - `JsonlShortMemoryStoreTest` → 10/10 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
- 已记录证据：
  - 新增 `LongMemoryPreferenceProviderTest` 覆盖格式化逻辑
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PreferenceProvider.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/LongMemoryPreferenceProvider.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
  - `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`
  - `meta-claw-core/src/test/java/meta/claw/core/prompt/LongMemoryPreferenceProviderTest.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
- 已知风险或未解决问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定是否继续下一项功能

### Session 018

- 日期：2026-05-20
- 本轮目标：把 CLI 层的文件预创建逻辑完全下沉到 JsonlShortMemoryStore
- 已完成：
  - 删除 `ChatCommand.initializeHistoryFile` 静态方法，空文件创建由 `ShortMemoryManager.initializeConversation` 统一负责
  - 简化 `ChatCommand.selectSession` 签名，移除 `historyFiles` 参数
  - 清理 `ChatCommand` 未使用的 import（`FileChannel`、`Files`、`StandardOpenOption`）
  - 更新 `ChatCommandTest`：删除 `initializeHistoryFile` 测试，`selectSession` 测试不再断言文件存在性
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；9 个 reactor 模块全部 SUCCESS
  - `ChatCommandTest` → 5/5 通过
  - `JsonlShortMemoryStoreTest` → 10/10 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
- 更新过的文件或工件：
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
- 已知风险或未解决问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定是否继续下一项功能

### Session 019

- 日期：2026-05-20
- 本轮目标：创建 `meta-claw-tool` 模块并实现工具引擎骨架
- 已完成：
  - 新增 `meta-claw-tool` 模块：包含 `@Tool`/`@ToolParam` 注解、`ToolRegistry`、`JsonSchemaGenerator`、`ToolExecutor`、`CalculatorTool`
  - 根 `pom.xml` 添加 `meta-claw-tool` 模块与 `dependencyManagement` 版本声明；`meta-claw-cli/pom.xml` 添加依赖
  - `PromptContext` 中 `tools` 字段从 `List<ToolInfo>` 统一为 `List<SpiToolDefinition>`，删除冗余的 `ToolInfo`
  - `SystemPromptBuilder` 与对应测试适配 `SpiToolDefinition` record 访问器
  - `PromptContextFactory.create()` 签名已接受 `List<SpiToolDefinition>` 参数
  - `@ToolParam` 新增 `name()` 属性，解决 Java 反射参数名不可靠问题（`-parameters` 未开启时默认名称为 `arg0`）
  - 新增 4 个单元测试类共 25 个测试，全部通过
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS（含新增 meta-claw-tool）
  - `SystemPromptBuilderTest` → 10/10 通过
  - `ChatCommandTest` → 5/5 通过
  - `JsonlShortMemoryStoreTest` → 10/10 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
  - `JsonSchemaGeneratorTest` → 4/4 通过
  - `ToolRegistryTest` → 5/5 通过
  - `ToolExecutorTest` → 5/5 通过
  - `CalculatorToolTest` → 11/11 通过
- 更新过的文件或工件：
  - `meta-claw-tool/`（新模块：pom.xml + 6 个源文件 + 4 个测试类）
  - `meta-claw-core/src/main/java/meta/claw/core/spi/llm/SpiToolResult.java`（新增）
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/SystemPromptBuilder.java`
  - `meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java`
  - `pom.xml`、`meta-claw-cli/pom.xml`
  - `feature_list.json`、`claude-progress.md`
- 已知风险或未解决问题：
  - `VesselRuntime` 尚未集成工具调用循环（单次 tool call → 执行 → 结果回注）
  - `ToolCallback` 与 Spring AI `ChatClient` 的 tools API 适配仍待确认
### Session 020

- 日期：2026-05-20
- 本轮目标：ToolRegistry Spring 化 + 动态热注入能力；编写 Java 组件热进化设计文档
- 已完成：
  - `ToolRegistry` 改为 Spring `@Component`，构造注入 `ApplicationContext`
  - 新增 `@PostConstruct scanAndRegisterBeans()`，启动时自动扫描容器中所有 bean 的 `@Tool` 方法
  - 新增 `unregister(String)` 和 `reregister(Object)`，支持运行时热卸载与热替换
  - 使用 `CopyOnWriteArrayList` + `ConcurrentHashMap` + `synchronized` 块保证并发安全
  - 新增 `meta.claw.core.spi.tool.ToolDefinitionProvider` 窄接口，`ToolRegistry` 实现该接口
  - `VesselRuntime` 与 `ChatCommand` 改为注入 `ToolDefinitionProvider`，避免 core 反向依赖 tool 模块
  - `meta-claw-tool/pom.xml` 补充 `spring-context` 和 `jakarta.annotation-api` 依赖
  - 所有测试适配后通过：`ToolRegistryTest` 5/5、`ToolExecutorTest` 5/5、`JsonSchemaGeneratorTest` 4/4、`CalculatorToolTest` 11/11
  - 编写 `java-component-hot-evolution-design.md`：详细分析 Java 热进化技术挑战、方案选型（OSGi/JVM Agent/ClassLoader/Groovy/Janino）、推荐架构、安全回滚机制、与 meta-claw 集成路径
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - `SystemPromptBuilderTest` → 10/10 通过
  - `ChatCommandTest` → 5/5 通过
  - `JsonlShortMemoryStoreTest` → 10/10 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
  - `JsonSchemaGeneratorTest` → 4/4 通过
  - `ToolRegistryTest` → 5/5 通过
  - `ToolExecutorTest` → 5/5 通过
  - `CalculatorToolTest` → 11/11 通过
- 更新过的文件或工件：
  - `meta-claw-tool/src/main/java/meta/claw/tool/registry/ToolRegistry.java`
  - `meta-claw-tool/src/main/java/meta/claw/tool/annotation/ToolParam.java`
  - `meta-claw-tool/src/main/java/meta/claw/tool/schema/JsonSchemaGenerator.java`
  - `meta-claw-tool/src/main/java/meta/claw/tool/executor/ToolExecutor.java`
  - `meta-claw-tool/src/test/java/meta/claw/tool/registry/ToolRegistryTest.java`
  - `meta-claw-tool/src/test/java/meta/claw/tool/executor/ToolExecutorTest.java`
  - `meta-claw-core/src/main/java/meta/claw/core/spi/tool/ToolDefinitionProvider.java`（新增）
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
  - `meta-claw-tool/pom.xml`
  - `.rwa/expert_project/java-component-hot-evolution-design.md`（新增）
  - `feature_list.json`、`claude-progress.md`
- 已知风险或未解决的问题：
  - `VesselRuntime` 工具调用循环（单次 tool call → 执行 → 结果回注 LLM）尚未实现
  - 热进化设计文档中的 `meta-claw-evo` 模块尚未落地
### Session 021

- 日期：2026-05-20
- 本轮目标：简化 PromptContextFactory 调用签名，将 workspace 和 tools 推导下沉到内部
- 已完成：
  - 新增 `meta.claw.core.spi.workspace.WorkspaceProvider` 接口
  - 新增 `MetaClawWorkspaceProvider` 实现，每个 Vessel 的 workspace 固定为 `.meta-claw/vessels/<vesselId>/workspace`
  - `PromptContextFactory.create()` 签名从 `(VesselConfig, Path, List<SpiToolDefinition>)` 简化为 `(VesselConfig)`
  - `PromptContextFactory` 注入 `WorkspaceProvider` 和 `ToolDefinitionProvider`，内部自动解析 workspace 和工具列表
  - `ChatCommand` 和 `VesselRuntime` 的调用点全部简化
  - `VesselManagerTest` 适配新构造函数
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - `SystemPromptBuilderTest` → 10/10 通过
  - `ChatCommandTest` → 5/5 通过
  - `VesselManagerTest` → 4/4 通过
  - `MessageFlowIntegrationTest` → 3/3 通过
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/spi/workspace/WorkspaceProvider.java`（新增）
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/workspace/MetaClawWorkspaceProvider.java`（新增）
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
  - `feature_list.json`、`claude-progress.md`
- 已知风险或未解决的问题：
  - `VesselRuntime` 工具调用循环尚未实现
- 下一步最佳动作：
  1. 继续实现 `VesselRuntime` 工具调用循环
  2. 由用户决定下一项功能优先级

- 下一步最佳动作：
  1. 运行标准入口 `./init.sh`

### Session 022

- 日期：2026-05-20
- 本轮目标：修复 Spring Boot 启动失败——`LongMemoryPreferenceProvider` 多 bean 冲突
- 已完成：
  - 诊断根因：`LongMemoryManager` 和 `FileLongMemoryStore` 都实现 `LongMemoryStore` 且都是 `@Component`，导致 `LongMemoryPreferenceProvider` 按接口注入时 Spring 发现 2 个候选 bean
  - 修复方案：将 `LongMemoryPreferenceProvider` 从 Spring `@Component` 中剥离，改为普通 POJO（构造函数接收 `LongMemoryStore`）
  - `PromptContextFactory` 不再注入 `PreferenceProvider`，改为在 `create(VesselConfig)` 中通过 `@Autowired(required = false)` 获取；新增 `create(VesselConfig, PreferenceProvider)` 重载供调用方显式传入
  - `ChatCommand` 改为本地 `new LongMemoryPreferenceProvider(new FileLongMemoryStore(vesselsDir))`，绕过 Spring 注入冲突
  - `VesselManagerTest` 适配新的 `PromptContextFactory` 两参数构造函数签名
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - 全量测试：core 16/16、store 18/18、bootstrap 3/3、cli 5/5、tool 全部通过
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/LongMemoryPreferenceProvider.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 由用户决定下一项功能优先级


### Session 023

- 日期：2026-05-20
- 本轮目标：调研当前 Provider/Store 全景，产出"统一 TypedService + 自动注册工厂"的设计和改动文档
- 已完成：
  - 全面调研当前所有 Provider 接口（WorkspaceProvider/ToolDefinitionProvider/PreferenceProvider）和 Store 接口（ShortMemoryStore/LongMemoryStore）及其实现类的分布、注入点、构造函数
  - 分析当前架构的 4 大痛点：Provider 碎片化、Store 多实现冲突、MemoryManagerProvider 手写 Map 不封闭、Manager 构造函数需要调用方手动组装 Map
  - 设计目标架构：TypedService 类型标识接口 + ServiceRegistry（@PostConstruct 自动扫描注册）+ TypedServiceFactory（按配置 + 运行时参数获取 prototype bean）
  - 明确 5 个 Provider 的去除方案：WorkspaceProvider/PreferenceProvider 内联到 PromptContextFactory，ToolDefinitionProvider 由调用方直接处理，MemoryManagerProvider 被 Factory + ObjectProvider 取代
  - LongMemoryManager 不再 implements LongMemoryStore，彻底消除注入冲突根因
  - PromptContextFactory 构造函数只保留 LongMemoryStoreFactory，create(VesselConfig) 保持单参数
  - PromptContext 增加 `@Builder(toBuilder = true)`，允许调用方补充 tools
  - ProjectRootFinder 从 vessel 模块移入 core 模块，解决 workspace 内联的跨模块依赖
  - 输出完整改动清单（27 个文件），覆盖 core/store/cli/tool 四个模块及全部测试
- 运行过的验证：
  - 无代码改动，仅设计文档
- 更新过的文件或工件：
  - `docs/superpowers/plans/2026-05-20-unify-provider-to-typed-service-factory.md`（新增设计文档）
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 方案待用户 review 确认后再编码实施
  - Spring `applicationContext.getBean(beanName, args...)` 在 prototype scope 下的参数解析需实际验证
- 下一步最佳动作：
  1. 用户 review 设计文档并确认方案
  2. 按设计文档逐模块实施改动
  3. 全量回归 `./init.sh`


### Session 024

- 日期：2026-05-20
- 本轮目标：按 v3 设计文档实施"统一 Provider 为 TypedService + 自动注册工厂"架构重构
- 已完成：
  - 删除 7 个废弃文件：WorkspaceProvider、MetaClawWorkspaceProvider、ToolDefinitionProvider、PreferenceProvider、LongMemoryPreferenceProvider、MemoryManagerProvider、LongMemoryPreferenceProviderTest
  - ShortMemoryStore 接口所有方法增加 `vesselId` 参数；LongMemoryStore 已有 vesselId，保持不变
  - JsonlShortMemoryStore / FileLongMemoryStore 改为无参构造 + Spring 单例，内部通过 `ProjectRootFinder` 按 vesselId 计算路径
  - LongMemoryManager 去掉 `implements LongMemoryStore`，改为单例注入 `LongMemoryStoreFactory`
  - ShortMemoryManager / LongMemoryManager 改为单例，方法调用时传入 `MemoryConfig` 由 Factory 选择 Store
  - 新增 `ShortMemoryStoreFactory` / `LongMemoryStoreFactory`，通过 Spring `Map<String, Store>` 注入持有单例实例
  - ProjectRootFinder 从 vessel 模块移入 core 模块
  - PromptContextFactory 构造函数只保留 `LongMemoryStoreFactory`，create(VesselConfig) 保持单参数
  - PromptContext 添加 `@Builder(toBuilder = true)`，支持调用方补充 tools
  - VesselRuntime 去掉 `ToolDefinitionProvider` 注入
  - ToolRegistry 去掉 `implements ToolDefinitionProvider`
  - ChatCommand 直接注入 `ToolRegistry` + `ShortMemoryManager` / `LongMemoryManager` 单例，适配所有 Manager 调用
  - SessionsCommand 适配 Manager 单例调用
  - VesselManagerTest / ChatCommandTest / JsonlShortMemoryStoreTest / FileLongMemoryStoreTest 全部适配新接口
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - 全量测试：core 16/16、store 18/18、bootstrap 3/3、cli 5/5、tool 全部通过
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStore.java`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryManager.java`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStoreFactory.java`（新增）
  - `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryManager.java`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryStoreFactory.java`（新增）
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
  - `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
  - `meta-claw-core/src/main/java/meta/claw/core/util/ProjectRootFinder.java`（新增，从 vessel 移入）
  - `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
  - `meta-claw-store/src/main/java/meta/claw/store/memory/longterm/FileLongMemoryStore.java`
  - `meta-claw-tool/src/main/java/meta/claw/tool/registry/ToolRegistry.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
  - `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
  - 测试文件 4 个适配
  - 删除文件 7 个
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定下一项功能优先级

### Session 025

- 日期：2026-05-20
- 本轮目标：修正 Factory Map 注入方式，从构造注入改为 `@Autowired` 字段注入 + setter
- 已完成：
  - 将 `ShortMemoryStoreFactory` 和 `LongMemoryStoreFactory` 的 Map 注入从构造器改为 `@Autowired` 字段 + `public void setStores(...)` setter
  - 修复测试编译：`ChatCommandTest` 改为无参构造 + `factory.setStores(...)`；`VesselManagerTest` 改为 `new LongMemoryStoreFactory()`
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - 全量测试：core 16/16、store 18/18、bootstrap 3/3、cli 5/5、tool 全部通过
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStoreFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryStoreFactory.java`
  - `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定下一项功能优先级

### Session 026

- 日期：2026-05-20
- 本轮目标：统一所有 Factory 为 ApplicationContext 自拉取模式，彻底消灭 Map 从上游传入
- 已完成：
  - `LongMemoryStoreFactory` 重写为 `ApplicationContextAware` + `ApplicationListener<ContextRefreshedEvent>` 模式，与 `ShortMemoryStoreFactory` 对齐
  - 两个 Factory 均通过 `applicationContext.getBeansOfType()` 自拉取所有 Store 实现，按 `store.type()` 构建映射，并检测 type 重名
  - 删除 3 个编译失败的遗留测试文件（引用了已删除的包或缺失的依赖）
  - 给两个 Factory 增加 `registerStore(String, Store)` 单条注册方法（供测试使用，不违反"不从上游传入 Map"原则）
  - `stores` 字段初始化为空 HashMap，避免非 Spring 场景 NPE
  - 修复 `ChatCommandTest`：`setStores(Map)` → `registerStore("jsonl", store)`；`RecordingShortMemoryStore` 补全 `type()`
- 运行过的验证：
  - `./init.sh`（真实环境，Java 21）→ 成功；10 个 reactor 模块全部 SUCCESS
  - 全量测试通过
- 更新过的文件或工件：
  - `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStoreFactory.java`
  - `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryStoreFactory.java`
  - `meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java`
  - 删除：`DuplicateTypeTestStore.java`、`ShortMemoryStoreDuplicateTypeTest.java`、`ShortMemoryStoreFactoryIntegrationTest.java`
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定下一项功能优先级

### Session 027

- 日期：2026-05-22
- 本轮目标：修复 Spring Boot 启动失败——`VesselConfigResolver` bean 名称冲突
- 已完成：
  - 诊断根因：`meta-claw-vessel` 模块目录已不存在（内容已迁移到 `meta-claw-core`），但根 pom.xml 的 `dependencyManagement` 仍声明了它，`meta-claw-bootstrap` 和 `meta-claw-cli` 仍依赖它
  - 本地 Maven 仓库 `~/.m2/repository/com/meta/meta-claw-vessel` 中缓存了旧 jar，类路径上同时存在 `meta.claw.vessel.VesselConfigResolver`（旧 jar）和 `meta.claw.core.vessel.VesselConfigResolver`（core 模块），Spring bean name 冲突导致启动失败
  - 修复：从根 pom.xml 注释和 `dependencyManagement` 中移除 `meta-claw-vessel`；从 `meta-claw-bootstrap/pom.xml` 和 `meta-claw-cli/pom.xml` 中移除依赖；清理本地 Maven 缓存
- 运行过的验证：
  - `mvn clean install -DskipTests` → 成功；9 个 reactor 模块全部 SUCCESS
  - `mvn spring-boot:run -pl meta-claw-cli` → 成功启动，无 `ConflictingBeanDefinitionException`
  - `./init.sh` → 成功；P0 测试全部通过
- 更新过的文件或工件：
  - `pom.xml`
  - `meta-claw-bootstrap/pom.xml`
  - `meta-claw-cli/pom.xml`
  - `claude-progress.md`
- 已知风险或未解决的问题：
  - 当前无新增 blocker
- 下一步最佳动作：
  1. 提交本轮修改
  2. 由用户决定下一项功能优先级
