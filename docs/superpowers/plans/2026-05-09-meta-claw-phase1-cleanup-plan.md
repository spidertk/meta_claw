# Meta-Claw Phase 1 收尾实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 Phase 1 所有遗留问题，修复 VesselManager 配置加载阻塞缺陷，使 ChatCommand 支持历史持久化，清理全部 Expert/专家 残留注释。

**Architecture:** 三条线独立推进：线 1 迁移 VesselConfigLoader 到 core 并重构 VesselManager；线 2 补充 Store 单元测试并集成到 ChatCommand；线 3 清理注释并修正模板拼写。每步 TDD，频繁提交。

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito, SnakeYAML, Jackson, Spring Boot 3.2.5, Spring AI 1.1.4, JLine3, Picocli, Lombok

---

## 文件结构

### 新增文件
- `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java` — VesselManager 单元测试
- `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java` — 迁移后的 VesselConfigLoader 测试
- `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java` — JSONL 对话存储测试
- `meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java` — 文件偏好存储测试

### 迁移文件
- `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java` → `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java`
- `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java` → `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java`

### 修改文件
- `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java` — 重构 loadVessels，删除冗余解析方法
- `meta-claw-cli/pom.xml` — 添加 meta-claw-store 依赖
- `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java` — 集成 JsonlConversationStore
- `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java` — 更新 import 路径
- `meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java` — 更新 import 路径
- `meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml` — 修正 provide → provider
- 注释清理文件列表（见 Task 9）

### 删除文件
- `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java`
- `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java`

---

## Task 1: 迁移 VesselConfigLoader 到 meta-claw-core

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java`
- Delete: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java`
- Delete: `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java`

- [ ] **Step 1: Copy VesselConfigLoader 到 core 并修改包名**

  Copy `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java` to `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java`.

  Change package declaration:
  ```java
  package meta.claw.core.config;
  ```

  Verify the class still imports `meta.claw.core.model.VesselConfig` (no change needed for this import).

- [ ] **Step 2: Copy VesselConfigLoaderTest 到 core 并修改包名**

  Copy `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java` to `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java`.

  Change package declaration:
  ```java
  package meta.claw.core.config;
  ```

  Update any imports referencing `meta.claw.vessel.VesselConfigLoader` to `meta.claw.core.config.VesselConfigLoader`.

- [ ] **Step 3: Delete old files from vessel module**

  ```bash
  rm meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
  rm meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
  ```

- [ ] **Step 4: Verify core module compiles**

  Run: `mvn compile -pl meta-claw-core -am`
  Expected: SUCCESS (VesselConfigLoader now in core)

- [ ] **Step 5: Verify VesselConfigLoaderTest passes in core**

  Run: `mvn test -pl meta-claw-core -Dtest=VesselConfigLoaderTest`
  Expected: All tests pass

- [ ] **Step 6: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java
  git add meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java
  git rm meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
  git rm meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
  git commit -m "refactor(vessel): migrate VesselConfigLoader from vessel to core module"
  ```

---

## Task 2: VesselManagerTest（基于 config.yaml + vessel.md 新格式）

**Files:**
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`

