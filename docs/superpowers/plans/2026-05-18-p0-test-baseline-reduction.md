# 初始化阶段 P0 测试基线收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将初始化阶段标准验证收敛到少量 P0 测试，删除当前阶段不再维护的旁支测试，并同步长期状态文件。

**Architecture:** 保留现有业务模块结构，只收缩测试面与标准验证入口。`./init.sh` 继续作为统一入口，但改为先编译全仓库，再只运行明确列出的 P0 测试集合；第三方模块继续编译，不再进入当前阶段测试基线。

**Tech Stack:** Java 21, Maven, JUnit 5, Bash

---

## 文件结构

**修改：**
- `init.sh`：把标准验证从 `mvn clean test` 改为“全仓编译 + P0 测试集合”
- `claude-progress.md`：记录新的验证口径与本轮状态
- `feature_list.json`：新增或更新与测试基线相关的功能状态
- `evaluator-rubric.md`：把当前阶段标准验证从“全量测试”改写为“P0 测试”

**删除：**
- `meta-claw-cli/src/test/java/meta/claw/cli/ConfigCommandTest.java`
- `meta-claw-cli/src/test/java/meta/claw/cli/SessionsCommandTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/PromptContextFactoryTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/prompt/TemplateLoaderTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ShortMemoryManagerTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/memory/longterm/LongMemoryManagerTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ConversationHistoryManagerTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientIntegrationTest.java`
- `meta-claw-core/src/test/java/meta/claw/core/spi/llm/MessageTest.java`
- `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/ILinkClientTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/exception/APIErrorTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/exception/HTTPErrorTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/exception/NoContextTokenExceptionTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/model/ModelSerializationTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/util/MessageHelperTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/util/URLHelperTest.java`
- `third-party/openilink-sdk-java/src/test/java/com/openilink/util/WechatHelperTest.java`

## Task 1: 固定 P0 测试入口

**Files:**
- Modify: `init.sh`

- [ ] **Step 1: 写入新的验证命令**

把 `init.sh` 中：

```bash
VERIFY_CMD=(mvn clean test)
```

改为：

```bash
COMPILE_CMD=(mvn clean compile)
VERIFY_CMD=(
  mvn test
  -pl meta-claw-core,meta-claw-store,meta-claw-cli,meta-claw-bootstrap
  -am
  -Dtest=VesselConfigLoaderTest,VesselManagerTest,SystemPromptBuilderTest,JsonlShortMemoryStoreTest,FileLongMemoryStoreTest,ChatCommandTest,MessageFlowIntegrationTest
  -Dsurefire.failIfNoSpecifiedTests=false
)
```

并把脚本主体改为先执行：

```bash
echo "==> 编译全仓库"
"${COMPILE_CMD[@]}"

echo "==> 运行 P0 验证"
"${VERIFY_CMD[@]}"
```

- [ ] **Step 2: 运行脚本确认失败边界或成功**

Run:

```bash
./init.sh
```

Expected:

- 在受限沙箱中，如果仍因环境问题失败，失败位置应落在命令执行环境而不是语法错误
- 在真实环境中，脚本应只显示新的编译阶段与 P0 验证阶段

- [ ] **Step 3: 提交**

```bash
git add init.sh
git commit -m "test: narrow init verification to p0 coverage"
```

## Task 2: 删除非 P0 测试

**Files:**
- Delete: 设计中列出的 19 个测试文件

- [ ] **Step 1: 删除旁支测试文件**

删除：

```text
meta-claw-cli/src/test/java/meta/claw/cli/ConfigCommandTest.java
meta-claw-cli/src/test/java/meta/claw/cli/SessionsCommandTest.java
meta-claw-core/src/test/java/meta/claw/core/prompt/PromptContextFactoryTest.java
meta-claw-core/src/test/java/meta/claw/core/prompt/TemplateLoaderTest.java
meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ShortMemoryManagerTest.java
meta-claw-core/src/test/java/meta/claw/core/memory/longterm/LongMemoryManagerTest.java
meta-claw-core/src/test/java/meta/claw/core/memory/shortterm/ConversationHistoryManagerTest.java
meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientTest.java
meta-claw-core/src/test/java/meta/claw/core/runtime/SpringAiLlmClientIntegrationTest.java
meta-claw-core/src/test/java/meta/claw/core/spi/llm/MessageTest.java
meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/ILinkClientTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/exception/APIErrorTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/exception/HTTPErrorTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/exception/NoContextTokenExceptionTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/model/ModelSerializationTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/util/MessageHelperTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/util/URLHelperTest.java
third-party/openilink-sdk-java/src/test/java/com/openilink/util/WechatHelperTest.java
```

