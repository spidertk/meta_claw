# Phase 1：Vessel MVP 基础骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将原有 Expert 模型重构为 Vessel（数字员工）模型，`meta-cli init` 创建新的目录结构（`config.yaml` + `vessels/` + `skills/`），`meta-cli chat default` 能读取 provider 配置并与 Kimi 对话。

**Architecture:** 模块 `meta-claw-export` 更名为 `meta-claw-vessel`，新增 `GlobalConfig`/`ProviderConfig` 模型和 `GlobalConfigLoader`，`VesselConfigLoader` 支持 `vessel.md` 格式。CLI 层重构 `InitCommand`、`ConfigCommand`、`ChatCommand` 以适配新目录结构。暂不改动 `meta-claw-core` 中 `ExpertConfig`/`ExpertRuntime`/`ExpertManager`（后续阶段统一改名）。

**Tech Stack:** Java 17, Maven, Spring Boot 3.2, Spring AI 0.8.0, picocli 4.7.6, SnakeYAML 2.2, Lombok 1.18.30

---

## 文件结构映射

### 删除/重命名
| 原路径 | 新路径 | 说明 |
|--------|--------|------|
| `meta-claw-export/` | `meta-claw-vessel/` | 模块目录重命名 |
| `meta-claw-export/pom.xml` | `meta-claw-vessel/pom.xml` | 更新坐标和依赖 |
| `meta-claw-export/src/main/java/meta/claw/export/` | `meta-claw-vessel/src/main/java/meta/claw/vessel/` | 包名变更 |
| `meta-claw-export/src/test/java/meta/claw/export/` | `meta-claw-vessel/src/test/java/meta/claw/vessel/` | 包名变更 |

### 新建文件
| 路径 | 职责 |
|------|------|
| `meta-claw-core/src/main/java/meta/claw/core/model/GlobalConfig.java` | 全局配置模型：`defaultProvider` + `providers` Map |
| `meta-claw-core/src/main/java/meta/claw/core/model/ProviderConfig.java` | Provider 配置模型：`apiKey`, `baseUrl`, `model`, `temperature`, `timeout` |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfig.java` | Vessel 配置模型（映射 `vessel.md`）：`id`, `name`, `description`, `emoji`, `model`, `systemPrompt`, `memoryEnabled`, `knowledgeDir` |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/GlobalConfigLoader.java` | 加载 `~/.meta-claw/config.yaml` 为 `GlobalConfig` |

### 修改文件
| 路径 | 修改内容 |
|------|----------|
| `pom.xml`（根） | 模块 `meta-claw-export` → `meta-claw-vessel` |
| `meta-claw-cli/pom.xml` | 依赖 `meta-claw-export` → `meta-claw-vessel` |
| `meta-claw-vessel/pom.xml` | `artifactId`, `name`, `description` 更新 |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java` | 原 `ExpertConfigLoader`：包名 + 类名 + 返回类型改为 `VesselConfig` |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java` | 原 `ExpertTemplate`：包名 + 类名 + 生成内容改为 `vessel.md` 格式 |
| `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java` | 原 `ExpertConfigLoaderTest`：包名 + 类名 + 引用更新 |
| `meta-claw-cli/src/main/java/meta/claw/cli/InitCommand.java` | 创建 `config.yaml` + `vessels/default/` 目录结构 + `skills/` |
| `meta-claw-cli/src/main/java/meta/claw/cli/ConfigCommand.java` | 操作 YAML 结构 `~/.meta-claw/config.yaml`（支持 nested key） |
| `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java` | 从 `GlobalConfigLoader` 读 provider，从 `VesselConfigLoader` 读 vessel，构造 `SpringAiLlmClient` |

---

## Task 1：模块重命名 `meta-claw-export` → `meta-claw-vessel`

**Files:**
- Modify: `pom.xml`（根模块列表）
- Modify: `meta-claw-cli/pom.xml`（依赖引用）
- Rename: `meta-claw-export/` → `meta-claw-vessel/`
- Modify: `meta-claw-vessel/pom.xml`（坐标、名称）

- [ ] **Step 1: 重命名目录**

```bash
cd /Users/kai/IdeaProjects/meta_claw
mv meta-claw-export meta-claw-vessel
```

- [ ] **Step 2: 更新根 pom.xml**

```xml
<!-- 原 -->
<module>meta-claw-export</module>
<!-- 改为 -->
<module>meta-claw-vessel</module>
```

