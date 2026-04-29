# Meta-Claw Java 版智能助手与网关设计文档

**日期**: 2026-04-28  
**状态**: Draft → Approved  
**参考项目**:
- CowAgent (`/Users/kai/IdeaProjects/meta_claw/.rwa/CowAgent`) — Channel/网关架构、微信渠道实现
- expert_project (`/Users/kai/IdeaProjects/meta_claw/.rwa/expert_project`) — 骨架分层、Expert/数字分身、CLI、Tools、Skills、Evo

---

## 1. 项目概述

### 1.1 目标
基于 CowAgent 的 Channel 渠道架构和 expert_project 的骨架分层，使用 **Spring Boot + Spring AI** 构建 Java 版多智能体平台。每个"数字分身"（Expert）拥有独立的配置、知识库、记忆、工具和技能，可通过多种渠道（优先微信）与用户交互。

### 1.2 技术栈
| 层级 | 技术 |
|------|------|
| 语言 | Java 17+ |
| 框架 | Spring Boot 3.x, Spring AI |
| 事件总线 | Google Guava EventBus (AsyncEventBus) |
| CLI | picocli |
| 微信 SDK | openilink-sdk-java |
| 构建 | Maven |

### 1.3 核心能力
- **多渠道网关**: 微信个人号（优先）、后续扩展公众号/企微/钉钉/飞书
- **数字分身**: CLI 生成 Expert，每个 Expert 独立配置/记忆/知识库
- **工具调用**: 内置工具 + 插件扩展 + LLM Function Calling
- **技能系统**: agentskills.io 规范，三级加载层次
- **Evo 自我进化**: 热加载工具/技能（P4 实现）

---

## 2. 架构设计

### 2.1 模块划分

```
meta_claw/                                 ← /Users/kai/IdeaProjects/meta_claw
├── meta-claw-core/                        # 核心层
│   └── src/main/java/meta/claw/core/
│       ├── eventbus/EventBusWrapper.java
│       ├── events/UserMessageReceived.java
│       ├── events/ExpertResponseReady.java
│       ├── events/SessionModeChanged.java
│       └── model/Context.java, Reply.java, ContextType.java, ReplyType.java
│
├── meta-claw-session/                     # 会话层
│   └── src/main/java/meta/claw/session/
│       ├── SessionManager.java
│       ├── model/UserSession.java, ChatMode.java
│       └── storage/
│           ├── SessionStorage.java
│           ├── InMemorySessionStorage.java
│           ├── FileSessionStorage.java
│           └── RoutingSessionStorage.java
│
├── meta-claw-tools/                       # 工具层
│   └── src/main/java/meta/claw/tools/
│       ├── BaseTool.java
│       ├── ToolRegistry.java
│       ├── builtins/
│       │   ├── BashTool.java
│       │   ├── FileTool.java
│       │   ├── GitTool.java
│       │   ├── SearchTool.java
│       │   ├── WebFetchTool.java
│       │   ├── KnowledgeTool.java
│       │   └── MemoryTool.java
│       └── executor/ToolExecutor.java
│
├── meta-claw-skills/                      # 技能层
│   └── src/main/java/meta/claw/skills/
│       ├── SkillLoader.java
│       ├── SkillsManager.java
│       ├── model/Skill.java, SkillMetadata.java
│       └── SkillToolAdapter.java
│
├── meta-claw-gateway/                     # 网关层
│   └── src/main/java/meta/claw/gateway/
│       ├── channel/Channel.java
│       ├── channel/ChatChannel.java
│       ├── channel/weixin/WeixinChannel.java
│       ├── Gateway.java
│       ├── ChannelRegistry.java
│       ├── ChannelFactory.java
│       └── model/GatewayMessage.java, OutboundMessage.java
│
├── meta-claw-runtime/                     # 运行时层
│   └── src/main/java/meta/claw/runtime/
│       ├── ExpertRuntime.java
│       ├── ExpertManager.java
│       ├── AgentLoop.java
│       └── model/ExpertConfig.java, SessionConfig.java
│
├── meta-claw-cli/                         # CLI 层
│   └── src/main/java/meta/claw/cli/
│       ├── MetaClawCli.java
│       ├── ExpertApp.java
│       ├── commands/
│       │   ├── CreateCommand.java
│       │   ├── ChatCommand.java
│       │   ├── ServeCommand.java
│       │   └── ListCommand.java
│       └── templates/ExpertTemplate.java
│
└── meta-claw-bootstrap/                   # Spring Boot 启动
    └── src/main/java/meta/claw/app/
        └── MetaClawApplication.java
```