- [ ] **Step 1: Write VesselManagerTest with failing tests**

  Create `meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java`:

  ```java
  package meta.claw.core.runtime;

  import meta.claw.core.model.VesselConfig;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  import java.io.IOException;
  import java.nio.file.Files;
  import java.nio.file.Path;

  import static org.junit.jupiter.api.Assertions.*;

  class VesselManagerTest {

      @TempDir
      Path tempDir;

      private Path createVesselDir(String vesselName, String configContent, String vesselMdContent) throws IOException {
          Path vesselDir = tempDir.resolve(vesselName);
          Files.createDirectories(vesselDir);
          Files.writeString(vesselDir.resolve("config.yaml"), configContent);
          if (vesselMdContent != null) {
              Files.writeString(vesselDir.resolve("vessel.md"), vesselMdContent);
          }
          return vesselDir;
      }

      @Test
      void loadVessels_shouldLoadFromConfigYamlAndVesselMd() throws IOException {
          String configYaml = """
              id: test-vessel
              name: Test Vessel
              description: A test vessel
              emoji: 🤖
              model: gpt-4
              system_prompt: You are a test assistant
              preferences_enabled: true
              role: member
              auto_serve: false
              exclude_tools: []
              """;
          String vesselMd = """
              # Test Vessel

              ## Identity

              Test identity content

              ## Soul

              Test soul content

              ## Capabilities

              Test capabilities content
              """;
          createVesselDir("test-vessel", configYaml, vesselMd);

          VesselManager manager = new VesselManager(tempDir.toString());
          manager.loadVessels();

          assertTrue(manager.hasVessel("test-vessel"));
          VesselConfig config = manager.getConfig("test-vessel");
          assertNotNull(config);
          assertEquals("test-vessel", config.getId());
          assertEquals("Test Vessel", config.getName());
          assertEquals("You are a test assistant", config.getSystemPrompt());
          assertEquals("member", config.getRole());
          assertTrue(config.isPreferencesEnabled());
          assertEquals("Test identity content", config.getIdentity());
          assertEquals("Test soul content", config.getSoul());
          assertEquals("Test capabilities content", config.getCapabilities());
      }

      @Test
      void loadVessels_shouldHandleMissingDirectory() {
          VesselManager manager = new VesselManager("/nonexistent/path");
          assertDoesNotThrow(manager::loadVessels);
          assertTrue(manager.listAvailableVessels().isEmpty());
      }

      @Test
      void loadVessels_shouldSkipConfigWithoutId() throws IOException {
          String configYaml = """
              name: No-Id Vessel
              description: Missing id field
              """;
          createVesselDir("no-id-vessel", configYaml, null);

          VesselManager manager = new VesselManager(tempDir.toString());
          manager.loadVessels();

          assertFalse(manager.hasVessel("no-id-vessel"));
          assertTrue(manager.listAvailableVessels().isEmpty());
      }

      @Test
      void runtimeRegistration_shouldWork() throws IOException {
          String configYaml = """
              id: runtime-test
              name: Runtime Test
              """;
          createVesselDir("runtime-test", configYaml, null);

          VesselManager manager = new VesselManager(tempDir.toString());
          manager.loadVessels();

          assertEquals(1, manager.listAvailableVessels().size());
          assertNotNull(manager.getConfig("runtime-test"));

          // Register a mock runtime
          VesselRuntime mockRuntime = new VesselRuntime(manager.getConfig("runtime-test"), null);
          manager.registerRuntime("runtime-test", mockRuntime);

          assertNotNull(manager.getRuntime("runtime-test"));
          assertEquals(mockRuntime, manager.getRuntime("runtime-test"));
      }
  }
  ```

- [ ] **Step 2: Run VesselManagerTest to verify it fails**

  Run: `mvn test -pl meta-claw-core -Dtest=VesselManagerTest`
  Expected: FAIL (current VesselManager reads vessel.md frontmatter, not config.yaml)

- [ ] **Step 3: Commit the failing test**

  ```bash
  git add meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java
  git commit -m "test(vessel): add VesselManagerTest for config.yaml + vessel.md format (RED)"
  ```

---

