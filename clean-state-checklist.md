# 干净状态检查清单

> 最后核对：2026-06-25
> 结论：已修复 Moonshot 流式请求偶发 `Connection prematurely closed BEFORE response` 的问题。`OpenAiWebClientFactory` 将连接池 `maxIdleTime` 缩短为 45 秒、`evictInBackground` 缩短为 15 秒并开启 `SO_KEEPALIVE`；`OpenAiLlmClientProvider` 将 provider 配置中的 `timeout` 透传给 RestClient/WebClient 的 read/response timeout。`./init.sh` 全量通过，core 107 个测试全部通过，tool 18 个测试全部通过。

- [x] 标准启动路径仍然可用
  证据：2026-06-25 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，core 107 个测试全部通过，tool 模块 18 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 061，记录连接池调优、timeout 透传与验证结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `llm-001` 已补充连接稳定性修复记录；`reasoning_content` 请求侧为空的问题作为已知风险记录
- [x] 没有任何半成品步骤处于未记录状态
  证据：`OpenAiWebClientFactory`、`OpenAiRestClientFactory`、`OpenAiLlmClientProvider` 修改、状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