### 2.2 依赖关系

```
meta-claw-bootstrap
    ├── meta-claw-cli
    ├── meta-claw-runtime
    │   ├── meta-claw-tools
    │   ├── meta-claw-skills
    │   └── meta-claw-session
    │       └── meta-claw-core
    └── meta-claw-gateway
        └── meta-claw-core
```

**原则**: Gateway 和 Runtime 都只依赖 Core，通过 EventBus 解耦，互不直接依赖。

---

## 3. Gateway / Channel 层

### 3.1 设计来源
- **CowAgent** `channel/channel.py` → `Channel` 接口
- **CowAgent** `channel/chat_channel.py` → `ChatChannel` 抽象类
- **CowAgent** `channel/channel_factory.py` → `ChannelFactory`
- **expert_project** `expert/gateway/gateway.py` + `base.py` → `Gateway` + `ChannelRegistry`

### 3.2 Channel 接口

```java
public interface Channel {
    String getChannelType();
    void startup();
    void stop();
    void handleText(ChatMessage msg);
    void send(Reply reply, Context context);
}
```

### 3.3 ChatChannel 抽象类

```java
public abstract class ChatChannel implements Channel {
    protected final ConcurrentHashMap<String, SessionQueue> sessions = new ConcurrentHashMap<>();
    protected final ExecutorService handlerPool = Executors.newFixedThreadPool(8);
    
    // CowAgent 核心逻辑平移
    protected Context composeContext(ContextType type, String content, ChatMessage msg) {
        // 群/单聊过滤、前缀匹配、@处理、白名单、黑名单
    }
    
    protected void handle(Context context) {
        Reply reply = generateReply(context);
        reply = decorateReply(context, reply);
        sendReply(context, reply);
    }
    
    protected Reply generateReply(Context context) {
        // 通过 EventBus 发布 UserMessageReceived，由 AgentLoop 处理
    }
    
    protected Reply decorateReply(Context context, Reply reply) {
        // 添加 @前缀、前缀后缀、语音转换
    }
    
    // 生产者/消费者模型
    public void produce(Context context) { ... }
    protected void consume() { ... }  // 独立线程
}
```

### 3.4 Gateway 中央控制器

```java
@Component
public class Gateway {
    private final ChannelRegistry registry;
    private final EventBusWrapper eventBus;
    private final SessionManager sessionManager;
    
    public void registerChannel(Channel channel) {
        registry.register(channel);
        channel.startup();
    }
    
    public void onInboundMessage(GatewayMessage message) {
        UserSession session = sessionManager.getSession(
            message.getUserId(), message.getSource(), message.getAgentId());
        eventBus.post(new UserMessageReceived(message, session));
    }
    
    @Subscribe
    public void onResponseReady(ExpertResponseReady event) {
        Channel channel = registry.get(event.getChannelType());
        channel.send(event.getReply(), event.getContext());
    }
}
```

### 3.5 微信 Channel 实现

```java
@Component
public class WeixinChannel extends ChatChannel {
    private ILinkClient client;
    
    @Override
    public void startup() {
        client = ILinkClient.builder()
            .token(config.getToken())
            .build();
        LoginResult result = client.loginWithQR(callbacks);
        if (!result.isConnected()) throw new RuntimeException("微信登录失败");
        startMonitor();
    }
    
    @Override
    public void send(Reply reply, Context context) {
        String userId = context.getReceiver();
        switch (reply.getType()) {
            case TEXT -> client.push(userId, reply.getContent());
            case IMAGE_URL -> client.pushImage(userId, reply.getContent());
        }
    }
    
    private void startMonitor() {
        client.monitor(msg -> {
            ChatMessage chatMsg = convertToChatMessage(msg);
            handleText(chatMsg);
        }, monitorOptions, stopFlag);
    }
}
```

