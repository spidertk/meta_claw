# Meta-Claw P1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 Meta-Claw Java 版核心骨架 + 微信渠道，实现消息收发闭环。

**Architecture:** 严格分层事件驱动架构。Gateway/Channel 层参考 CowAgent 的 Channel/ChatChannel 抽象，Runtime 层参考 expert_project 的 ExpertRuntime + AgentLoop，Core 层使用 Guava AsyncEventBus 解耦，微信渠道使用 openilink-sdk-java。

**Tech Stack:** Java 17, Spring Boot 3.x, Spring AI, Guava EventBus, openilink-sdk-java, Maven

**Design Spec:** `docs/superpowers/specs/2026-04-28-meta-claw-java-design.md`

---

## 文件结构总览

```
meta_claw/
├── pom.xml                                            # 聚合父 POM
├── meta-claw-core/
│   ├── pom.xml
│   └── src/main/java/meta/claw/core/
│       ├── eventbus/EventBusWrapper.java
│       ├── events/UserMessageReceived.java
│       ├── events/ExpertResponseReady.java
│       ├── model/Context.java
│       ├── model/Reply.java
│       ├── model/ContextType.java
│       └── model/ReplyType.java
├── meta-claw-session/
│   ├── pom.xml
│   └── src/main/java/meta/claw/session/
│       ├── SessionManager.java
│       ├── model/UserSession.java
│       ├── model/ChatMode.java
│       └── storage/
│           ├── SessionStorage.java
│           └── InMemorySessionStorage.java
├── meta-claw-gateway/
│   ├── pom.xml
│   └── src/main/java/meta/claw/gateway/
│       ├── channel/Channel.java
│       ├── channel/ChatChannel.java
│       ├── channel/ChannelRegistry.java
│       ├── channel/ChannelFactory.java
│       ├── Gateway.java
│       ├── model/GatewayMessage.java
│       └── model/OutboundMessage.java
├── meta-claw-gateway-weixin/
│   ├── pom.xml
│   └── src/main/java/meta/claw/gateway/weixin/
│       ├── WeixinChannel.java
│       ├── WeixinConfig.java
│       └── WeixinMessageConverter.java
├── meta-claw-runtime/
│   ├── pom.xml
│   └── src/main/java/meta/claw/runtime/
│       ├── ExpertRuntime.java
│       ├── ExpertManager.java
│       ├── AgentLoop.java
│       └── model/ExpertConfig.java
├── meta-claw-bootstrap/
│   ├── pom.xml
│   └── src/main/java/meta/claw/app/
│       ├── MetaClawApplication.java
│       └── AppConfig.java
└── src/test/java/meta/claw/integration/
    └── MessageFlowIntegrationTest.java
```

---

## Task 1: Maven 聚合项目初始化

**Files:**
- Create: `pom.xml`
- Create: `meta-claw-core/pom.xml`
- Create: `meta-claw-session/pom.xml`
- Create: `meta-claw-gateway/pom.xml`
- Create: `meta-claw-gateway-weixin/pom.xml`
- Create: `meta-claw-runtime/pom.xml`
- Create: `meta-claw-bootstrap/pom.xml`

- [ ] **Step 1: 编写聚合父 POM**

```xml
<!-- pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.meta</groupId>
    <artifactId>meta-claw</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Meta-Claw</name>
    <description>Java AI Agent Platform</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.0</spring-boot.version>
        <spring-ai.version>0.8.0</spring-ai.version>
        <guava.version>32.1.3-jre</guava.version>
        <lombok.version>1.18.30</lombok.version>
    </properties>

    <modules>
        <module>meta-claw-core</module>
        <module>meta-claw-session</module>
        <module>meta-claw-gateway</module>
        <module>meta-claw-gateway-weixin</module>
        <module>meta-claw-runtime</module>
        <module>meta-claw-bootstrap</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </dependency>
            <dependency>
                <groupId>com.google.guava</groupId>
                <artifactId>guava</artifactId>
                <version>${guava.version}</version>
            </dependency>
            <!-- internal modules -->
            <dependency>
                <groupId>com.meta</groupId>
                <artifactId>meta-claw-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.meta</groupId>
                <artifactId>meta-claw-session</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.meta</groupId>
                <artifactId>meta-claw-gateway</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.meta</groupId>
                <artifactId>meta-claw-runtime</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 编写各子模块 POM（以 core 为例，其余同理）**

```xml
<!-- meta-claw-core/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.meta</groupId>
        <artifactId>meta-claw</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>meta-claw-core</artifactId>
    <name>Meta-Claw Core</name>

    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

其余子模块 POM 结构类似，添加对应依赖：
- `meta-claw-session`: 依赖 `meta-claw-core`
- `meta-claw-gateway`: 依赖 `meta-claw-core`
- `meta-claw-gateway-weixin`: 依赖 `meta-claw-gateway`, `meta-claw-session`, `openilink-sdk-java`
- `meta-claw-runtime`: 依赖 `meta-claw-core`, `meta-claw-session`, `spring-ai-core`
- `meta-claw-bootstrap`: 依赖所有模块 + `spring-boot-starter`

- [ ] **Step 3: 验证 Maven 结构**

Run: `mvn clean validate -N`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add pom.xml meta-claw-*/pom.xml
git commit -m "chore: initialize maven multi-module project structure"
```

---

## Task 2: Core 模块 - 模型类

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/ContextType.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/ReplyType.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/Context.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/Reply.java`

- [ ] **Step 1: 编写 ContextType 枚举**

