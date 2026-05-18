# Memory Entry 统一重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用统一的 `MemoryEntry` 替代短期与长期分裂实体，删除 `ConversationHistoryManager` 与 `UserPreferenceStore`，并把短期历史策略下沉到 store 契约。

**Architecture:** 保留 `ShortMemoryStore` / `LongMemoryStore` 两个领域接口，但两边统一使用 `MemoryEntry`。短期窗口读取能力由 `ShortMemoryStore` 直接承载，`ShortMemoryManager` 仅保留 backend 选择与委托；长期调用方直接依赖 `LongMemoryStore`。

**Tech Stack:** Java 21, Maven, Lombok, Jackson

---

## 文件结构

**新增：**
- `meta-claw-core/src/main/java/meta/claw/core/memory/MemoryEntry.java`

**修改：**
- `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStore.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryManager.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryStore.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryManager.java`
- `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
- `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
- `meta-claw-store/src/main/java/meta/claw/store/memory/longterm/FileLongMemoryStore.java`
- `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
- `meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java`
- `meta-claw-store/src/test/java/meta/claw/store/memory/longterm/FileLongMemoryStoreTest.java`

**删除：**
- `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/SessionSummary.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ConversationHistoryManager.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/PreferenceEntry.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/UserPreferenceStore.java`

## Task 1: 统一记忆实体

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/memory/MemoryEntry.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryStore.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryManager.java`
- Modify: `meta-claw-store/src/main/java/meta/claw/store/memory/longterm/FileLongMemoryStore.java`
- Modify: `meta-claw-store/src/test/java/meta/claw/store/memory/longterm/FileLongMemoryStoreTest.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/PreferenceEntry.java`

- [ ] **Step 1: 新建 `MemoryEntry`**
- [ ] **Step 2: 将长期记忆接口和实现迁移到 `MemoryEntry`**
- [ ] **Step 3: 更新 `FileLongMemoryStoreTest`**
- [ ] **Step 4: 运行 `FileLongMemoryStoreTest`**

## Task 2: 下沉短期历史能力

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryStore.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryManager.java`
- Modify: `meta-claw-store/src/main/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStore.java`
- Modify: `meta-claw-store/src/test/java/meta/claw/store/memory/shortterm/JsonlShortMemoryStoreTest.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/SessionSummary.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ConversationHistoryManager.java`

- [ ] **Step 1: `ShortMemoryStore` 改用 `MemoryEntry` 并加入窗口能力**
- [ ] **Step 2: `JsonlShortMemoryStore` 实现窗口能力**
- [ ] **Step 3: `ShortMemoryManager` 改为直接委托 store**
- [ ] **Step 4: 更新 `JsonlShortMemoryStoreTest`**
- [ ] **Step 5: 运行 `JsonlShortMemoryStoreTest`**

## Task 3: 清理长期别名接口与调用方

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/prompt/PromptContextFactory.java`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/UserPreferenceStore.java`

- [ ] **Step 1: `PromptContextFactory` 直接依赖 `LongMemoryStore`**
- [ ] **Step 2: `SessionsCommand` 改用 `MemoryEntry`**
- [ ] **Step 3: 全仓搜索旧类型残留**

## Task 4: 验证与收尾

**Files:**
- Modify: `claude-progress.md`
- Modify: `feature_list.json`

- [ ] **Step 1: 运行定向验证**

```bash
mvn test -pl meta-claw-store -am -Dtest=JsonlShortMemoryStoreTest,FileLongMemoryStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 2: 运行标准入口**

```bash
./init.sh
```

- [ ] **Step 3: 更新长期状态文件并提交**

## 自审

- 计划覆盖已确认设计中的三个目标
- 计划只依赖当前保留的 P0 测试，不把已删除的旁支测试重新引回
- 文件名、实体名、调用方向一致