---

## 4. Session 层

### 4.1 设计来源
- **expert_project** `expert/gateway/session.py` + `expert/core/session.py`

### 4.2 SessionStorage 接口

```java
public interface SessionStorage {
    UserSession get(String sessionKey);
    void save(UserSession session);
    void delete(String sessionKey);
    List<UserSession> listAll();
    void cleanupExpired(long timeoutSeconds);
}
```

### 4.3 实现类

| 实现 | 说明 |
|------|------|
| `InMemorySessionStorage` | 内存存储，P1 默认 |
| `FileSessionStorage` | JSON 文件持久化，每个 session 一个文件 |
| `RoutingSessionStorage` | 路由代理，支持不同 agent 使用不同后端 |

### 4.4 SessionManager

```java
public class SessionManager {
    private final SessionStorage storage;
    private final long sessionTimeout;
    
    // Session Key = userId + ":" + source + ":" + agentId
    private String buildSessionKey(String userId, MessageSource source, String agentId) {
        return userId + ":" + source.name() + ":" + agentId;
    }
    
    public UserSession getSession(String userId, MessageSource source, String agentId) {
        String key = buildSessionKey(userId, source, agentId);
        UserSession session = storage.get(key);
        if (session == null) {
            session = UserSession.builder()
                .sessionKey(key)
                .userId(userId)
                .source(source)
                .agentId(agentId)
                .mode(ChatMode.SINGLE)
                .build();
            storage.save(session);
        }
        session.touch();
        return session;
    }
    
    public void setSingleMode(String userId, MessageSource source, String expertName, String agentId) { ... }
    public void setGroupMode(String userId, MessageSource source, String groupSessionId, String agentId) { ... }
    public List<String> getTargetExperts(UserSession session, List<String> availableExperts) { ... }
}
```

### 4.5 RoutingSessionStorage（多后端支持）

```java
@Component
public class RoutingSessionStorage implements SessionStorage {
    private final Map<String, SessionStorage> storages = new HashMap<>();
    private final Map<String, String> agentStorageConfig = new HashMap<>();
    
    @Override
    public void save(UserSession session) {
        String storageType = agentStorageConfig.getOrDefault(session.getAgentId(), "memory");
        storages.get(storageType).save(session);
    }
}
```

---

## 5. Core / EventBus 层

### 5.1 Guava EventBus 封装

```java
@Component
public class EventBusWrapper {
    private final AsyncEventBus asyncEventBus;
    private final EventBus syncEventBus;
    
    public EventBusWrapper() {
        this.asyncEventBus = new AsyncEventBus(
            Executors.newFixedThreadPool(10,
                new ThreadFactoryBuilder().setNameFormat("eventbus-%d").build()),
            (exception, context) -> log.error("EventBus error", exception)
        );
        this.syncEventBus = new EventBus();
    }
    
    public void post(Object event) { asyncEventBus.post(event); }
    public void register(Object subscriber) { asyncEventBus.register(subscriber); }
    public void unregister(Object subscriber) { asyncEventBus.unregister(subscriber); }
}
```

### 5.2 领域事件

```java
@Getter @AllArgsConstructor
public class UserMessageReceived {
    private final GatewayMessage message;
    private final UserSession session;
}

@Getter @AllArgsConstructor
public class ExpertResponseReady {
    private final String channelType;
    private final Reply reply;
    private final Context context;
}
```

### 5.3 Context & Reply 模型

```java
@Getter @Setter
public class Context {
    private ContextType type;        // TEXT, VOICE, IMAGE, IMAGE_CREATE...
    private String content;
    private Map<String, Object> kwargs = new HashMap<>();
    private String sessionId;
    private String receiver;
    private String channelType;
    private boolean isGroup;
    private ReplyType desireRtype;
}

@Getter @AllArgsConstructor
public class Reply {
    private ReplyType type;          // TEXT, IMAGE_URL, VOICE, ERROR, INFO...
    private String content;
    private String textContent;
}
```

---

## 6. Tools 层

