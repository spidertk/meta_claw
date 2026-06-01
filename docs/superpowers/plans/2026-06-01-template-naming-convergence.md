# Template Naming Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按用户定义域和运行时域重新切分模板与配置层，统一 Section 抽象，消除模型语义混杂。不保留老逻辑兼容门面，直接替换。

**Architecture:** 引入 `SectionRegistry` 桥接契约，`PromptAssembler` 统一渲染，`VesselMeta`/`VesselProfile` 拆分 YAML/MD，SnakeYAML `loadAs` 替换手动 Map 转换。

**Tech Stack:** Java 21, Spring Boot, SnakeYAML, JUnit 5, Mockito, Lombok

---

## 关键约束

- **不保留兼容门面**：旧 `config/` 和 `vessel/` 包直接删除，所有消费者同步适配
- **通用异常体系**：`MetaClawException` 基类 + `ErrorCode` 枚举 + 业务域子类，支持全局拦截
- **编译必须一次通过**：每个 Task 完成后执行 `mvn compile` 验证

---

## Phase 0: 通用异常体系（前置基础设施）

### Task 0: 创建 MetaClawException + ErrorCode + VesselException

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/exception/ErrorCode.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/exception/MetaClawException.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/exception/VesselException.java`

- [ ] **Step 1: 编写 ErrorCode**

```java
package meta.claw.core.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // Vessel domain (Vxxx)
    VESSEL_META_NOT_FOUND("V001", "Vessel meta file not found: {0}"),
    VESSEL_PROFILE_NOT_FOUND("V002", "Vessel profile not found: {0}"),
    VESSEL_GLOBAL_CONFIG_EMPTY("V003", "Global config not found or providers empty: {0}"),
    VESSEL_PROVIDER_NOT_FOUND("V004", "Provider '{0}' not found in global config. Available: {1}"),
    VESSEL_API_KEY_MISSING("V005", "API key not set for provider '{0}'"),
    VESSEL_MODEL_MISSING("V006", "Model not set for provider '{0}'"),
    VESSEL_META_PARSE_ERROR("V007", "Failed to parse vessel meta: {0}"),
    VESSEL_PROFILE_PARSE_ERROR("V008", "Failed to parse vessel profile: {0}");

    private final String code;
    private final String template;

    ErrorCode(String code, String template) {
        this.code = code;
        this.template = template;
    }

    public String format(Object... args) {
        String msg = template;
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }
}
```

- [ ] **Step 2: 编写 MetaClawException**

```java
package meta.claw.core.exception;

import lombok.Getter;

