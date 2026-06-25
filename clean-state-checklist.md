# 干净状态检查清单

> 最后核对：2026-06-25
> 结论：已重构 CLI HITL 审批交互并收口到 `CliHitlGate`。新增 `TerminalConfig` 把 `Terminal`/`LineReader` 注册为 Spring bean；`ChatCommand` 注入共享 `Terminal`/`LineReader`，移除本地构造与 `BufferedReader`，并把 `SpiStreamingCallback.onHitlSuspend()` 委托给 `HitlSubSystem.awaitResolution(ticket)` → `CliHitlGate.await(ticket)`；`CliHitlGate` 改为使用共享 `Terminal`/`LineReader` 统一审批提示；父 pom 与 `meta-claw-cli/pom.xml` 新增 `jline-reader` 依赖。`./init.sh` 全量通过，core 106 个测试全部通过，tool 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-25 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 106 个测试全部通过（含 HITL 全局加载、VesselManager HITL 配置、null require/skip NPE 测试），tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 059，记录 CLI HITL 审批交互收口、TerminalConfig 注入、依赖新增与验证
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `hitl-001` 已补充 CLI HITL 审批交互收口记录；CLI 真实端到端验证仍待用户执行
- [x] 没有任何半成品步骤处于未记录状态
  证据：`TerminalConfig` 新增、`ChatCommand` 注入与委托改造、`CliHitlGate` 共享终端资源、`jline-reader` 依赖、测试与状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入 CLI 真实 HITL 端到端验证

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
