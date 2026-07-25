# 干净状态检查清单

> 最后核对：2026-07-25（Session 083）
> 结论：微信渠道接入（`channel-weixin-001`）in_progress——全路径设计 v2.0 + 本期实现 + P3 多模态 + agent 主动发媒体（SendMediaTool，解决知识库原始图片发不出）均已完成，渠道相关 35 个测试全绿，待用户真实联通自测（含 sendMedia 实测）。

- [x] 标准启动路径仍然可用
  证据：2026-07-25 P3 实现后 `./init.sh` 真实跑通，BUILD SUCCESS，9 个 reactor 模块全部 SUCCESS；改动模块已 `mvn install` 同步本地仓库（spring-boot:run 依赖本地 jar）。
- [x] 标准验证路径仍然可运行
  证据：本轮 `./init.sh` 全量通过，含 P3 新增 20 个测试与 SendMediaToolTest 6 个测试，init.sh 两处 VERIFY_CMD 均已同步。
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增证据 37/38 与 Session 083（sendMedia 主动发媒体）。
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `channel-weixin-001` 已补充 2026-07-25 实现证据；未验证边界：真实微信扫码联通（需用户手机操作，按 docs/weixin-channel-connectivity-selftest.md 执行）。
- [x] 没有任何半成品步骤处于未记录状态
  证据：设计文档 `docs/weixin-channel-integration-design.md` v2.0 明确划分本期实现范围与 P3/P4 设计定稿未实现部分；代码与设计一一对应。
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；本次改动及此前未提交改动（MemoryTool、知识索引注入、spinner v2、微信设计文档等）均未 commit，按规则需用户确认后提交。

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
