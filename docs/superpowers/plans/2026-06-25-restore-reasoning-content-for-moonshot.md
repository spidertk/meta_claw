# 恢复 OpenAI 兼容请求中 reasoning_content 真实值 Implementation Plan

> **⚠️ 状态更新（2026-06-26）：** 本计划中的 "Advisor + ThreadLocal Context Bridge + Serializer Patch" 方案已在真实流式调用中被证明不可行：Advisor 在 `boundedElastic` 线程执行，而 Jackson 序列化在 `reactor-http-nio` 线程执行，ThreadLocal 无法跨线程传递。当前实现已改为同包 subclass `ReasoningAwareOpenAiChatModel` 直接重写 package-private 的 `OpenAiChatModel.createRequest(Prompt, boolean)`，在请求构造阶段从原始 Prompt 的 `AssistantMessage.metadata` 回填真实 `reasoning_content`。旧的 ThreadLocal 桥代码（`OpenAiReasoningContentContext`、`OpenAiReasoningContentAdvisor`、`OpenAiReasoningContentModule`）及其测试已删除。本计划文档保留作为历史上下文，实际代码实现以 `ReasoningAwareOpenAiChatModel` 为准，详见 `claude-progress.md` Session 063。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Spring AI 1.1.8 `OpenAiChatModel` 在 `AssistantMessage → ChatCompletionMessage` 转换时硬编码 `reasoningContent = null` 的缺陷，让所有基于 OpenAI 兼容协议的 provider（Moonshot、DeepSeek、OpenAI 等）在 assistant tool_call 请求中都能携带真实的 `reasoning_content`，而不是只能补空字符串。

**Architecture:** 参考 Spring AI Alibaba Playground 的 `ReasoningContentAdvisor` 模式，新增一个 `OpenAiReasoningContentAdvisor`：在 `before()` 阶段从 outgoing `Prompt` 的 `AssistantMessage.metadata` 提取 `reasoningContent` 并写入线程上下文；`OpenAiReasoningContentModule` 在序列化 `OpenAiApi.ChatCompletionMessage` 时从上下文读取并回填到 JSON。Advisor 与 Serializer Module 随 `OpenAiLlmClientProvider` 统一注册，所有 OpenAI 兼容 provider 自动受益。

**Tech Stack:** Java 21, Spring AI 1.1.8 (OpenAI module + client-chat module), Jackson, Lombok, JUnit 5, Maven

---

## Background & Root Cause

- `OpenAiApi.ChatCompletionMessage` 已经是 Java Record，包含 `reasoningContent` 字段，但 `OpenAiChatModel.createRequest()` 在把 `AssistantMessage` 转成 `ChatCompletionMessage` 时，最后一个参数硬编码传了 `null`。
- `LlmClientManager.toSpringMessage()` 目前把 `reasoningContent` 放进 `AssistantMessage.properties`（key=`reasoningContent`），但 Spring AI 在构造 `ChatCompletionMessage` 时不会读这个 property。
- 当前 `MoonshotSerializerModule` 只能给缺失的 `reasoning_content` 补空字符串，无法恢复真实内容。
- 这个问题不是 Moonshot 特有的，而是 Spring AI OpenAI 模块的缺陷；任何需要 `reasoning_content` 的 OpenAI 兼容 provider 都会遇到。

## 关于 Spring AI Alibaba `ReasoningContentAdvisor` 的调研结论

- 位置：`spring-ai-alibaba-examples/spring-ai-alibaba-playground/.../advisor/ReasoningContentAdvisor.java`
- 实现：实现 `BaseAdvisor`，`before()` 直接透传请求，`after()` 从响应 `AssistantMessage.metadata["reasoningContent"]` 读取 reasoning 并包进 `<think>` 标签，拼接到 assistant 的 `content` 中用于展示。
- 与我们的问题关系：它解决的是**响应展示**，不是**请求透传**；但它展示了如何用 Advisor 在 `before()`/`after()` 中读写 `Prompt`/`ChatResponse` 的 metadata。
- 借鉴点：我们也用 `BaseAdvisor` 在 `before()` 中扫描 outgoing `Prompt` 的 assistant 消息，把 `reasoningContent` 提取出来交给序列化器。这比在 `LlmClientManager` 里硬编码 ThreadLocal 操作更内聚、更符合 Spring AI 扩展风格。

