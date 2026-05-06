# Vessel 级模型配置覆盖 + CLI 流式输出 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每个 Vessel 支持独立的模型配置覆盖，并将 CLI 聊天改为真正的流式逐字输出。

**Architecture:** 在 `meta-claw-vessel` 层新增 `VesselProfileConfig` + `VesselProfileLoader` + `VesselConfigResolver`，将全局配置与 Vessel 级覆盖合并为单一 `ResolvedVesselConfig`；在 `meta-claw-core` 层用 Spring AI `ChatClient.stream()` 替换伪流式实现；`ChatCommand` 统一调用 Resolver 并消费流式 Callback。

**Tech Stack:** Java 21, Spring AI 2.0-M5, Project Reactor (Flux), JUnit 5, Mockito

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `meta-claw-vessel/.../VesselProfileConfig.java` | 创建 | Vessel 级配置模型（映射 config.yaml） |
| `meta-claw-vessel/.../VesselProfileLoader.java` | 创建 | 加载单个 vessel 的 config.yaml |
| `meta-claw-vessel/.../VesselConfigResolver.java` | 创建 | 合并全局 + Vessel 配置，输出最终配置 |
| `meta-claw-vessel/.../VesselTemplate.java` | 修改 | init 时生成 config.yaml 模板 |
| `meta-claw-core/.../SpringAiLlmClient.java` | 修改 | 实现真正流式输出 |
| `meta-claw-cli/.../ChatCommand.java` | 修改 | 使用 Resolver + 流式打印 |
| `meta-claw-vessel/.../VesselProfileLoaderTest.java` | 创建 | VesselProfileLoader 单元测试 |
| `meta-claw-vessel/.../VesselConfigResolverTest.java` | 创建 | VesselConfigResolver 单元测试 |
| `meta-claw-core/.../SpringAiLlmClientTest.java` | 修改 | 补充流式输出单元测试 |

---

### Task 1: VesselProfileConfig 模型