- [ ] **Step 2: 确认仓库只剩 P0 测试**

Run:

```bash
find . -path '*/src/test/java/*Test.java' | sort
```

Expected:

```text
./meta-claw-bootstrap/src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java
./meta-claw-cli/src/test/java/meta/claw/cli/ChatCommandTest.java
./meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java
./meta-claw-core/src/test/java/meta/claw/core/prompt/SystemPromptBuilderTest.java
./meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java
./meta-claw-store/src/test/java/meta/claw/store/memory/longterm/FileLongMemoryStoreTest.java
./meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java
```

- [ ] **Step 3: 运行 P0 验证**

Run:

```bash
./init.sh
```

Expected:

- 编译全仓库成功
- 只运行 7 个 P0 测试类

- [ ] **Step 4: 提交**

```bash
git add meta-claw-cli/src/test meta-claw-core/src/test meta-claw-vessel/src/test third-party/openilink-sdk-java/src/test
git commit -m "test: remove non-p0 initialization tests"
```

## Task 3: 同步长期状态文件

**Files:**
- Modify: `claude-progress.md`
- Modify: `feature_list.json`
- Modify: `evaluator-rubric.md`

- [ ] **Step 1: 更新 `claude-progress.md`**

新增一条本轮 session，明确记录：

```markdown
- 当前标准验证路径：`./init.sh`
- 当前标准验证语义：先编译全仓库，再执行初始化阶段 P0 测试集合
- 第三方 `openilink-sdk-java` 当前只参与编译，不参与初始化阶段测试基线
```

并记录本轮实际运行过的验证结果。

- [ ] **Step 2: 更新 `feature_list.json`**

新增或更新一个功能项，例如：

```json
{
  "id": "test-baseline-001",
  "priority": 9,
  "area": "repository-hygiene",
  "title": "收敛初始化阶段 P0 测试基线",
  "user_visible_behavior": "开发初始化阶段只维护能够保护主链路的少量 P0 测试，标准验证更短、更聚焦。",
  "status": "passing",
  "verification": [
    "确认仓库只保留设计定义的 7 个 P0 测试类。",
    "运行 ./init.sh。",
    "确认第三方 openilink-sdk-java 仅参与编译，不参与初始化阶段测试基线。"
  ],
  "evidence": [
    {
      "date": "2026-05-18",
      "result": "passing",
      "detail": "已删除非 P0 测试；./init.sh 先编译全仓库，再只运行 7 个 P0 测试类。"
    }
  ],
  "notes": "这是开发初始化阶段的主动收敛策略；后续能力进入主链后再补对应测试。"
}
```

- [ ] **Step 3: 更新 `evaluator-rubric.md`**

把涉及“标准入口”“标准验证”“测试证据”的表述同步为：

- 当前阶段看 `./init.sh` 是否通过 P0 测试集
- 不再把“全量测试”作为当前阶段默认承诺

- [ ] **Step 4: 运行验证**

Run:

```bash
./init.sh
```

Expected:

- 文档更新后仍然通过 P0 验证

- [ ] **Step 5: 提交**

```bash
git add claude-progress.md feature_list.json evaluator-rubric.md
git commit -m "docs: record p0 verification baseline"
```

## Task 4: 最终复核

**Files:**
- Verify only

- [ ] **Step 1: 检查测试文件清单**

Run:

```bash
find . -path '*/src/test/java/*Test.java' | sort
```

Expected: 只剩 7 个 P0 测试类。

- [ ] **Step 2: 运行最终标准入口**

Run:

```bash
./init.sh
```

Expected:

- 全仓编译成功
- 7 个 P0 测试类通过

- [ ] **Step 3: 检查工作树**

Run:

```bash
git status --short
```

Expected:

- 除非用户本来已有未提交改动，否则只应看到本轮明确改动

## 自审

- 该计划覆盖了 spec 中的所有要求：P0 定义、删测清单、`./init.sh` 收敛、长期状态同步
- 未包含占位步骤
- 文件路径、测试类名、提交信息在全文中保持一致

