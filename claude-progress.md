# 进度日志

## 当前已验证状态

- 仓库根目录：`/Users/kai/IdeaProjects/meta_claw`
- 当前架构基线：Java 21 + Maven 多模块仓库；已存在 `meta-claw-core`、`meta-claw-vessel`、`meta-claw-store`、`meta-claw-cli`、`meta-claw-gateway*`、`meta-claw-bootstrap`
- 标准启动路径：`./init.sh` 已修正为 Maven 工作流；2026-05-15 实测会先检查 Maven，再进入验证流程。当前仍**不可完成**，因为环境缺少 `mvn`
- 标准验证路径：仓库实际验证入口应围绕 Maven；但 2026-05-15 当前 shell 环境执行 `mvn test` 失败，原因是 `mvn: command not found`
- 最近可见的已通过证据：各模块 `target/surefire-reports/*.txt` 显示上一轮 Maven 测试已通过，包括 `VesselManagerTest`、`SystemPromptBuilderTest`、`MemoryManagerTest`、`JsonlConversationStore*Test`、`FilePreferenceStoreTest` 等
- 当前最高优先级未完成功能：恢复可重复工作的标准入口（补齐可执行的 Maven 路径，并让 `./init.sh` 真正跑完整套验证）
- 当前 blocker：
  1. 当前环境缺少可直接调用的 `mvn`
  2. 工作树存在未跟踪文件 `package-lock.json`，系旧版错误入口中的 `npm install` 产生

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

- `./init.sh` 已迁移到当前 Java/Maven 实际启动路径，但尚未在具备 Maven 的环境中跑完整套验证
- 本轮未能重新跑通全量验证；因此不能把“当前仓库可从干净环境直接验证通过”记为事实
- `Expert/专家` 残留仍存在于测试文本与资源配置注释中，不满足“全仓语义完全清理”
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