## Chosen Approach: Advisor + ThreadLocal Context Bridge + Serializer Patch

**为什么不直接包装 `OpenAiChatModel`：** `createRequest` 是 package-private，子类需要放在 `org.springframework.ai.openai` 包内，且高度依赖 Spring AI 内部字节码，升级时容易断裂。

**为什么 ThreadLocal 仍然必要：** Jackson 序列化器只能访问被序列化的对象；Advisor 的 `context` 无法直接传递到 Jackson。因此 Advisor 负责把值放到 ThreadLocal，Serializer 负责读取。

**为什么用 Advisor 而不是 `LlmClientManager` 写 ThreadLocal：**
- 所有 reasoning_content 处理逻辑集中在 Advisor + Serializer 中。
- `LlmClientManager` 不需要知道 OpenAI 模块的序列化缺陷。
- 通过 `OpenAiLlmClientProvider` 统一注册，所有 OpenAI 兼容 provider 自动生效。

**线程安全：** `before()` 和 `createRequest()` 的序列化都发生在同一次 `call()/stream()` 调用的同一线程内（`LlmClientManager` 当前使用 `blockLast()` 阻塞订阅）。每次 Advisor `before()` 先 `clear()`，调用结束后由 `after()` `remove()`，避免泄漏。

---

## File Structure

| File | Responsibility |
|------|----------------|
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentContext.java` | 线程安全的 reasoning_content 队列上下文（ThreadLocal） |
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisor.java` | 从 outgoing Prompt 提取 reasoningContent 并写入上下文 |
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentModule.java` | Jackson Module，序列化时从上下文读取并回填 `reasoning_content` |
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java` | 统一注册 Advisor 与 Serializer Module |
| `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentContextTest.java` | 验证上下文的 push/poll/clear 行为 |
| `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisorTest.java` | 验证 Advisor 能从 Prompt 提取并写入上下文 |
| `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentModuleTest.java` | 验证序列化器能从上下文恢复真实 reasoning_content |

---

## Task 1: Create `OpenAiReasoningContentContext`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentContext.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentContextTest.java`

- [ ] **Step 1: Write the context helper**

```java
package meta.claw.core.llm.provider;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 在同一线程内按消息顺序传递 assistant 消息的 reasoningContent。
 * 由 OpenAiReasoningContentAdvisor 按 Prompt.messages 顺序写入，
 * 由 OpenAiReasoningContentModule 在序列化 ChatCompletionMessage 时按顺序读取。
 */
public final class OpenAiReasoningContentContext {

    private static final ThreadLocal<Deque<String>> REASONING_QUEUE = ThreadLocal.withInitial(ArrayDeque::new);

    private OpenAiReasoningContentContext() {
    }

    public static void push(String reasoningContent) {
        REASONING_QUEUE.get().offerLast(reasoningContent != null ? reasoningContent : "");
    }

    public static String poll() {
        return REASONING_QUEUE.get().pollFirst();
    }

    public static boolean isEmpty() {
        return REASONING_QUEUE.get().isEmpty();
    }

    public static void clear() {
        REASONING_QUEUE.get().clear();
    }

    public static void remove() {
        REASONING_QUEUE.remove();
    }
}
```

- [ ] **Step 2: Write the unit test**

