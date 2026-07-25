# 微信渠道接入 · 全路径设计

> 版本：v2.0（全路径定稿）
> 定位：微信渠道从当前骨架到**最终形态**的唯一设计文档。后续所有实现（含 P3 多模态、P4 群聊/HITL）以本文档为准，不再重新设计。
> 验收手册：见 `docs/weixin-channel-connectivity-selftest.md`（P0 联通自测 U1-U6，不变）。

## 1. 设计原则

1. **一次设计到位**：配置模型、状态布局、路由模型、组件边界按最终形态定义；分阶段只改"实现深度"，不改"设计形状"。
2. **只用官方协议**：腾讯 iLink Bot 协议（`ilinkai.weixin.qq.com`，bot_type=3）。不引入 gewe/wechaty 等灰色方案。
3. **无冗余设计**：不设计协议不支持的能力（@检测、主动发起会话、历史消息拉取）；不为未来不确定需求预留抽象。
4. **多账号形状先行**：配置和注册表从第一天就是"账号列表"结构，但本期只跑通单账号；加账号 = 加配置项，零代码改动。

## 2. 最终形态总览

### 2.1 目标架构

```
微信号A(生活Bot)  微信号B(工作Bot)
      │ iLink 长轮询          │
┌─────▼───────────────────────▼──────┐
│ WeixinChannel[weixin:life]  [weixin:work] │  ← 1 账号 = 1 微信号 = 1 token = 1 Channel 实例
│   （登录态/sync_buf 持久化、白名单、状态上报）│
└─────┬───────────────────────┬──────┘
      │ gateway.onInboundMessage(msg, channelKey, defaultVesselId)
┌─────▼──────────────────────────────▼──────┐
│ Gateway                                    │
│  · /vessel 命令拦截（切换绑定）              │
│  · ChannelVesselRouter 解析 vesselId        │
│  · Context(channelKey + vesselId hint)     │
│  · EventBus: UserMessageReceived           │
└─────┬──────────────────────────────────────┘
┌─────▼──────────────────────────────────────┐
│ AgentLoop → VesselRuntime.chat()           │
│ EventBus: VesselResponseReady              │
└─────┬──────────────────────────────────────┘
      │ Gateway 按 context.channelKey 找回 Channel → send()
      ▼
   微信用户
```

### 2.2 关键语义

- **channelKey**：`weixin:<accountId>`（如 `weixin:main`）。渠道实例在注册表中的唯一键，也是回复路由的依据。单渠道类型多实例靠它区分。
- **chatKey**：对话标识。私聊 = 对端 userId；群聊 = groupId（P4）。路由表以 `{channelKey, chatKey}` 为键。
- **vesselId 解析优先级**（ChannelVesselRouter）：
  1. 路由表绑定 `{channelKey, chatKey} → vesselId`（用户通过 `/vessel xxx` 显式切换，持久化）；
  2. 账号配置 `default-vessel-id`；
  3. 系统第一个可用 Vessel（兜底，现状行为）。
- **回复路由**：Context 全程携带 channelKey，`VesselResponseReady` 到达 Gateway 后按 channelKey 找回正确的 Channel 实例发送。

## 3. 协议与 SDK 边界（既定事实，不再变）

- 登录：`loginWithQR` 扫码换长期 botToken；登录成功 SDK 自动更新 client token/baseUrl。
- 收消息：`monitor(handler, MonitorOptions, stopFlag)`，35s 长轮询；`MonitorOptions` 支持 `initialBuf` / `onBufUpdate`（断点续传游标）、`onError`、`onSessionExpired`（errcode -14）。
- 发消息：`push(to, text)` 用 SDK 缓存的 contextToken；`sendText(to, text, ctxToken)` 显式指定。
- context_token 24h 过期 → **只能回复，不能主动发起会话**（设计约束，所有功能不得依赖主动推送；管理端点走本地 HTTP 而非微信消息）。
- 消息模型：`WeixinMessage` 有 `groupId` 但**无昵称、无@列表** → 群聊只能用"前缀触发"（§8）。
- 媒体：`getUploadUrl` 预签名 + CDN + AES-128-ECB（`CDNMedia.aesKey`），P3 使用。
- 必须用**另一个微信号**给 Bot 发消息才触发入站；ClawBot 灰度要求 iOS 微信 8.0.70+。