### 6.1 设计来源
- **expert_project** `expert/runtime/tools/base.py` → `BaseTool`
- **expert_project** `expert/runtime/tools/registry.py` → `ToolRegistry`

### 6.2 BaseTool 抽象类

```java
public abstract class BaseTool {
    public abstract String getName();
    public abstract String getDescription();
    public abstract Map<String, Object> getParameters();  // JSON Schema
    public abstract String execute(Map<String, Object> args);
    
    public FunctionCallback toFunctionCallback() {
        // 转换为 Spring AI FunctionCallback
    }
}
```

### 6.3 ToolRegistry

```java
@Component
public class ToolRegistry {
    private final Map<String, BaseTool> tools = new ConcurrentHashMap<>();
    
    public void register(BaseTool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("Tool already registered: " + tool.getName());
        }
        tools.put(tool.getName(), tool);
    }
    
    public String execute(String name, Map<String, Object> args) {
        BaseTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Tool not found: " + name);
        return tool.execute(args);
    }
    
    public List<FunctionCallback> toFunctionCallbacks() {
        return tools.values().stream()
            .map(BaseTool::toFunctionCallback)
            .collect(Collectors.toList());
    }
}
```

### 6.4 内置工具列表

| 工具 | 说明 |
|------|------|
| `bash` | 执行 shell 命令 |
| `file` | 文件读写操作 |
| `git` | Git 操作 |
| `search` | 网络搜索 |
| `web_fetch` | 网页抓取 |
| `knowledge` | 知识库查询 |
| `memory` | 记忆读写 |

---

## 7. Skills 层

### 7.1 设计来源
- **expert_project** `expert/runtime/skills.py` → `SkillLoader` + `SkillsManager`

### 7.2 agentskills.io 规范

每个 Skill 是一个目录，包含：
```
skills/my-skill/
└── SKILL.md              # YAML frontmatter + Markdown instructions
```

`SKILL.md` 示例：
```markdown
---
name: web-search-enhanced
description: Enhanced web search with filtering
---

# Web Search Enhanced

When performing web searches, always...
```

### 7.3 SkillsManager

```java
public class SkillsManager {
    private final Path expertDir;
    private final List<Path> systemSkillsDirs;
    
    // 三级加载层次（后覆盖先）
    public List<Skill> loadAll() {
        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        
        // 1. System skills (lowest priority)
        for (Path dir : systemSkillsDirs) {
            loadFromDir(dir).forEach(s -> skillsByName.put(s.getMetadata().getName(), s));
        }
        
        // 2. Expert skills
        loadFromDir(expertDir.resolve("skills"))
            .forEach(s -> skillsByName.put(s.getMetadata().getName(), s));
        
        // 3. Workspace skills (highest priority)
        loadFromDir(expertDir.resolve("workspace/skills"))
            .forEach(s -> skillsByName.put(s.getMetadata().getName(), s));
        
        return new ArrayList<>(skillsByName.values());
    }
    
    // 将 skills 拼接为 system prompt 的一部分
    public String buildSkillsPrompt() {
        return skills.stream()
            .map(s -> "## " + s.getMetadata().getName() + "\n" + s.getInstructions())
            .collect(Collectors.joining("\n\n"));
    }
}
```

---

## 8. Runtime 层

### 8.1 ExpertConfig

```java
@Getter @Setter
public class ExpertConfig {
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String model;
    private String systemPrompt;
    private boolean memoryEnabled;
    private String knowledgeDir;
    private List<String> excludeTools;
    private SessionConfig session;
}

@Getter @Setter
public class SessionConfig {
    private String storageType;     // memory | file | redis
    private String filePath;
}
```

### 8.2 ExpertManager

```java
@Component
public class ExpertManager {
    private final Map<String, ExpertConfig> experts = new ConcurrentHashMap<>();
    private final Map<String, ExpertRuntime> runtimes = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadExperts() {
        Path expertsDir = Paths.get("experts");
        Files.walk(expertsDir)
            .filter(p -> p.endsWith("expert.yaml"))
            .forEach(path -> {
                ExpertConfig config = YamlMapper.read(path, ExpertConfig.class);
                experts.put(config.getId(), config);
                runtimes.put(config.getId(), createRuntime(config));
            });
    }
    
    public ExpertRuntime getRuntime(String expertId) {
        return runtimes.get(expertId);
    }
}
```