- [ ] **Step 3: 更新 `meta-claw-vessel/pom.xml`**

```xml
<artifactId>meta-claw-vessel</artifactId>
<name>Meta-Claw Vessel</name>
<description>数字员工（Vessel）配置管理模块，提供 YAML 配置加载、模板生成和目录结构初始化</description>
```

- [ ] **Step 4: 更新 `meta-claw-cli/pom.xml` 依赖**

```xml
<!-- 原 -->
<dependency>
    <groupId>com.meta</groupId>
    <artifactId>meta-claw-export</artifactId>
</dependency>
<!-- 改为 -->
<dependency>
    <groupId>com.meta</groupId>
    <artifactId>meta-claw-vessel</artifactId>
</dependency>
```

- [ ] **Step 5: 在 IDEA 中 Reload Maven Project，验证无报错**

---

## Task 2：包名和类名重构（`meta-claw-vessel` 内部）

**Files:**
- Rename/Move: `meta-claw-vessel/src/main/java/meta/claw/export/ExpertConfigLoader.java` → `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java`
- Rename/Move: `meta-claw-vessel/src/main/java/meta/claw/export/ExpertTemplate.java` → `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java`
- Rename/Move: `meta-claw-vessel/src/test/java/meta/claw/export/ExpertConfigLoaderTest.java` → `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java`
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/GlobalConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/ProviderConfig.java`
- Create: `meta-claw-vessel/src/main/java/meta/claw/vessel/GlobalConfigLoader.java`

- [ ] **Step 1: 移动主代码文件到新包**

```bash
mkdir -p meta-claw-vessel/src/main/java/meta/claw/vessel
mkdir -p meta-claw-vessel/src/test/java/meta/claw/vessel
mv meta-claw-vessel/src/main/java/meta/claw/export/ExpertConfigLoader.java meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
mv meta-claw-vessel/src/main/java/meta/claw/export/ExpertTemplate.java meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java
mv meta-claw-vessel/src/test/java/meta/claw/export/ExpertConfigLoaderTest.java meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
rm -rf meta-claw-vessel/src/main/java/meta/claw/export
rm -rf meta-claw-vessel/src/test/java/meta/claw/export
```

- [ ] **Step 2: 创建 `VesselConfig.java`**

```java
package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;

/**
 * Vessel 配置模型，映射 vessel.md 的 YAML frontmatter。
 */
@Getter
@Setter
public class VesselConfig {
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String model;
    private String systemPrompt;
    private boolean memoryEnabled;
    private String knowledgeDir;
}
```

- [ ] **Step 3: 创建 `GlobalConfig.java`**

```java
package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 全局配置模型，映射 ~/.meta-claw/config.yaml。
 */
@Getter
@Setter
public class GlobalConfig {
    private String defaultProvider;
    private Map<String, ProviderConfig> providers;
}
```

- [ ] **Step 4: 创建 `ProviderConfig.java`**

```java
package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Provider 配置模型，对应 config.yaml 中 providers.<name> 下的字段。
 */
