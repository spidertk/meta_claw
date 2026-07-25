# 微信渠道联通自测说明文档

> 版本：v1.0  日期：2026-07-22
> 适用范围：P0 阶段——`meta-claw-gateway-weixin`（微信 iLink 个人号 Bot）文本链路真实联通自测
> 关联设计：`docs/weixin-channel-integration-design.md`

---

## 1. 自测目标

验证「微信扫码登录 → 微信发文本 → meta-claw agent 处理（工具/记忆/知识库）→ 微信收到回复」全链路真实可用，并沉淀实测问题清单。

> ⚠️ iLink 是腾讯官方个人号 Bot 协议，**必须**用真实微信扫码、真实微信客户端收发消息，无法离线自动化。本自测为人工执行 + 逐项打钩。

## 2. 前置条件

| 项 | 要求 | 检查方式 |
|---|---|---|
| JDK | Java 21（`~/.local/jdks/jdk-21.0.10+7` 或 JAVA_HOME 已配） | `java -version` |
| Maven | 3.9.15（`~/.local/tools/apache-maven-3.9.15`） | 见 init.sh 检测逻辑 |
| LLM 配置 | 全局 `~/.meta-claw/config.yaml` 或 vessel 的 provider 可用（CLI 已验证过的同一套） | CLI 能正常对话即可 |
| Vessel | 至少一个 vessel（默认 `default`；知识库实测用 `alibaba`） | `ls .meta-claw/vessels/` |
| 微信 | 手机微信，且**愿意把一个微信号扫码绑定为 Bot**（该号成为机器人身份） | — |
| 网络 | 能访问 `ilinkai.weixin.qq.com` 与 `novac2c.cdn.weixin.qq.com` | `curl -I https://ilinkai.weixin.qq.com` |

## 3. 启动与登录

### 3.1 编译与基础验证

```bash
cd /Users/kai/IdeaProjects/meta_claw
./init.sh        # 全仓编译 + P0 测试，必须 BUILD SUCCESS
```

### 3.2 配置微信 token

首次自测**没有 token**，留空即可（会走扫码登录）：

```bash
# meta-claw-bootstrap 的 application.yml 已支持环境变量：
export WEIXIN_TOKEN=""     # 首次留空；P1 token 持久化落地前，二次启动可填上次扫码下发的 botToken
```

### 3.3 启动 gateway 模式

```bash
mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests
# 或：RUN_START_COMMAND=1 ./init.sh
```

### 3.4 扫码登录（在哪操作、怎么操作）

**不需要去任何网站注册，没有 AppID/审核流程**——二维码由本程序生成，全程只有「启动程序 → 微信扫码 → 确认」三步：

1. 观察启动日志，出现 `请扫码: https://liteapp.weixin.qq.com/q/...?qrcode=xxx&bot_type=3` 字样的 QR URL（程序调 iLink 的 `get_bot_qrcode` 接口实时生成）；
2. 用手机微信**扫一扫**扫该二维码（或把链接粘到微信「文件传输助手」里点开），微信会弹出 **ClawBot 确认页**；
3. 点确认后微信下发长期 `botToken`，日志出现登录成功（`botId=xxx@im.bot`）→ 进入长轮询监听。

⚠️ 三个关键认知：
- **你扫码用的微信号本身就是 Bot**：它的好友给它发消息，就是 agent 在回复。建议用**备用号**，不要用主力号；
- **该微信号需为 iOS 微信 8.0.70+ 且已放量 ClawBot 功能**（灰度放量中，部分账号暂无入口，表现为扫码后无 ClawBot 确认页）；
- **立即从日志抄下 botToken 备用**（当前版本 token 不持久化，重启需重新扫码，见设计文档 G1）。

✅ **检查点 L1**：日志依次出现 QR URL → onScanned → 登录成功，无 `ILinkException`。

## 4. 联通自测用例

> 用**另一个微信号**（或让家人/同事）给 Bot 发消息；消息流向：微信 → iLink 长轮询 → WeixinChannel → Gateway → AgentLoop → VesselRuntime → 回推。

### U1 文本收发闭环（必过）

| 步骤 | 操作 | 预期 |
|---|---|---|
| 1 | 微信给 Bot 发：`你好` | 10~60s 内收到 agent 回复（首条含 LLM 调用，属正常延迟） |
| 2 | 再发：`1+1 等于几` | 回复 `2`（日志可见 `calculate` 工具调用） |
| 3 | 连续快速发 3 条消息 | 每条都有回复，无丢失、无串答 |

✅ **检查点 U1**：3 步全部有回复；日志有 `onInboundMessage` → `VesselResponseReady` → `push` 记录。

### U2 会话连续性（必过）

| 步骤 | 操作 | 预期 |
|---|---|---|
| 1 | 发：`我叫小明` | 正常应答 |
| 2 | 发：`我叫什么？` | 回答「小明」（短期记忆在同 session 生效） |

### U3 长期偏好记忆（必过）

