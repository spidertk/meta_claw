# 干净状态检查清单

> 最后核对：2026-06-24
> 结论：已补全 HITL 全局配置加载与模板示例，彻底解决两个 Spring 循环依赖，并修复 HITL 运行时 NPE。`GlobalConfig` 与 `VesselConfig` 共用独立类 `HitlConfig`；`HitlSubSystem` 在 `@PostConstruct` 中读取 `~/.meta-claw/config.yaml` 的全局 hitl 配置；按 Vessel 的 HITL 策略配置由 `VesselManager` 在加载/创建 Vessel 时直接注入 `ConfigurableHitlPolicy`；`ShortMemoryAdvisor` 使用 `ObjectProvider<VesselManager>` 延迟解析 `VesselManager`；`ConfigurableHitlPolicy` 在 `require`/`skip` 为 null 时回退到空集合，避免 `getSummary()`/`decide()` 空指针；`global-config.tmpl.yaml` 与 `vessel.meta.tmpl.yaml` 均包含 HITL 配置示例与继承/覆盖规则说明。`./init.sh` 全量通过，core 106 个测试全部通过，tool 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-24 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 106 个测试全部通过（含 HITL 全局加载、VesselManager HITL 配置、null require/skip NPE 测试），tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 058，记录全局 HITL 配置加载、模板示例更新、两次循环依赖修复、NPE 修复与验证
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `hitl-001` 已补充全局 HITL 配置加载、模板示例、两次循环依赖修复与 NPE 修复记录；CLI 真实端到端验证仍待用户执行
- [x] 没有任何半成品步骤处于未记录状态
  证据：`HitlConfig` 提取、`GlobalConfig`/`VesselConfig` 复用、`VesselManager` per-vessel HITL 配置、`HitlSubSystem` 全局加载、`ShortMemoryAdvisor` ObjectProvider 解环、`ConfigurableHitlPolicy` null 回退、模板示例更新、测试与状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入 CLI 真实 HITL 端到端验证

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
