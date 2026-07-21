# 干净状态检查清单

> 最后核对：2026-07-21
> 结论：知识采集链路重构（资产 hash 去重 + LLM 调用合并 4→2 + HITL 审核入库）已完成并验证，`knowledge-acquire-refactor-001` passing；本轮 dryRun 语义调整（只提取不分析，approve 时补跑分析）与 CLI 工具执行转圈动画已验证，其余历史功能保持 passing。

- [x] 标准启动路径仍然可用
  证据：2026-07-21 本轮改动后 `./init.sh` 真实跑通；BUILD SUCCESS，core 119 个 P0 测试全部通过，tool 模块 26 个 P0 测试全部通过。
- [x] 标准验证路径仍然可运行
  证据：本轮改动后 `./init.sh` 全量通过；KnowledgeAcquisitionSmokeTest 8/8 通过（含新语义 dryRunSkipsAnalysisAndDefersItToApproval：dryRun 仅 1 次视觉调用 / 重复 dryRun 零调用 / approve 补跑分析后落库）。
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增证据 34 与 Session 079，记录 dryRun 跳过分析 + approve 补跑分析、CLI ToolSpinner 的设计与验证结果。
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `knowledge-acquire-refactor-001` 已补充 2026-07-21 证据（dryRun 新语义 + ToolSpinner）；未验证边界不变：①SpiMessageConverterMultimodalTest 硬编码 /tmp/test.png 的历史失败与本次无关；②真实 CLI 端到端图片采集（真实模型）与转圈动画效果未在本轮复测。
- [x] 没有任何半成品步骤处于未记录状态
  证据：Phase 1+2+3 均已实现并有测试覆盖；设计文档 `docs/knowledge-acquire-refactor-design.md` 状态已更新为「Phase 1+2+3 已实现」；Phase 4（purpose 模型路由、语义去重、采纳率统计）明确标记为可选未做。
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；本次改动未提交 git（按规则需用户确认后提交）。

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
