# 干净状态检查清单

> 最后核对：2026-06-30
> 结论：multimodal knowledge extension 计划 Task 1~15 已全部完成；knowledge 子系统已迁入 meta-claw-core，Spring 循环依赖已消除；meta-claw-bootstrap web 模式可正常启动。全量 `./init.sh` 通过，全量 `meta-claw-tool` 测试通过；`feature_list.json`、`claude-progress.md` 与本清单已更新。

- [x] 标准启动路径仍然可用
  证据：`./init.sh` 在当前环境（Java 21 + Maven 3.9.15）真实跑通；9 个 reactor 模块全部 SUCCESS，core 114 个测试全部通过，tool 模块 18 个 P0 测试全部通过。
- [x] 标准验证路径仍然可运行
  证据：`mvn -pl meta-claw-tool -am test -q` BUILD SUCCESS，tool 模块 57 个测试全部通过（1 个跳过）；`KnowledgeAcquisitionSmokeTest` 1/1 通过。
- [x] Bootstrap web 模式可启动
  证据：`mvn spring-boot:run -DskipTests` 在 `meta-claw-bootstrap` 真实启动，Tomcat 监听 8080，`ToolRegistry` 注册 6 个工具实例（含 `KnowledgeTool`），无 `HitlGate` 缺失错误。
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增证据 22 与 Session 071，记录 knowledge 迁移、循环依赖消除、HITL fallback 修复与验证结果。
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已新增 `arch-002` 并更新 multimodal-knowledge 系列描述；`hitl-001` 已补充 bootstrap 启动修复证据。
- [x] 没有任何半成品步骤处于未记录状态
  证据：knowledge 迁移、LlmClientManager 去 ToolRegistry 依赖、InMemoryHitlGate 无条件兜底、文档与状态文件均已完成并通过验证。
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；`meta-claw-bootstrap` 可直接执行 `mvn spring-boot:run -DskipTests` 启动。

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