## Task 3: 重构 VesselManager 以复用 VesselConfigLoader

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java`

- [ ] **Step 1: Refactor VesselManager.loadVessels()**

  Open `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java`.

  Replace the entire `loadVessels()` method with:
  ```java
  import meta.claw.core.config.VesselConfigLoader;
  import java.nio.file.Path;
  import java.util.List;

  public void loadVessels() {
      VesselConfigLoader loader = new VesselConfigLoader();
      List<VesselConfig> loaded = loader.loadFromDirectory(Path.of(vesselsDir));
      for (VesselConfig config : loaded) {
          if (config.getId() != null && !config.getId().isEmpty()) {
              vessels.put(config.getId(), config);
              log.info("成功加载 Vessel 配置: {} ({})", config.getId(), config.getName());
          }
      }
  }
  ```

- [ ] **Step 2: Delete redundant parsing methods**

  Delete these methods from VesselManager:
  - `mapToVesselConfig(Map<String, Object>)`
  - `getString(Map, String)`
  - `getBoolean(Map, String)`
  - `getStringList(Map, String)`

  Also remove unused imports:
  - `org.yaml.snakeyaml.Yaml`
  - `java.io.File`
  - `java.io.FileInputStream`
  - `java.io.InputStream`
  - `java.util.Map`

- [ ] **Step 3: Run VesselManagerTest to verify it passes**

  Run: `mvn test -pl meta-claw-core -Dtest=VesselManagerTest`
  Expected: All 4 tests pass

- [ ] **Step 4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java
  git commit -m "refactor(vessel): VesselManager uses VesselConfigLoader, remove redundant parsing"
  ```

---

## Task 4: 更新引用方 import 路径

