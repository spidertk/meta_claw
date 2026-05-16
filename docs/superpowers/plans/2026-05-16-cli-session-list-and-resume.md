# CLI Session List And Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let CLI users discover existing sessions for a Vessel and explicitly resume one by id.

**Architecture:** Extend the conversation store with a Vessel-scoped listing API, expose that through a new `sessions` command, and add an opt-in `--resume` path to `chat`. Rebuild the system prompt on resume while replaying persisted conversational messages into the in-memory history.

**Tech Stack:** Java 21, Picocli, JUnit 5, Maven

---

### Task 1: Add Vessel-scoped session listing

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/session/ConversationStore.java`
- Modify: `meta-claw-store/src/main/java/meta/claw/store/conversation/JsonlConversationStore.java`
- Modify: `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java`

- [ ] Add `listConversations(String vesselId)` to the storage contract.
- [ ] Implement Vessel-scoped listing in `JsonlConversationStore`.
- [ ] Add a test proving one Vessel only sees its own conversations.

### Task 2: Add `sessions` CLI command

**Files:**
- Create: `meta-claw-cli/src/main/java/meta/claw/cli/SessionsCommand.java`
- Create: `meta-claw-cli/src/test/java/meta/claw/cli/SessionsCommandTest.java`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/MetaClawCommand.java`

- [ ] Add a `sessions <vessel>` command.
- [ ] Print session id, updated time, and message count.
- [ ] Test that output is scoped to the requested Vessel.

### Task 3: Add explicit resume support to chat

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
- Add/modify tests under: `meta-claw-cli/src/test/java/meta/claw/cli/`

- [ ] Add `--resume <session-id>`.
- [ ] Keep UUID creation for the default new-session path.
- [ ] Validate resumed session existence.
- [ ] Load persisted history and convert it into `SpiMessage` entries after the rebuilt system prompt.
- [ ] Continue appending new messages into the resumed session.

### Task 4: Verify and sync state

**Files:**
- Modify: `feature_list.json`
- Modify: `claude-progress.md`
- Modify: `clean-state-checklist.md`
- Modify: `evaluator-rubric.md`

- [ ] Run targeted tests.
- [ ] Run `./init.sh`.
- [ ] Update `chat-001` only after the explicit resume path is proven.
- [ ] Record any newly discovered limitations or follow-up work.
