# Memory Entry Unification Design

## Goal

Unify short-term and long-term memory around a single `MemoryEntry` entity while removing two redundant abstractions:

- replace `PreferenceEntry` and `SessionSummary` with `MemoryEntry`
- remove `ConversationHistoryManager` by moving history-window behavior into the short-term store contract
- remove `UserPreferenceStore` and make prompt construction depend directly on `LongMemoryStore`

## Current State

The repository already has a useful memory boundary:

- `core.memory.shortterm` manages conversation history
- `core.memory.longterm` manages persisted preferences
- `ShortMemoryManager` and `LongMemoryManager` select configured backends

Three seams remain unnecessarily split:

1. short-term session listing returns `SessionSummary`, while long-term memory uses `PreferenceEntry`
2. short-term windowing lives in a separate `ConversationHistoryManager`, although callers only reach it through `ShortMemoryManager`
3. `UserPreferenceStore` is only an alias of `LongMemoryStore`

These splits create extra names without adding a meaningful boundary.

## Chosen Approach

Keep the existing short-term / long-term domain split, but use one shared memory entity across both branches.

This preserves the different access patterns of the two memory categories while removing duplicate model concepts and pass-through interfaces. It is a narrower and cleaner change than merging both stores into a single wide `MemoryStore`.

## Domain Model

Introduce `MemoryEntry` under `core.memory`.

`MemoryEntry` is the shared memory record for both branches and contains:

- `id`
- `timestamp`
- `category`
- `content`
- `metadata`
- `sessionId`
- `updatedAt`
- `messageCount`

The long-term branch uses the general memory fields such as `id`, `timestamp`, `category`, `content`, and `metadata`.

The short-term branch uses `sessionId`, `updatedAt`, and `messageCount` when returning session-list projections. Fields that are not meaningful for a branch may remain unset.

`PreferenceEntry` and `SessionSummary` are removed once all callers migrate to `MemoryEntry`.

## Short-Term Memory

`ShortMemoryStore` becomes the owner of persisted short-term history behavior, including windowed history reads.

The interface will expose:

- append message
- read full or bounded history
- list sessions as `List<MemoryEntry>`
- clear history
- detect conversation existence
- truncate history by round
- truncate history by token estimate
- summarize conversation

`JsonlShortMemoryStore` implements these methods directly.

`ConversationHistoryManager` is removed. `ShortMemoryManager` remains a backend selector and delegator, but no longer composes a second helper object for history policies.

Where the existing `getHistory(sessionKey, limit)` behavior overlaps with the new store-level windowing behavior, the API should be consolidated so callers have one obvious read path instead of parallel concepts that differ only slightly.

## Long-Term Memory

`LongMemoryStore` is updated to use `MemoryEntry` instead of `PreferenceEntry`.

`LongMemoryManager` continues to select and delegate to the configured backend, but it implements only `LongMemoryStore`.

`UserPreferenceStore` is removed because it does not add behavior beyond `LongMemoryStore`.

`PromptContextFactory` depends directly on `LongMemoryStore` and continues to render recent long-term entries into the prompt preferences section.

## Data Flow

### Short-Term

1. CLI code appends `SpiMessage` objects through `ShortMemoryManager`
2. `ShortMemoryManager` delegates to the selected `ShortMemoryStore`
3. callers that need bounded history request it through the store-backed API
4. session listing returns `MemoryEntry` projections

### Long-Term

1. long-term entries are written through `LongMemoryManager`
2. the selected `LongMemoryStore` backend persists `MemoryEntry`
3. `PromptContextFactory` requests recent entries from `LongMemoryStore`
4. recent entry contents are formatted into the prompt context

## Error Handling

- Unsupported backend names continue to fail fast in the manager layer
- Store implementations continue returning empty collections for unreadable or missing persisted data where that is already the current behavior
- JSON parsing failures continue to be logged and skipped rather than failing whole reads
- Removing aliases must not weaken existing null and empty checks in prompt generation

## Testing Strategy

Update and extend tests across the same boundaries that change:

- `JsonlShortMemoryStoreTest`
  - session listings now return `MemoryEntry`
  - truncation by round and token stays behaviorally identical after moving into the store
- `ShortMemoryManagerTest`
  - delegation remains intact without `ConversationHistoryManager`
- `FileLongMemoryStoreTest`
  - all persisted long-term records use `MemoryEntry`
- `LongMemoryManagerTest`
  - delegation uses the new entity type
- `PromptContextFactoryTest`
  - prompt preference rendering works through `LongMemoryStore`
- `SessionsCommandTest`
  - CLI session display remains unchanged with `MemoryEntry`

Verification sequence:

1. targeted Maven tests for core, store, and CLI memory-related classes
2. repository-wide `./init.sh`

## Non-Goals

- do not merge `ShortMemoryStore` and `LongMemoryStore`
- do not redesign on-disk directory layout
- do not add new memory categories beyond the fields needed for the current short-term and long-term behaviors
- do not change user-visible CLI commands

## Expected Result

After this change:

- the memory domain exposes one shared entity instead of two overlapping models
- short-term history policy belongs to the short-term store boundary
- prompt construction depends on the real long-term contract instead of an empty semantic alias
- the current manager/backend architecture remains intact, but with fewer unnecessary names and fewer pass-through layers