**Files:**
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java`

- [ ] **Step 1: Update VesselConfigResolver import**

  Open `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java`.

  Change:
  ```java
  // Remove this line:
  // import meta.claw.vessel.VesselConfigLoader;

  // Add this line:
  import meta.claw.core.config.VesselConfigLoader;
  ```

- [ ] **Step 2: Update ListCommand import**

  Open `meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java`.

  Change:
  ```java
  // Remove this line:
  // import meta.claw.vessel.VesselConfigLoader;

  // Add this line:
  import meta.claw.core.config.VesselConfigLoader;
  ```

- [ ] **Step 3: Verify vessel and cli modules compile**

  Run: `mvn compile -pl meta-claw-vessel,meta-claw-cli -am`
  Expected: SUCCESS

- [ ] **Step 4: Run existing tests for vessel module**

  Run: `mvn test -pl meta-claw-vessel`
  Expected: All tests pass (VesselConfigResolverTest, VesselProfileLoaderTest)

- [ ] **Step 5: Commit**

  ```bash
  git add meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java
  git add meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java
  git commit -m "refactor(vessel): update imports for VesselConfigLoader migration"
  ```

---

## Task 5: JsonlConversationStore 单元测试

**Files:**
- Create: `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java`

- [ ] **Step 1: Write JsonlConversationStoreTest**

  Create `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java`:

  ```java
  package meta.claw.store.conversation;

  import meta.claw.core.session.ChatMessage;
  import meta.claw.core.session.ConversationInfo;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  import java.io.IOException;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.concurrent.CountDownLatch;
  import java.util.concurrent.ExecutorService;
  import java.util.concurrent.Executors;

  import static org.junit.jupiter.api.Assertions.*;

  class JsonlConversationStoreTest {

      @TempDir
      Path tempDir;

      private JsonlConversationStore createStore() {
          return new JsonlConversationStore(tempDir);
      }

      private ChatMessage msg(String role, String content) {
          return ChatMessage.builder()
                  .sessionKey("test-session")
                  .role(role)
                  .content(content)
                  .timestamp(LocalDateTime.now())
                  .build();
      }

      @Test
      void appendMessage_andGetHistory_roundTrip() {
          JsonlConversationStore store = createStore();
          store.appendMessage("test-session", msg("user", "Hello"));
          store.appendMessage("test-session", msg("assistant", "Hi there"));
          store.appendMessage("test-session", msg("user", "How are you?"));

          List<ChatMessage> history = store.getHistory("test-session");
          assertEquals(3, history.size());
          assertEquals("Hello", history.get(0).getContent());
          assertEquals("Hi there", history.get(1).getContent());
          assertEquals("How are you?", history.get(2).getContent());
      }

      @Test
      void getHistory_withLimit() {
          JsonlConversationStore store = createStore();
          for (int i = 0; i < 10; i++) {
              store.appendMessage("test-session", msg("user", "Message " + i));
          }

          List<ChatMessage> history = store.getHistory("test-session", 5);
          assertEquals(5, history.size());
          assertEquals("Message 5", history.get(0).getContent());
          assertEquals("Message 9", history.get(4).getContent());
      }

      @Test
      void getHistory_unlimited_shouldReturnAll() {
          JsonlConversationStore store = createStore();
          store.appendMessage("test-session", msg("user", "Only one"));

          List<ChatMessage> history = store.getHistory("test-session", 0);
          assertEquals(1, history.size());
      }

      @Test
      void clearHistory_shouldTruncate() {
          JsonlConversationStore store = createStore();
          store.appendMessage("test-session", msg("user", "Hello"));
          assertTrue(store.clearHistory("test-session"));

          List<ChatMessage> history = store.getHistory("test-session");
          assertTrue(history.isEmpty());
      }

      @Test
      void deleteConversation_shouldRemoveDir() {
          JsonlConversationStore store = createStore();
          store.appendMessage("test-session", msg("user", "Hello"));
          assertTrue(store.deleteConversation("test-session"));

          assertFalse(store.conversationExists("test-session"));
      }

      @Test
      void conversationExists_shouldReturnCorrectly() {
          JsonlConversationStore store = createStore();
          assertFalse(store.conversationExists("nonexistent"));
          store.appendMessage("exists", msg("user", "Hello"));
          assertTrue(store.conversationExists("exists"));
      }

      @Test
      void concurrentAppend_shouldNotCorrupt() throws InterruptedException {
          JsonlConversationStore store = createStore();
          int threadCount = 10;
          int messagesPerThread = 10;
          ExecutorService executor = Executors.newFixedThreadPool(threadCount);
          CountDownLatch latch = new CountDownLatch(threadCount);

          for (int t = 0; t < threadCount; t++) {
              final int threadId = t;
              executor.submit(() -> {
                  try {
                      for (int m = 0; m < messagesPerThread; m++) {
                          store.appendMessage("concurrent", msg("user", "T" + threadId + "M" + m));
                      }
                  } finally {
                      latch.countDown();
                  }
              });
          }

          latch.await();
          executor.shutdown();

          List<ChatMessage> history = store.getHistory("concurrent");
          assertEquals(threadCount * messagesPerThread, history.size());
      }

      @Test
      void listConversations_shouldReturnSortedByUpdatedAt() {
          JsonlConversationStore store = createStore();
          store.appendMessage("session-a", msg("user", "First"));
          store.appendMessage("session-b", msg("user", "Second"));
          store.appendMessage("session-a", msg("user", "Third"));

          List<ConversationInfo> conversations = store.listConversations();
          assertEquals(2, conversations.size());
          // session-a should be first (most recently updated)
          assertEquals("session-a", conversations.get(0).getSessionId());
          assertEquals("session-b", conversations.get(1).getSessionId());
          assertEquals(2, conversations.get(0).getMessageCount());
          assertEquals(1, conversations.get(1).getMessageCount());
      }
  }
  ```

- [ ] **Step 2: Run JsonlConversationStoreTest**

  Run: `mvn test -pl meta-claw-store -Dtest=JsonlConversationStoreTest`
  Expected: All 8 tests pass

- [ ] **Step 3: Commit**

  ```bash
  git add meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java
  git commit -m "test(store): add JsonlConversationStoreTest"
  ```


---

## Task 6: FilePreferenceStore 单元测试

**Files:**
- Create: `meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java`

- [ ] **Step 1: Write FilePreferenceStoreTest**

  Create `meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java`:

  ```java
  package meta.claw.store.preferences;

  import meta.claw.core.session.PreferenceEntry;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  import java.nio.file.Path;
  import java.time.LocalDateTime;
  import java.util.List;
  import java.util.Map;

  import static org.junit.jupiter.api.Assertions.*;

  class FilePreferenceStoreTest {

      @TempDir
      Path tempDir;

      private FilePreferenceStore createStore() {
          return new FilePreferenceStore(tempDir);
      }

      private PreferenceEntry entry(String id, String content, String category) {
          return PreferenceEntry.builder()
                  .id(id)
                  .content(content)
                  .category(category)
                  .timestamp(LocalDateTime.now())
                  .build();
      }

      private PreferenceEntry entryWithMetadata(String id, String content, Map<String, Object> metadata) {
          return PreferenceEntry.builder()
                  .id(id)
                  .content(content)
                  .category("test")
                  .timestamp(LocalDateTime.now())
                  .metadata(metadata)
                  .build();
      }

      @Test
      void addAndLookupPreference_shouldMatch() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "I like Java", "language"));
          store.addPreference("vessel-1", entry("p2", "I prefer Python", "language"));

          List<PreferenceEntry> results = store.lookupPreference("vessel-1", "java");
          assertEquals(1, results.size());
          assertEquals("I like Java", results.get(0).getContent());
      }

      @Test
      void lookupPreference_noMatch_shouldReturnEmpty() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "Hello", "greeting"));

          List<PreferenceEntry> results = store.lookupPreference("vessel-1", "nonexistent");
          assertTrue(results.isEmpty());
      }

      @Test
      void listRecentPreferences_withLimit() {
          FilePreferenceStore store = createStore();
          for (int i = 0; i < 10; i++) {
              store.addPreference("vessel-1", entry("p" + i, "Content " + i, "test"));
          }

          List<PreferenceEntry> results = store.listRecentPreferences("vessel-1", 5);
          assertEquals(5, results.size());
          assertEquals("Content 5", results.get(0).getContent());
          assertEquals("Content 9", results.get(4).getContent());
      }

      @Test
      void listRecentPreferences_unlimited_shouldReturnAll() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "One", "test"));

          List<PreferenceEntry> results = store.listRecentPreferences("vessel-1", 0);
          assertEquals(1, results.size());
      }

      @Test
      void deletePreference_shouldRemove() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "To be deleted", "test"));
          store.addPreference("vessel-1", entry("p2", "Keep this", "test"));

          assertTrue(store.deletePreference("vessel-1", "p1"));

          List<PreferenceEntry> results = store.listRecentPreferences("vessel-1", 0);
          assertEquals(1, results.size());
          assertEquals("Keep this", results.get(0).getContent());
      }

      @Test
      void clearPreferences_shouldTruncate() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "Hello", "test"));
          assertTrue(store.clearPreferences("vessel-1"));

          List<PreferenceEntry> results = store.listRecentPreferences("vessel-1", 0);
          assertTrue(results.isEmpty());
      }

      @Test
      void addPreference_withMetadata_shouldPreserve() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entryWithMetadata("p1", "With metadata",
                  Map.of("key1", "value1", "key2", 42)));

          List<PreferenceEntry> results = store.lookupPreference("vessel-1", "value1");
          assertEquals(1, results.size());
          assertNotNull(results.get(0).getMetadata());
          assertEquals("value1", results.get(0).getMetadata().get("key1"));
          assertEquals(42, results.get(0).getMetadata().get("key2"));
      }

      @Test
      void lookupPreference_byCategory_shouldMatch() {
          FilePreferenceStore store = createStore();
          store.addPreference("vessel-1", entry("p1", "Content", "favorite-color"));

          List<PreferenceEntry> results = store.lookupPreference("vessel-1", "favorite-color");
          assertEquals(1, results.size());
      }
  }
  ```

- [ ] **Step 2: Run FilePreferenceStoreTest**

  Run: `mvn test -pl meta-claw-store -Dtest=FilePreferenceStoreTest`
  Expected: All 8 tests pass

- [ ] **Step 3: Commit**

  ```bash
  git add meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java
  git commit -m "test(store): add FilePreferenceStoreTest"
  ```

---

## Task 7: ChatCommand 集成 JsonlConversationStore

**Files:**
- Modify: `meta-claw-cli/pom.xml`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: Add meta-claw-store dependency to CLI pom.xml**

  Open `meta-claw-cli/pom.xml`. Add inside `<dependencies>`:

  ```xml
  <dependency>
      <groupId>com.meta</groupId>
      <artifactId>meta-claw-store</artifactId>
  </dependency>
  ```

- [ ] **Step 2: Verify CLI module compiles with new dependency**

  Run: `mvn compile -pl meta-claw-cli -am`
  Expected: SUCCESS

- [ ] **Step 3: Modify ChatCommand to integrate JsonlConversationStore**

  Open `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`.

  Add imports:
  ```java
  import meta.claw.core.session.ChatMessage;
  import meta.claw.store.conversation.JsonlConversationStore;
  import java.time.LocalDateTime;
  import java.util.UUID;
  ```

  Add field:
  ```java
  private JsonlConversationStore conversationStore;
  private String sessionKey;
  ```

  In the `run()` method, after resolving `vesselConfig`, add store initialization:
  ```java
  // Initialize conversation store and session
  Path vesselsDir = configDir.resolve("vessels");
  this.conversationStore = new JsonlConversationStore(vesselsDir);
  this.sessionKey = UUID.randomUUID().toString();
  ```

  Replace the history initialization block (currently just system prompt):
  ```java
  // Before:
  // List<SpiMessage> history = new ArrayList<>();
  // String systemPrompt = vesselConfig.getSystemPrompt();
  // if (systemPrompt != null && !systemPrompt.isBlank()) {
  //     history.add(SpiMessage.system(systemPrompt));
  // }

  // After:
  List<SpiMessage> history = new ArrayList<>();
  String systemPrompt = vesselConfig.getSystemPrompt();
  if (systemPrompt != null && !systemPrompt.isBlank()) {
      history.add(SpiMessage.system(systemPrompt));
  }
  ```

  Note: For Phase 1, we intentionally do NOT load persisted history on CLI startup (each chat session is independent). The store is only used for persistence during the session.

  Modify the `/clear` handler to also clear persisted history:
  ```java
  if ("/clear".equalsIgnoreCase(input.trim())) {
      history.clear();
      if (systemPrompt != null && !systemPrompt.isBlank()) {
          history.add(SpiMessage.system(systemPrompt));
      }
      try {
          conversationStore.clearHistory(sessionKey);
      } catch (Exception e) {
          log.warn("Failed to clear persisted history for session {}", sessionKey, e);
      }
      terminal.writer().println("History cleared.");
      terminal.flush();
      continue;
  }
  ```

  Modify the user input handling to persist user message before sending to LLM:
  ```java
  history.add(SpiMessage.user(input));

  // Persist user message
  try {
      ChatMessage userMsg = ChatMessage.builder()
              .sessionKey(sessionKey)
              .role("user")
              .content(input)
              .vesselName(vesselName)
              .timestamp(LocalDateTime.now())
              .build();
      conversationStore.appendMessage(sessionKey, userMsg);
  } catch (Exception e) {
      log.error("Failed to persist user message", e);
  }

  SpiChatRequest request = SpiChatRequest.builder().messages(history).build();
  ```

  Modify the `onComplete` callback to persist assistant message:
  ```java
  @Override
  public void onComplete(SpiChatResponse response) {
      terminal.writer().println();
      terminal.writer().flush();
      String responseText = responseBuffer.toString();
      history.add(SpiMessage.assistant(responseText));

      // Persist assistant message
      try {
          ChatMessage assistantMsg = ChatMessage.builder()
                  .sessionKey(sessionKey)
                  .role("assistant")
                  .content(responseText)
                  .vesselName(vesselName)
                  .timestamp(LocalDateTime.now())
                  .build();
          conversationStore.appendMessage(sessionKey, assistantMsg);
      } catch (Exception e) {
          log.error("Failed to persist assistant message", e);
      }
  }
  ```

- [ ] **Step 4: Verify CLI module compiles after changes**

  Run: `mvn compile -pl meta-claw-cli -am`
  Expected: SUCCESS

- [ ] **Step 5: Commit**

  ```bash
  git add meta-claw-cli/pom.xml
  git add meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
  git commit -m "feat(cli): integrate JsonlConversationStore into ChatCommand for history persistence"
  ```

---

## Task 8: 全项目注释清理

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/UserSession.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/ChatMode.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/SessionManager.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/ChatMessage.java`
- Modify: `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: Replace "专家" with "数字员工" / "Vessel" in all comments**

  Execute the following replacements. Only modify comments/Javadoc, never change code identifiers.

  **UserSession.java:**
  ```java
  // "封装了用户在系统中的完整会话状态，包括会话标识、用户身份、聊天模式、关联专家等信息"
  // → "封装了用户在系统中的完整会话状态，包括会话标识、用户身份、聊天模式、关联 Vessel 等信息"

  // "目标专家标识，单聊模式下有效"
  // → "目标 Vessel 标识，单聊模式下有效"

  // "同时将单聊目标专家字段清空"
  // → "同时将单聊目标 Vessel 字段清空"
  ```

  **ChatMode.java:**
  ```java
  // "单聊模式：用户与单个专家代理一对一对话"
  // → "单聊模式：用户与单个数字员工一对一对话"

  // "群聊模式：用户在群组会话中与多个专家代理互动"
  // → "群聊模式：用户在群组会话中与多个数字员工互动"
  ```

  **SessionManager.java:**
  ```java
  // "负责用户会话的生命周期管理，包括会话的获取、创建、模式切换、目标专家解析以及过期清理等核心逻辑"
  // → "负责用户会话的生命周期管理，包括会话的获取、创建、模式切换、目标 Vessel 解析以及过期清理等核心逻辑"

  // "设置会话为单聊模式，并绑定目标专家"
  // → "设置会话为单聊模式，并绑定目标 Vessel"

  // "@param vesselName 目标专家名称"
  // → "@param vesselName 目标 Vessel 名称"

  // "根据当前会话模式获取目标专家列表"
  // → "根据当前会话模式获取目标 Vessel 列表"

  // "<li>单聊模式：返回已绑定的专家（若未绑定则回退返回可用专家列表中的第一个）</li>"
  // → "<li>单聊模式：返回已绑定的 Vessel（若未绑定则回退返回可用 Vessel 列表中的第一个）</li>"

  // "<li>群聊模式：返回所有可用专家</li>"
  // → "<li>群聊模式：返回所有可用 Vessel</li>"

  // "@param availableVessels 可用专家列表"
  // → "@param availableVessels 可用 Vessel 列表"

  // "@return 目标专家列表，不会返回 null"
  // → "@return 目标 Vessel 列表，不会返回 null"

  // "// 已绑定专家，返回该专家"
  // → "// 已绑定 Vessel，返回该 Vessel"

  // "// 未绑定专家，回退返回第一个可用专家"
  // → "// 未绑定 Vessel，回退返回第一个可用 Vessel"
  ```

  **ChatMessage.java:**
  ```java
  // "关联的专家/代理名称"
  // → "关联的数字员工/Vessel 名称"
  ```

  **AppConfig.java:**
  ```java
  // "负责用户会话的生命周期管理，包括获取/创建会话、模式切换、目标专家解析及过期清理。"
  // → "负责用户会话的生命周期管理，包括获取/创建会话、模式切换、目标 Vessel 解析及过期清理。"
  ```

  **ChatCommand.java:**
  ```java
  // "Welcome screen (inspired by expert_cli/cli.py print_welcome)"
  // → "Welcome screen (inspired by vessel_cli/cli.py print_welcome)"
  ```

- [ ] **Step 2: Verify no "专家" or "Expert" remains in Java comments**

  Run: `grep -r "专家\|Expert" --include="*.java" meta-claw-core/src/main/java/ meta-claw-bootstrap/src/main/java/ meta-claw-cli/src/main/java/ meta-claw-gateway/src/main/java/`
  Expected: Empty output (no matches)

- [ ] **Step 3: Verify compilation still succeeds**

  Run: `mvn compile`
  Expected: SUCCESS

- [ ] **Step 4: Commit**

  ```bash
  git add -A
  git commit -m "docs: replace all 专家/Expert references with 数字员工/Vessel in comments"
  ```

---

## Task 9: 模板拼写修正

**Files:**
- Modify: `meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`

- [ ] **Step 1: Fix template typo**

  Open `meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`.

  Change line 21:
  ```yaml
  # Before:
  provide: moonshot

  # After:
  provider: moonshot
  ```

- [ ] **Step 2: Verify fix by creating a test vessel**

  Run the CLI create command to verify the generated config has the correct field:
  ```bash
  # Build CLI first if needed
  mvn package -pl meta-claw-cli -am -DskipTests
  java -jar meta-claw-cli/target/meta-claw-cli-0.1.0-SNAPSHOT.jar create test-spellcheck
  ```

  Check generated file:
  ```bash
  grep "provider:" ~/.meta-claw/vessels/test-spellcheck/config.yaml
  ```
  Expected: `provider: moonshot`

  Also verify `provide:` does NOT exist:
  ```bash
  grep "provide:" ~/.meta-claw/vessels/test-spellcheck/config.yaml || echo "No typo found - good"
  ```
  Expected: "No typo found - good"

- [ ] **Step 3: Commit**

  ```bash
  git add meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml
  git commit -m "fix(template): correct 'provide' to 'provider' in vessel-config.tmpl.yaml"
  ```

---

## Task 10: 全量验证与最终提交

- [ ] **Step 1: Run full test suite**

  Run: `mvn clean test`
  Expected: ALL TESTS PASS across all modules

  Modules to verify:
  - `meta-claw-core` (VesselManagerTest, VesselConfigLoaderTest, existing tests)
  - `meta-claw-vessel` (VesselConfigResolverTest, VesselProfileLoaderTest)
  - `meta-claw-store` (JsonlConversationStoreTest, FilePreferenceStoreTest)
  - `meta-claw-cli` (ConfigCommandTest)
  - `meta-claw-bootstrap` (MessageFlowIntegrationTest)

- [ ] **Step 2: Verify Bootstrap can load vessels**

  Run: `mvn spring-boot:run -pl meta-claw-bootstrap -DskipTests`
  Check logs for: "成功加载 Vessel 配置: default (Default)"
  Stop with Ctrl+C after verifying.

- [ ] **Step 3: Manual integration test - ChatCommand persistence**

  ```bash
  # Run chat command
  java -jar meta-claw-cli/target/meta-claw-cli-0.1.0-SNAPSHOT.jar chat default
  ```

  Type a message, wait for response, then type `/exit`.

  Verify history file exists:
  ```bash
  ls ~/.meta-claw/vessels/default/conversations/
  # Should show a UUID directory

  cat ~/.meta-claw/vessels/default/conversations/*/history.jsonl
  # Should show 2 JSON lines (user + assistant)
  ```

- [ ] **Step 4: Final review of all commits**

  Run: `git log --oneline -15`
  Verify commit history is clean and each commit has a clear purpose.

- [ ] **Step 5: Tag completion**

  ```bash
  git tag -a phase1-cleanup-complete -m "Phase 1 cleanup complete: VesselManager refactor, Store integration, comment cleanup"
  ```

---

## Self-Review Checklist

Before execution, verify:

- [ ] **Spec coverage**: Every item in `2026-05-09-meta-claw-phase1-cleanup-design.md` has a corresponding task
  - VesselConfigLoader 迁移 → Task 1 ✅
  - VesselManagerTest → Task 2 ✅
  - VesselManager 重构 → Task 3 ✅
  - import 更新 → Task 4 ✅
  - JsonlConversationStoreTest → Task 5 ✅
  - FilePreferenceStoreTest → Task 6 ✅
  - ChatCommand 集成 → Task 7 ✅
  - 注释清理 → Task 8 ✅
  - 模板修正 → Task 9 ✅
  - 全量验证 → Task 10 ✅

- [ ] **No placeholders**: No TBD, TODO, "implement later", or vague steps in this plan

- [ ] **Type consistency**: All method signatures and property names match between tasks and existing codebase

- [ ] **File paths exact**: All file paths are correct relative to project root
