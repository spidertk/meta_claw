# 干净状态检查清单

> 最后核对：2026-06-20
> 结论：已修复两个真实 CLI 问题。问题 1：CLI 流式工具调用时 `SpiStreamingCallback.onToolCall()` 未触发——Moonshot 将 `tool_calls[].function.arguments` 分段发送，原代码在每个 chunk 中立即解析 JSON 导致永远失败，已改为累积 arguments 并在 `finishReason == "tool_calls"` 时解析完整 JSON 后通知 UI。问题 2：第二轮 LLM 请求丢失 assistant 的 reasoning_content——`LlmClientManager.toSpringMessage()` 与 `SpiMessageConverter.toSpringMessage()` 未把 `SpiMessage.reasoningContent` 写入 `AssistantMessage.properties`，且 `StreamingAgentExecutor` / `AgentExecutor` 保存 assistant 消息时未保留 reasoningContent，已一并修复。`./init.sh` 全量通过，core 96 个测试全部通过，tool 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-20 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 96 个测试全部通过，tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增/更新 Session 054，记录累积式 tool-call 回调与 reasoningContent 透传的修复、测试与验证结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `spi-002` 已补充最新修复记录；真实 CLI 端到端验证仍待用户确认
- [x] 没有任何半成品步骤处于未记录状态
  证据：`LlmClientManager`、`StreamingAgentExecutor`、`AgentExecutor`、`SpiMessageConverter`、`ChatCommand` 修改、文档与状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入改进方案 Phase A（真实 LLM 端到端验证、Alibaba 流式 HITL fallback 等）

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
