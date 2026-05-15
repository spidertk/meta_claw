# 进度日志

## 当前已验证状态

- 仓库根目录：`/Users/kai/IdeaProjects/meta_claw`
- 当前架构基线：Java 21 + Maven 多模块仓库；已存在 `meta-claw-core`、`meta-claw-vessel`、`meta-claw-store`、`meta-claw-cli`、`meta-claw-gateway*`、`meta-claw-bootstrap`
- 标准启动路径：`./init.sh` 目前**不可用**。2026-05-15 实测在根目录执行后失败，因为脚本仍调用 `npm install`，但仓库根目录没有 `package.json`
- 标准验证路径：仓库实际验证入口应围绕 Maven；但 2026-05-15 当前 shell 环境执行 `mvn test` 失败，原因是 `mvn: command not found`
- 最近可见的已通过证据：各模块 `target/surefire-reports/*.txt` 显示上一轮 Maven 测试已通过，包括 `VesselManagerTest`、`SystemPromptBuilderTest`、`MemoryManagerTest`、`JsonlConversationStore*Test`、`FilePreferenceStoreTest` 等
- 当前最高优先级未完成功能：恢复可重复工作的标准入口（修正 `init.sh`，并明确 Maven 验证/启动路径）
- 当前 blocker：
  1. `init.sh` 仍是 npm 模板脚本，不符合当前 Java/Maven 仓库
  2. 当前环境缺少可直接调用的 `mvn`
  3. 工作树存在未跟踪文件 `package-lock.json`，系失败的 `npm install` 产生

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

- `./init.sh` 未迁移到当前 Java/Maven 实际启动路径
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