```java
package meta.claw.core.llm.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiReasoningContentContextTest {

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldPushAndPollInFifoOrder() {
        OpenAiReasoningContentContext.push("first");
        OpenAiReasoningContentContext.push("second");

        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("first");
        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("second");
        assertThat(OpenAiReasoningContentContext.poll()).isNull();
    }

    @Test
    void shouldTreatNullAsEmptyString() {
        OpenAiReasoningContentContext.push(null);

        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("");
    }

    @Test
    void shouldClearAllValues() {
        OpenAiReasoningContentContext.push("value");
        OpenAiReasoningContentContext.clear();

        assertThat(OpenAiReasoningContentContext.isEmpty()).isTrue();
        assertThat(OpenAiReasoningContentContext.poll()).isNull();
    }

    @Test
    void shouldRemoveThreadLocal() {
        OpenAiReasoningContentContext.push("value");
        OpenAiReasoningContentContext.remove();

        assertThat(OpenAiReasoningContentContext.isEmpty()).isTrue();
    }
}
```

- [ ] **Step 3: Run the test**

Run:
```bash
cd /Users/kai/IdeaProjects/meta_claw && ~/.local/tools/apache-maven-3.9.15/bin/mvn -pl meta-claw-core test -Dtest=OpenAiReasoningContentContextTest
```
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentContext.java \
       meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentContextTest.java
git commit -m "feat(openai-reasoning): add ThreadLocal context for reasoning_content passthrough"
```

---

## Task 2: Create `OpenAiReasoningContentModule`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentModule.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/MoonshotSerializerModule.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentModuleTest.java`

- [ ] **Step 1: Write the serializer module**

```java
package meta.claw.core.llm.provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;

/**
 * Jackson Module，用于修复 Spring AI 1.1.8 的 OpenAiChatModel 在把
 * {@code AssistantMessage} 序列化为 {@link OpenAiApi.ChatCompletionMessage}
 * 时硬编码 {@code reasoningContent = null} 的缺陷。
 * <p>
 * 部分 OpenAI 兼容 provider（如 Moonshot K2.5/K2.6）要求 assistant tool_call 消息
 * 必须包含 {@code reasoning_content} 字段。本模块从 {@link OpenAiReasoningContentContext}
 * 读取真实 reasoningContent 并回填；取不到时兜底空字符串。
 */
@Slf4j
public class OpenAiReasoningContentModule extends SimpleModule {

    // 干净的 ObjectMapper，不带自定义序列化器，用于执行默认序列化
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public OpenAiReasoningContentModule() {
        super("openai-reasoning-fix");
        addSerializer(OpenAiApi.ChatCompletionMessage.class, new ChatCompletionMessageSerializer());
    }

    private static class ChatCompletionMessageSerializer extends JsonSerializer<OpenAiApi.ChatCompletionMessage> {

        @Override
        public void serialize(OpenAiApi.ChatCompletionMessage value, JsonGenerator gen,
                              SerializerProvider serializers) throws IOException {
            JsonNode node = DEFAULT_MAPPER.valueToTree(value);

            if (node instanceof ObjectNode objectNode) {
                String role = objectNode.path("role").asText("");
                boolean hasToolCalls = objectNode.has("tool_calls")
                        && objectNode.get("tool_calls").isArray()
                        && objectNode.get("tool_calls").size() > 0;
                boolean hasReasoning = objectNode.hasNonNull("reasoning_content");

                if ("assistant".equals(role) && hasToolCalls && !hasReasoning) {
                    String reasoningContent = OpenAiReasoningContentContext.poll();
                    if (reasoningContent == null) {
                        reasoningContent = "";
                    }
                    objectNode.put("reasoning_content", reasoningContent);
                    log.debug("[OpenAiReasoningContentModule] Patched reasoning_content for assistant tool_call message: {}",
                            abbreviate(reasoningContent, 50));
                }
            }

            DEFAULT_MAPPER.writeValue(gen, node);
        }

        private static String abbreviate(String s, int maxLen) {
            if (s == null || s.length() <= maxLen) {
                return s;
            }
            return s.substring(0, maxLen) + "...";
        }
    }
}
```

