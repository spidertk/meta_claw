# 干净状态检查清单

> 最后核对：2026-06-19
> 结论：修复两个运行时阻塞问题。问题 1：`HitlSubSystem` 中 `HitlGate` 注入歧义导致 Spring Boot 启动失败；为 `InMemoryHitlGate` 增加 `@ConditionalOnMissingBean(HitlGate.class)`，使其在 `CliHitlGate` 存在时自动退让。问题 2：`VesselProfile.promptVars()` 因 `VesselConfigBundle` 的 profile 字段访问方法返回 null 而在 `Map.of(...)` 中抛出 NullPointerException；统一将 null 转换为空字符串。`./init.sh` 全量通过，core 96 个测试全部通过。另输出 `docs/superpowers/plans/2026-06-17-agent-engine-risk-remediation-plan.md` 作为后续改进方案。

- [x] 标准启动路径仍然可用
  证据：2026-06-19 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 96 个测试全部通过，tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 053，记录两个阻塞问题的修复、测试与验证结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `hitl-001` 与 `vessel-001` 已补充修复记录；主设计文档中的风险与不足点未改变，仍按原状态跟踪
- [x] 没有任何半成品步骤处于未记录状态
  证据：`InMemoryHitlGate`、`VesselConfigBundle`、新增测试无、文档与状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入改进方案 Phase A（真实 LLM 端到端验证、Alibaba 流式 HITL fallback 等）

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
