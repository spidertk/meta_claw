# 干净状态检查清单

> 最后核对：2026-07-19
> 结论：Knowledge 子系统 vesselId NPE 与本地图片 base64 编码问题均已修复（file:// URI → byte[]，由 Spring AI 序列化为 data URI）；`multimodal-knowledge-005`、`multimodal-knowledge-006`、`multimodal-core-002` 保持 passing。

- [x] 标准启动路径仍然可用
  证据：`./init.sh` 在当前环境（Java 21 + Maven 3.9.15）真实跑通；9 个 reactor 模块全部 SUCCESS，core 114 个测试全部通过，tool 模块 20 个 P0 测试全部通过。
- [x] 标准验证路径仍然可运行
  证据：修复后 `./init.sh` 全量通过；`mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests` 在上一轮验证中成功启动 Tomcat on 8080，无循环依赖报错。
- [x] 当前进度已经记录到进度日志
  证据：`claude-progress.md` 已新增证据 27/28 与 Session 074/075，分别记录 Knowledge 子系统 vesselId NPE 修复与本地图片 file:// URI → base64 data URI 修复的根因、方案与验证结果。
- [x] 功能状态真实反映了 passing 和未验证的边界
  证据：`feature_list.json` 的 `multimodal-knowledge-005`、`multimodal-knowledge-006`、`multimodal-core-002` 已补充 2026-07-19 修复证据并维持 passing；未引入新的未验证功能。真实 CLI 端到端图片采集未在本轮复测，已在 Session 075 风险项中记录。
- [x] 没有任何半成品步骤处于未记录状态
  证据：`SpiMessageConverter.toSpringMedia()` 已对 file scheme 读取字节走 base64 data URI、远程 URL 透传；新增两个测试用例覆盖；相关状态文件已同步更新。
- [x] 下一轮会话无需人工修复即可继续
  证据：下一轮可直接运行 `./init.sh`；bootstrap 启动路径已验证可用。

## 进入下一轮前必须先确认

1. `./init.sh` 仍能在当前环境真实跑通
2. 标准验证命令已经实际跑过，而不是只引用旧的 `target/surefire-reports`
3. 如再次运行全量验证，记得区分“验证证据”和 tracked `target/` 构建产物噪音