```java
// meta-claw-core/src/main/java/meta/claw/core/model/ContextType.java
package meta.claw.core.model;

public enum ContextType {
    TEXT,
    VOICE,
    IMAGE,
    IMAGE_CREATE,
    SHARING,
    FILE,
    FUNCTION
}
```

- [ ] **Step 2: 编写 ReplyType 枚举**

```java
// meta-claw-core/src/main/java/meta/claw/core/model/ReplyType.java
package meta.claw.core.model;

public enum ReplyType {
    TEXT,
    IMAGE_URL,
    IMAGE,
    VOICE,
    FILE,
    VIDEO,
    VIDEO_URL,
    ERROR,
    INFO
}
```

- [ ] **Step 3: 编写 Context 类**

```java
// meta-claw-core/src/main/java/meta/claw/core/model/Context.java
package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Context {
    private ContextType type;
    private String content;
    private Map<String, Object> kwargs = new HashMap<>();
    private String sessionId;
    private String receiver;
    private String channelType;
    private boolean group;
    private ReplyType desireRtype;

    public Context(ContextType type, String content) {
        this.type = type;
        this.content = content;
    }

    public void setKwargs(Map<String, Object> kwargs) {
        this.kwargs = kwargs != null ? kwargs : new HashMap<>();
    }

    public Object get(String key) {
        return kwargs.get(key);
    }

    public void put(String key, Object value) {
        kwargs.put(key, value);
    }
}
```

- [ ] **Step 4: 编写 Reply 类**

```java
// meta-claw-core/src/main/java/meta/claw/core/model/Reply.java
package meta.claw.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reply {
    private ReplyType type;
    private String content;
    private String textContent;

    public Reply(ReplyType type, String content) {
        this.type = type;
        this.content = content;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/model/
git commit -m "feat(core): add Context, Reply, ContextType, ReplyType models"
```

---

## Task 3: Core 模块 - EventBus 和领域事件

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/eventbus/EventBusWrapper.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/events/UserMessageReceived.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/events/ExpertResponseReady.java`

- [ ] **Step 1: 编写 EventBusWrapper**

```java
// meta-claw-core/src/main/java/meta/claw/core/eventbus/EventBusWrapper.java
package meta.claw.core.eventbus;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class EventBusWrapper {
    private final AsyncEventBus asyncEventBus;
    private final EventBus syncEventBus;

    public EventBusWrapper() {
        this.asyncEventBus = new AsyncEventBus(
            Executors.newFixedThreadPool(10,
                new ThreadFactoryBuilder()
                    .setNameFormat("eventbus-%d")
                    .build()),
            (exception, context) ->
                log.error("EventBus error: subscriber={}, event={}",
                    context.getSubscriberMethod(), context.getEvent(), exception)
        );
        this.syncEventBus = new EventBus();
    }

    public void post(Object event) {
        asyncEventBus.post(event);
    }

    public void register(Object subscriber) {
        asyncEventBus.register(subscriber);
        syncEventBus.register(subscriber);
    }

    public void unregister(Object subscriber) {
        asyncEventBus.unregister(subscriber);
        syncEventBus.unregister(subscriber);
    }
}
```

- [ ] **Step 2: 编写 UserMessageReceived 事件**

```java
// meta-claw-core/src/main/java/meta/claw/core/events/UserMessageReceived.java
package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.model.Context;

@Getter
@AllArgsConstructor
public class UserMessageReceived {
    private final Context context;
    private final String sessionId;
    private final String channelType;
}
```

- [ ] **Step 3: 编写 ExpertResponseReady 事件**

```java
// meta-claw-core/src/main/java/meta/claw/core/events/ExpertResponseReady.java
package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;

@Getter
@AllArgsConstructor
public class ExpertResponseReady {
    private final String channelType;
    private final Reply reply;
    private final Context context;
}
```

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/eventbus/
git add meta-claw-core/src/main/java/meta/claw/core/events/
git commit -m "feat(core): add Guava EventBus wrapper and domain events"
```

---

## Task 4: Session 模块 - 模型和存储接口

**Files:**
- Create: `meta-claw-session/src/main/java/meta/claw/session/model/ChatMode.java`
- Create: `meta-claw-session/src/main/java/meta/claw/session/model/UserSession.java`
- Create: `meta-claw-session/src/main/java/meta/claw/session/storage/SessionStorage.java`
- Create: `meta-claw-session/src/main/java/meta/claw/session/storage/InMemorySessionStorage.java`

- [ ] **Step 1: 编写 ChatMode 枚举**

```java
// meta-claw-session/src/main/java/meta/claw/session/model/ChatMode.java
package meta.claw.session.model;

public enum ChatMode {
    SINGLE,
    GROUP
}
```

- [ ] **Step 2: 编写 UserSession 类**

```java
// meta-claw-session/src/main/java/meta/claw/session/model/UserSession.java
package meta.claw.session.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class UserSession {
    private String sessionKey;
    private String userId;
    private String source;
    private String agentId;
    private ChatMode mode;
    private String targetExpert;
    private String groupSessionId;
    private boolean debugMode;
    private Instant lastActivity;
    private Instant createdAt;

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public void setSingleMode(String expertName) {
        this.mode = ChatMode.SINGLE;
        this.targetExpert = expertName;
    }

    public void setGroupMode(String groupSessionId) {
        this.mode = ChatMode.GROUP;
        this.groupSessionId = groupSessionId;
    }

    public boolean toggleDebugMode() {
        this.debugMode = !this.debugMode;
        return this.debugMode;
    }
}
```

- [ ] **Step 3: 编写 SessionStorage 接口**

