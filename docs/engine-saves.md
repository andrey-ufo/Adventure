# Engine saves

**Status:** stable (Ф5.10, 2026-11-27).

This is the reference for the on-disk save format and the reader's
persistence behaviour:

- exact player state as `baseSnapshot + delta`;
- versioning and legacy migration;
- autosave (`progress.json`);
- manual slots (`saves/<adventureKey>/slot-<uuid>.json`);
- why autosave and slots cannot overwrite each other (S2).

S3/S4/S7 (undo/backlog/history UI) remain Nice. The model is designed so
they can be added without changing the v1 file shape.

## Decisions

| ID | Decision | Why |
|---|---|---|
| D1 | **Operation log**, not a rendered snapshot diff | Replayable, debuggable, and usable for future undo/backlog. |
| D2 | **Version first, then delta** | The envelope can evolve without guessing how old shapes map. |
| D3 | **One file per manual slot** | Autosave cannot physically overwrite a slot; list/delete is independent. |
| D4 | **Slots under `saves/<adventureKey>/`** | Avoids hundreds of files in the adventure root. |
| D5 | **Autosave stays `progress.json`** in the root adventure folder | Keeps all quests' autosaves in one cache/file; S2 writes only this file. |
| D6 | **Slot id is an opaque UUID**; the name is metadata | Renaming cannot break references or create filename collisions. |
| D7 | **No `manualSlots` list inside every save** | The slot directory is the catalog; duplicating it would drift. |
| D8 | **`StateDelta` is player-state only** | `apply` stays a simple `PlayerState` fold; cursor/flow state lives in the snapshot. |

## On-disk shape (v1)

`progress.json` is a map keyed by **quest folder document id** (not by
`quest.json`, which every quest now uses):

```json
{
  "primary:Adventure/Adventures/my-quest": {
    "version": 1,
    "adventureFileName": "",
    "baseSnapshot": {
      "currentChapterIndex": 0,
      "currentSceneId": "start",
      "flags": ["started"],
      "stats": {"health": 100, "gold": 3},
      "itemOrder": ["potion"],
      "appliedSceneEffects": ["start"],
      "remainingSteps": 0,
      "isGateActive": false,
      "warningsFingerprint": "",
      "passedGates": []
    },
    "delta": [
      {"type": "add_stat", "name": "health", "delta": 25},
      {"type": "set_flag", "name": "found_sword"}
    ],
    "currentChapterIndex": 0,
    "currentSceneId": "cave",
    "flags": ["started", "found_sword"],
    "stats": {"health": 125, "gold": 3},
    "appliedSceneEffects": ["start", "cave"],
    "itemOrder": ["potion"],
    "previousStepSnapshot": null,
    "chapterStartSnapshot": null,
    "remainingSteps": 0,
    "isGateActive": false,
    "lastAbsoluteAtSave": 123456,
    "warningsFingerprint": "",
    "passedGates": []
  }
}
```

Field notes:

- The **map key is the autosave identity** — the quest folder document
  id. `adventureFileName` is informational and is usually empty in
  autosave entries; slots store the derived quest key there.
- `baseSnapshot` and `delta` are additive v1 fields. `null` means the
  entry predates the delta model (legacy fallback).
- Current cursor/flow fields (`currentSceneId`, applied effects, gate,
  item order, warnings, passed gates) are authoritative on load.
  `baseSnapshot` stores them at the base point for future cursor-log work,
  but v1 replay only folds flags/stats.
- `StateDelta` variants are exactly:
  `set_flag`, `remove_flag`, `set_stat`, `add_stat`, `sub_stat`.
  `add_stat`/`sub_stat` are operations, not idempotent resulting diffs;
  each operation is replayed once, in order.
- Unknown delta variants make the player-state replay invalid; the reader
  falls back to the legacy current-state fields rather than silently
  skipping an operation.

## Replay and compaction

On load, when both `baseSnapshot` and `delta` are present and every
delta is known:

```text
state = applyDeltas(
  PlayerState(baseSnapshot.flags, baseSnapshot.stats),
  delta
)
```

`flags`/`stats` come from that replay; all other fields come from the
top-level current-state fields. If the base is missing or a delta is
unknown, the reader uses the legacy top-level `flags`/`stats`.

The operation log is bounded to **32** deltas. When an append would exceed
it, the reader folds the log into the current snapshot:

```text
baseSnapshot = current snapshot
delta = []
```

This is exact (the new base is the current state) and keeps per-step work
and save size bounded. Loading an older save with more than 32 deltas
compacts it once. Undo/restart use `previousStepSnapshot` /
`chapterStartSnapshot`, not this log, so compaction does not affect them.

## Versioning and migration (S6)

- `version = 1` is the current envelope. Legacy JSON without `version`
  is v0.
