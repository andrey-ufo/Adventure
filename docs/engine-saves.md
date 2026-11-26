# Engine saves

**Status:** draft (Ф5.1, 2026-11-25). This document fixes the **save
model** before any format change. It will become `stable` after Ф5.9 /
Ф5.10 (integration tests + implementation).

This is the design reference for:

- `SaveState` / `StateDelta` — exact state as an operation log;
- `ProgressData` versioning and legacy migration;
- manual slots and their folder layout;
- how autosave and manual slots stay independent (S2).

Implementation steps: Ф5.2 (delta model), Ф5.3–Ф5.4 (version/migration),
Ф5.5–Ф5.6 (slots), Ф5.7–Ф5.8 (P2). S3/S4/S7 (undo/backlog/history UI)
remain Nice; the model must allow them without a rewrite.

## Decisions (Ф5.1)

| ID | Decision | Why |
|---|---|---|
| D1 | **Operation log**, not a saved snapshot diff | Replayable; supports undo/backlog (S3), merge/N5 delta analysis, and readable debugging. The checklist recommends it. |
| D2 | **Version first, then delta** | `version` must exist before the shape changes; otherwise migration is guessing. |
| D3 | **Manual slots are separate files**, one file per slot | Autosave cannot physically overwrite a slot; listing/deleting a slot is independent. |
| D4 | **Slots live under `saves/<adventureKey>/`**, not in the root folder | 50 quests × 5 slots would put ~250 files next to the adventures. A per-adventure subfolder keeps browsing and cleanup sane. |
| D5 | **Autosave stays `progress.json` in the root adventure folder** | Backward compatibility with current files; S2 asks that autosave writes only `progress.json`. |
| D6 | **Slot id is opaque (UUID)**; the human name is metadata | Renaming an adventure/slot cannot break references; no filename collisions. |
| D7 | **`manualSlots` is not duplicated inside every `SaveState`** | The slot directory is the source of truth. The checklist's `manualSlots: List<SlotRef>` is satisfied by the slot catalog/filename layout; keeping a second list in each autosave would drift. |
| D8 | **`StateDelta` is player-state only; cursor/flow state stays in the snapshot** | Exact restore also needs scene/gate/applied-effects, but mixing them into player deltas makes `apply(PlayerState, delta)` awkward. Cursor/step ops can be a separate log for S3 without changing this model. |

## Target model

```text
SaveState {
  version: Int,                 // 1; bumps on breaking shape changes
  baseSnapshot: ProgressSnapshot,
  delta: List<StateDelta>       // ordered operation log since base
}

ProgressSnapshot {
  adventureFileName: String,
  currentChapterIndex: Int,
  currentSceneId: String,
  flags: Set<String>,
  stats: Map<String, Int>,
  itemOrder: List<String>,
  appliedSceneEffects: Set<String>,
  remainingSteps: Int,
  isGateActive: Boolean,
  warningsFingerprint: String,
  passedGates: Set<String>
}

StateDelta = one of:
  SetFlag(name)                 // idempotent
  RemoveFlag(name)              // idempotent
  SetStat(name, value)          // idempotent
  AddStat(name, delta)          // operation log; not idempotent
  SubStat(name, delta)          // operation log; not idempotent
```

Cursor/flow fields (`currentSceneId`, `chapter`, gate, applied scene
effects, item order, warnings, passed gates) live in
`ProgressSnapshot`; a future cursor/step log (S3) can record them without
changing `StateDelta`.

`apply(base, delta): ProgressSnapshot` is a **pure fold**. Applying each
delta once in order is deterministic; `Set*`/flag deltas are idempotent,
while `AddStat`/`SubStat` are intentionally not (they are operations, not
resulting diffs). The DoD wording "apply() идемпотентен для одного delta"
is read as "one delta is applied once, with no double-application from
the persistence layer"; replaying the whole log twice is never done.

`applyAll(base, deltas)` returns the current snapshot. A save is valid iff
`applyAll(base, delta)` can be computed without an unknown delta variant
(unsupported variant = explicit schema error, not silent skip).

### Where deltas come from

- `Engine.step` exposes player-state operations (`choice effects` then
  top-level subroutine output writes) as
  `StepResult.Success.appliedDeltas` (Ф5.2). `SubroutineExecutor` returns
  only the top-level output writes; internal frame changes are atomic and
  invisible.
- Scene effects are applied by the reader; when it records its cursor it
  also appends their `StateDelta`s to the player-state log.
- No "diff before/after" reconstruction: a diff would lose cancellation
  and intent (e.g. `Add 5` then `Sub 5`), which undo/merge need.

### Base snapshot and growth

- `baseSnapshot` is the full state at the most recent compaction point
  (new adventure, chapter start, or an explicit compaction).
- Until S3 (undo) exists, the operation log is append-only per
  playthrough. A later compaction policy may fold old operations into
  `baseSnapshot` while keeping a bounded undo tail; the format already
  allows it (replace base + trim deltas).
- Manual slots store the same `baseSnapshot + delta`, so a slot is an
  exact restore, not a rendered snapshot.

## ProgressData v1 and migration (S6)

The current `progress.json` is a map `adventureFileName -> ProgressData`
with no version. v1 keeps that outer shape and adds a version per entry:

```json
{
  "my_adventure.json": {
    "version": 1,
    "base_snapshot": { "...": "..." },
    "delta": [ { "type": "add_stat", "name": "health", "delta": 25 } ]
  }
}
```

**Implemented so far (Ф5.3/Ф5.4):** v1 is the **versioned legacy shape** —
the same fields as v0 plus `version: 1`. `base_snapshot` / `delta` are
optional additive fields added in **Ф5.2b** under the same v1 envelope;
old readers ignore unknown fields, new readers default them to empty, so
this is non-breaking.