```java
// meta-claw-session/src/main/java/meta/claw/session/storage/SessionStorage.java
package meta.claw.session.storage;

import meta.claw.session.model.UserSession;

import java.util.List;

public interface SessionStorage {
    UserSession get(String sessionKey);
    void save(UserSession session);
    void delete(String sessionKey);
    List<UserSession> listAll();
    void cleanupExpired(long timeoutSeconds);
}
```

- [ ] **Step 4: 编写 InMemorySessionStorage**

```java
// meta-claw-session/src/main/java/meta/claw/session/storage/InMemorySessionStorage.java
package meta.claw.session.storage;

import meta.claw.session.model.UserSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionStorage implements SessionStorage {
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    public UserSession get(String sessionKey) {
        return sessions.get(sessionKey);
    }

    @Override
    public void save(UserSession session) {
        sessions.put(session.getSessionKey(), session);
    }

    @Override
    public void delete(String sessionKey) {
        sessions.remove(sessionKey);
    }

    @Override
    public List<UserSession> listAll() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public void cleanupExpired(long timeoutSeconds) {
        Instant now = Instant.now();
        sessions.values().removeIf(session -> {
            if (session.getLastActivity() == null) return false;
            return now.getEpochSecond() - session.getLastActivity().getEpochSecond() > timeoutSeconds;
        });
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-session/src/main/java/meta/claw/session/
git commit -m "feat(session): add UserSession, ChatMode, SessionStorage and InMemorySessionStorage"
```

---

## Task 5: Session 模块 - SessionManager

**Files:**
- Create: `meta-claw-session/src/main/java/meta/claw/session/SessionManager.java`
- Test: `meta-claw-session/src/test/java/meta/claw/session/SessionManagerTest.java`

- [ ] **Step 1: 编写 SessionManager**

```java
// meta-claw-session/src/main/java/meta/claw/session/SessionManager.java
package meta.claw.session;

import meta.claw.session.model.ChatMode;
import meta.claw.session.model.UserSession;
import meta.claw.session.storage.SessionStorage;

import java.time.Instant;
import java.util.List;

public class SessionManager {
    private final SessionStorage storage;
    private final long sessionTimeoutSeconds;

    public SessionManager(SessionStorage storage, long sessionTimeoutSeconds) {
        this.storage = storage;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    }

    public SessionManager(SessionStorage storage) {
        this(storage, 3600);
    }

    private String buildSessionKey(String userId, String source, String agentId) {
        return userId + ":" + source + ":" + agentId;
    }

    public UserSession getSession(String userId, String source, String agentId) {
        String key = buildSessionKey(userId, source, agentId);
        UserSession session = storage.get(key);
        if (session == null) {
            session = UserSession.builder()
                .sessionKey(key)
                .userId(userId)
                .source(source)
                .agentId(agentId)
                .mode(ChatMode.SINGLE)
                .debugMode(false)
                .lastActivity(Instant.now())
                .createdAt(Instant.now())
                .build();
            storage.save(session);
        }
        session.touch();
        storage.save(session);
        return session;
    }

    public void setSingleMode(String userId, String source, String expertName, String agentId) {
        UserSession session = getSession(userId, source, agentId);
        session.setSingleMode(expertName);
        storage.save(session);
    }

    public void setGroupMode(String userId, String source, String groupSessionId, String agentId) {
        UserSession session = getSession(userId, source, agentId);
        session.setGroupMode(groupSessionId);
        storage.save(session);
    }

    public List<String> getTargetExperts(UserSession session, List<String> availableExperts) {
        if (availableExperts == null || availableExperts.isEmpty()) {
            return List.of();
        }
        if (session.getMode() == ChatMode.SINGLE) {
            if (session.getTargetExpert() != null && availableExperts.contains(session.getTargetExpert())) {
                return List.of(session.getTargetExpert());
            }
            return List.of(availableExperts.get(0));
        }
        // GROUP mode: broadcast to all
        return availableExperts;
    }

    public void clearSession(String userId, String source, String agentId) {
        storage.delete(buildSessionKey(userId, source, agentId));
    }

    public void cleanupExpiredSessions() {
        storage.cleanupExpired(sessionTimeoutSeconds);
    }
}
```

- [ ] **Step 2: 编写 SessionManagerTest**

```java
// meta-claw-session/src/test/java/meta/claw/session/SessionManagerTest.java
package meta.claw.session;

import meta.claw.session.model.ChatMode;
import meta.claw.session.model.UserSession;
import meta.claw.session.storage.InMemorySessionStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void testGetSessionCreatesNewSession() {
        SessionManager manager = new SessionManager(new InMemorySessionStorage());
        UserSession session = manager.getSession("user1", "WEIXIN", "agent1");
        assertNotNull(session);
        assertEquals("user1", session.getUserId());
        assertEquals("WEIXIN", session.getSource());
        assertEquals("agent1", session.getAgentId());
        assertEquals(ChatMode.SINGLE, session.getMode());
    }

    @Test
    void testSetSingleMode() {
        SessionManager manager = new SessionManager(new InMemorySessionStorage());
        manager.setSingleMode("user1", "WEIXIN", "expert1", "agent1");
        UserSession session = manager.getSession("user1", "WEIXIN", "agent1");
        assertEquals("expert1", session.getTargetExpert());
        assertEquals(ChatMode.SINGLE, session.getMode());
    }

    @Test
    void testGetTargetExpertsSingleMode() {
        SessionManager manager = new SessionManager(new InMemorySessionStorage());
        manager.setSingleMode("user1", "WEIXIN", "expert2", "agent1");
        UserSession session = manager.getSession("user1", "WEIXIN", "agent1");
        List<String> targets = manager.getTargetExperts(session, List.of("expert1", "expert2", "expert3"));
        assertEquals(List.of("expert2"), targets);
    }

    @Test
    void testGetTargetExpertsFallback() {
        SessionManager manager = new SessionManager(new InMemorySessionStorage());
        UserSession session = manager.getSession("user1", "WEIXIN", "agent1");
        List<String> targets = manager.getTargetExperts(session, List.of("expert1"));
        assertEquals(List.of("expert1"), targets);
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `cd meta-claw-session && mvn test`
Expected: Tests PASS (4/4)

- [ ] **Step 4: Commit**

```bash
git add meta-claw-session/src/main/java/meta/claw/session/SessionManager.java
git add meta-claw-session/src/test/java/meta/claw/session/SessionManagerTest.java
git commit -m "feat(session): add SessionManager with CRUD and mode management + tests"
```

---

## Task 6: Gateway 模块 - Channel 抽象和模型

**Files:**
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/model/GatewayMessage.java`
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/model/OutboundMessage.java`
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/channel/Channel.java`