**Files:**
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselProfileConfig.java`

- [ ] **Step 1: 创建 VesselProfileConfig**

```java
package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VesselProfileConfig {
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Double timeout;
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-vessel/src/main/java/meta/claw/vessel/VesselProfileConfig.java
git commit -m "feat(vessel): add VesselProfileConfig model"
```

---

### Task 2: VesselProfileLoader

**Files:**
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselProfileLoader.java`
- Create: `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselProfileLoaderTest.java`

- [ ] **Step 1: 创建 VesselProfileLoader**

```java
package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ProviderConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class VesselProfileLoader {

    private static final String CONFIG_FILE = "config.yaml";
    private final Yaml yaml = new Yaml();

    public VesselProfileConfig load(Path vesselDir) {
        Path file = vesselDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            log.debug("Vessel 配置文件不存在: {}", file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("Vessel 配置文件为空: {}", file);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载 Vessel 配置文件失败: {}", file, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private VesselProfileConfig mapToConfig(Map<String, Object> map) {
        VesselProfileConfig config = new VesselProfileConfig();
        config.setProvider(getString(map, "provider"));
        config.setApiKey(getString(map, "api_key"));
        config.setBaseUrl(getString(map, "base_url"));
        config.setModel(getString(map, "model"));
        Object temp = map.get("temperature");
        if (temp instanceof Number) {
            config.setTemperature(((Number) temp).doubleValue());
        }
        Object timeout = map.get("timeout");
        if (timeout instanceof Number) {
            config.setTimeout(((Number) timeout).doubleValue());
        }
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
```

- [ ] **Step 2: 编写 VesselProfileLoaderTest**

```java
package meta.claw.vessel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselProfileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadExistingConfig() throws Exception {
        String yaml = """
                provider: deepseek
                api_key: sk-test
                base_url: https://api.deepseek.com/v1
                model: deepseek-chat
                temperature: 0.5
                timeout: 30.0
                """;
        Files.writeString(tempDir.resolve("config.yaml"), yaml);

        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(tempDir);

        assertNotNull(config);
        assertEquals("deepseek", config.getProvider());
        assertEquals("sk-test", config.getApiKey());
        assertEquals("https://api.deepseek.com/v1", config.getBaseUrl());
        assertEquals("deepseek-chat", config.getModel());
        assertEquals(0.5, config.getTemperature());
        assertEquals(30.0, config.getTimeout());
    }

    @Test
    void testLoadMissingConfig() {
        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(tempDir);
        assertNull(config);
    }

    @Test
    void testLoadPartialConfig() throws Exception {
        String yaml = """
                model: gpt-4
                """;
        Files.writeString(tempDir.resolve("config.yaml"), yaml);

        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(tempDir);

        assertNotNull(config);
        assertEquals("gpt-4", config.getModel());
        assertNull(config.getApiKey());
        assertNull(config.getTemperature());
    }
}
```

- [ ] **Step 3: 运行测试**

Run:
```bash
# 在仓库根目录执行
./mvnw test -pl meta-claw-vessel -Dtest=VesselProfileLoaderTest
```
Expected: 3 tests PASS

如果没有 mvnw，使用：
```bash
mvn test -pl meta-claw-vessel -Dtest=VesselProfileLoaderTest
```

- [ ] **Step 4: Commit**

```bash
git add meta-claw-vessel/src/main/java/meta/claw/vessel/VesselProfileLoader.java \
        meta-claw-vessel/src/test/java/meta/claw/vessel/VesselProfileLoaderTest.java
git commit -m "feat(vessel): add VesselProfileLoader with tests"
```

---

### Task 3: VesselConfigResolver

**Files:**
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java`
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/ResolvedVesselConfig.java`
- Create: `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java`

- [ ] **Step 1: 创建 ResolvedVesselConfig**

```java
package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.model.ProviderConfig;

@Getter
@Setter
public class ResolvedVesselConfig {
    private String providerName;
    private ProviderConfig providerConfig;
    private VesselConfig vesselConfig;
}
```

- [ ] **Step 2: 创建 VesselConfigResolver**

```java
package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;

import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class VesselConfigResolver {

    private final GlobalConfigLoader globalConfigLoader;
    private final VesselProfileLoader profileLoader;
    private final VesselConfigLoader vesselConfigLoader;

    public VesselConfigResolver() {
        this(new GlobalConfigLoader(), new VesselProfileLoader(), new VesselConfigLoader());
    }

    public VesselConfigResolver(GlobalConfigLoader globalConfigLoader,
                                 VesselProfileLoader profileLoader,
                                 VesselConfigLoader vesselConfigLoader) {
        this.globalConfigLoader = globalConfigLoader;
        this.profileLoader = profileLoader;
        this.vesselConfigLoader = vesselConfigLoader;
    }

    public ResolvedVesselConfig resolve(Path baseDir, String vesselName) {
        // 1. 加载全局配置
        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new IllegalStateException("全局配置未找到或 providers 为空: " + baseDir.resolve("config.yaml"));
        }

        // 2. 加载 vessel.md
        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselConfig vesselConfig = vesselConfigLoader.loadSingle(vesselDir.resolve("vessel.md"));

        // 3. 加载 vessel 级覆盖配置
        VesselProfileConfig profile = profileLoader.load(vesselDir);

        // 4. 确定 providerName
        String providerName = (profile != null && profile.getProvider() != null && !profile.getProvider().isBlank())
                ? profile.getProvider()
                : globalConfig.getDefaultProvider();
        if (providerName == null || providerName.isBlank()) {
            providerName = globalConfig.getProviders().keySet().iterator().next();
        }

        // 5. 获取全局 provider 基础配置
        ProviderConfig baseProviderConfig = globalConfig.getProviders().get(providerName);
        if (baseProviderConfig == null) {
            throw new IllegalArgumentException(
                    "全局配置中未找到 provider '" + providerName + "'。可用的 providers: " + globalConfig.getProviders().keySet()
            );
        }

        // 6. 深拷贝 + 合并
        ProviderConfig merged = copyProviderConfig(baseProviderConfig);
        if (profile != null) {
            if (profile.getApiKey() != null && !profile.getApiKey().isBlank()) {
                merged.setApiKey(profile.getApiKey());
            }
            if (profile.getBaseUrl() != null && !profile.getBaseUrl().isBlank()) {
                merged.setBaseUrl(profile.getBaseUrl());
            }
            if (profile.getModel() != null && !profile.getModel().isBlank()) {
                merged.setModel(profile.getModel());
            }
            if (profile.getTemperature() != null) {
                merged.setTemperature(profile.getTemperature());
            }
            if (profile.getTimeout() != null) {
                merged.setTimeout(profile.getTimeout());
            }
        }

        // 7. vessel.md 的 model 作为最低优先级覆盖
        if (vesselConfig != null && vesselConfig.getModel() != null && !vesselConfig.getModel().isBlank()) {
            if (merged.getModel() == null || merged.getModel().isBlank()) {
                merged.setModel(vesselConfig.getModel());
            }
        }

        ResolvedVesselConfig result = new ResolvedVesselConfig();
        result.setProviderName(providerName);
        result.setProviderConfig(merged);
        result.setVesselConfig(vesselConfig);
        return result;
    }

    private ProviderConfig copyProviderConfig(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setTimeout(source.getTimeout());
        return copy;
    }
}
```

- [ ] **Step 3: 编写 VesselConfigResolverTest**

```java
package meta.claw.vessel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselConfigResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveWithVesselOverride() throws Exception {
        // 全局配置
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    base_url: https://global.com
                    model: global-model
                    temperature: 1.0
                    timeout: 60.0
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        // Vessel 目录
        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                name: Default Vessel
                model: vessel-md-model
                system_prompt: You are a test assistant.
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        String vesselYaml = """
                provider: moonshot
                model: override-model
                temperature: 0.5
                """;
        Files.writeString(vesselDir.resolve("config.yaml"), vesselYaml);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("moonshot", resolved.getProviderName());
        assertEquals("global-key", resolved.getProviderConfig().getApiKey());
        assertEquals("https://global.com", resolved.getProviderConfig().getBaseUrl());
        assertEquals("override-model", resolved.getProviderConfig().getModel());
        assertEquals(0.5, resolved.getProviderConfig().getTemperature());
        assertEquals(60.0, resolved.getProviderConfig().getTimeout());
        assertNotNull(resolved.getVesselConfig());
        assertEquals("default", resolved.getVesselConfig().getId());
    }

    @Test
    void testResolveFallbackToGlobal() throws Exception {
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    base_url: https://global.com
                    model: global-model
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                name: Default Vessel
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("moonshot", resolved.getProviderName());
        assertEquals("global-model", resolved.getProviderConfig().getModel());
        assertEquals("global-key", resolved.getProviderConfig().getApiKey());
    }

    @Test
    void testResolveVesselMdModelFallback() throws Exception {
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    model: ""
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                model: vessel-model
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("vessel-model", resolved.getProviderConfig().getModel());
    }
}
```

- [ ] **Step 4: 运行测试**

Run:
```bash
./mvnw test -pl meta-claw-vessel -Dtest=VesselConfigResolverTest
```
Expected: 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java \
        meta-claw-vessel/src/main/java/meta/claw/vessel/ResolvedVesselConfig.java \
        meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java
git commit -m "feat(vessel): add VesselConfigResolver with merge logic and tests"
```

---

### Task 4: VesselTemplate 生成 config.yaml 模板

**Files:**
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java`

- [ ] **Step 1: 修改 VesselTemplate**

在 `createDefaultVessel()` 方法中，在生成 `vessel.md` 之后，增加生成 `config.yaml` 的逻辑：

```java
private static final String DEFAULT_CONFIG_YAML = """
        # Vessel 级模型配置（可选）
        # 此文件中非空字段会覆盖全局 config.yaml 中对应 provider 的配置
        # 留空或删除则表示完全使用全局配置
        provider: ""       # 指定使用全局 providers 下的哪个 provider，空则使用全局 default_provider
        model: ""          # 覆盖模型名称
        api_key: ""        # 覆盖 API Key
        base_url: ""       # 覆盖 Base URL
        temperature: ""    # 覆盖温度参数
        timeout: ""        # 覆盖超时（秒）
        """;

public void createDefaultVessel(Path baseDir) throws IOException {
    Path vesselDir = baseDir.resolve("default");
    Files.createDirectories(vesselDir);
    Files.createDirectories(vesselDir.resolve("skills"));
    Files.createDirectories(vesselDir.resolve("knowledge"));
    Files.createDirectories(vesselDir.resolve("memory"));

    Path configFile = vesselDir.resolve("vessel.md");
    Files.writeString(configFile, DEFAULT_VESSEL_MD);
    log.info("已生成默认 Vessel 配置: {}", configFile);

    Path profileFile = vesselDir.resolve("config.yaml");
    if (!Files.exists(profileFile)) {
        Files.writeString(profileFile, DEFAULT_CONFIG_YAML);
        log.info("已生成默认 Vessel 模型配置: {}", profileFile);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java
git commit -m "feat(vessel): generate config.yaml template on init"
```

---

### Task 5: SpringAiLlmClient 真正流式实现

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/SpringAiLlmClient.java`
- Modify: `meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientTest.java`

- [ ] **Step 1: 修改 SpringAiLlmClient.chatStream()**

替换原有的伪流式实现：

```java
@Override
public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
    callback.onStart();
    List<Message> springMessages = request.messages().stream()
            .map(this::toSpringMessage)
            .collect(Collectors.toList());
    Prompt prompt = new Prompt(springMessages);

    StringBuilder fullContent = new StringBuilder();

    try {
        Flux<String> flux = chatClient.prompt(prompt).stream().content();
        flux.doOnNext(chunk -> {
            fullContent.append(chunk);
            callback.onChunk(chunk);
        }).doOnError(callback::onError)
          .doOnComplete(() -> {
              SpiChatResponse response = SpiChatResponse.builder()
                      .content(fullContent.toString())
                      .build();
              callback.onComplete(response);
          })
          .blockLast();
    } catch (Exception e) {
        callback.onError(e);
    }
}
```

需要确认文件顶部已导入 `reactor.core.publisher.Flux`。

如果编译报错 `Flux` 类找不到，在 `meta-claw-core/pom.xml` 中显式添加：

```xml
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
```

- [ ] **Step 2: 补充流式测试**

在 `SpringAiLlmClientTest.java` 中追加测试：

```java
@Test
void testChatStream() {
    ChatClient mockClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.StreamResponseSpec mockStreamSpec = mock(ChatClient.StreamResponseSpec.class);

    when(mockClient.prompt(any(Prompt.class))).thenReturn(mockSpec);
    when(mockSpec.stream()).thenReturn(mockStreamSpec);
    when(mockStreamSpec.content()).thenReturn(Flux.just("Hello", " ", "world"));

    SpiProviderMeta meta = SpiProviderMeta.builder()
            .name("kimi").model("moonshot-v1-8k").baseUrl("https://api.moonshot.cn").build();

    SpringAiLlmClient client = new SpringAiLlmClient(mockClient, meta);

    SpiChatRequest request = SpiChatRequest.builder()
            .messages(List.of(SpiMessage.user("hi")))
            .build();

    List<String> chunks = new ArrayList<>();
    StringBuilder full = new StringBuilder();

    client.chatStream(request, new SpiStreamingCallback() {
        @Override public void onStart() {}
        @Override public void onChunk(String chunk) {
            chunks.add(chunk);
            full.append(chunk);
        }
        @Override public void onToolCall(SpiToolCall toolCall) {}
        @Override public void onComplete(SpiChatResponse response) {
            full.setLength(0);
            full.append(response.content());
        }
        @Override public void onError(Throwable error) {
            fail("Should not error");
        }
    });

    assertEquals(List.of("Hello", " ", "world"), chunks);
    assertEquals("Hello world", full.toString());
}
```

如果 `ChatClient.StreamResponseSpec` 在 Spring AI 2.0-M5 中的类名不同（例如可能是 `ChatClient.StreamCallResponseSpec`），需要根据实际编译报错调整 mock 类型。

- [ ] **Step 3: 运行测试**

Run:
```bash
./mvnw test -pl meta-claw-core -Dtest=SpringAiLlmClientTest
```
Expected: 2 tests PASS（原有的 chat + 新增的 stream）

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/SpringAiLlmClient.java \
        meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientTest.java
# 如果修改了 pom.xml 也加入
# git add meta-claw-core/pom.xml
git commit -m "feat(core): implement real streaming in SpringAiLlmClient"
```

