# Architecture

**Status:** draft. High-level only. Deep per-class documentation will
be added once the feature set stabilizes (see
[`documentation-rules.md`](./documentation-rules.md) §8).

This document describes the high-level architecture of Adventure: the
layers, the data flow at a coarse grain, and the invariants that new
features must not violate. It does **not** describe individual classes
or algorithms — those are in [`engine-architecture.md`](./engine-architecture.md).

## Layers

Adventure has two Gradle modules:

| Module | Platform | Responsibility |
|--------|----------|----------------|
| `:engine` | Pure Kotlin/JVM (no Android) | IR, parser, runtime, analyzer, evaluator. Testable without a device. |
| `:app` | Android | UI (editor, reader, library, settings), persistence (Storage Access Framework), step counter service, Health Connect integration. |

`:app` depends on `:engine`, **never** the other way around. All game
logic — conditions, effects, reachability, transition rules — lives in
`:engine`. `:app` only orchestrates: reads user input, calls `:engine`,
renders the result.

## Data flow (coarse)

### Adventure file → gameplay

```
adventure.json
  │
  ▼
JsonToIr.parse          (parser, :engine)
  │  ParseResult<Adventure>
  ▼
Engine.compile          (runtime indexes, :engine)
  │  CompiledAdventure (scenesById, chapterIndexBySceneId)
  ▼
Analyzer.lint           (semantic checks, :engine)
  │  List<LintIssue>
  ▼
AdventureReaderFragment (reader, :app)
  │  Engine.step() on choice
  ▼
PlayerState             (immutable, :engine)
```

### Step sensor → gameplay

```
Android step sensor
  │
  ▼
StepCounterService      (foreground service, :app)
  │  raw absolute readings, deltas
  ▼
StepHistoryManager      (30-day buckets + monotonic total, :app)
  │  aggregates (getTodaySteps, getLastNDaysSum, …)
  ▼
AdventureReaderFragment (reader, :app)
  │  gates: "walk N steps here"
  ▼
Engine.step             (only called when the gate is open, :engine)
```

**Three invariants** for the step pipeline (from `README.md`, formalized
here):

1. **Source** (`StepCounterService`, `HealthConnectStepSource`) emits
   step *deltas*; never touches game state or `progress.json`.
2. **History** (`StepHistoryManager`) is the single owner of step
   history (30 daily buckets + monotonic `totalStepsAllTime`). No other
   component writes `step_history.json`.
3. **Game** (`AdventureReaderFragment`) reads only aggregates from
   `StepHistoryManager` and owns chapter-gate state
   (`remainingSteps`, `isGateActive`). It must **not** open
   `progress.json` or `step_history.json` directly.

Future features (achievements, step-gated choices, adventure unlocks,
ranks) build on `StepHistoryManager`'s read APIs and must not violate
these invariants.

## Invariants

These are rules new features must not break. Each has a rationale and
an enforcement mechanism.

### I1. `:engine` does not depend on Android

- **Why:** `:engine` must be testable on a plain JVM (CI without a
  device), and reusable by the planned JS runtime (E1.base, roadmap).
- **Enforcement:** `engine/build.gradle.kts` has no `com.android.*`
  plugin; `./gradlew :engine:build` succeeds without Android SDK.

### I2. Conditions and effects have exactly one evaluator / applier

- **Why:** if two call sites interpreted `has_sword` differently, the
  engine could allow a choice the UI shows as disabled (or vice versa).
  This is the N2/Ф3 class of bug.
- **Enforcement:** all condition evaluation goes through
  `Evaluator.evaluate`; all effect application through
  `EffectsApplier.apply`. Grep for hand-written parsers in `:app`
  returns zero.

### I3. Parser vs compiler vs analyzer boundary

- **Parser** (`JsonToIr.parse`): "can the file be turned into IR?" —
  JSON syntax, condition syntax, required fields.
- **Compiler** (`Engine.compile`): "can the runtime indexes be built?"
  — duplicate scene ids, missing start scene, dangling `nextScene`.
- **Analyzer** (`Analyzer.lint`): "is the IR coherent?" — reachability,
  unestablished variables, dead ends, contradictory endings.

Mixing these layers makes both harder to test. A file that parses but
does not compile is rejected before the reader opens it. A file that
compiles but has `WARNING` issues opens with a dialog — the player
decides.

### I4. Error severity reflects player impact

- `ParseError` / `CompileError` — the file cannot be opened at all.
- `LintIssue` with `Severity.ERROR` — the player would see dead
  buttons or a stuck screen; the reader refuses to load.
- `LintIssue` with `Severity.WARNING` — the adventure plays, but the
  author probably made a mistake. The reader shows a dialog once per
  `warningsFingerprint`, then plays.

Revised 2026-11-07: an implicit terminal scene (no choices, no
`is_ending`) used to be `ERROR`; it is now `WARNING` — the player can
still read up to that point, and only explicitly contradictory
constructs (`ending` + choices) remain `ERROR`.

### I5. Immutable `PlayerState`

- **Why:** step back, undo, and save/load all become trivial if state
  changes are values, not mutations.
- **Enforcement:** `PlayerState.flags: Set`, `stats: Map`,
  `EffectsApplier.apply` returns a new instance. No mutator methods.

### I6. JSON field names are stable

- **Why:** every adventure file in the wild (including
  `test_adventure.json`) references these fields by name.
- **Enforcement:** `SchemaFingerprintTest` (field names),
  `GoldenIrTest` (values after parsing), migration tests
  (`JsonToIrTest > v1 file migrates ...`).

## See also

- [`engine-architecture.md`](./engine-architecture.md) — deep dive into
  the engine's IR, parser, runtime, and analyzer.
- [`glossary.md`](./glossary.md) — domain terms.
- [`../README.md`](../README.md) — project overview, quick start.
- [`documentation-rules.md`](./documentation-rules.md) — why this
  document is high-level only.