## 4. 配置模型（最终 schema，本期落地）

```yaml
meta:
  claw:
    weixin:
      enabled: true                 # 渠道总开关；false 时不创建任何账号实例
      accounts:                     # 多账号列表；本期通常只配 1 个
        - account-id: main          # 必填，账号唯一标识，channelKey = weixin:main
          enabled: true             # 单账号开关
          token: ${WEIXIN_TOKEN:}   # 可选；stateDir 有 login.json 时以持久化为准
          base-url: https://ilinkai.weixin.qq.com   # 可选，空用 SDK 默认
          default-vessel-id: ""     # 可选；空 = 系统第一个 Vessel
          allow-from: []            # 私聊白名单（对端 userId）；空 = 不限制（仅调试期建议）
          groups: []                # P4 群聊绑定，见 §8；本期不实现解析外的逻辑
```

- 对应 `WeixinProperties @ConfigurationProperties(prefix = "meta.claw.weixin")`（含 `List<AccountProperties>`），放在 `meta-claw-gateway-weixin` 模块，由 `@EnableConfigurationProperties` 启用。
- 旧的 `meta.claw.weixin.token` 单值配置**删除**，AppConfig 中对应 `@Value` 一并删除。
- `groups[].group-id / vessel-id / trigger-prefix` 字段本期即定义在 `AccountProperties` 中（形状先行），P4 才消费。

## 5. 状态与持久化（最终布局，本期落地）

每个账号独立状态目录：`.meta-claw/channels/weixin/<accountId>/`

```
.meta-claw/channels/weixin/main/
├── login.json      # {"botToken","botId","baseUrl","userId","updatedAt"}，权限 600
└── sync_buf        # monitor 断点续传游标，纯文本，权限 600
.meta-claw/channels/
└── routes.json     # 全局路由表 {"weixin:main|wxid_xxx": "alibaba"}，权限 600
```

- **WeixinStateStore**（gateway-weixin 模块）：每账号一个实例，负责 login.json / sync_buf 的读写；写入用"临时文件 + atomic move"，并 `PosixFilePermissions` 设 600（非 POSIX 文件系统降级仅告警）。
- **登录态语义**：
  - 启动时 login.json 存在 → 用其中 token+baseUrl 构建 client，**跳过扫码直接 monitor**；若 token 已死，monitor 回调 `onSessionExpired` → 走重登录（§6.3）。
  - 扫码登录成功 → 立即写 login.json。
  - `relogin`（管理端点或会话过期触发）→ 停 monitor → 删 login.json → 重新扫码 → 写 login.json → 重启 monitor。
- **sync_buf 语义**：`onBufUpdate` 回调即写文件（每次覆盖写，体积小无性能问题）；重启后 `initialBuf` 读入，不漏消息。
- **routes.json**：ChannelVesselRouter 持有（§6.5），全局唯一文件，非账号级。

## 6. 组件设计

> 标注：【本期】= 本次实现；【P3】【P4】= 设计定稿、后续实现。

### 6.1 WeixinProperties / AccountProperties【本期】

`@ConfigurationProperties(prefix="meta.claw.weixin")`，字段与 §4 schema 一一对应。`groups` 元素为静态嵌套类 `GroupBinding{groupId, vesselId, triggerPrefix}`。

### 6.2 WeixinChannel 重构【本期】

现有类保留" extends ChatChannel、直推 EventBus"的骨架，改造点：

1. 构造参数改为 `(AccountProperties account, WeixinStateStore stateStore, Gateway gateway, WeixinMessageConverter converter)`。
2. `getChannelKey()` 返回 `"weixin:" + accountId`（见 §6.4）。
3. `startup()`：
   - login.json 存在 → 构建 client（token+baseUrl 来自 login.json）→ `startMonitor()`；
   - 不存在 → `login()`：扫码（`onQRCode` 回调除打日志外，把 URL 存到 `pendingQrUrl` 供管理端点查询）→ 成功写 login.json → `startMonitor()`。
