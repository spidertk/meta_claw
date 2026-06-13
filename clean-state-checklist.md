# 干净状态检查清单

> 最后核对：2026-06-13
> 结论：方向 C 第一阶段完成。Spring Boot 3.5.15 + Spring AI 1.1.8 + Spring AI Alibaba 1.1.2.3 基线编译通过，本地工具（shell / file / git）保留自研，网页搜索改由 Alibaba searches starter 提供，GitHub 远程能力由 githubtoolkit starter 提供，`./init.sh` 可自动检测 JDK 21 与 Maven 路径并跑通 P0 测试。

- [x] 标准启动路径仍然可用
  证据：2026-06-13 执行 `./init.sh` 成功，内部 `mvn clean compile` + P0 测试完成，9 个 reactor 模块全部 SUCCESS；脚本自动使用 `~/.local/jdks/jdk-21.0.10+7/Contents/Home` 与 `~/.local/tools/apache-maven-3.9.15/bin/mvn`
- [x] 标准验证路径仍然可运行
  证据：`./init.sh` 全量通过，`ShellToolTest` 4/4、`FileToolTest` 4/4、`GitToolTest` 3/3 通过，tool 模块 18 个测试全部通过，core 36 个测试全部通过
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增 Session 044，记录方向 C 第一阶段完成
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 已更新 `tool-ecosystem-002` 并新增 `arch-001`；Alibaba searches/githubtoolkit 运行时 API Key 配置明确记录为待补充
- [x] 没有任何半成品步骤处于未记录状态
  证据：版本升级、Alibaba BOM、tool starter 替换、仓库配置修复、init.sh 增强、状态文件更新均已完成
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`，再进入用户指定的下一项功能

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分"验证证据"和 tracked `target/` 构建产物噪音
