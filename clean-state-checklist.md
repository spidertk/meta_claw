# 干净状态检查清单

> 最后核对：2026-06-26
> 结论：已彻底解决 Spring AI 1.1.8 `OpenAiChatModel` 硬编码 `reasoningContent = null` 导致 OpenAI 兼容 provider 请求丢失真实 `reasoning_content` 的问题。采用同包 subclass `ReasoningAwareOpenAiChatModel` 重写 package-private 的 `createRequest(Prompt, boolean)`，在请求构造阶段直接从原始 Prompt 的 `AssistantMessage.metadata` 回填真实 `reasoning_content`，避免了 ThreadLocal 跨线程失效风险。已删除旧的 ThreadLocal 桥实现（`OpenAiReasoningContentContext`、`OpenAiReasoningContentAdvisor`、`OpenAiReasoningContentModule` 及其测试）。`./init.sh` 全量通过，core 112 个 P0 测试全部通过，tool 模块 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-26 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 112 个测试全部通过（含新增 `ReasoningAwareOpenAiChatModelTest`），tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 063，记录 subclass 方案、删除 ThreadLocal 桥与验证结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `llm-001` 已更新为 subclass 方案并说明 ThreadLocal 方案被替换；真实 CLI 端到端验证仍作为已知待办事项记录
- [x] 没有任何半成品步骤处于未记录状态
  证据：`ReasoningAwareOpenAiChatModel`、`ReasoningAwareOpenAiChatModelTest`、`OpenAiLlmClientProvider` 修改、ThreadLocal 相关代码删除、`init.sh` P0 基线更新、状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
