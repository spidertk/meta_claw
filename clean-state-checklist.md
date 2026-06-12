# 干净状态检查清单

> 最后核对：2026-06-12
> 结论：全仓编译测试通过，Phase 5（Metrics 子系统：任务完成与步数基础指标）已落地，仓库可按约定重新开始工作。

- [x] 标准启动路径仍然可用
  证据：2026-06-12 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，新增 `MetricsSubSystemTest` 2/2 通过，core 22 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 040，记录 Phase 5 实施
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已新增 `metrics-001` 并标记为 passing
- [x] 没有任何半成品步骤处于未记录状态
  证据：MetricsSubSystem、MetricSnapshot、MetricsSubSystemTest、init.sh 更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入用户指定的下一项功能

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