@Getter
@Setter
public class ProviderConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Double timeout;
}
```

- [ ] **Step 5: 创建 `GlobalConfigLoader.java`**

```java
package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class GlobalConfigLoader {

    private static final String CONFIG_FILE = "config.yaml";
    private final Yaml yaml = new Yaml();

    public GlobalConfig load(Path baseDir) {
        Path file = baseDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            log.warn("全局配置文件不存在: {}", file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("配置文件为空: {}", file);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载全局配置失败: {}", file, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GlobalConfig mapToConfig(Map<String, Object> map) {
        GlobalConfig config = new GlobalConfig();
        config.setDefaultProvider(getString(map, "default_provider"));
        Object providersObj = map.get("providers");
        if (providersObj instanceof Map) {
            Map<String, Object> providersMap = (Map<String, Object>) providersObj;
            Map<String, ProviderConfig> providers = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : providersMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    providers.put(entry.getKey(), mapToProviderConfig((Map<String, Object>) entry.getValue()));
                }
            }
            config.setProviders(providers);
        }
        return config;
    }

    private ProviderConfig mapToProviderConfig(Map<String, Object> map) {
        ProviderConfig config = new ProviderConfig();
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

- [ ] **Step 6: 重写 `VesselConfigLoader.java`**

```java
package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class VesselConfigLoader {

    private static final String CONFIG_FILE = "vessel.md";
    private final Yaml yaml = new Yaml();

    public List<VesselConfig> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Vessel 配置目录不存在: {}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(sub -> sub.resolve(CONFIG_FILE))
                    .filter(Files::exists)
                    .map(this::loadSingle)
                    .filter(config -> config != null && config.getId() != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描 Vessel 配置目录失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    public VesselConfig loadSingle(Path path) {
        try {
            String content = Files.readString(path);
            String yamlContent = extractYamlFrontmatter(content);
            if (yamlContent == null || yamlContent.isBlank()) {
                log.warn("vessel.md 中没有 YAML frontmatter: {}", path);
                return null;
            }
            Map<String, Object> map = yaml.load(yamlContent);
            if (map == null) {
                log.warn("YAML frontmatter 解析为空: {}", path);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载 Vessel 配置失败: {}", path, e);
            return null;
        }
    }

    /**
     * 从 vessel.md 内容中提取 YAML frontmatter（--- 包围的部分）。
     */
    private String extractYamlFrontmatter(String content) {
        int first = content.indexOf("---");
        if (first == -1) return null;
        int second = content.indexOf("---", first + 3);
        if (second == -1) return null;
        return content.substring(first + 3, second).trim();
    }

    @SuppressWarnings("unchecked")
    private VesselConfig mapToConfig(Map<String, Object> map) {
        VesselConfig config = new VesselConfig();
        config.setId(getString(map, "id"));
        config.setName(getString(map, "name"));
        config.setDescription(getString(map, "description"));
        config.setEmoji(getString(map, "emoji"));
        config.setModel(getString(map, "model"));
        config.setSystemPrompt(getString(map, "system_prompt"));
        Object memory = map.get("memory_enabled");
        config.setMemoryEnabled(memory instanceof Boolean ? (Boolean) memory : false);
        config.setKnowledgeDir(getString(map, "knowledge_dir"));
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
```

- [ ] **Step 7: 重写 `VesselTemplate.java`**

```java
package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class VesselTemplate {

    private static final String DEFAULT_VESSEL_MD = """
            ---
            id: default
            name: Default Vessel
            description: A general-purpose AI assistant
            emoji: 🤖
            model: kimi-k2.5
            system_prompt: |
              You are a helpful AI assistant. Answer user questions concisely and accurately.
            memory_enabled: true
            knowledge_dir: knowledge
            ---
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
    }
}
```

- [ ] **Step 8: 更新 `VesselConfigLoaderTest.java`**

```java
package meta.claw.vessel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VesselConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadSingle() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);
        String content = """
                ---
                id: test-vessel
                name: Test Vessel
                model: kimi-k2.5
                system_prompt: You are a test assistant.
                memory_enabled: true
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), content);

        VesselConfigLoader loader = new VesselConfigLoader();
        VesselConfig config = loader.loadSingle(vesselDir.resolve("vessel.md"));

        assertNotNull(config);
        assertEquals("test-vessel", config.getId());
        assertEquals("Test Vessel", config.getName());
        assertEquals("kimi-k2.5", config.getModel());
        assertTrue(config.isMemoryEnabled());
    }

    @Test
    void testLoadFromDirectory() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.md"), """
                ---
                id: default
                name: Default
                ---
                """);

        VesselConfigLoader loader = new VesselConfigLoader();
        List<VesselConfig> configs = loader.loadFromDirectory(tempDir);

        assertEquals(1, configs.size());
        assertEquals("default", configs.get(0).getId());
    }
}
```

- [ ] **Step 9: 编译验证 `meta-claw-vessel` 模块**

在 IDEA 中 Reload Maven Project，然后编译 `meta-claw-vessel`，确保无报错。

---

## Task 3：重构 `InitCommand`（创建新目录结构）

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/InitCommand.java`

- [ ] **Step 1: 重写 `InitCommand.java`**

```java
package meta.claw.cli;

import meta.claw.vessel.VesselTemplate;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "init", description = "Initialize Meta-Claw config directory and default vessel")
public class InitCommand implements Runnable {

