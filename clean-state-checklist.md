# 干净状态检查清单

> 最后核对：2026-06-29
> 结论：已完成 multimodal knowledge extension 计划的 Task 9。`VisionDescriber` 与 `ImageExtractor` 已实现；`ImageExtractorTest` 1/1 通过，全量 `meta-claw-tool` 测试通过。

- [x] 标准启动路径仍然可用
  证据：上一轮 `./init.sh` 使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn` 全量通过；本轮新增代码未改动启动链路
- [x] 标准验证路径仍然可运行
  证据：`mvn -pl meta-claw-tool -am test -Dtest=ImageExtractorTest -Dsurefire.failIfNoSpecifiedTests=false -q` BUILD SUCCESS，`ImageExtractorTest` 1/1 通过；`mvn -pl meta-claw-tool -am test -q` BUILD SUCCESS，tool 模块全部测试通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 069，记录 Task 9 实现、验证命令与结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已新增 `multimodal-knowledge-006` 并标记为 passing；Task 10（扩展 `KnowledgeEntry` frontmatter 资产引用）尚未开始
- [x] 没有任何半成品步骤处于未记录状态
  证据：Task 9 要求的 `VisionDescriber`、`ImageExtractor`、`ImageExtractorTest` 均已完成并通过验证
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；Task 10 可基于当前 `ContentExtractor` SPI、`AssetManager`、`KnowledgeManager` 继续扩展 `KnowledgeEntry` frontmatter

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