- [ ] **Step 2: Add unit tests**

```java
package meta.claw.core.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiReasoningContentModuleTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new OpenAiReasoningContentModule());

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldPatchEmptyReasoningContentForToolCallMessage() throws Exception {
        OpenAiApi.ChatCompletionMessage.ToolCall toolCall = new OpenAiApi.ChatCompletionMessage.ToolCall(
                "call_1",
                "function",
                new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("foo", "{}"));

        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "hello",
                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                null,
                null,
                List.of(toolCall),
                null,
                null,
                null,
                null);

        String json = mapper.writeValueAsString(message);

        assertThat(json).contains("\"reasoning_content\":\"\"");
    }

    @Test
    void shouldRestoreRealReasoningContentFromContext() throws Exception {
        OpenAiReasoningContentContext.push("这是真实的 reasoning 内容");

        OpenAiApi.ChatCompletionMessage.ToolCall toolCall = new OpenAiApi.ChatCompletionMessage.ToolCall(
                "call_1",
                "function",
                new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction("foo", "{}"));

        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "hello",
                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                null,
                null,
                List.of(toolCall),
                null,
                null,
                null,
                null);

        String json = mapper.writeValueAsString(message);

        assertThat(json).contains("\"reasoning_content\":\"这是真实的 reasoning 内容\"");
    }

    @Test
    void shouldNotPatchNonAssistantMessage() throws Exception {
        OpenAiApi.ChatCompletionMessage message = new OpenAiApi.ChatCompletionMessage(
                "user message",
                OpenAiApi.ChatCompletionMessage.Role.USER);

        String json = mapper.writeValueAsString(message);

        assertThat(json).doesNotContain("reasoning_content");
    }
}
```

- [ ] **Step 3: Run the tests**

Run:
```bash
cd /Users/kai/IdeaProjects/meta_claw && ~/.local/tools/apache-maven-3.9.15/bin/mvn -pl meta-claw-core test -Dtest=OpenAiReasoningContentModuleTest
```
Expected: PASS

- [ ] **Step 4: Commit**

```bash
# 先删除旧的 MoonshotSerializerModule，再添加新的 OpenAiReasoningContentModule
rm meta-claw-core/src/main/java/meta/claw/core/llm/provider/MoonshotSerializerModule.java
git add -A
git commit -m "feat(openai-reasoning): replace MoonshotSerializerModule with generic OpenAiReasoningContentModule"
```

---

## Task 3: Create `OpenAiReasoningContentAdvisor`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisor.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisorTest.java`

- [ ] **Step 1: Implement the Advisor**

```java
package meta.claw.core.llm.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 参考 Spring AI Alibaba Playground 的 ReasoningContentAdvisor 实现。
 * 在请求发送前扫描 Prompt 中的 assistant 消息，将其 metadata 中的 reasoningContent
 * 写入 {@link OpenAiReasoningContentContext}，供 {@link OpenAiReasoningContentModule}
 * 在序列化 OpenAI 兼容请求时回填。
 */
@Slf4j
public class OpenAiReasoningContentAdvisor implements BaseAdvisor {

    private final int order;

    public OpenAiReasoningContentAdvisor(Integer order) {
        this.order = order != null ? order : 0;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 每次新的 LLM 调用前清空旧上下文，避免历史数据污染
        OpenAiReasoningContentContext.clear();

        List<Message> messages = chatClientRequest.prompt().getInstructions();
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistantMessage) {
                String reasoningContent = extractReasoningContent(assistantMessage);
                OpenAiReasoningContentContext.push(reasoningContent);
                if (log.isDebugEnabled() && !reasoningContent.isEmpty()) {
                    log.debug("[OpenAiReasoningContentAdvisor] Pushed reasoning_content for assistant message: {}",
                            reasoningContent.substring(0, Math.min(50, reasoningContent.length())) + "...");
                }
            }
        }

        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 请求-响应周期结束，清理线程上下文
        OpenAiReasoningContentContext.remove();
        return chatClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static String extractReasoningContent(AssistantMessage assistantMessage) {
        Object reasoning = null;
        if (assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoningContent");
        }
        if (reasoning == null && assistantMessage.getMetadata() != null) {
            reasoning = assistantMessage.getMetadata().get("reasoning_content");
        }
        return reasoning instanceof String s ? s : "";
    }
}
```