    @Override
    public void run() {
        try {
            Path baseDir = Paths.get(System.getProperty("user.home"), ".meta-claw");
            Files.createDirectories(baseDir);
            Files.createDirectories(baseDir.resolve("vessels"));
            Files.createDirectories(baseDir.resolve("skills"));

            // 创建默认 vessel
            VesselTemplate template = new VesselTemplate();
            template.createDefaultVessel(baseDir.resolve("vessels"));

            // 创建全局配置文件（如果不存在）
            Path configFile = baseDir.resolve("config.yaml");
            if (!Files.exists(configFile)) {
                String defaultConfig = """
                        default_provider: moonshot
                        providers:
                          moonshot:
                            api_key: "your-api-key"
                            base_url: "https://api.moonshot.cn/v1"
                            model: "kimi-k2.5"
                            temperature: 1
                            timeout: 60.0
                        """;
                Files.writeString(configFile, defaultConfig);
            }

            System.out.println("Meta-Claw initialized at: " + baseDir);
            System.out.println("Run 'meta-claw chat default' to start chatting.");
            System.out.println("Remember to set your API key: meta-claw config set providers.moonshot.api_key <your-key>");
        } catch (Exception e) {
            System.err.println("Init failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

---

## Task 4：重构 `ConfigCommand`（操作 YAML 结构 config.yaml）

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ConfigCommand.java`

- [ ] **Step 1: 重写 `ConfigCommand.java`**

```java
package meta.claw.cli;

import meta.claw.vessel.GlobalConfigLoader;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Command(name = "config", description = "Manage global configuration")
public class ConfigCommand implements Runnable {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".meta-claw");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yaml");

    @Parameters(index = "0", description = "Action: set, get, list")
    private String action;

    @Parameters(index = "1", arity = "0..1", description = "Key, e.g. providers.moonshot.api_key")
    private String key;

    @Parameters(index = "2", arity = "0..1", description = "Value")
    private String value;

    @Override
    public void run() {
        try {
            switch (action) {
                case "set" -> setConfig(key, value);
                case "get" -> getConfig(key);
                case "list" -> listConfig();
                default -> System.err.println("Unknown action: " + action);
            }
        } catch (IOException e) {
            System.err.println("Config error: " + e.getMessage());
        }
    }

    private void setConfig(String key, String value) throws IOException {
        if (key == null || value == null) {
            System.err.println("Usage: meta-claw config set <key> <value>");
            System.err.println("Example: meta-claw config set providers.moonshot.api_key sk-xxx");
            return;
        }
        Files.createDirectories(CONFIG_DIR);
        Yaml yaml = new Yaml();
        Map<String, Object> configMap;
        if (Files.exists(CONFIG_FILE)) {
            configMap = yaml.load(Files.readString(CONFIG_FILE));
        } else {
            configMap = new java.util.LinkedHashMap<>();
        }
        setNestedValue(configMap, key.split("\\."), value);
        Files.writeString(CONFIG_FILE, yaml.dump(configMap));
        System.out.println("Config saved: " + key);
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String[] keys, String value) {
        for (int i = 0; i < keys.length - 1; i++) {
            map = (Map<String, Object>) map.computeIfAbsent(keys[i], k -> new java.util.LinkedHashMap<>());
        }
        // 尝试解析为数字
        Object parsedValue = parseValue(value);
        map.put(keys[keys.length - 1], parsedValue);
    }

    private Object parseValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return value;
    }

    @SuppressWarnings("unchecked")
    private void getConfig(String key) throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            System.out.println("No config found.");
            return;
        }
        GlobalConfigLoader loader = new GlobalConfigLoader();
        GlobalConfig config = loader.load(CONFIG_DIR);
        if (config == null) {
            System.out.println("Config file is empty.");
            return;
        }
        if (key == null) {
            System.out.println("default_provider: " + config.getDefaultProvider());
            return;
        }
        String[] keys = key.split("\\.");
        if (keys.length >= 2 && "providers".equals(keys[0])) {
            ProviderConfig provider = config.getProviders() != null ? config.getProviders().get(keys[1]) : null;
            if (provider == null) {
                System.out.println("Provider not found: " + keys[1]);
                return;
            }
            if (keys.length == 2) {
                System.out.println("api_key: " + maskKey(provider.getApiKey()));
                System.out.println("base_url: " + provider.getBaseUrl());
                System.out.println("model: " + provider.getModel());
                System.out.println("temperature: " + provider.getTemperature());
                System.out.println("timeout: " + provider.getTimeout());
            } else if (keys.length >= 3) {
                String field = keys[2];
                switch (field) {
                    case "api_key" -> System.out.println(maskKey(provider.getApiKey()));
                    case "base_url" -> System.out.println(provider.getBaseUrl());
                    case "model" -> System.out.println(provider.getModel());
                    case "temperature" -> System.out.println(provider.getTemperature());
                    case "timeout" -> System.out.println(provider.getTimeout());
                    default -> System.out.println("Unknown field: " + field);
                }
            }
        } else {
            System.out.println("Key not found: " + key);
        }
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) return "***";
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private void listConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            System.out.println("No config found. Run 'meta-claw init' first.");
            return;
        }
        System.out.println(Files.readString(CONFIG_FILE));
    }
}
```

- [ ] **Step 2: 编译验证**

---

## Task 5：重构 `ChatCommand`（整合新配置读取）

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: 重写 `ChatCommand.java`**

```java
package meta.claw.cli;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.GlobalConfig;
import meta.claw.core.model.ProviderConfig;
import meta.claw.core.runtime.ExpertRuntime;
import meta.claw.core.runtime.SpringAiLlmClient;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.vessel.GlobalConfigLoader;
import meta.claw.vessel.VesselConfig;
import meta.claw.vessel.VesselConfigLoader;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Command(name = "chat", description = "Chat with a vessel")
public class ChatCommand implements Runnable {