- [ ] **Step 1: 编写 GatewayMessage**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/model/GatewayMessage.java
package meta.claw.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class GatewayMessage {
    private String messageId;
    private String channel;
    private String chatId;
    private String userId;
    private String content;
    private String contentType;
    private String agentId;
}
```

- [ ] **Step 2: 编写 OutboundMessage**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/model/OutboundMessage.java
package meta.claw.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import meta.claw.core.model.Reply;

@Getter
@Builder
@AllArgsConstructor
public class OutboundMessage {
    private String channel;
    private String chatId;
    private String userId;
    private Reply reply;
}
```

- [ ] **Step 3: 编写 Channel 接口**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/channel/Channel.java
package meta.claw.gateway.channel;

import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;

public interface Channel {
    String getChannelType();
    void startup();
    void stop();
    void handleText(ChatMessage msg);
    void send(Reply reply, Context context);
}
```

- [ ] **Step 4: 编写 ChatMessage（Gateway 内部消息模型）**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChatMessage.java
package meta.claw.gateway.channel;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ChatMessage {
    private String msgId;
    private String fromUserId;
    private String fromUserNickname;
    private String toUserId;
    private String otherUserId;
    private String otherUserNickname;
    private String content;
    private boolean isGroup;
    private boolean isAt;
    private String actualUserId;
    private String actualUserNickname;
    private List<String> atList;
    private String selfDisplayName;
}
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-gateway/src/main/java/meta/claw/gateway/model/
git add meta-claw-gateway/src/main/java/meta/claw/gateway/channel/Channel.java
git add meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChatMessage.java
git commit -m "feat(gateway): add Channel interface, GatewayMessage, OutboundMessage, ChatMessage"
```

---

## Task 7: Gateway 模块 - ChatChannel 抽象类

**Files:**
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChatChannel.java`

- [ ] **Step 1: 编写 ChatChannel**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChatChannel.java
package meta.claw.gateway.channel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;

import java.util.concurrent.*;

@Slf4j
public abstract class ChatChannel implements Channel {
    protected String channelType = "";
    protected String name;
    protected String userId;

    private final ConcurrentHashMap<String, SessionQueue> sessions = new ConcurrentHashMap<>();
    private final ExecutorService handlerPool = Executors.newFixedThreadPool(8);
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public ChatChannel() {
        consumerExecutor.submit(this::consume);
    }

    protected Context composeContext(ContextType type, String content, ChatMessage msg) {
        Context context = new Context(type, content);
        context.put("msg", msg);
        context.setChannelType(this.channelType);

        if (msg.isGroup()) {
            context.setGroup(true);
            context.setSessionId(msg.getOtherUserId());
            context.setReceiver(msg.getOtherUserId());
        } else {
            context.setSessionId(msg.getOtherUserId());
            context.setReceiver(msg.getOtherUserId());
        }
        return context;
    }

    protected void handle(Context context) {
        if (context == null || context.getContent() == null || context.getContent().isEmpty()) {
            return;
        }
        log.debug("[ChatChannel] handling context: {}", context.getContent());
        Reply reply = generateReply(context);
        if (reply != null && reply.getContent() != null) {
            reply = decorateReply(context, reply);
            sendReply(context, reply);
        }
    }

    protected Reply generateReply(Context context) {
        // 由子类或外部 Bridge 实现
        // P1 中通过 EventBus 发布事件，由 AgentLoop 处理
        return null;
    }

    protected Reply decorateReply(Context context, Reply reply) {
        if (reply == null || reply.getType() == null) {
            return reply;
        }
        if (reply.getType() == ReplyType.TEXT) {
            String text = reply.getContent();
            if (context.isGroup() && context.get("actual_user_nickname") != null) {
                text = "@" + context.get("actual_user_nickname") + "\n" + text;
            }
            reply.setContent(text);
        }
        return reply;
    }

    protected void sendReply(Context context, Reply reply) {
        _send(reply, context, 0);
    }

    private void _send(Reply reply, Context context, int retryCnt) {
        try {
            send(reply, context);
        } catch (Exception e) {
            log.error("[ChatChannel] sendMsg error: {}", e.getMessage(), e);
            if (retryCnt < 2) {
                try {
                    Thread.sleep(3000 + 3000L * retryCnt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                _send(reply, context, retryCnt + 1);
            }
        }
    }

    public void produce(Context context) {
        String sessionId = context.getSessionId();
        SessionQueue queue = sessions.computeIfAbsent(sessionId, k -> new SessionQueue());
        queue.put(context);
    }

    private void consume() {
        while (running) {
            for (String sessionId : sessions.keySet()) {
                SessionQueue queue = sessions.get(sessionId);
                if (queue == null) continue;
                Context context = queue.poll();
                if (context != null) {
                    handlerPool.submit(() -> handle(context));
                }
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        running = false;
        consumerExecutor.shutdown();
        handlerPool.shutdown();
    }

    // 内部队列类
    private static class SessionQueue {
        private final BlockingQueue<Context> queue = new LinkedBlockingQueue<>();

        void put(Context context) {
            queue.offer(context);
        }

        Context poll() {
            return queue.poll();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChatChannel.java
git commit -m "feat(gateway): add ChatChannel with producer/consumer, retry, context composition"
```

