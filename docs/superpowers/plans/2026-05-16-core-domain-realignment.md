# Core Domain Realignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the unused session domain, split the former model package into config/message domains, and add formal short-/long-term memory manager abstractions.

**Architecture:** Delete the detached session lifecycle branch entirely, move configuration classes beside their loaders, move message-flow classes into a dedicated message domain, and introduce `ShortMemoryManager` / `LongMemoryManager` interfaces without changing runtime behavior. `ConversationHistoryManager` becomes the concrete short-term implementation; long-term memory remains intentionally abstract at this stage.

**Tech Stack:** Java 21, Maven, JUnit 5, Spring Boot.

---

## File Map

### Create
- `meta-claw-core/src/main/java/meta/claw/core/memory/shortterm/ShortMemoryManager.java`
- `meta-claw-core/src/main/java/meta/claw/core/memory/longterm/LongMemoryManager.java`

### Move to `core.config`
- `GlobalConfig`
- `ProviderConfig`
- `VesselConfig`

### Move to `core.message`
- `Context`
- `ContextType`
- `Reply`
- `ReplyType`

### Delete
- `meta-claw-core/src/main/java/meta/claw/core/session/*`
- `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java`
- `meta-claw-core/src/main/java/meta/claw/core/model/SessionConfig.java`

### Modify
- `ConversationHistoryManager`
- imports across core/gateway/bootstrap/cli/vessel tests
- `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java`
- active docs and state artifacts

### Task 1: Add the memory abstractions
- [ ] Add `ShortMemoryManager` with the existing short-term methods.
- [ ] Add marker-style `LongMemoryManager`.
- [ ] Update `ConversationHistoryManager` to implement `ShortMemoryManager`.
- [ ] Extend/adjust `ConversationHistoryManagerTest` to assert the class satisfies the interface contract.
- [ ] Run `mvn test -pl meta-claw-core -Dtest=ConversationHistoryManagerTest`.

### Task 2: Move config models beside config loaders
- [ ] Move `GlobalConfig`, `ProviderConfig`, and `VesselConfig` from `core.model` into `core.config`.
- [ ] Update all imports in loaders, runtime, CLI, vessel tests, and bootstrap.
- [ ] Delete `SessionConfig`.
- [ ] Run focused config/runtime tests.

### Task 3: Move message-flow models into `core.message`
- [ ] Move `Context`, `ContextType`, `Reply`, and `ReplyType` into `core.message`.
- [ ] Update imports across `events`, `runtime`, `gateway`, `gateway-weixin`, and bootstrap integration tests.
- [ ] Run focused gateway/runtime tests.

### Task 4: Delete the detached session branch
- [ ] Delete `core.session` source files and `SessionManagerTest`.
- [ ] Remove old session Bean imports, factories, and comments from `AppConfig`.
- [ ] Confirm no active source still imports `meta.claw.core.session`.
- [ ] Run `mvn test -pl meta-claw-core,meta-claw-bootstrap -am`.

### Task 5: Align active documentation and state
- [ ] Add a tracked feature entry for core realignment.
- [ ] Update `claude-progress.md`, `clean-state-checklist.md`, `evaluator-rubric.md`, and active architecture docs.
- [ ] Confirm active docs no longer describe deleted session/model structures as current truth.

### Task 6: Verify the whole path
- [ ] Run focused validation across core/bootstrap/gateway modules.
- [ ] Run `./init.sh`.
- [ ] Restore generated `target/` noise if needed.
- [ ] Commit implementation and documentation separately.