- [ ] **Step 2: Add unit test**

```java
package meta.claw.core.llm.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiReasoningContentAdvisorTest {

    private final OpenAiReasoningContentAdvisor advisor = new OpenAiReasoningContentAdvisor(0);

    @AfterEach
    void tearDown() {
        OpenAiReasoningContentContext.remove();
    }

    @Test
    void shouldExtractReasoningContentFromAssistantMessages() {
        List<org.springframework.ai.chat.messages.Message> messages = List.of(
                new UserMessage("hi"),
                AssistantMessage.builder()
                        .content("thinking...")
                        .properties(Map.of("reasoningContent", "real reasoning"))
                        .build(),
                new UserMessage("ok")
        );
        ChatClientRequest request = new ChatClientRequest(new Prompt(messages), Map.of());

        advisor.before(request, null);

        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("");
        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("real reasoning");
        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("");
    }

    @Test
    void shouldClearContextBeforeEachRequest() {
        OpenAiReasoningContentContext.push("stale");
        ChatClientRequest request = new ChatClientRequest(new Prompt(List.of(new UserMessage("hi"))), Map.of());

        advisor.before(request, null);

        assertThat(OpenAiReasoningContentContext.poll()).isEqualTo("");
        assertThat(OpenAiReasoningContentContext.isEmpty()).isTrue();
    }

    @Test
    void shouldRemoveContextAfterResponse() {
        OpenAiReasoningContentContext.push("value");
        org.springframework.ai.chat.model.ChatResponse chatResponse = org.springframework.ai.chat.model.ChatResponse.builder()
                .build();
        ChatClientResponse response = new ChatClientResponse(chatResponse, Map.of());

        advisor.after(response, null);

        assertThat(OpenAiReasoningContentContext.isEmpty()).isTrue();
    }
}
```

- [ ] **Step 3: Run the test**

Run:
```bash
cd /Users/kai/IdeaProjects/meta_claw && ~/.local/tools/apache-maven-3.9.15/bin/mvn -pl meta-claw-core test -Dtest=OpenAiReasoningContentAdvisorTest
```
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisor.java \
       meta-claw-core/src/test/java/meta/claw/core/llm/provider/OpenAiReasoningContentAdvisorTest.java
git commit -m "feat(openai-reasoning): add Advisor to feed reasoning_content into serializer context"
```

---

## Task 4: Wire Everything into `OpenAiLlmClientProvider`

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java`

- [ ] **Step 1: Update `selectObjectMapper` to always register `OpenAiReasoningContentModule`**

```java
private ObjectMapper selectObjectMapper(String model) {
    // 所有 OpenAI 兼容 provider 都需要修复 reasoningContent 序列化缺陷
    ObjectMapper copy = objectMapper.copy();
    copy.registerModule(new OpenAiReasoningContentModule());
    log.debug("Registered OpenAiReasoningContentModule for model: {}", model);
    return copy;
}
```

- [ ] **Step 2: Update `buildChatClient` to always add `OpenAiReasoningContentAdvisor`**

```java
private ChatClient buildChatClient(ProviderConfig providerConfig) {
    ChatClient chatClient = ChatClient.builder(buildChatModel(providerConfig))
            .defaultAdvisors(
                    new OpenAiReasoningContentAdvisor(100),  // 最外层：在请求发送前提取 reasoningContent
                    ToolCallAdvisor.builder().build(),       // 外层：自动处理 tool calling 循环
                    shortMemoryAdvisor                         // 内层：流式响应持久化到 ShortMemory
            )
            .build();

    if (log.isDebugEnabled()) {
        log.debug("ChatClient created successfully for model: {}", providerConfig.getModel());
    }

    return chatClient;
}
```