- **v0 → v1** preserves all existing fields and sets `version = 1`.
  Missing fields are normalized (Gson can leave `null` for absent
  collection/string fields despite Kotlin's non-null types).
- New writes always set the current version and write both the delta
  fields and the legacy current-state fields. Older readers can still
  read the current state; newer readers can replay the log.
- An unknown/future version is an explicit blocking error, never a silent
  reset. The same applies to a corrupt `progress.json`: `ProgressStore`
  raises `ProgressMigrationException`, the reader shows a blocking
  dialog, and the damaged file is not overwritten by a background save.
- One bad map entry does not hide other adventures; loading that entry is
  an explicit error.
- Migration entry point: `utils/ProgressMigration`
  (`migrate` / `normalize`). Callers do not special-case versions.

## Autosave

- Path: `progress.json` in the selected adventure folder root.
- `ProgressManager` owns the process-wide cache and debounce;
  `ReaderRoute` maps ON_PAUSE/ON_STOP to save/flush; `ProgressStore` owns
  the actual cache/file logic over `SaveStorage`.
- `ON_PAUSE` requests a save; `ON_STOP` flushes to disk. A failed write
  keeps the cache dirty so a later flush can retry.
- Preview mode never persists progress.
- `ProgressManager` caches progress for the process lifetime. To test
  external edits to `progress.json`, force-stop the app first or the
  in-memory cache may overwrite the edit.

## Manual slots (S1/S2)

### Layout

```text
<selected root>/
  Adventures/
    my-quest/
      quest.json
      theme.json
      assets/
  NewAdventures/
    my-project/
      quest.json
  progress.json
  saves/
    my-quest-8f3a1c2b/
      slot-4f6c....json
      slot-a19e....json
```

`adventureKey` is derived from the **quest folder document id**, not
`quest.json` (which is identical for every quest). A readable slug plus a
short SHA-256 suffix prevents collisions, e.g. `my-quest-8f3a1c2b`.
Nested directories are created through SAF.

Autosave writes only `progress.json`; slots write only under
`saves/<adventureKey>/`. S2 is structural, not a runtime check.

### Slot file

```json
{
  "format_version": 1,
  "slot": {
    "id": "4f6c8e0a-...",
    "name": "Перед подземельем",
    "created_at": 1769000000000,
    "updated_at": 1769000000000
  },
  "adventure_key": "my_adventure-8f3a1c2b",
  "state": {
    "version": 1,
    "adventureFileName": "my_adventure.json",
    "baseSnapshot": { "...": "..." },
    "delta": ["..."],
    "currentChapterIndex": 0,
    "currentSceneId": "cave",
    "flags": ["started"],
    "stats": {"health": 100},
    "itemOrder": [],
    "appliedSceneEffects": ["start"],
    "remainingSteps": 0,
    "isGateActive": false,
    "lastAbsoluteAtSave": -1,
    "warningsFingerprint": "",
    "passedGates": []
  }
}
```

- `slot.id` is a UUID; the file is `slot-<uuid>.json`. Renaming updates
  metadata, not the file name.
- `state` is the same `ProgressData` shape as autosave (including legacy
  current-state fields), so a slot is an exact restore.
- `lastAbsoluteAtSave = -1` for slots: loading a manual save must not
  deduct real-world steps taken after the checkpoint was created.
- List order is newest `updated_at` first.
- Cap: **20** slots per adventure. The oldest is never auto-deleted; the
  21st creation is refused with a clear message.
- A corrupt/newer slot is an explicit error that includes the slot file
  name. The current list is all-or-nothing: one unreadable slot prevents
  listing until it is removed manually. This is intentional to avoid
  silently hiding saves.
- Deleting an adventure removes its quest folder and calls the slots
  cleanup; orphan `saves/<key>/` folders from older alpha data may remain
  until a maintenance pass.

## Compatibility and tests

- `previousStepSnapshot` / `chapterStartSnapshot` remain serialized for
  existing death/step-back/chapter-restart features. They are not the
  persistence source of truth; reconstructing them from the log is S3.
- `lastAbsoluteAtSave` stays in autosave metadata; slot loading uses `-1`
  as above. Step catch-up semantics are unchanged.
- Coverage includes:
  - `ProgressStoreTest` — corrupt/invalid entries, no-op/flush/write
    failure, multi-adventure, v0 entry, cache reads.
  - `SlotStoreTest` — cap, delete/missing, ordering, corrupt slot, blank
    name, write failure.
  - `SaveSlotCodecTest` — format/version/state/id errors, keys, names.
  - `SaveRoundTripIntegrationTest` — 45-step save/load, all fields,
    1000-step bounded JSON, autosave/slot isolation.
  - `OperationLogBufferTest` — 32/33 boundary, 1000 real engine steps,
    500-scene adventure.
  - `AdventureReaderViewModelTest` — normal/legacy/future/corrupt init,
    step→save→reload, undo, restart, oversized-log compaction.
  - `ProgressManagerTest`, `SlotManagerTest`, `SafSaveStorageTest` —
    cache/debounce/errors and real SAF paths via Robolectric.
  - `ReaderSlotsScreenTest` + instrumented `ReaderSlotsScreenTest` /
    `SafPersistenceInstrumentedTest` — UI and real ContentResolver on
    device.

## Known limitations / future work

- S3 (undo), S4 (backlog), S7 (compact history UI) are Nice and not in
  scope; the delta model is ready for them.
- The slot list is all-or-nothing for corrupt/newer files; partial listing
  is a possible future improvement.
- Slot preview text is not stored in the slot file; the current UI shows
  name/time only.
- Orphan slot folders are not automatically cleaned up.
- The device macrobenchmark/allocation profiler is deferred; performance
  evidence currently consists of the 1000-step bounded tests and the
  manual smoke checks. The CI instrumented job covers UI/SAF correctness.
