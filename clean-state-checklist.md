# 干净状态检查清单

> 最后核对：2026-07-06
> 结论：上下文体系整合重构已完成；`context-001` 已通过验证。

- [x] 标准启动路径仍然可用
  证据：`./init.sh` 在当前环境（Java 21 + Maven 3.9.15）真实跑通；9 个 reactor 模块全部 SUCCESS，core 134 个测试全部通过，tool 模块 20 个 P0 测试全部通过。
- [x] 标准验证路径仍然可运行
  证据：`mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests` 成功启动 Tomcat on 8080，无循环依赖报错。
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增证据 24 与 Session 073，记录 MetaClawCallContext/VesselTask 合并、TaskContext.LlmCallContext、VesselContext 显式绑定、messages 与计数统一等实现、验证命令与结果。
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已新增 `context-001` 并标记为 passing；上一轮功能仍保持 passing。
- [x] 没有任何半成品步骤处于未记录状态
  证据：TaskContext、VesselContext、AgentExecutor、StreamingAgentExecutor、VesselRuntime、LlmClientManager、Advisors、MetaClawModelMetricsHook 等改动与对应状态文件均已完成并通过验证。
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；bootstrap 启动路径已验证可用。

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