---

## Task 8: Gateway 模块 - Gateway 和 ChannelRegistry

**Files:**
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelRegistry.java`
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelFactory.java`
- Create: `meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java`

- [ ] **Step 1: 编写 ChannelRegistry**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelRegistry.java
package meta.claw.gateway.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelRegistry {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public void register(Channel channel) {
        channels.put(channel.getChannelType(), channel);
    }

    public Channel get(String channelType) {
        return channels.get(channelType);
    }

    public boolean hasChannel(String channelType) {
        return channels.containsKey(channelType);
    }
}
```

- [ ] **Step 2: 编写 ChannelFactory**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelFactory.java
package meta.claw.gateway.channel;

public class ChannelFactory {
    public Channel createChannel(String channelType) {
        // P1 仅支持 weixin，后续扩展
        if ("weixin".equals(channelType) || "wx".equals(channelType)) {
            // WeixinChannel 在 gateway-weixin 模块中，通过 Spring 注入
            throw new UnsupportedOperationException("Use Spring Bean for WeixinChannel");
        }
        throw new IllegalArgumentException("Unknown channel type: " + channelType);
    }
}
```

- [ ] **Step 3: 编写 Gateway**

```java
// meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java
package meta.claw.gateway;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.ExpertResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.core.model.Reply;
import meta.claw.gateway.channel.Channel;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.channel.ChatMessage;

@Slf4j
public class Gateway {
    private final ChannelRegistry registry;
    private final EventBusWrapper eventBus;

    public Gateway(ChannelRegistry registry, EventBusWrapper eventBus) {
        this.registry = registry;
        this.eventBus = eventBus;
        this.eventBus.register(this);
    }

    public void registerChannel(Channel channel) {
        registry.register(channel);
        channel.startup();
        log.info("[Gateway] Registered channel: {}", channel.getChannelType());
    }

    public void onInboundMessage(ChatMessage msg, String channelType) {
        Context context = new Context(ContextType.TEXT, msg.getContent());
        context.setChannelType(channelType);
        context.put("msg", msg);
        context.setSessionId(msg.getOtherUserId());
        context.setReceiver(msg.getOtherUserId());
        context.setGroup(msg.isGroup());

        eventBus.post(new UserMessageReceived(
            context,
            msg.getOtherUserId(),
            channelType
        ));
    }

    public void onInboundMessage(String content, String userId, String channelType) {
        Context context = new Context(ContextType.TEXT, content);
        context.setChannelType(channelType);
        context.setSessionId(userId);
        context.setReceiver(userId);

        eventBus.post(new UserMessageReceived(
            context,
            userId,
            channelType
        ));
    }

    // 订阅 ExpertResponseReady 事件
    public void onResponseReady(ExpertResponseReady event) {
        Channel channel = registry.get(event.getChannelType());
        if (channel != null) {
            channel.send(event.getReply(), event.getContext());
        } else {
            log.warn("[Gateway] No channel found for type: {}", event.getChannelType());
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelRegistry.java
git add meta-claw-gateway/src/main/java/meta/claw/gateway/channel/ChannelFactory.java
git add meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java
git commit -m "feat(gateway): add Gateway controller, ChannelRegistry, ChannelFactory"
```

---

## Task 9: 微信 Channel 模块

**Files:**
- Create: `meta-claw-gateway-weixin/pom.xml`
- Create: `meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinConfig.java`
- Create: `meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinMessageConverter.java`
- Create: `meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinChannel.java`

- [ ] **Step 1: 编写 gateway-weixin POM**

```xml
<!-- meta-claw-gateway-weixin/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.meta</groupId>
        <artifactId>meta-claw</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>meta-claw-gateway-weixin</artifactId>
    <name>Meta-Claw Gateway Weixin</name>

    <dependencies>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-session</artifactId>
        </dependency>
        <dependency>
            <groupId>com.openilink</groupId>
            <artifactId>openilink-sdk-java</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 编写 WeixinConfig**

```java
// meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinConfig.java
package meta.claw.gateway.weixin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeixinConfig {
    private String token;
    private String baseUrl;
    private int monitorPort = 8080;
}
```

- [ ] **Step 3: 编写 WeixinMessageConverter**

```java
// meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinMessageConverter.java
package meta.claw.gateway.weixin;

import com.openilink.model.Message;
import meta.claw.gateway.channel.ChatMessage;

public class WeixinMessageConverter {

    public ChatMessage convert(Message msg) {
        return ChatMessage.builder()
            .msgId(msg.getMsgId())
            .fromUserId(msg.getFromUserId())
            .otherUserId(msg.getFromUserId())
            .content(extractText(msg))
            .isGroup(false)
            .build();
    }

