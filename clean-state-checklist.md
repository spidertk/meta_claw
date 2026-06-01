# 干净状态检查清单

> 最后核对：2026-05-30
> 结论：全仓编译通过，旧包引用已清理完毕，仓库可按约定重新开始工作。

- [x] 标准启动路径仍然可用
  证据：2026-05-30 执行 `mvn clean compile`（全仓）成功，无编译错误
- [x] 标准验证路径仍然可运行
  证据：`mvn test -pl meta-claw-core` 通过；全仓编译零错误
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 030，记录 CLI/Store/Bootstrap 旧包引用修复
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` last_updated 已更新至 2026-05-30
- [x] 没有任何半成品步骤处于未记录状态
  证据：全仓库 grep 确认不再有任何 `meta.claw.core.config.`、`meta.claw.core.vessel.`、`meta.claw.core.util.` import 残留
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`（或 `mvn clean compile`），再进入下一个已记录功能

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