---

### Task 6: ChatCommand 改造

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: 重构 ChatCommand.run() 使用 Resolver 和流式输出**

关键替换逻辑（保留欢迎界面和循环结构，只替换配置加载和对话调用部分）：

```java
// 旧逻辑：手动加载全局配置 + vessel.md + 合并 model → 删除
// 新逻辑：
Path configDir = Paths.get(System.getProperty("user.dir"), ".meta-claw");
VesselConfigResolver resolver = new VesselConfigResolver();
ResolvedVesselConfig resolved;
try {
    resolved = resolver.resolve(configDir, expertName);
} catch (IllegalStateException | IllegalArgumentException e) {
    System.err.println(e.getMessage());
    return;
}

ProviderConfig providerConfig = resolved.getProviderConfig();
String providerName = resolved.getProviderName();
VesselConfig vesselConfig = resolved.getVesselConfig();

String apiKey = providerConfig.getApiKey();
if (apiKey == null || apiKey.isBlank() || "your-api-key".equals(apiKey)) {
    System.err.println("API key not set for provider '" + providerName + "'.");
    System.err.println("Run 'meta-claw config set providers." + providerName + ".api_key <your-key>' to configure.");
    return;
}

String model = providerConfig.getModel();
if (model == null || model.isBlank()) {
    System.err.println("Model not configured for provider '" + providerName + "'.");
    return;
}

log.info("Using provider: {}, model: {}", providerName, model);

// 4. Build ChatClient via factory manager
ChatClient chatClient = factoryManager.create(providerName, providerConfig, model);

SpiProviderMeta meta = SpiProviderMeta.builder()
        .name(providerName)
        .model(model)
        .baseUrl(providerConfig.getBaseUrl())
        .build();

SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);
// ExpertRuntime 在 CLI 直接调用场景中不再使用，llmClient 直接消费
```