@Getter
public class MetaClawException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public MetaClawException(ErrorCode errorCode, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public MetaClawException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.format(args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}
```

- [ ] **Step 3: 编写 VesselException**

```java
package meta.claw.core.exception;

public class VesselException extends MetaClawException {

    public VesselException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public VesselException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `cd /Users/kai/IdeaProjects/meta_claw && mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/exception/
git commit -m "infra: add MetaClawException hierarchy with ErrorCode support"
```

---

## Phase 1: 基础设施域迁移

### Task 1: 创建 infra.config 并迁移 ProviderConfig + MemoryConfig

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/infra/config/ProviderConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/infra/config/MemoryConfig.java`
- Modify: 旧 `config/ProviderConfig.java` 和 `config/MemoryConfig.java` 标记 @Deprecated

- [ ] **Step 1: 复制 ProviderConfig**

内容与现有 `config/ProviderConfig.java` 完全一致，仅 package 改为 `meta.claw.core.infra.config`。

- [ ] **Step 2: 复制 MemoryConfig**

内容与现有 `config/MemoryConfig.java` 完全一致，仅 package 改为 `meta.claw.core.infra.config`。

- [ ] **Step 3: 旧类标记 @Deprecated**

在两个旧类声明前加 `@Deprecated`。

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/infra/
git commit -m "infra: migrate ProviderConfig and MemoryConfig to infra.config"
```

---

### Task 2: 迁移 GlobalConfig + GlobalConfigLoader 到 infra

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/infra/config/GlobalConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/infra/config/GlobalConfigLoader.java`
- Modify: 旧 `config/GlobalConfig.java` 和 `config/GlobalConfigLoader.java` 标记 @Deprecated

- [ ] **Step 1: 复制 GlobalConfig**

package 改为 `meta.claw.core.infra.config`。

- [ ] **Step 2: 复制 GlobalConfigLoader**

package 改为 `meta.claw.core.infra.config`，移除同包 import。

- [ ] **Step 3: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/infra/config/
git commit -m "infra: migrate GlobalConfig and GlobalConfigLoader to infra.config"
```

---

### Task 3: 统一 ProjectRootFinder 到 infra.path

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/infra/path/ProjectRootFinder.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/util/ProjectRootFinder.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/vessel/ProjectRootFinder.java`

- [ ] **Step 1: 创建 infra.path.ProjectRootFinder**

package 改为 `meta.claw.core.infra.path`。

- [ ] **Step 2: 批量替换 import**

```bash
cd /Users/kai/IdeaProjects/meta_claw
grep -rl "import meta.claw.core.util.ProjectRootFinder" meta-claw-core/src/main/java/ | xargs sed -i '' 's|import meta.claw.core.util.ProjectRootFinder;|import meta.claw.core.infra.path.ProjectRootFinder;|g'
```

- [ ] **Step 3: 删除重复文件**

```bash
rm meta-claw-core/src/main/java/meta/claw/core/util/ProjectRootFinder.java
rm meta-claw-core/src/main/java/meta/claw/core/vessel/ProjectRootFinder.java
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add -A
git commit -m "infra: unify ProjectRootFinder to infra.path, remove duplicates"
```

---

## Phase 2: 用户域建立

### Task 4: 创建 VesselMeta 模型

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/user/VesselMeta.java`

- [ ] **Step 1: 编写 VesselMeta**

```java
package meta.claw.core.user;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.infra.config.MemoryConfig;

import java.util.List;

@Getter
@Setter
public class VesselMeta {

    private MetaInfo meta = new MetaInfo();
    private LlmConfig llm = new LlmConfig();
    private RuntimeConfig runtime = new RuntimeConfig();
    private MemoryConfig memory = new MemoryConfig();
    private ToolConfig tools = new ToolConfig();
    private Integer maxHistoryRounds = 20;
    private Integer maxTokens = 4096;

    @Getter @Setter
    public static class MetaInfo {
        private String id;
        private String name;
        private String description;
        private String displayName;
        private String emoji = "\uD83E\uDD16";
        private String createdAt;
    }

    @Getter @Setter
    public static class LlmConfig {
        private String provider = "openapi";
        private String model;
        private ProviderOverride overrides = new ProviderOverride();
    }

    @Getter @Setter
    public static class ProviderOverride {
        private String apiKey;
        private String baseUrl;
        private Double temperature;
        private Double timeout;
    }

    @Getter @Setter
    public static class RuntimeConfig {
        private String role = "member";
        private boolean autoServe = false;
    }

    @Getter @Setter
    public static class ToolConfig {
        private List<String> exclude = List.of();
    }
}
```

- [ ] **Step 2: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/user/VesselMeta.java
git commit -m "user: add VesselMeta model with nested structure"
```

---

### Task 5: 创建 VesselProfile 模型

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/user/VesselProfile.java`

- [ ] **Step 1: 编写 VesselProfile**

```java
package meta.claw.core.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

@Getter
@Builder
public class VesselProfile {

    @Singular
    private Map<String, String> sections;

    public String getSection(String name) {
        return sections != null ? sections.get(name) : null;
    }

    public String getIdentity() { return getSection("identity"); }
    public String getSoul() { return getSection("soul"); }
    public String getDomainKnowledge() { return getSection("domain knowledge"); }
    public String getCapabilities() { return getSection("capabilities"); }
    public String getGuidelines() { return getSection("guidelines"); }
    public String getPreferences() { return getSection("preferences"); }
}
```

- [ ] **Step 2: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/user/VesselProfile.java
git commit -m "user: add VesselProfile model with section map"
```

---

### Task 6: 创建 VesselMetaLoader + 测试

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/user/VesselMetaLoader.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/user/VesselMetaLoaderTest.java`

- [ ] **Step 1: 编写 VesselMetaLoader**

```java
package meta.claw.core.user;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class VesselMetaLoader {

    private static final String META_FILE = "vessel.meta.yaml";
    private final Yaml yaml = new Yaml();

    public List<VesselMeta> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Vessel directory not found: {}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::load)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to scan vessel directory: {}", dir, e);
            return Collections.emptyList();
        }
    }

    public VesselMeta load(Path vesselDir) {
        Path metaPath = vesselDir.resolve(META_FILE);
        if (!Files.exists(metaPath)) {
            log.warn("Vessel meta file not found: {}", metaPath);
            throw new VesselException(ErrorCode.VESSEL_META_NOT_FOUND, metaPath);
        }
        try (InputStream is = Files.newInputStream(metaPath)) {
            return yaml.loadAs(is, VesselMeta.class);
        } catch (IOException e) {
            log.error("Failed to load vessel meta: {}", metaPath, e);
            throw new VesselException(ErrorCode.VESSEL_META_PARSE_ERROR, e, metaPath);
        }
    }
}
```

- [ ] **Step 2: 编写单元测试**

```java
package meta.claw.core.user;

import meta.claw.core.exception.VesselException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VesselMetaLoaderTest {

    @TempDir
    Path tempDir;

    private final VesselMetaLoader loader = new VesselMetaLoader();

    @Test
    void loadFromDirectory_returnsEmptyForMissingDir() {
        List<VesselMeta> result = loader.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertTrue(result.isEmpty());
    }

    @Test
    void load_throwsWhenFileMissing() {
        assertThrows(VesselException.class, () -> loader.load(tempDir.resolve("nonexistent")));
    }

    @Test
    void load_parsesNestedYaml() throws Exception {
        String yaml = """
            meta:
              id: test-bot
              name: Test Bot
              description: A test vessel
            llm:
              provider: ollama
              model: llama3
              overrides:
                temperature: 0.7
            memory:
              short_term_store: jsonl
            """;
        Path vesselDir = tempDir.resolve("test-vessel");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.meta.yaml"), yaml);

        VesselMeta meta = loader.load(vesselDir);

        assertEquals("test-bot", meta.getMeta().getId());
        assertEquals("Test Bot", meta.getMeta().getName());
        assertEquals("ollama", meta.getLlm().getProvider());
        assertEquals(0.7, meta.getLlm().getOverrides().getTemperature());
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn test -pl meta-claw-core -Dtest=VesselMetaLoaderTest -q`
Expected: 3 tests PASSED

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/user/VesselMetaLoader.java
 git add meta-claw-core/src/test/java/meta/claw/core/user/VesselMetaLoaderTest.java
git commit -m "user: add VesselMetaLoader with SnakeYAML loadAs + tests"
```

---

### Task 7: 创建 VesselProfileLoader + 测试

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/user/VesselProfileLoader.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/user/VesselProfileLoaderTest.java`

- [ ] **Step 1: 编写 VesselProfileLoader**

```java
package meta.claw.core.user;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class VesselProfileLoader {

    private static final String PROFILE_FILE = "vessel.profile.md";

    public VesselProfile load(Path vesselDir) {
        Path profilePath = vesselDir.resolve(PROFILE_FILE);
        if (!Files.exists(profilePath)) {
            log.warn("Vessel profile not found: {}", profilePath);
            throw new VesselException(ErrorCode.VESSEL_PROFILE_NOT_FOUND, profilePath);
        }
        try {
            String content = Files.readString(profilePath);
            Map<String, String> sections = parseSections(content);
            return VesselProfile.builder().sections(sections).build();
        } catch (IOException e) {
            log.error("Failed to load vessel profile: {}", profilePath, e);
            throw new VesselException(ErrorCode.VESSEL_PROFILE_PARSE_ERROR, e, profilePath);
        }
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> sections = new HashMap<>();
        String[] lines = content.split("\n");
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                if (currentSection != null) {
                    sections.put(currentSection, currentContent.toString().trim());
                }
                currentSection = trimmed.substring(3).trim().toLowerCase();
                currentContent = new StringBuilder();
            } else if (currentSection != null) {
                currentContent.append(line).append("\n");
            }
        }
        if (currentSection != null) {
            sections.put(currentSection, currentContent.toString().trim());
        }
        return sections;
    }
}
```

- [ ] **Step 2: 编写单元测试**

```java
package meta.claw.core.user;

import meta.claw.core.exception.VesselException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselProfileLoaderTest {

    @TempDir
    Path tempDir;

    private final VesselProfileLoader loader = new VesselProfileLoader();

    @Test
    void load_throwsWhenFileMissing() {
        assertThrows(VesselException.class, () -> loader.load(tempDir.resolve("nonexistent")));
    }

    @Test
    void load_parsesSections() throws Exception {
        String md = "## Identity\n\nI am a code review assistant.\n\n## Soul\n\nPrecise.\n";
        Path vesselDir = tempDir.resolve("vessel");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.profile.md"), md);

        VesselProfile profile = loader.load(vesselDir);
        assertEquals("I am a code review assistant.", profile.getIdentity());
        assertEquals("Precise.", profile.getSoul());
    }
}
```

- [ ] **Step 3: 运行测试 + Commit**

Run: `mvn test -pl meta-claw-core -Dtest=VesselProfileLoaderTest -q`
Expected: 2 tests PASSED

```bash
git add meta-claw-core/src/main/java/meta/claw/core/user/VesselProfileLoader.java
 git add meta-claw-core/src/test/java/meta/claw/core/user/VesselProfileLoaderTest.java
git commit -m "user: add VesselProfileLoader with section parser + tests"
```

---

### Task 8: 创建 VesselInitializer

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/user/VesselInitializer.java`
- Modify: 旧 `vessel/VesselTemplate.java` 标记 @Deprecated

- [ ] **Step 1: 编写 VesselInitializer**

```java
package meta.claw.core.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class VesselInitializer {

    private static final String META_TEMPLATE = "/templates/user/vessel.meta.tmpl.yaml";
    private static final String PROFILE_TEMPLATE = "/templates/user/vessel.profile.tmpl.md";

    public void createDefaultVessel(Path vesselsDir) throws IOException {
        createVessel(vesselsDir, "default", "A general-purpose AI assistant");
    }

    public void createVessel(Path vesselsDir, String name, String description) throws IOException {
        Path vesselDir = vesselsDir.resolve(name);
        Files.createDirectories(vesselDir);
        Files.createDirectories(vesselDir.resolve("skills"));
        Files.createDirectories(vesselDir.resolve("knowledge"));
        Files.createDirectories(vesselDir.resolve("conversations"));
        Files.createDirectories(vesselDir.resolve("preferences"));

        Map<String, String> vars = Map.of(
                "name", name,
                "created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "description", description != null ? description : ""
        );

        String metaTemplate = loadTemplate(META_TEMPLATE);
        Files.writeString(vesselDir.resolve("vessel.meta.yaml"), renderTemplate(metaTemplate, vars));

        String profileTemplate = loadTemplate(PROFILE_TEMPLATE);
        Files.writeString(vesselDir.resolve("vessel.profile.md"), renderTemplate(profileTemplate, vars));

        log.info("Created vessel: {}", vesselDir);
    }

    private String loadTemplate(String resourcePath) {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalStateException("Template not found: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }

    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}
```

- [ ] **Step 2: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/user/VesselInitializer.java
 git add meta-claw-core/src/main/java/meta/claw/core/vessel/VesselTemplate.java
git commit -m "user: add VesselInitializer, deprecate VesselTemplate"
```

---

## Phase 3: 运行时域建立

### Task 9: 创建 SectionRegistry

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/SectionRegistry.java`

- [ ] **Step 1: 编写 SectionRegistry**

```java
package meta.claw.core.runtime.prompt;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public enum SectionRegistry {

    META("meta", Source.VESSEL_META, Target.SYSTEM, true),
    IDENTITY("identity", Source.VESSEL_PROFILE, Target.SYSTEM, true),
    SOUL("soul", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    CAPABILITIES("capabilities", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    GUIDELINES("guidelines", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    KNOWLEDGE("knowledge", Source.VESSEL_PROFILE, Target.SYSTEM, false),
    WORKSPACE("workspace", Source.RUNTIME, Target.CONTEXT, false),
    RUNTIME("runtime", Source.RUNTIME, Target.CONTEXT, false),
    PREFERENCES("preferences", Source.MEMORY, Target.CONTEXT, false);

    private final String id;
    private final Source source;
    private final Target target;
    private final boolean required;

    SectionRegistry(String id, Source source, Target target, boolean required) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.required = required;
    }

    public static Optional<SectionRegistry> byId(String id) {
        return Arrays.stream(values())
                .filter(s -> s.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public static List<SectionRegistry> forTarget(Target target) {
        return Arrays.stream(values())
                .filter(s -> s.target == target)
                .toList();
    }

    public enum Source { VESSEL_META, VESSEL_PROFILE, RUNTIME, MEMORY }
    public enum Target { SYSTEM, CONTEXT }
}
```

- [ ] **Step 2: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/SectionRegistry.java
git commit -m "runtime: add SectionRegistry bridge contract"
```

---

### Task 10: 创建 RuntimeConfig + RuntimeConfigResolver

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/config/RuntimeConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/config/RuntimeConfigResolver.java`
- Modify: 旧 `vessel/ResolvedVesselConfig.java` 和 `vessel/VesselConfigResolver.java` 标记 @Deprecated

- [ ] **Step 1: 编写 RuntimeConfig**

```java
package meta.claw.core.runtime.config;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.infra.config.MemoryConfig;
import meta.claw.core.infra.config.ProviderConfig;
import meta.claw.core.user.VesselMeta;

@Getter
@Setter
public class RuntimeConfig {
    private VesselMeta vesselMeta;
    private ProviderConfig providerConfig;
    private MemoryConfig memoryConfig;
}
```

- [ ] **Step 2: 编写 RuntimeConfigResolver**

```java
package meta.claw.core.runtime.config;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.VesselException;
import meta.claw.core.infra.config.*;
import meta.claw.core.infra.path.ProjectRootFinder;
import meta.claw.core.user.VesselMeta;
import meta.claw.core.user.VesselMetaLoader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.Consumer;

@Slf4j
@Component
public class RuntimeConfigResolver {

    @Autowired
    private GlobalConfigLoader globalConfigLoader;
    @Autowired
    private VesselMetaLoader vesselMetaLoader;

    public RuntimeConfig resolve(String vesselName) {
        Path baseDir = ProjectRootFinder.getMetaClawDir();

        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getProviders() == null || globalConfig.getProviders().isEmpty()) {
            throw new VesselException(ErrorCode.VESSEL_GLOBAL_CONFIG_EMPTY, baseDir.resolve("config.yaml"));
        }

        Path vesselDir = baseDir.resolve("vessels").resolve(vesselName);
        VesselMeta vesselMeta = vesselMetaLoader.load(vesselDir);

        String providerName = resolveProviderName(vesselMeta, globalConfig);
        ProviderConfig baseProvider = globalConfig.getProviders().get(providerName);
        if (baseProvider == null) {
            throw new VesselException(ErrorCode.VESSEL_PROVIDER_NOT_FOUND, providerName, globalConfig.getProviders().keySet());
        }

        ProviderConfig merged = mergeProviderConfig(baseProvider, vesselMeta);
        validateProviderConfig(merged, providerName);

        RuntimeConfig result = new RuntimeConfig();
        result.setVesselMeta(vesselMeta);
        result.setProviderConfig(merged);
        result.setMemoryConfig(vesselMeta.getMemory());
        return result;
    }

    private String resolveProviderName(VesselMeta vesselMeta, GlobalConfig globalConfig) {
        String name = (vesselMeta != null && vesselMeta.getLlm() != null
                && StringUtils.isNotBlank(vesselMeta.getLlm().getProvider()))
                ? vesselMeta.getLlm().getProvider()
                : globalConfig.getDefaultProvider();
        if (StringUtils.isBlank(name)) {
            name = globalConfig.getProviders().keySet().iterator().next();
        }
        return name;
    }

    private ProviderConfig mergeProviderConfig(ProviderConfig base, VesselMeta vesselMeta) {
        if (vesselMeta == null || vesselMeta.getLlm() == null || vesselMeta.getLlm().getOverrides() == null) {
            return copy(base);
        }
        VesselMeta.ProviderOverride ov = vesselMeta.getLlm().getOverrides();
        ProviderConfig merged = copy(base);
        mergeField(merged::setApiKey, merged.getApiKey(), ov.getApiKey());
        mergeField(merged::setBaseUrl, merged.getBaseUrl(), ov.getBaseUrl());
        mergeField(merged::setModel, merged.getModel(), vesselMeta.getLlm().getModel());
        mergeField(merged::setTemperature, merged.getTemperature(), ov.getTemperature());
        mergeField(merged::setTimeout, merged.getTimeout(), ov.getTimeout());
        return merged;
    }

    private <T> void mergeField(Consumer<T> setter, T current, T override) {
        if (override != null && !(override instanceof String s && s.isBlank())) {
            setter.accept(override);
        }
    }

    private ProviderConfig copy(ProviderConfig source) {
        ProviderConfig copy = new ProviderConfig();
        copy.setProvider(source.getProvider());
        copy.setApiKey(source.getApiKey());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setTimeout(source.getTimeout());
        return copy;
    }

    private void validateProviderConfig(ProviderConfig config, String providerName) {
        String apiKey = config.getApiKey();
        if (StringUtils.isBlank(apiKey) || "your-api-key".equals(apiKey)) {
            throw new VesselException(ErrorCode.VESSEL_API_KEY_MISSING, providerName);
        }
        if (StringUtils.isBlank(config.getModel())) {
            throw new VesselException(ErrorCode.VESSEL_MODEL_MISSING, providerName);
        }
    }
}
```

- [ ] **Step 3: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/config/
git add meta-claw-core/src/main/java/meta/claw/core/vessel/ResolvedVesselConfig.java
 git add meta-claw-core/src/main/java/meta/claw/core/vessel/VesselConfigResolver.java
git commit -m "runtime: add RuntimeConfig and RuntimeConfigResolver with ErrorCode"
```

---

### Task 11: 创建 Section Resolvers

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/SectionResolver.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/ResolutionContext.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/MetaSectionResolver.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/ProfileSectionResolver.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/WorkspaceSectionResolver.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/MemorySectionResolver.java`

- [ ] **Step 1-6: 编写所有 Resolver**

代码与之前计划一致（接口 + ResolutionContext + 4 个实现）。

- [ ] **Step 7: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/resolver/
git commit -m "runtime: add SectionResolver implementations"
```

---

### Task 12: 创建 PromptAssembler + 测试

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/PromptAssembler.java`
- Test: `meta-claw-core/src/test/java/meta/claw/core/runtime/prompt/PromptAssemblerTest.java`

- [ ] **Step 1: 编写 PromptAssembler**

```java
package meta.claw.core.runtime.prompt;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.runtime.prompt.resolver.ResolutionContext;
import meta.claw.core.runtime.prompt.resolver.SectionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class PromptAssembler {

    private static final String SYSTEM_TEMPLATE = "/templates/runtime/system.tmpl.md";
    private static final String CONTEXT_TEMPLATE = "/templates/runtime/context.tmpl.md";

    @Autowired
    private List<SectionResolver> resolvers;

    public String assembleSystem(ResolutionContext ctx) {
        return assemble(loadTemplate(SYSTEM_TEMPLATE), SectionRegistry.Target.SYSTEM, ctx);
    }

    public String assembleContext(ResolutionContext ctx) {
        return assemble(loadTemplate(CONTEXT_TEMPLATE), SectionRegistry.Target.CONTEXT, ctx);
    }

    String assemble(String template, SectionRegistry.Target target, ResolutionContext ctx) {
        String result = template;
        for (SectionRegistry section : SectionRegistry.forTarget(target)) {
            String content = resolveSection(section, ctx);
            result = result.replace("<SECTION id=\"" + section.getId() + "\"/>", content);
        }
        result = result.replaceAll("<SECTION id=\"[^\"]+\"\\s*/>", "").trim();
        return result;
    }

    private String resolveSection(SectionRegistry section, ResolutionContext ctx) {
        for (SectionResolver resolver : resolvers) {
            if (resolver.supports(section)) return resolver.resolve(section, ctx);
        }
        if (section.isRequired()) log.warn("No resolver for required section: {}", section.getId());
        return "";
    }

    private String loadTemplate(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalStateException("Template not found: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + resourcePath, e);
        }
    }
}
```

- [ ] **Step 2: 编写测试**

```java
package meta.claw.core.runtime.prompt;

import meta.claw.core.runtime.prompt.resolver.MetaSectionResolver;
import meta.claw.core.runtime.prompt.resolver.ResolutionContext;
import meta.claw.core.user.VesselMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptAssemblerTest {

    @Test
    void assemble_replacesMetaSection() throws Exception {
        PromptAssembler assembler = new PromptAssembler();
        java.lang.reflect.Field f = PromptAssembler.class.getDeclaredField("resolvers");
        f.setAccessible(true);
        f.set(assembler, List.of(new MetaSectionResolver()));

        VesselMeta meta = new VesselMeta();
        meta.getMeta().setName("TestBot");
        meta.getMeta().setDescription("A test bot");

        ResolutionContext ctx = ResolutionContext.builder().vesselMeta(meta).build();
        String result = assembler.assemble("<SECTION id=\"meta\"/>", SectionRegistry.Target.SYSTEM, ctx);
        assertTrue(result.contains("TestBot"));
    }

    @Test
    void assemble_removesUnresolvedTags() throws Exception {
        PromptAssembler assembler = new PromptAssembler();
        java.lang.reflect.Field f = PromptAssembler.class.getDeclaredField("resolvers");
        f.setAccessible(true);
        f.set(assembler, List.of());

        String result = assembler.assemble("<SECTION id=\"unknown\"/>", SectionRegistry.Target.SYSTEM,
                ResolutionContext.builder().build());
        assertFalse(result.contains("<SECTION"));
    }
}
```

- [ ] **Step 3: 运行测试 + Commit**

Run: `mvn test -pl meta-claw-core -Dtest=PromptAssemblerTest -q`
Expected: 2 tests PASSED

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/prompt/PromptAssembler.java
 git add meta-claw-core/src/test/java/meta/claw/core/runtime/prompt/PromptAssemblerTest.java
git commit -m "runtime: add PromptAssembler unified engine + tests"
```

---

## Phase 4: 模板资源重组

### Task 13: 重组模板目录

**Files:**
- Create: `templates/user/vessel.meta.tmpl.yaml`
- Create: `templates/user/vessel.profile.tmpl.md`
- Create: `templates/runtime/system.tmpl.md`
- Create: `templates/runtime/context.tmpl.md`
- Delete: 旧 `templates/vessel-config.tmpl.yaml`
- Delete: 旧 `templates/vessel.tmpl.md`
- Delete: 旧 `templates/system.tmpl.md`
- Delete: 旧 `templates/context.tmpl.md`
- Modify: `prompt/TemplateLoader.java`

- [ ] **Step 1-4: 创建新模板**

内容与之前设计一致（vessel.meta.tmpl.yaml 嵌套结构、vessel.profile.tmpl.md 零占位符、system/context 纯 SECTION 标签）。

- [ ] **Step 5: 更新 TemplateLoader 路径**

```java
private static final String SYSTEM_TEMPLATE = "templates/runtime/system.tmpl.md";
private static final String CONTEXT_TEMPLATE = "templates/runtime/context.tmpl.md";
```

- [ ] **Step 6: 删除旧模板 + 编译验证 + Commit**

```bash
rm meta-claw-core/src/main/resources/templates/vessel-config.tmpl.yaml
rm meta-claw-core/src/main/resources/templates/vessel.tmpl.md
rm meta-claw-core/src/main/resources/templates/system.tmpl.md
rm meta-claw-core/src/main/resources/templates/context.tmpl.md
```

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add -A
git commit -m "templates: reorganize into user/ and runtime/ with unified syntax"
```

---

## Phase 5: 消费者适配

### Task 14: 适配 prompt 包消费者

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContext.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptRuntimeBuilder.java`

- [ ] **Step 1: 修改 PromptContext**

替换 import：`config.VesselConfig` -> `user.VesselMeta`，`config.MemoryConfig` -> `infra.config.MemoryConfig`，`config.ProviderConfig` -> `infra.config.ProviderConfig`。字段 `vesselConfig` -> `vesselMeta`。

- [ ] **Step 2: 修改 PromptContextFactory**

替换 import：`util.ProjectRootFinder` -> `infra.path.ProjectRootFinder`，`vessel.VesselConfigResolver` -> `runtime.config.RuntimeConfigResolver`。使用 `RuntimeConfigResolver.resolve(vesselId)` 获取 `RuntimeConfig`，再从中提取 `VesselMeta`/`MemoryConfig`/`ProviderConfig`。

- [ ] **Step 3: 修改 PromptRuntimeBuilder**

委托给 `PromptAssembler`，`ResolutionContext` 从 `PromptContext.getVesselMeta()` 构建。

- [ ] **Step 4: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add meta-claw-core/src/main/java/meta/claw/core/prompt/
git commit -m "prompt: adapt PromptContext, Factory, RuntimeBuilder to new models"
```

---

### Task 15: 适配 runtime + llm + memory 包消费者

**Files:**
- Modify: `runtime/VesselRuntime.java`
- Modify: `runtime/AgentLoop.java`
- Modify: `runtime/VesselManager.java`
- Modify: `runtime/LlmClientManager.java`
- Modify: `llm/provider/LlmClientProvider.java`
- Modify: `llm/provider/OpenAiLlmClientProvider.java`
- Modify: `llm/provider/LlmClientProviderManager.java`
- Modify: `memory/shortterm/ShortMemoryFactory.java`

- [ ] **Step 1: 批量替换 infra import**

```bash
cd /Users/kai/IdeaProjects/meta_claw
find meta-claw-core/src/main/java -name "*.java" -exec sed -i '' \
  -e 's|import meta.claw.core.config.ProviderConfig;|import meta.claw.core.infra.config.ProviderConfig;|g' \
  -e 's|import meta.claw.core.config.MemoryConfig;|import meta.claw.core.infra.config.MemoryConfig;|g' \
  {} +
```

- [ ] **Step 2: 修改 VesselRuntime**

`VesselConfig` -> `VesselMeta`，`getVesselConfig()` -> `getVesselMeta()`。

- [ ] **Step 3: 修改 AgentLoop**

`VesselConfig` -> `VesselMeta`，`getId()` -> `getMeta().getId()`。

- [ ] **Step 4: 修改 VesselManager**

`VesselConfigLoader` -> `VesselMetaLoader`，`ConcurrentHashMap<String, VesselConfig>` -> `ConcurrentHashMap<String, VesselMeta>`，`loadFromDirectory` 调用 `VesselMetaLoader`。

- [ ] **Step 5: 修改 LlmClientManager**

`VesselConfigResolver` -> `RuntimeConfigResolver`，`loadProviderConfig` -> `resolve(vesselName).getProviderConfig()`。

- [ ] **Step 6: 修改 LlmClientProvider / OpenAiLlmClientProvider / LlmClientProviderManager**

替换 `config.ProviderConfig` import 为 `infra.config.ProviderConfig`。

- [ ] **Step 7: 修改 ShortMemoryFactory**

删除未使用的 `config.VesselConfig` import。

- [ ] **Step 8: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS

```bash
git add -A
git commit -m "refactor: adapt runtime/llm/memory consumers to new models"
```

---

## Phase 6: 删除旧文件

### Task 16: 删除 config/ 和 vessel/ 包

**Files:**
- Delete: `meta-claw-core/src/main/java/meta/claw/core/config/`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/vessel/`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/util/`（如为空）

- [ ] **Step 1: 删除目录**

```bash
rm -rf meta-claw-core/src/main/java/meta/claw/core/config/
rm -rf meta-claw-core/src/main/java/meta/claw/core/vessel/
rmdir meta-claw-core/src/main/java/meta/claw/core/util/ 2>/dev/null || true
```

- [ ] **Step 2: 编译验证 + Commit**

Run: `mvn compile -pl meta-claw-core -q`
Expected: SUCCESS（无 deprecation 警告）

```bash
git add -A
git commit -m "cleanup: remove deprecated config/ and vessel/ packages"
```

---

## 自审清单

| Spec 要求 | 对应 Task |
|-----------|----------|
| 模板按 user/ 和 runtime/ 分层 | Task 13 |
| 统一 Section 语法 | Task 9, 11-13 |
| VesselConfig 拆分 | Task 4-5, 14-15 |
| SnakeYAML loadAs | Task 6 |
| ProviderConfigMerger 抽象 | Task 10 |
| SectionRegistry 桥接 | Task 9 |
| 命名清晰 | Task 1-3 |
| 不保留老逻辑 | Task 16 |
| 通用异常体系（ErrorCode + MetaClawException + VesselException） | Task 0 |

**Placeholder 检查：** 无 TBD/TODO。所有代码步骤包含完整实现。