**注意：** `OpenAiReasoningContentAdvisor` 的 order 设为 100，确保它的 `before()` 在请求发送前执行。`BaseAdvisor` 的 `before()` 按 order 升序执行，order 越大越靠后（但仍在最外层先执行）。这里只要 `before()` 在最终调用 `ChatModel` 前执行即可，100 足够在外层。

- [ ] **Step 3: Run core tests**

Run:
```bash
cd /Users/kai/IdeaProjects/meta_claw && ~/.local/tools/apache-maven-3.9.15/bin/mvn -pl meta-claw-core test
```
Expected: core 模块所有测试通过（当前基线 107 个）

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java
git commit -m "feat(openai-reasoning): register Advisor and Module for all OpenAI-compatible providers"
```

---

## Task 5: Full Smoke Test

- [ ] **Step 1: Run `./init.sh`**

Run:
```bash
cd /Users/kai/IdeaProjects/meta_claw && ./init.sh
```
Expected: 9 个 reactor 模块全部 SUCCESS，core 107 个测试全部通过，tool 18 个测试全部通过。

- [ ] **Step 2: (Optional) Real CLI smoke test**

如果环境允许，启动 CLI 并对 Moonshot 发起一轮需要 reasoning + tool call 的对话，确认请求 JSON 中 `reasoning_content` 不再是空字符串。

可以在 `OpenAiWebClientFactory` 的日志过滤器中临时打印请求体，或在 `LlmClientManager.logRequestParams` 中打印完整序列化后的消息。

- [ ] **Step 3: Update progress files**

Update:
- `claude-progress.md`：记录本次修复、验证结果
- `feature_list.json`：`llm-001` 增加证据
- `clean-state-checklist.md`：更新最后核对状态

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "fix(openai-reasoning): restore real reasoning_content in outgoing tool_call requests"
```

---

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| ThreadLocal 泄漏 | 低 | Advisor `before()` 先 `clear()`；`after()` 调用 `remove()` |
| 序列化顺序与 Advisor push 顺序不一致 | 低 | 两者都按 `Prompt.messages` 顺序；只 push assistant 消息，serializer 只消费 assistant tool_call 消息 |
| 多线程 Reactive 执行导致上下文不可用 | 中 | 当前 `LlmClientManager` 使用 `blockLast()` 阻塞订阅；若未来改纯异步，需改用 Reactor Context 或重写为自定义 ChatModel |
| `AssistantMessage.metadata` key 变化 | 低 | `extractReasoningContent` 同时检查 `reasoningContent` 和 `reasoning_content` |
| 非 Moonshot 的 OpenAI 兼容 provider 不需要该字段 | 低 | 仅在 assistant + tool_calls + 缺失 reasoning_content 时补字段；空字符串兜底与现有 Moonshot 行为一致 |
| Spring AI 升级后 `reasoningContent` 不再硬编码 null | 低 | 届时 `ChatCompletionMessage` 自身会携带真实值，serializer 不再需要从上下文 poll，可安全移除 Advisor/Module |

---

## Spec Coverage Check

- ✅ 从 `AssistantMessage` 的 metadata/properties 找回 `reasoningContent` → Task 3
- ✅ 在序列化时回填到 `reasoning_content` JSON 字段 → Task 2
- ✅ 空字符串兜底保持向后兼容 → Task 2
- ✅ 不影响非 assistant 消息 → Task 2 test
- ✅ 上下文不泄漏 → Task 3
- ✅ 所有 OpenAI 兼容 provider 自动生效 → Task 4
- ✅ 有测试覆盖 → Task 1/2/3 tests

## Placeholder Scan

- 无 TBD/TODO
- 所有代码块均为可直接运行的 Java
- 所有命令均为真实可执行命令