    private final ChatClient chatClient;

    public ChatCommand(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Parameters(index = "0", defaultValue = "default", description = "Vessel name")
    private String vesselName;

    @Override
    public void run() {
        Path baseDir = Paths.get(System.getProperty("user.home"), ".meta-claw");
        Path vesselsDir = baseDir.resolve("vessels");

        // 1. 加载全局配置（provider 信息）
        GlobalConfigLoader globalLoader = new GlobalConfigLoader();
        GlobalConfig globalConfig = globalLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null) {
            System.err.println("Global config not found. Run 'meta-claw init' first.");
            return;
        }

        String providerName = globalConfig.getDefaultProvider();
        ProviderConfig providerConfig = globalConfig.getProviders().get(providerName);
        if (providerConfig == null) {
            System.err.println("Provider not configured: " + providerName);
            return;
        }
        if (providerConfig.getApiKey() == null || providerConfig.getApiKey().isBlank()
                || "your-api-key".equals(providerConfig.getApiKey())) {
            System.err.println("API key not set. Run: meta-claw config set providers." + providerName + ".api_key <your-key>");
            return;
        }

        // 2. 加载 Vessel 配置
        VesselConfigLoader vesselLoader = new VesselConfigLoader();
        VesselConfig vesselConfig = vesselLoader.loadSingle(vesselsDir.resolve(vesselName).resolve("vessel.md"));
        if (vesselConfig == null) {
            System.err.println("Vessel not found: " + vesselName);
            System.err.println("Run 'meta-claw init' to create default vessel.");
            return;
        }

        // 3. 构造 ProviderMeta 和 LlmClient
        String model = vesselConfig.getModel() != null ? vesselConfig.getModel() : providerConfig.getModel();
        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name(providerName)
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);
        ExpertRuntime runtime = new ExpertRuntime(null, chatClient);

        System.out.println("Chat with " + vesselConfig.getName() + " (" + model + ")");
        System.out.println("Type /exit to quit.");
        System.out.println();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<SpiMessage> history = new ArrayList<>();
        history.add(SpiMessage.system(vesselConfig.getSystemPrompt()));

        try {
            while (true) {
                System.out.print("> ");
                String input = reader.readLine();
                if (input == null || "/exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                if ("/clear".equalsIgnoreCase(input.trim())) {
                    history.clear();
                    history.add(SpiMessage.system(vesselConfig.getSystemPrompt()));
                    System.out.println("History cleared.");
                    continue;
                }

                history.add(SpiMessage.user(input));
                SpiChatRequest request = SpiChatRequest.builder().messages(history).build();

                System.out.print("AI: ");
                SpiChatResponse response = llmClient.chat(request);
                System.out.println(response.content());
                System.out.println();

                history.add(SpiMessage.assistant(response.content()));
            }
        } catch (Exception e) {
            log.error("Chat error", e);
            System.err.println("Error: " + e.getMessage());
        }

