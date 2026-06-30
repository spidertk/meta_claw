# 干净状态检查清单

> 最后核对：2026-06-30
> 结论：已完成 multimodal knowledge extension 计划的 Task 4。新增 `ContentExtractor` SPI、`ExtractionContext`、`ContentExtractorService`、`TextExtractor` 及对应单元测试；`ContentExtractorServiceTest` 2/2 通过。为编译 `ExtractionContext` 提前创建 `AssetManager` 接口，具体实现留待 Task 5。

- [x] 标准启动路径仍然可用
  证据：`mvn -pl meta-claw-tool -am test -Dtest=ContentExtractorServiceTest -Dsurefire.failIfNoSpecifiedTests=false -q` 使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn` 通过
- [x] 标准验证路径仍然可运行
  证据：上述定向测试 BUILD SUCCESS，tool 模块新增 2 个测试全部通过；全仓 `./init.sh` 基线未因本次改动破坏
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 064，记录 Task 4 实现、验证命令与结果
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已新增 `multimodal-knowledge-001` 并标记为 passing；`AssetManager` 接口已创建但实现仍为后续任务
- [x] 没有任何半成品步骤处于未记录状态
  证据：Task 4 要求的 5 个文件与 1 个测试文件均已创建并通过测试；状态文件已同步更新
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；Task 5 可基于已创建的 `AssetManager` 接口继续实现 `LocalAssetManager`

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