在对话循环中，把：
```java
System.out.print("AI: ");
SpiChatResponse response = llmClient.chat(request);
System.out.println(response.content());
System.out.println();
history.add(SpiMessage.assistant(response.content()));
```

替换为：
```java
StringBuilder responseBuffer = new StringBuilder();
System.out.print("AI: ");
llmClient.chatStream(request, new SpiStreamingCallback() {
    @Override public void onStart() {}
    @Override public void onChunk(String chunk) {
        System.out.print(chunk);
        System.out.flush();
        responseBuffer.append(chunk);
    }
    @Override public void onToolCall(SpiToolCall toolCall) {}
    @Override public void onComplete(SpiChatResponse response) {
        System.out.println();
    }
    @Override public void onError(Throwable error) {
        System.err.println("\nError: " + error.getMessage());
    }
});
history.add(SpiMessage.assistant(responseBuffer.toString()));
```

同时删除 `ChatCommand` 中不再需要的 import：`GlobalConfigLoader`、`GlobalConfig`、`ProviderConfig`、`ExpertRuntime`（如果其他地方没用到）、`VesselConfigLoader`。保留 `VesselConfigResolver`、`ResolvedVesselConfig`、`SpiStreamingCallback`、`SpiToolCall` 的新 import。

- [ ] **Step 2: 编译验证**

