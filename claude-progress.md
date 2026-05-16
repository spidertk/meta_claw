# 进度日志

## 当前已验证状态

- 仓库根目录：`/Users/kai/IdeaProjects/meta_claw`
- 当前架构基线：Java 21 + Maven 多模块仓库；已存在 `meta-claw-core`、`meta-claw-vessel`、`meta-claw-store`、`meta-claw-cli`、`meta-claw-gateway*`、`meta-claw-bootstrap`
- 标准启动路径：`./init.sh`
- 标准验证路径：`./init.sh` 内部执行 `mvn clean test`
- 最近已通过证据：2026-05-16 在真实 Maven 环境中执行 `./init.sh`，9 个 reactor 模块全部 `SUCCESS`
- 当前最高优先级未完成功能：`chat-001` 重启后恢复同一会话历史
- 当前 blocker：
  1. 当前无 blocker

## 与设计文档对齐后的真实现状

### 已经落地

- `Vessel` 语义主干已完成：`VesselManager`、`VesselRuntime`、`VesselResponseReady` 已替代核心 `Expert*` 代码
- `VesselManager.loadVessels()` 已改为复用 `VesselConfigLoader`
- Vessel 模板中的 `provider` 字段已正确存在
- CLI 基础命令已存在：`init`、`create`、`list`、`delete`、`config`、`chat`
- Prompt Engineering Phase 2 的主件已存在：`PromptContext`、`TemplateLoader`、`SystemPromptBuilder`、`PromptContextFactory`
- `MemoryManager` 已存在并被 `ChatCommand` 使用
- `meta-claw-store` 已存在，含 `JsonlConversationStore` 与 `FilePreferenceStore`
- `ChatCommand` 已把用户/assistant 消息追加到 `JsonlConversationStore`

### 仍未完成或不能算完成

- `./init.sh` 已迁移到当前 Java/Maven 实际启动路径，并已于 2026-05-16 完整跑通
- `ChatCommand` 每次启动生成新 `sessionKey`，当前代码虽会落盘消息，但未体现“重启后自动恢复历史”
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