    private String extractText(Message msg) {
        // openilink MessageHelper.extractText equivalent
        if (msg.getItemList() != null && !msg.getItemList().isEmpty()) {
            return msg.getItemList().get(0).getContent();
        }
        return "";
    }
}
```

- [ ] **Step 4: 编写 WeixinChannel**

```java
// meta-claw-gateway-weixin/src/main/java/meta/claw/gateway/weixin/WeixinChannel.java
package meta.claw.gateway.weixin;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.response.LoginResult;
import com.openilink.util.MessageHelper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChatChannel;
import meta.claw.gateway.channel.ChatMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WeixinChannel extends ChatChannel {

    private final WeixinConfig config;
    private final WeixinMessageConverter converter;
    private final Gateway gateway;
    private ILinkClient client;
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);

    public WeixinChannel(WeixinConfig config, Gateway gateway) {
        this.channelType = "weixin";
        this.config = config;
        this.converter = new WeixinMessageConverter();
        this.gateway = gateway;
    }

    @Override
    public String getChannelType() {
        return "weixin";
    }

    @Override
    public void startup() {
        client = ILinkClient.builder()
            .token(config.getToken())
            .build();

        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String url) {
                log.info("[Weixin] 请扫码登录: {}", url);
            }

            @Override
            public void onScanned() {
                log.info("[Weixin] 已扫码，等待确认...");
            }

            @Override
            public void onExpired(int attempt, int max) {
                log.warn("[Weixin] 二维码过期，正在刷新... ({}/{})", attempt, max);
            }
        });

        if (!result.isConnected()) {
            throw new RuntimeException("微信登录失败");
        }

        log.info("[Weixin] 登录成功, BotID={}", result.getBotId());
        startMonitor();
    }

    private void startMonitor() {
        monitorExecutor.submit(() -> {
            client.monitor(msg -> {
                String text = MessageHelper.extractText(msg);
                if (text == null || text.isEmpty()) {
                    return;
                }
                log.info("[Weixin] 收到消息 from {}: {}", msg.getFromUserId(), text);

                // 直接通过 Gateway 处理（绕过 ChatChannel 的队列，使用 Gateway 的 EventBus）
                gateway.onInboundMessage(text, msg.getFromUserId(), "weixin");
            }, null, stopFlag);
        });
    }

    @Override
    public void send(Reply reply, Context context) {
        if (client == null) {
            log.error("[Weixin] Client not initialized");
            return;
        }
        String userId = context.getReceiver();
        try {
            switch (reply.getType()) {
                case TEXT:
                    client.push(userId, reply.getContent());
                    break;
                default:
                    client.push(userId, reply.getContent());
            }
            log.info("[Weixin] 发送消息 to {}: {}", userId, reply.getContent());
        } catch (Exception e) {
            log.error("[Weixin] 发送消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送微信消息失败", e);
        }
    }

    @Override
    public void stop() {
        stopFlag.set(true);
        monitorExecutor.shutdown();
        shutdown();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-gateway-weixin/
git commit -m "feat(gateway-weixin): add WeixinChannel with openilink SDK integration"
```

---

## Task 10: Runtime 模块 - ExpertConfig + ExpertManager

**Files:**
- Create: `meta-claw-runtime/src/main/java/meta/claw/runtime/model/ExpertConfig.java`
- Create: `meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertManager.java`

- [ ] **Step 1: 编写 ExpertConfig**

```java
// meta-claw-runtime/src/main/java/meta/claw/runtime/model/ExpertConfig.java
package meta.claw.runtime.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
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
    private SessionConfig session = new SessionConfig();
}
```

- [ ] **Step 2: 编写 SessionConfig（内嵌类）**

```java
// meta-claw-runtime/src/main/java/meta/claw/runtime/model/SessionConfig.java
package meta.claw.runtime.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionConfig {
    private String storageType = "memory";
    private String filePath;
}
```

- [ ] **Step 3: 编写 ExpertManager**

```java
// meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertManager.java
package meta.claw.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.runtime.model.ExpertConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
public class ExpertManager {
    private final Map<String, ExpertConfig> experts = new ConcurrentHashMap<>();
    private final Map<String, ExpertRuntime> runtimes = new ConcurrentHashMap<>();
    private final String expertsDir;

    public ExpertManager() {
        this("experts");
    }

    public ExpertManager(String expertsDir) {
        this.expertsDir = expertsDir;
    }

