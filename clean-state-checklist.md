# 干净状态检查清单

> 最后核对：2026-06-22
> 结论：已修复禁用 Spring AI 内部 tool execution 后 Moonshot 报 `"tool_call_id is not found"` 400 错误的问题。根因是 `SpiMessage` 未保存 `toolCallId`/`toolName`，`AgentExecutor`/`StreamingAgentExecutor` 把工具结果包装成 JSON 导致原始 content 丢失，且 `LlmClientManager.toSpringMessage()` 与 `VesselRuntime.toSpiMessages()` 把 tool 消息的 id/name 硬编码为 `"tool"`。已为 `SpiMessage`/`MemoryMessage` 新增 `toolCallId`/`toolName` 字段与工厂方法，所有执行器直接以原始结果和真实 id/name 构造 tool 消息，转换器优先使用字段、缺失时回退旧版 JSON 解析（含修复前已持久化的 JSON 包装历史消息）。新增 `LlmClientManagerToolMessageTest` 覆盖显式字段与旧版 JSON 包装两种路径。`./init.sh` 全量通过，core 97 个测试全部通过，tool 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-22 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 97 个测试全部通过（含新增 `LlmClientManagerToolMessageTest`），tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 056，记录 tool 消息 `toolCallId`/`toolName` 修复、新增单元测试以及 `init.sh` 基线更新
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `spi-002` 已补充最新修复记录（含 tool 消息 id/name 修复与旧版 JSON 兼容）；真实 CLI 端到端验证仍待用户确认
- [x] 没有任何半成品步骤处于未记录状态
  证据：`SpiMessage`、`MemoryMessage`、`MemoryMessageConverter`、`LlmClientManager`、`VesselRuntime`、`AgentExecutor`、`StreamingAgentExecutor`、`SpiMessageConverter`、`SpringAiAlibabaAgentEngine`、新增 `LlmClientManagerToolMessageTest`、文档与状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入真实 LLM 端到端验证

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