4. `startMonitor()`：MonitorOptions 不再传 null：
   - `initialBuf` ← stateStore.loadSyncBuf()
   - `onBufUpdate` → stateStore.saveSyncBuf(buf)
   - `onError` → log.warn
   - `onSessionExpired` → 异步触发 `relogin()`（不得在 monitor 线程内直接重登录，避免阻塞/递归）
5. 入站 handler 前置过滤：
   - `allow-from` 非空且 fromUserId 不在名单 → log.info 后丢弃；
   - 记录 `lastInboundAt`（管理端点用）；
   - 空文本消息（图片/语音/文件）本期仍跳过，log.debug 标明 messageType（P3 接管）。
6. `relogin()`：`stopFlag.set(true)` → 等 monitor 线程退出 → stateStore.deleteLogin() → `login()` → `startMonitor()`（新 stopFlag）。整个方法同步执行，由调用方决定异步化。
7. 状态访问器（管理端点用）：`getAccountId()` / `isOnline()`（monitor 线程存活）/ `getBotId()` / `getPendingQrUrl()`（扫码中等确认时为最新 QR URL，其余为 null）/ `getLastInboundAt()`。
8. client 创建抽成 `protected ILinkClient createClient(String token, String baseUrl)`，便于测试子类替换。
9. `send()` 逻辑不变（文本 push，媒体类型文本兜底）。

### 6.3 WeixinChannelManager【本期】

`@Component`（gateway-weixin 模块），实现 `SmartLifecycle`（或 `@PostConstruct`/`@PreDestroy`）：

- 读 `WeixinProperties`；`enabled=false` 或 accounts 为空 → 记日志直接跳过。
- 对每个 `enabled` 的 account：创建 WeixinStateStore + WeixinChannel，调 `gateway.registerChannel(channel)`（启动失败只记日志，不拖垮整个应用——Gateway.registerChannel 已是该语义）。
- 维护 `Map<accountId, WeixinChannel>` 供管理端点查询与触发 relogin。
- `MetaClawApplication.run()` 中**删除**手工 `gateway.registerChannel(weixinChannel)`；AppConfig 中**删除** `weixinChannel` @Bean 与 `@Value weixinToken`。run() 只保留 `agentLoop.start()`。

### 6.4 channelKey 贯通【本期】

最小侵入改造，四处：

1. `Channel` 接口新增 `default String getChannelKey() { return getChannelType(); }`。
2. `ChannelRegistry.register()` 改按 `channel.getChannelKey()` 存（get/hasChannel 参数语义随之变为 channelKey，方法名不改）。
3. `Context` 新增 `channelKey` 字段（可空）。
4. `Gateway`：
   - `onInboundMessage` 新增完整签名 `(ChatMessage msg, String channelKey, String defaultVesselId)`：设置 `context.channelKey`；旧签名 `(msg, channelType)` 保留委托（channelKey=null，单渠道行为不变）。
   - `onResponseReady`：优先 `context.getChannelKey()` 找渠道，为空回退 `channelType`（兼容现有 CLI/测试事件）。

### 6.5 ChannelVesselRouter + /vessel 命令【本期】

- **ChannelVesselRouter**（gateway 模块，纯 POJO，AppConfig 手动 @Bean）：
  - `String resolve(String channelKey, String chatKey, String defaultVesselId)`：按 §2.2 优先级解析。
  - `void bind(String channelKey, String chatKey, String vesselId)`：写内存 + 持久化 routes.json（key 格式 `channelKey|chatKey`）。
  - 构造时加载 routes.json；文件不存在视为空表。
- **Gateway 改造**（完整签名入口内）：
  1. chatKey = `msg.isGroup() ? groupId : otherUserId`（本期 groupId 不可得，群消息本期直接忽略并 log——见 §8）。
  2. 若 content 匹配 `^/vessel\s+(\S+)$` → `router.bind(...)`，直接发布 `VesselResponseReady`（INFO："已切换到 vessel xxx"），**不再进 AgentLoop**；vesselId 不存在也照绑（vessel 可能后注册），回复中提示。
  3. 否则 `vesselId = router.resolve(...)` 放入 `context.kwargs["vesselId"]`，发布 `UserMessageReceived`。