| 步骤 | 操作 | 预期 |
|---|---|---|
| 1 | 发：`我喜欢简洁的回答` | agent 调 `memorySave` 并确认已记住 |
| 2 | 检查文件 | `.meta-claw/vessels/<vessel>/preferences/preferences.jsonl` 新增一行 |
| 3 | 发：`我有什么偏好？` | 回答中包含「简洁」（memorySearch / prompt 注入生效） |

### U4 知识库检索（alibaba vessel 才有效）

> 前置：路由到的 vessel 知识库非空。当前 `AgentLoop` 路由策略是「取第一个 vessel」——先确认 `alibaba` 是否会被路由到；若路由到的是 default，请把报告知识先采集进 default，或记录为实测问题（属设计文档 P4 多 vessel 路由缺口）。

| 步骤 | 操作 | 预期 |
|---|---|---|
| 1 | 发：`帮我看看不耐受检查报告里哪些食物是中度不耐受` | agent 调 `knowledgeRetrieve` 命中《上海大印90项食物不耐受检测报告》后作答，而不是说「没有相关信息」 |

### U5 重启与会话恢复（记录现状）

| 步骤 | 操作 | 预期（当前版本） |
|---|---|---|
| 1 | 停掉进程重新启动 | 需要**重新扫码**（G1：token 未持久化，属已知缺口，记录即可） |
| 2 | 重启后微信再发消息 | 可能收到**重复**的历史消息或丢失（G3：sync_buf 未持久化，属已知缺口，记录现象） |

### U6 边界与限制探测（记录现状）

| 步骤 | 操作 | 记录 |
|---|---|---|
| 1 | 给 Bot 发一张图片 | 当前版本入站图片被丢弃（G5），记录 agent 是否无响应 |
| 2 | 群聊探测（三步）：①用扫码号把 Bot 当普通好友拉进一个测试群；②群里发一条普通消息，观察日志是否出现带 `group_id`（预期形如 `xxx@chatroom`）的入站消息；③群里发 `/ai 你好`，观察 Bot 是否回复 | 验证设计文档 §3.4 的三个未定点：能否入群、群消息是否送达、无 @检测下前缀触发是否可行。实测结论回填 §3.4 |
| 3 | 超过 24h 不回话后，观察 Bot 能否主动 push | 预期 context_token 过期无法触达，记录实际行为 |

## 5. 通过标准

- **P0 联通判定通过**：L1 + U1 + U2 + U3 全部 ✅；
- U4 取决于 vessel 路由现状，若失败需定性为「路由问题」还是「检索问题」并记录；
- U5/U6 为现状记录项，不阻塞通过判定，但必须如实回填设计文档缺口表。

## 6. 故障排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 启动即报 `ILinkException` / 401 | token 失效（errcode -14 会话过期） | 清空 WEIXIN_TOKEN 重新扫码 |
| 日志没有 QR URL | token 非空但无效；或网络不通 | 留空 token；`curl -I https://ilinkai.weixin.qq.com` 查网络 |
| 扫码后一直 onExpired | QR 有效期短、超时未确认 | 重新启动获取新 QR，尽快扫码（最多自动刷新 3 次） |
| 微信发消息无回复、日志无 onInboundMessage | 长轮询断开 / 扫码号与发消息号是同一个 | 看 monitor 错误日志；**必须用另一个微信号发消息** |
| 有入站日志但无回复 | `NoContextTokenException`（contextToken 未缓存/过期） | 让用户再发一条消息重建会话；查 AgentLoop→VesselRuntime 链路日志 |
| 回复是「Error: ...」 | LLM provider 配置问题 | 先用 CLI（`chat` 命令）验证同 vessel 能正常对话 |
| 回复重复两条 | monitor 回调与队列重复消费 | 记录现象（WeixinChannel 直推模式与 ChatChannel 队列语义，设计文档 G10） |
| 重启后收到一堆历史消息 | sync_buf 未持久化（G3） | 已知缺口，P1 修复 |

## 7. 自测记录表（填写后归档）

| 用例 | 结果(✅/❌) | 实测现象与日志摘要 | 备注/回填缺口 |
|---|---|---|---|
| L1 扫码登录 | | | |
| U1 文本闭环 | | | |
| U2 会话连续 | | | |
| U3 偏好记忆 | | | |
| U4 知识库检索 | | | |
| U5 重启恢复 | | | |
| U6-1 图片入站 | | | |
| U6-2 群聊 | | | |
| U6-3 24h 主动触达 | | | |

- 自测人：____  日期：____  Bot 微信号（脱敏）：____  vessel：____
- 结论：☐ P0 联通通过  ☐ 未通过（原因：____）

## 8. 安全提醒

- `botToken`、`login.json`、`sync_buf.dat` 均为敏感凭证，**不要提交 git、不要发到群里**；
- 自测用的 Bot 微信号会真实暴露「机器人自动回复」行为，建议用备用号；
- 生产使用前补齐 `allowFrom` 白名单（设计文档 P1），避免任何人加 Bot 都能调用你的 LLM 额度。