### 8.3 ExpertRuntime

```java
public class ExpertRuntime {
    private final ExpertConfig config;
    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final SkillsManager skillsManager;
    
    public ExpertRuntime(ExpertConfig config, ToolRegistry tools, SkillsManager skills) {
        this.config = config;
        this.toolRegistry = tools;
        this.skillsManager = skills;
        
        // 构建完整 system prompt
        StringBuilder sb = new StringBuilder();
        sb.append(config.getSystemPrompt()).append("\n\n");
        sb.append(skillsManager.buildSkillsPrompt());
        
        this.chatClient = ChatClient.builder(buildModel(config.getModel()))
            .defaultSystem(sb.toString())
            .defaultFunctions(toolRegistry.toFunctionCallbacks())
            .build();
    }
    
    public Reply chat(String userMessage, List<Message> history) {
        String response = chatClient.prompt()
            .user(userMessage)
            .call()
            .content();
        return new Reply(ReplyType.TEXT, response);
    }
}
```

### 8.4 AgentLoop

```java
@Component
public class AgentLoop {
    @Autowired private EventBusWrapper eventBus;
    @Autowired private ExpertManager expertManager;
    @Autowired private SessionManager sessionManager;
    
    @PostConstruct
    public void init() { eventBus.register(this); }
    
    @Subscribe
    public void onUserMessage(UserMessageReceived event) {
        GatewayMessage msg = event.getMessage();
        UserSession session = event.getSession();
        
        // 确定目标 Expert
        String expertId = session.getTargetExpert();
        if (expertId == null) {
            expertId = expertManager.listAvailableExperts().get(0);
            sessionManager.setSingleMode(
                session.getUserId(), session.getSource(), expertId, session.getAgentId());
        }
        
        ExpertRuntime runtime = expertManager.getRuntime(expertId);
        Context context = buildContext(msg, session);
        Reply reply = runtime.chat(msg.getContent(), Collections.emptyList());
        
        eventBus.post(new ExpertResponseReady(
            msg.getSource().name(), reply, context));
    }
}
```

---

## 9. CLI 层

### 9.1 设计来源
- **expert_project** `expert_cli/cli.py` → picocli 命令
- **expert_project** `expert_cli/app.py` → ExpertApp 会话管理

### 9.2 命令列表

| 命令 | 说明 |
|------|------|
| `meta-claw create <name>` | 创建新 Expert |
| `meta-claw chat <expert>` | 进入交互式会话 |
| `meta-claw serve` | 启动 Spring Boot 服务 |
| `meta-claw list` | 列出所有 Experts |

### 9.3 ExpertApp（会话封装）

```java
public class ExpertApp {
    private final ExpertRuntime runtime;
    private final String sessionId;
    private final List<Message> conversationHistory = new ArrayList<>();
    
    public String startSession() {
        this.sessionId = runtime.getMemoryManager().startSession();
        return sessionId;
    }
    
    public String chat(String message) {
        String response = runtime.chat(message, conversationHistory);
        conversationHistory.add(new Message("user", message));
        conversationHistory.add(new Message("assistant", response));
        return response;
    }
    
    public void endSession() {
        runtime.getMemoryManager().endSession(sessionId);
    }
    
    public ExpertInfo getExpertInfo() {
        return ExpertInfo.builder()
            .builtinTools(runtime.getToolRegistry().listTools())
            .skills(runtime.getSkillsManager().listSkills())
            .workspace(runtime.getWorkspaceDir())
            .build();
    }
}
```

### 9.4 ChatCommand（交互式会话）

```java
@Command(name = "chat")
public class ChatCommand implements Callable<Integer> {
    @Parameters(paramLabel = "EXPERT") String expertName;
    
    @Override
    public Integer call() {
        ExpertApp app = new ExpertApp(expertName);
        app.startSession();
        
        System.out.println("Entering chat with " + expertName + ". Type /exit to quit.");
        
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            
            if ("/exit".equals(input)) break;
            if ("/tools".equals(input)) { showTools(app); continue; }
            if ("/skills".equals(input)) { showSkills(app); continue; }
            if ("/clear".equals(input)) { app.clearHistory(); continue; }
            
            String response = app.chat(input);
            System.out.println(response);
        }
        
        app.endSession();
        return 0;
    }
}
```