- **AgentLoop 改造**：`determineTargetVessel()` → `determineTargetVessel(Context)`：
  - kwargs["vesselId"] 非空且 `vesselManager.getRuntime(it)` 可用 → 用之；
  - 否则回退现有"第一个可用 Vessel"。
- Gateway 构造器追加可选 router 参数（AppConfig 装配）；router 为 null 时跳过命令拦截与 hint 注入（旧行为）。

### 6.6 WeixinAdminController【本期】

`@RestController`（gateway-weixin 模块，pom 增加 `spring-boot-starter-web`），注入 WeixinChannelManager：

| 端点 | 方法 | 返回 |
|---|---|---|
| `/bot/weixin/status` | GET | 全账号状态数组：`{accountId, online, botId, pendingQrUrl(脱敏为是否存在), lastInboundAt}` |
| `/bot/weixin/qrcode?account=main` | GET | `{accountId, qrUrl}`；无待确认 QR 时 404 |
| `/bot/weixin/relogin?account=main` | POST | 异步触发 relogin，返回 `{accepted: true}`；账号不存在 404 |

> 设计约束：管理操作只走本地 HTTP，不走微信消息（协议无法主动发起会话，§3）。

### 6.7 多模态【P3，设计定稿】

- **入站图片**：handler 检测 `MessageItemType.IMAGE` → 用 `CDNMedia` URL 下载 + `aesKey`（AES-128-ECB）解密 → 存 `.meta-claw/channels/weixin/<accountId>/media/<msgId>.<ext>` → 以多模态 user message（mediaPart=本地路径）走现有 SpiMessage 通道进 VesselRuntime；提示词附带路径，agent 可自行调用 `knowledgeAcquireFromFile`（差异化卖点：微信发报告图片→采集进知识库）。语音/文件/视频同路径落地为附件消息。
- **出站媒体**：Reply 携带本地文件路径 + 媒体类型 → `getUploadUrl` 预签名 → 加密上传 CDN → `sendMessage` 带媒体 item。新增 `Reply` 扩展字段而非新类型。
- 不重设计点：转换仍在 WeixinMessageConverter 内扩展；ChatMessage 增加 `mediaParts` 字段。

### 6.8 群聊【P4，设计定稿】

- 协议事实：有 groupId，无昵称/@列表 → **只能前缀触发**。
- 账号配置 `groups[]`（§4）：`{group-id, vessel-id, trigger-prefix}`。
- 入站群消息：groupId 不在绑定表 → 忽略；在绑定表 → 检查 trigger-prefix 前缀（默认 `/ai`），命中则去前缀后按私聊同一链路处理，chatKey=groupId，vessel 默认取绑定配置；每群每分钟限频（简单计数窗口，默认 6 条/分，超限静默丢弃）。
- 回复目标 = groupId（SDK push 的 to 语义同私聊）。
- ChatMessage 需新增 `groupId` 字段（converter 从 WeixinMessage.groupId 填入）——P4 唯一的数据模型变更，已在本文档声明。

### 6.9 渠道 HITL【P4，设计定稿】

- 现状：HITL 挂起只在 CLI（CliHitlGate）可审批，渠道用户无法审批。
- 设计：新增 `ChannelHitlGate implements HitlGate`（gateway 模块）：
  - `await(ticket)` 时：按 ticket 关联的 Context.channelKey 找渠道，向用户发送审批卡片文本（操作、参数摘要、"回复 y 同意 / n 拒绝"），ticket 存入 `PendingApprovalStore`（内存 Map，key=`channelKey|chatKey`）。
  - 渠道下一条入站消息到达时，Gateway 先查 PendingApprovalStore：有待审批且内容为 y/n（容忍大小写与"同意/拒绝"）→ 解析为审批结果 resume，**不作为普通消息进 AgentLoop**；非 y/n → 提示一次后仍按普通消息处理并取消该 pending。
  - HitlGate 的选择按 channelKey 前缀：CLI 会话走 CliHitlGate，渠道会话走 ChannelHitlGate（复合门 `RoutingHitlGate`，P4 实现时落地）。

## 7. 消息全路径时序（私聊文本，最终形态）