        System.out.println("Goodbye!");
    }
}
```

**注意：** `ExpertRuntime` 的构造函数目前接受 `(ExpertConfig, ChatClient)`。这里传 `null` 作为第一个参数是因为当前 `ExpertRuntime.chat(String)` 内部只使用 `config.getSystemPrompt()` 和 `config.getId()`，而我们在 `ChatCommand` 中已经自己处理了 system prompt 注入到 `history` 中。后续阶段会重构 `ExpertRuntime` → `VesselRuntime`。

如果 `ExpertRuntime` 不接受 `null`，需要临时修改其构造函数允许 `null` 的 `ExpertConfig`：

```java
public ExpertRuntime(ExpertConfig config, ChatClient chatClient) {
    this.config = config;
    this.chatClient = chatClient;
    if (config != null) {
        log.info("ExpertRuntime 初始化完成: expertId={}, model={}",
                config.getId(), config.getModel());
    }
}
```

- [ ] **Step 2: 临时修改 `ExpertRuntime` 构造函数允许 null config**

```java
public ExpertRuntime(ExpertConfig config, ChatClient chatClient) {
    this.config = config;
    this.chatClient = chatClient;
    if (config != null) {
        log.info("ExpertRuntime 初始化完成: expertId={}, model={}, systemPromptLength={}",
                config.getId(), config.getModel(),
                config.getSystemPrompt() != null ? config.getSystemPrompt().length() : 0);
    }
}
```

- [ ] **Step 3: 编译验证 `meta-claw-cli`**

---

## Task 6：根 pom.xml 和依赖检查

**Files:**
- Modify: `pom.xml`（根）
- Modify: `meta-claw-vessel/pom.xml`

- [ ] **Step 1: 确保根 pom.xml 中 dependencyManagement 包含 `meta-claw-vessel`**

检查根 `pom.xml` 的 `dependencyManagement` 中是否有：

```xml
<dependency>
    <groupId>com.meta</groupId>
    <artifactId>meta-claw-vessel</artifactId>
    <version>${project.version}</version>
</dependency>
```

如果没有，添加。

- [ ] **Step 2: 确保 `meta-claw-vessel/pom.xml` 依赖 `meta-claw-core` 和 Lombok**

```xml
<dependencies>
    <dependency>
        <groupId>com.meta</groupId>
        <artifactId>meta-claw-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 3: 确保 `meta-claw-vessel/pom.xml` 有 maven-compiler-plugin + Lombok annotationProcessorPaths**

参照 `meta-claw-core/pom.xml` 中的 `maven-compiler-plugin` 配置，复制到 `meta-claw-vessel/pom.xml`。

---

## Task 7：全量编译验证与验收测试

- [ ] **Step 1: IDEA 中 Reload All Maven Projects**

- [ ] **Step 2: Build → Rebuild Project**

预期结果：所有模块编译通过，无错误。

- [ ] **Step 3: 运行 `VesselConfigLoaderTest` 和 `VesselConfigLoaderTest`**

预期结果：测试通过。

- [ ] **Step 4: 手动验收测试**

在 IDEA 中配置 `CliApplication` 的运行参数：
- **Program arguments:** `init`
- **Environment variables:** 无

运行后检查 `~/.meta-claw/` 目录：
```bash
ls ~/.meta-claw/
# 期望输出：config.yaml  skills/  vessels/

ls ~/.meta-claw/vessels/default/
# 期望输出：knowledge/  memory/  skills/  vessel.md

cat ~/.meta-claw/config.yaml
# 期望输出：default_provider: moonshot ...
```

- [ ] **Step 5: 设置 API key 并测试 chat**

运行参数改为 `config set providers.moonshot.api_key sk-kimi-CgMHWhx9CPhWtRdQVupfTrjsgQbcJuUAfKjnIHhFdouuMesg9uWbsOrIZ0KbS8rS`

然后运行参数改为 `chat default`，预期能与 Kimi 对话。

---

## Self-Review

**1. Spec coverage:**
- ✅ 模块重命名 `meta-claw-export` → `meta-claw-vessel`
- ✅ 新增 `GlobalConfig` / `ProviderConfig` / `VesselConfig` 模型
- ✅ 新增 `GlobalConfigLoader` / `VesselConfigLoader`
- ✅ `InitCommand` 创建新目录结构
- ✅ `ConfigCommand` 操作 YAML 结构
- ✅ `ChatCommand` 整合新配置读取
- ✅ 编译验证步骤

**2. Placeholder scan:**
- 无 "TBD"、"TODO"、"implement later"
- 所有步骤包含具体代码
- 无模糊描述

**3. Type consistency:**
- `VesselConfigLoader` 返回 `VesselConfig`（Task 2）
- `GlobalConfigLoader` 返回 `GlobalConfig`（Task 2）
- `ChatCommand` 使用 `VesselConfig` 和 `GlobalConfig`（Task 5）
- 类型名称一致，无冲突

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-30-phase1-vessel-mvp.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