---

## 10. 数据流与消息生命周期

```
用户发微信消息
    │
    ▼
┌─────────────────┐
│ WeixinChannel   │  ← openilink monitor() 收到消息
│  .handleText()  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  ChatChannel    │  ← composeContext() 构造 Context
│  .composeContext│    （前缀匹配、@处理、白名单过滤）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    produce()    │  ← 放入 session 队列
│    consume()    │  ← 消费者线程取出处理
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  EventBus.post  │  ← UserMessageReceived
│ UserMessageRecv │    （异步，不阻塞监听线程）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   AgentLoop     │  ← 订阅者处理
│  .onUserMessage │    （确定 Expert → ExpertRuntime.chat()）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  EventBus.post  │  ← ExpertResponseReady
│ ExpertResponse  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    Gateway      │  ← 订阅者，路由到对应 Channel
│ .onResponseReady│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ WeixinChannel   │  ← openilink push() 发送回复
│    .send()      │
└─────────────────┘
```

---

## 11. 错误处理与重试

| 层级 | 策略 |
|------|------|
| **Channel 发送** | `ChatChannel._send()` 重试 3 次，指数退避 3s→6s→9s |
| **openilink 连接** | 监听 `onSessionExpired` 自动重新登录；`monitor()` 自带长轮询重连 |
| **EventBus 订阅者** | Guava `SubscriberExceptionHandler` 捕获异常，不中断其他订阅者 |
| **AI 调用失败** | `ExpertRuntime.chat()` try-catch，返回 `Reply(ReplyType.ERROR, "服务异常")` |
| **工具执行失败** | `ToolRegistry.execute()` 捕获异常，返回 `"Error: " + e.getMessage()` |
| **全局异常** | Spring Boot `@ControllerAdvice` 兜底 |

---

## 12. 测试策略

| 层级 | 测试方式 |
|------|----------|
| **Channel** | Mockito 模拟 `ILinkClient`，测试消息收发 |
| **Session** | `InMemorySessionStorage` 内存实现方便单元测试 |
| **EventBus** | 直接 post 事件，验证订阅者触发 |
| **Tools** | 各 BaseTool 子类独立单元测试 |
| **Runtime** | `TestChatClient` 模拟 AI 调用 |
| **集成测试** | `@SpringBootTest` 启动完整上下文，端到端消息流 |

---

## 13. 项目分解（实施阶段）

| 阶段 | 子项目 | 核心内容 |
|------|--------|----------|
| **P1** | 核心骨架 + 微信渠道 | Gateway/Channel 抽象、微信 Channel（openilink）、Spring AI 基础对话、单 Expert 跑通 |
| **P2** | Expert/数字分身系统 | CLI create/chat/serve、多 Expert 管理、独立记忆/知识库 |
| **P3** | 工具与插件系统 | ToolRegistry、BaseTool 抽象、内置工具、插件加载 |
| **P4** | Evo 自我进化 | 热加载框架、代码生成审批工作流、运行时替换工具 |
| **P5** | 多渠道扩展 | 公众号、企微、钉钉、飞书等 Channel 适配器 |

**本文档聚焦 P1 设计**。

---

## 14. Expert 目录结构（创建时生成）

```
experts/
└── code-expert/
    ├── expert.yaml              # 配置
    ├── knowledge/               # 知识库文件
    ├── skills/                  # 专属技能
    │   └── my-skill/
    │       └── SKILL.md
    └── workspace/               # 工作区
        └── skills/              # 运行时动态创建的技能（最高优先级）
```

`expert.yaml` 示例：
```yaml
id: code-expert
name: "代码专家"
description: "擅长代码审查和重构"
emoji: "💻"
model: "deepseek-chat"
system_prompt: |
  你是一个资深的软件工程师...
knowledge_dir: "./knowledge"
memory_enabled: true
exclude_tools: ["bash"]
session:
  storage-type: memory
```