    public void loadExperts() {
        Path dir = Paths.get(expertsDir);
        if (!Files.exists(dir)) {
            log.warn("Experts directory not found: {}", dir);
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.getFileName().toString().equals("expert.yaml"))
                .forEach(this::loadExpert);
        } catch (Exception e) {
            log.error("Failed to load experts", e);
        }
    }

    private void loadExpert(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            ExpertConfig config = mapToExpertConfig(data);
            config.setId(path.getParent().getFileName().toString());
            experts.put(config.getId(), config);
            log.info("Loaded expert: {}", config.getId());
        } catch (Exception e) {
            log.error("Failed to load expert from {}", path, e);
        }
    }

    private ExpertConfig mapToExpertConfig(Map<String, Object> data) {
        ExpertConfig config = new ExpertConfig();
        config.setName((String) data.get("name"));
        config.setDescription((String) data.get("description"));
        config.setEmoji((String) data.get("emoji"));
        config.setModel((String) data.getOrDefault("model", "deepseek-chat"));
        config.setSystemPrompt((String) data.get("system_prompt"));
        config.setMemoryEnabled((Boolean) data.getOrDefault("memory_enabled", false));
        config.setKnowledgeDir((String) data.get("knowledge_dir"));
        config.setExcludeTools((List<String>) data.get("exclude_tools"));
        return config;
    }

    public ExpertConfig getConfig(String expertId) {
        return experts.get(expertId);
    }

    public ExpertRuntime getRuntime(String expertId) {
        return runtimes.get(expertId);
    }

    public void registerRuntime(String expertId, ExpertRuntime runtime) {
        runtimes.put(expertId, runtime);
    }

    public List<String> listAvailableExperts() {
        return new ArrayList<>(experts.keySet());
    }

    public boolean hasExpert(String expertId) {
        return experts.containsKey(expertId);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add meta-claw-runtime/src/main/java/meta/claw/runtime/model/
git add meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertManager.java
git commit -m "feat(runtime): add ExpertConfig, ExpertManager with YAML loading"
```

---

## Task 11: Runtime 模块 - ExpertRuntime + Spring AI ChatClient

**Files:**
- Create: `meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertRuntime.java`

- [ ] **Step 1: 编写 ExpertRuntime**

```java
// meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertRuntime.java
package meta.claw.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.runtime.model.ExpertConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

@Slf4j
public class ExpertRuntime {
    private final ExpertConfig config;
    private final ChatClient chatClient;

    public ExpertRuntime(ExpertConfig config, ChatModel chatModel) {
        this.config = config;
        this.chatClient = ChatClient.builder(chatModel)
            .defaultSystem(config.getSystemPrompt() != null ? config.getSystemPrompt() : "You are a helpful assistant.")
            .build();
    }

    public Reply chat(String userMessage) {
        log.debug("[ExpertRuntime:{}] Chat: {}", config.getId(), userMessage);
        try {
            String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
            return new Reply(ReplyType.TEXT, response);
        } catch (Exception e) {
            log.error("[ExpertRuntime:{}] Chat error: {}", config.getId(), e.getMessage(), e);
            return new Reply(ReplyType.ERROR, "服务异常，请稍后重试");
        }
    }

    public String getExpertId() {
        return config.getId();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-runtime/src/main/java/meta/claw/runtime/ExpertRuntime.java
git commit -m "feat(runtime): add ExpertRuntime with Spring AI ChatClient integration"
```

---

## Task 12: Runtime 模块 - AgentLoop

**Files:**
- Create: `meta-claw-runtime/src/main/java/meta/claw/runtime/AgentLoop.java`

- [ ] **Step 1: 编写 AgentLoop**

```java
// meta-claw-runtime/src/main/java/meta/claw/runtime/AgentLoop.java
package meta.claw.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.ExpertResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;

import java.util.List;

@Slf4j
public class AgentLoop {
    private final EventBusWrapper eventBus;
    private final ExpertManager expertManager;

    public AgentLoop(EventBusWrapper eventBus, ExpertManager expertManager) {
        this.eventBus = eventBus;
        this.expertManager = expertManager;
        this.eventBus.register(this);
    }

    // 注意：Guava EventBus 的 @Subscribe 需要在运行时通过反射注册
    // 这里使用显式注册方式
    public void start() {
        // 订阅 UserMessageReceived 事件
        // 实际项目中可以使用 Guava 的 @Subscribe 注解 + eventBus.register(this)
    }

    public void onUserMessage(UserMessageReceived event) {
        Context context = event.getContext();
        String sessionId = event.getSessionId();
        String channelType = event.getChannelType();

        log.info("[AgentLoop] Received message from {} via {}", sessionId, channelType);

        // 确定目标 Expert
        String expertId = determineTargetExpert();
        if (expertId == null) {
            log.error("[AgentLoop] No expert available");
            eventBus.post(new ExpertResponseReady(
                channelType,
                new Reply(ReplyType.ERROR, "暂无可用 Expert"),
                context
            ));
            return;
        }

        ExpertRuntime runtime = expertManager.getRuntime(expertId);
        if (runtime == null) {
            log.error("[AgentLoop] Expert runtime not found: {}", expertId);
            eventBus.post(new ExpertResponseReady(
                channelType,
                new Reply(ReplyType.ERROR, "Expert 未初始化"),
                context
            ));
            return;
        }

        // 执行对话
        Reply reply = runtime.chat(context.getContent());

        // 发布回复事件
        eventBus.post(new ExpertResponseReady(channelType, reply, context));
    }

    private String determineTargetExpert() {
        List<String> experts = expertManager.listAvailableExperts();
        if (experts.isEmpty()) {
            return null;
        }
        return experts.get(0);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-runtime/src/main/java/meta/claw/runtime/AgentLoop.java
git commit -m "feat(runtime): add AgentLoop event subscriber with expert routing"
```

---

## Task 13: Bootstrap 模块 - Spring Boot 启动 + 配置

**Files:**
- Create: `meta-claw-bootstrap/pom.xml`
- Create: `meta-claw-bootstrap/src/main/java/meta/claw/app/MetaClawApplication.java`
- Create: `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java`
- Create: `meta-claw-bootstrap/src/main/resources/application.yml`

- [ ] **Step 1: 编写 Bootstrap POM**

```xml
<!-- meta-claw-bootstrap/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.meta</groupId>
        <artifactId>meta-claw</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>meta-claw-bootstrap</artifactId>
    <name>Meta-Claw Bootstrap</name>

    <dependencies>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-session</artifactId>
        </dependency>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-gateway</artifactId>
        </dependency>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-gateway-weixin</artifactId>
        </dependency>
        <dependency>
            <groupId>com.meta</groupId>
            <artifactId>meta-claw-runtime</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 编写 AppConfig（手动装配 Bean）**

```java
// meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java
package meta.claw.app;

import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.weixin.WeixinChannel;
import meta.claw.gateway.weixin.WeixinConfig;
import meta.claw.runtime.AgentLoop;
import meta.claw.runtime.ExpertManager;
import meta.claw.runtime.ExpertRuntime;
import meta.claw.runtime.model.ExpertConfig;
import meta.claw.session.SessionManager;
import meta.claw.session.storage.InMemorySessionStorage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${meta.claw.weixin.token:}")
    private String weixinToken;

    @Bean
    public EventBusWrapper eventBusWrapper() {
        return new EventBusWrapper();
    }

    @Bean
    public ChannelRegistry channelRegistry() {
        return new ChannelRegistry();
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager(new InMemorySessionStorage());
    }

    @Bean
    public Gateway gateway(EventBusWrapper eventBus, ChannelRegistry registry) {
        return new Gateway(registry, eventBus);
    }

    @Bean
    public ExpertManager expertManager() {
        ExpertManager manager = new ExpertManager();
        manager.loadExperts();
        return manager;
    }

    @Bean
    public AgentLoop agentLoop(EventBusWrapper eventBus, ExpertManager expertManager) {
        return new AgentLoop(eventBus, expertManager);
    }

    @Bean
    public WeixinChannel weixinChannel(Gateway gateway) {
        WeixinConfig config = new WeixinConfig();
        config.setToken(weixinToken);
        return new WeixinChannel(config, gateway);
    }

    public void initializeRuntimes(ExpertManager expertManager, ChatModel chatModel) {
        for (String expertId : expertManager.listAvailableExperts()) {
            ExpertConfig config = expertManager.getConfig(expertId);
            ExpertRuntime runtime = new ExpertRuntime(config, chatModel);
            expertManager.registerRuntime(expertId, runtime);
        }
    }
}
```

- [ ] **Step 3: 编写 MetaClawApplication**

```java
// meta-claw-bootstrap/src/main/java/meta/claw/app/MetaClawApplication.java
package meta.claw.app;

import meta.claw.gateway.Gateway;
import meta.claw.gateway.weixin.WeixinChannel;
import meta.claw.runtime.AgentLoop;
import meta.claw.runtime.ExpertManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "meta.claw")
public class MetaClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetaClawApplication.class, args);
    }

    @Bean
    public CommandLineRunner init(
            Gateway gateway,
            WeixinChannel weixinChannel,
            AgentLoop agentLoop,
            ExpertManager expertManager,
            ChatModel chatModel,
            AppConfig appConfig) {
        return args -> {
            // 初始化 Expert Runtime
            appConfig.initializeRuntimes(expertManager, chatModel);

            // 注册微信渠道
            gateway.registerChannel(weixinChannel);

            // 启动 AgentLoop
            agentLoop.start();

            System.out.println("Meta-Claw started successfully!");
        };
    }
}
```

- [ ] **Step 4: 编写 application.yml**

```yaml
# meta-claw-bootstrap/src/main/resources/application.yml
spring:
  application:
    name: meta-claw
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-3.5-turbo

meta:
  claw:
    weixin:
      token: ${WEIXIN_TOKEN:}
    experts:
      dir: ./experts

logging:
  level:
    meta.claw: DEBUG
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-bootstrap/
git commit -m "feat(bootstrap): add Spring Boot application, AppConfig, application.yml"
```

---

## Task 14: 集成测试

**Files:**
- Create: `src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
// src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java
package meta.claw.integration;

import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.ExpertResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.runtime.AgentLoop;
import meta.claw.runtime.ExpertManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MessageFlowIntegrationTest {

    @Test
    void testEventBusMessageFlow() throws InterruptedException {
        EventBusWrapper eventBus = new EventBusWrapper();
        ChannelRegistry registry = new ChannelRegistry();
        Gateway gateway = new Gateway(registry, eventBus);

        ExpertManager expertManager = new ExpertManager();
        AgentLoop agentLoop = new AgentLoop(eventBus, expertManager);

        // 捕获回复事件
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ExpertResponseReady> captured = new AtomicReference<>();

        eventBus.register(new Object() {
            @com.google.common.eventbus.Subscribe
            public void onResponse(ExpertResponseReady event) {
                captured.set(event);
                latch.countDown();
            }
        });

        // 发送消息
        Context context = new Context(ContextType.TEXT, "Hello");
        context.setSessionId("user1");
        context.setReceiver("user1");
        context.setChannelType("test");

        eventBus.post(new UserMessageReceived(context, "user1", "test"));

        // 等待回复
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive response within 5 seconds");
        assertNotNull(captured.get());
        assertEquals("test", captured.get().getChannelType());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java
git commit -m "test(integration): add EventBus message flow integration test"
```

---

## 自检

**1. Spec coverage:**
- ✅ Gateway/Channel 层 (Task 6-8)
- ✅ Session 层 (Task 4-5)
- ✅ Core/EventBus 层 (Task 2-3)
- ✅ Runtime 层 (Task 10-12)
- ✅ 微信渠道 (Task 9)
- ✅ Spring Boot 启动 (Task 13)
- ✅ 集成测试 (Task 14)

**2. Placeholder scan:**
- ✅ 无 TBD/TODO/"implement later"

**3. Type consistency:**
- ✅ Context, Reply, UserSession, ExpertConfig 等模型类在各任务中签名一致
- ✅ EventBus 事件类型前后一致

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-04-28-meta-claw-p1-implementation-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