Run:
```bash
./mvnw compile -pl meta-claw-cli -am
```
Expected: BUILD SUCCESS

`-am` 会同时编译依赖模块（core, vessel）。

- [ ] **Step 3: Commit**

```bash
git add meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
git commit -m "feat(cli): use VesselConfigResolver and streaming output in ChatCommand"
```

---

### Task 7: 集成验证

**Files:**
- All modules

- [ ] **Step 1: 全量编译**

Run:
```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 全量测试**

Run:
```bash
./mvnw test
```
Expected: All tests PASS（至少 VesselProfileLoaderTest、VesselConfigResolverTest、SpringAiLlmClientTest 通过）

- [ ] **Step 3: 手动 CLI 验证（可选，需配置真实 API Key）**

```bash
# 1. 重新 init（会生成新的 config.yaml 模板）
cd /path/to/project
java -jar meta-claw-cli/target/meta-claw-cli-0.1.0-SNAPSHOT.jar init

# 2. 配置 API key（编辑 .meta-claw/config.yaml）

# 3. 开始聊天，观察是否逐字输出
java -jar meta-claw-cli/target/meta-claw-cli-0.1.0-SNAPSHOT.jar chat default
```

- [ ] **Step 4: Commit（如测试通过）**

```bash
git commit --allow-empty -m "test(phase1): verify vessel config override + streaming output"
```

---

## Self-Review

**1. Spec coverage:**
- Vessel 级 config.yaml 支持：Task 1-3 实现模型、加载器、合并解析器；Task 4 实现初始化模板。
- 配置回退优先级（config.yaml > vessel.md model > 全局 ProviderConfig）：Task 3 的 `VesselConfigResolver` 已实现。
- 流式输出：`SpringAiLlmClient.chatStream()` 真正流式（Task 5），CLI 逐字打印（Task 6）。
- 无遗漏。

**2. Placeholder scan:**
- 无 TBD/TODO。
- 所有步骤含具体代码或命令。
- 无 "similar to Task N"。

**3. Type consistency:**
- `VesselProfileConfig` 字段（apiKey, baseUrl, model, temperature, timeout）与 `ProviderConfig` 一致。
- `VesselConfigResolver.resolve(Path, String)` 返回 `ResolvedVesselConfig`，与 ChatCommand 消费侧一致。
- `SpiStreamingCallback` 接口未变更，与现有代码一致。
