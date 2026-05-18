# AGENTS.md

这个仓库面向长时运行的 coding agent 工作流。目标不是尽可能快地产出代码，而是让每一轮会话结束后，下一个会话仍然能无猜测地继续工作。

## 开工流程

写代码前先做这些事：

1. 用 `pwd` 确认当前目录。
2. 读取 `claude-progress.md`，了解最新已验证状态和下一步。
3. 读取 `feature_list.json`，选择优先级最高的未完成功能。
4. 用 `git log --oneline -5` 看最近提交。
5. 运行 `./init.sh`。
6. 在开始新功能前，先跑必需的 smoke test 或端到端验证。

如果基础验证一开始就失败，先修基础状态，不要在坏的起点上继续叠新功能。

## 工作规则

- 一次只做一个功能。
- 不要因为“代码已经写了”就把功能标记为完成。
- 除非为了消除当前 blocker 的窄范围修复，否则不要扩大到其他功能。
- 实现过程中不要悄悄改弱验证规则。
- 优先依赖仓库里的持久化文件，而不是聊天记录。

## 开发规范

- 业务逻辑对象默认交给 Spring 容器管理。
  - 可复用的 service、manager、loader、converter、provider、factory 等，优先使用 `@Component`、`@Service`、`@Repository`、`@Configuration` 等方式纳入容器，并通过构造注入协作。
  - 业务调用链中不得直接 `new` 业务服务或可复用组件；如果对象依赖运行时参数，优先使用 Spring 管理的 provider / prototype bean，而不是在调用方手工拼装依赖图。
- DTO / 值对象统一使用 Lombok builder 风格。
  - 新增 DTO、事件对象、消息对象、配置快照对象等数据载体时，默认提供 `@Builder`。
  - 调用方创建 DTO / 值对象时，统一使用 `Type.builder()...build()`，不要直接 `new Type(...)`。
  - 只有集合、`StringBuilder`、reader、lock、第三方 SDK 回调等纯临时对象，才允许直接构造。
- 代码评审时，默认把“手工 `new` 业务对象”和“DTO 直接 `new`”视为需要解释或修正的设计问题。

## 必需文件

- `feature_list.json`：功能状态的唯一事实来源
- `claude-progress.md`：会话进度和当前已验证状态
- `init.sh`：统一的启动与验证入口
- `session-handoff.md`：较长会话可选的交接摘要

## 完成定义

一个功能只有在以下条件都满足时才算完成：

- 目标行为已经实现
- 要求的验证真的跑过
- 证据记录在 `feature_list.json` 或 `claude-progress.md`
- 仓库仍然能按标准启动路径重新开始工作

## 收尾

结束会话前：

1. 更新 `claude-progress.md`
2. 更新 `feature_list.json`
3. 记录仍未解决的风险或 blocker
4. 在工作处于安全状态后，用清晰的提交信息提交
5. 保证下一轮会话可以直接运行 `./init.sh`