```
用户(另一微信号) → iLink → monitor 回调
  → WeixinChannel: allowFrom 过滤 → converter.convert
  → Gateway.onInboundMessage(msg, "weixin:main", defaultVesselId)
      ├─ /vessel 命令? → router.bind + INFO 回复（终）
      └─ vesselId = router.resolve(...) → Context(channelKey, kwargs.vesselId)
  → EventBus: UserMessageReceived
  → AgentLoop: determineTargetVessel(context) → VesselRuntime.chat(sessionId, content)
  → EventBus: VesselResponseReady
  → Gateway: registry.get(context.channelKey).send(reply, context)
  → ILinkClient.push(userId, text) → 微信用户
```

## 8. 安全设计【本期落地 1-3】

1. login.json / sync_buf / routes.json 均 600 权限，目录在 `.gitignore`（检查并补充 `.meta-claw/channels/`）。
2. token 只从环境变量或 login.json 读取，禁止写进 git 管理文件；日志不打印 token。
3. `allow-from` 白名单：非空时严格执行；为空时启动打 WARN（"不限制入站来源，仅建议调试期"）。
4. 管理端点无鉴权 → 只绑定本地回环使用场景，文档注明生产需反向代理鉴权（不实现鉴权本身）。

## 9. 分阶段实施路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| **本期** | §4 配置、§5 持久化、§6.1-6.6 全部组件、§8.1-3、§10 测试 | 本次实现 |
| P3 | §6.7 多模态入站/出站 | 设计已定 |
| P4 | §6.8 群聊、§6.9 渠道 HITL、ChatMessage.groupId | 设计已定 |
| P5 | 更多通道（slack/dingtalk 等）复用 channelKey+router 体系 | 不设计 |

本期完成后系统行为：扫码一次 → 重启免扫码（token 有效内）；断点续传不漏消息；会话过期自动转重扫码；HTTP 可查状态/取 QR/触发重登录；白名单过滤；多 vessel 按账号默认 + `/vessel` 命令切换并持久化；加第二个微信号只需在 accounts 里加一段配置并再扫一次码。

## 10. 测试策略【本期】

新增测试（gateway / gateway-weixin 模块 pom 补 junit + mockito）：

| 测试类 | 覆盖点 |
|---|---|
| `WeixinStateStoreTest` | login.json 读写往返、sync_buf 读写、缺文件返回空、deleteLogin |
| `WeixinMessageConverterTest` | 文本提取、groupId→isGroup、msgId 转换 |
| `ChannelVesselRouterTest` | 三级优先级、bind 持久化往返、未知回退 |
| `GatewayRoutingTest` | /vessel 命令拦截与回复、vesselId hint 注入、channelKey 回复路由 |
| `WeixinChannelTest` | mock ILinkClient：send() 文本 push、allowFrom 过滤、状态访问器 |
| `WeixinPropertiesBindTest` | accounts 列表绑定（Binder 或 ApplicationContextRunner，视依赖而定） |

接入 `init.sh`：`-pl` 增加 `meta-claw-gateway,meta-claw-gateway-weixin`（脚本中两处 VERIFY_CMD 都改），`-Dtest` 列表追加上述类（两处）。

## 11. 验收标准（本期）

- `mvn -am compile` 全仓通过；`./init.sh` 通过（含新增测试）。
- 真实联通按《联通自测手册》执行：L1（启动扫码/免扫码）+ U1（文本闭环）+ U2（会话连续）+ U3（重启恢复，验证 login.json/sync_buf 生效）为必过项；U5（偏好记忆）、U4（知识检索）在微信链路抽测。
- 管理端点三件套手工 curl 验证通过。
- `/vessel` 切换在真实微信对话中验证（两个 vessel 回复风格可区分）。

## 12. 已知边界与风险（不变）

- context_token 24h 过期：超时回复会失败，send() 记 ERROR 日志（不实现主动提醒）。
- bot_token 服务端失效时间未公开：靠 onSessionExpired → relogin 兜底。
- iLink 限流阈值未公开：P4 群聊限频是自我约束，非协议约束。
- 扫码的微信号本身成为 Bot：建议专用小号；iOS 微信 8.0.70+ 灰度限制。
