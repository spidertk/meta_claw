# 干净状态检查清单

> 最后核对：2026-06-20
> 结论：已修复真实 CLI 流式工具调用回调未触发的问题。根因是 Moonshot 把 `tool_calls[].function.arguments` 分段发送，且 `finishReason == "tool_calls"` 的最终 chunk 本身 `delta` 为空，导致原解析逻辑永远失败。已改为累积 arguments 并把 finishReason 判断移到 `am.hasToolCalls()` 之外，确保 tool call 完成时必然解析并通知 UI。同时把 assistant 的 `reasoningContent` 在 `toSpringMessage` 与执行器中保留；但受 Spring AI 1.1.8 `OpenAiChatModel` 不读取 `AssistantMessage.properties` 的限制，第二次请求仍只能由 `MoonshotSerializerModule` 补上空的 `reasoning_content` 字段。`./init.sh` 全量通过，core 96 个测试全部通过，tool 18 个测试全部通过。

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