- **v0** = current data (missing `version`). On read, `MigrationRegistry`
  treats it as version 0.
- **v0 → v1:** copy the existing state and set `version = 1`. All
  existing fields (scene, flags, stats, applied effects, item order,
  gates, `lastAbsoluteAtSave`, warnings, passed gates, both snapshots)
  are preserved. The catch-up delta is applied from `lastAbsoluteAtSave`
  as today.
- **Gson normalization:** legacy JSON may omit fields that were added
  later; Gson then leaves `null` despite Kotlin's non-null types.
  `ProgressMigration` fills absent lists/maps/sets/strings (and nested
  snapshots) with safe empty values before copying. This is what prevents
  a legacy save from crashing on `flags = null`.
- **Unknown version** (e.g. a future v2 opened by an older app) is an
  explicit read failure with a user-visible explanation; never a silent
  "start over". The reader shows a blocking "Не удалось открыть
  сохранение" dialog and does not overwrite the file.
- `MigrationRegistry.migrate(version, json)` is the only place that knows
  old shapes. Individual readers/UI never special-case versions.
- Slot files have their own `format_version` and the same registry
  (slot `format_version` starts at 1; unlike progress, there is no v0
  slot file because slots do not exist yet).

## Manual slots and folder layout (S1/S2)

### Why not one flat folder

The owner is right that 50 adventures × 5 slots will clutter a single
folder. The options:

| Layout | Pros | Cons |
|---|---|---|
| Flat `saves/quest-slot1.json` | simplest SAF calls | ~250 files; collisions; hard to delete one quest's saves |
| One file per adventure with a list of slots | easy listing | user chose file-per-slot; a partial write risks all slots of that quest |
| **`saves/<adventureKey>/slot-<uuid>.json` (chosen)** | per-quest isolation; file-per-slot; easy delete/list; no cross-quest collisions | needs nested-directory creation/cleanup |

### Paths

```text
<base adventure folder>/
  my_adventure.json
  other_adventure.json
  progress.json                    ← autosave map (legacy path, S2)
  saves/
    my_adventure-8f3a1c2b/
      slot-4f6c....json
      slot-a19e....json
    other_adventure-71d0e9aa/
      slot-....json
```

- `adventureKey` = sanitized display name + a short stable hash, e.g.
  `my_adventure-8f3a1c2b`. Sanitizing keeps the folder readable; the hash
  prevents collisions between `my adventure.json` and `my_adventure.json`
  (and limits path length/illegal characters).
- The nested folders are created through SAF
  (`DocumentsContract.createDocument` with the directory MIME type).
  Reads list children of the per-adventure folder; they never scan the
  whole `saves/` tree for one quest.
- `saves/` is disposable: if it is deleted, `progress.json` is untouched
  and the player can still continue; manual slots are simply gone.
- Autosave never writes inside `saves/<adventureKey>/`; slots never write
  `progress.json`. S2 is guaranteed by the path split, not by a runtime
  check.

### Slot file

```json
{
  "format_version": 1,
  "slot": {
    "id": "4f6c8e0a-...",
    "name": "Перед подземельем",
    "created_at": 1769...,
    "updated_at": 1769...
  },
  "adventure_key": "my_adventure-8f3a1c2b",
  "state": {
    "version": 1,
    "base_snapshot": { "...": "..." },
    "delta": [ "..." ]
  }
}
```

- The list UI reads slot files in one adventure folder (typically 1–20;
  cap policy below) and sorts by `updated_at`.
- Slot `id` is a UUID; the filename is `slot-<uuid>.json`. The display
  name is inside the JSON, so renaming is a metadata update, not a file
  move.
- **Cap:** allow a configurable maximum (default 20) per adventure; the
  oldest slot is not auto-deleted — creation is refused with a clear
  message so the player chooses what to remove.
- **Orphans:** if an adventure file is deleted, its `saves/<key>/` folder
  remains. Cleanup is a maintenance action (or a future "delete
  adventure" flow); opening the app must not silently delete slot files.

## Compatibility with the current code

- `PlayerStateSnapshot` already carries scene/chapter/flags/stats/order/
  applied effects; v1 `ProgressSnapshot` extends it with gate/warnings/
  passed gates.
- **Implemented in Ф5.2b:** `ProgressData` has additive
  `baseSnapshot` + `delta`; `Engine.step.appliedDeltas` and reader scene
  effects feed the log; load replays base+delta for flags/stats and falls
  back to legacy fields when absent. Legacy current-state fields are
  still written so older readers and v0 files keep working.
- `previousStepSnapshot` / `chapterStartSnapshot` are still serialized
  for the existing death/step-back/chapter-restart features; they are
  not the persistence source of truth. Reconstructing them from the log
  (S3) remains future work.
- `lastAbsoluteAtSave` stays in the autosave cursor/metadata; step
  catch-up semantics are unchanged (S6).
- Reader preview still does not persist progress.

## Open questions (resolved later)

- Exact compaction threshold + whether S3 undo keeps a bounded tail.
- Whether `manualSlots` ever needs a catalog file (for very large slot
  counts); D7 says no for the current cap.
- Slot preview text (scene title/snippet) — read from the state's current
  scene at list time; no duplicated preview field required.
- Cleanup of orphan `saves/<key>/` folders after adventure deletion.

## Next

**Ф5.2:** add `StateDelta` + `apply` in `:engine` (or `:app` shared
model), make `Engine.step` expose applied operations, and replace the
implicit snapshots in the reader. No v1 file is written until Ф5.3/Ф5.4
provide the migration path.
