# Engine architecture

**Status:** draft. Describes the `:engine` module as of IR v5 (Ф1.7.0.4).
Sections marked "deferred" will grow in Ф2 (types), Ф3 (evaluator and
invariants), Ф5 (saves). See [`roadmap.md`](./roadmap.md) for the phase
plan.

This document is the **deep dive** into `:engine`: the IR, the parser,
the runtime, the analyzer, and the entry points that `:app` calls. It
is the companion to [`architecture.md`](./architecture.md), which
describes the layers at a higher level.

## Module boundary

`:engine` is a **pure Kotlin/JVM** module. It has no Android dependency,
no `context`, no file I/O. All file access lives in `:app`. `:engine`
operates on strings (JSON, conditions) and on in-memory data (IR,
`PlayerState`, `StepResult`).

This boundary is enforced by `engine/build.gradle.kts`: no
`com.android.*` plugin; `./gradlew :engine:build` succeeds without
Android SDK. If an engine class ever needs `android.*`, the answer is
"put it in `:app` instead" — the engine must remain testable on a
plain JVM and reusable by the planned JS runtime (E1.base, roadmap).

## Packages

```
com.myapp.adventure.engine
├── ir/                    — typed, serializable IR (Ф1.2)
├── parse/                 — condition parser, error types (Ф1.3.1)
├── format/                — JSON → IR (Ф1.3)
│   └── dto/               — wire-format DTOs
├── analyze/               — reachability, lint (Ф1.6)
├── CompiledAdventure.kt   — runtime indexes (Ф1.5.5.1)
├── CompileResult.kt       — compile errors
├── Engine.kt              — compile, step
├── Evaluator.kt           — condition evaluation
├── EffectsApplier.kt      — effect application
└── StepResult.kt          — outcome of Engine.step
```

## IR (`ir/`)

The typed representation of an adventure. Every field is `val`;
collections are immutable; `PlayerState` is a runtime value, distinct
from `InitialState`.

Key classes:

- `Adventure` — root. `version: Int` (CURRENT_VERSION = 5), `title`,
  `author`, `description`, `variables`, `tags`, `initialState`,
  `deathScene`, `startScene: SceneId`, `chapters`.
- `Chapter` — pure narrative grouping. No mechanical meaning. `id`,
  `title`, `scenes`.
- `Scene` — `id: SceneId`, `text`, `effects: List<Effect>`,
  `choices: List<Choice>`, `gate: Gate?`, `isEnding: Boolean`.
- `Choice` — `id: ChoiceId`, `text`, `nextScene: SceneId`,
  `condition: Condition?`, `effects: List<Effect>`.
- `Condition` — sealed: `Has`, `Not`, `And`, `Or`, `Compare`,
  `InRange`. AST, not string.
- `Effect` — sealed: `SetFlag`, `RemoveFlag`, `ModifyStat`. List order
  is applied order.
- `Gate` — `requiredSteps: Int`, `repeatable: Boolean`. Steps on the
  **scene**, not the chapter (Ф1.5.4).
- `PlayerState` — runtime state: `flags: Set<String>`,
  `stats: Map<String, Int>`. Immutable.

Value classes (`@JvmInline`, serialized transparently):
`SceneId`, `ChoiceId`.

Version history of the on-disk format is in
`Adventure.CURRENT_VERSION` KDoc (Ф1.2, updated by Ф1.5.4, Ф1.6.0,
Ф1.7.0, Ф1.7.0.4).

**Deferred:** `Adventure.variables` registry (Ф2.8), `VarDecl`-based
type checking (Ф2.2).

## Parser (`format/`, `parse/`)

### Single entry point: `JsonToIr.parse(text): ParseResult<Adventure>`

This is the only public function in `:engine` that turns raw JSON into
an `Adventure`. It is used by:

- the reader (`:app`) to load an adventure for playing;
- the editor (`:app`) to validate an in-progress JSON;
- the analyzer (Ф1.6) after parsing, to check reachability and
  duplicates;
- tests, including `test_adventure.json`.

Do not write a second parser alongside it. If a new format is needed
(YAML, binary, a future JS runtime), either add a `parse` overload
here or create a new `<Format>ToIr` with the same
`ParseResult<Adventure>` contract.

### Two-stage parsing

1. **DTO layer** (`format/dto/Dto.kt`, `@Serializable`): mirrors the
   JSON exactly. Optional fields are non-null with defaults; `version`
   is the only nullable (to distinguish "no version" from an explicit
   version).
2. **Mapper** (`JsonToIr`): DTO → IR. Accumulates semantic errors
   (blank ids, empty chapters, unparseable conditions) into a
   `ParseResult.Failure` with **all** errors at once.

### Condition parser (`parse/ConditionParser`)

Syntactic only. Knows nothing about variable types. Supported in Ф1.3:

| Input | Result |
|-------|--------|
| `has_X` | `Condition.Has("X")` |
| `!has_X` | `Condition.Not(Has("X"))` |
| `X >= N` / `X > N` / `X <= N` / `X < N` / `X == N` / `X != N` | `Condition.Compare(X, op, N)` |

Deferred to Ф3.2 / N7.base: `&&`, `||`, `!X` (general negation),
parentheses, `X in a..b`. Passing them today returns a
`ParseError.ConditionError` — **not** a silent pass. (A regression in
Ф1.3.3.2 was fixed: `has_sword && health` used to parse as
`Has("sword && health")`, hiding the author's error.)

## Runtime (`CompiledAdventure`, `Engine`, `StepResult`)

### `Engine.compile(adventure): CompileResult`

Builds the runtime indexes once per adventure:

- `scenesById: Map<SceneId, Scene>` — O(1) scene lookup.
- `chapterIndexBySceneId: Map<SceneId, Int>` — "which chapter is this
  scene in?".

O(n) in the number of scenes; n is thousands at most. Never call
`compile` from inside `step`.

**Compile errors** (the file cannot be played at all):
- `DuplicateSceneId(sceneId, chapterIndices)`
- `MissingStartScene(startSceneId)`
- `MissingNextScene(sceneId, choiceId, targetSceneId)`

**Not compile errors:** unreachable scenes, unestablished items, dead
ends — those are `LintIssue`s (below). A file with a lint-only issue
must never prevent the player from opening it.

### `Engine.step(compiled, state, sceneId, choiceId, stepsInScene): StepResult`

The core transition. Order of checks:

1. Current scene exists → `Rejected(SceneNotFound)`.
2. Choice exists → `Rejected(ChoiceNotFound)`.
3. Collect all blockers (condition, gate) → `Rejected(Blocked(all))`.
4. Target scene exists → `Rejected(SceneNotFound)` — checked
   **before** applying effects.
5. Apply `choice.effects` → `Success(state, nextScene, isDead)`.

**Scene effects are not applied by `step`.** They are applied by the
reader on first entry (`appliedSceneEffects` in progress). Doing it in
`step` would re-drain health on every re-entry — a data-loss bug.
Consequence: `Success.isDead` reflects only the choice's effects; the
reader re-checks `Evaluator.isDead` after applying scene effects.

**`stepsInScene`:** `null` means "unknown" → treated as 0 walked (safe
default: a caller that forgets to pass steps cannot bypass a gate).
Ignored for scenes without a gate.

### `StepResult` and `RejectReason`

```
StepResult.Success(state, currentScene, isDead)
StepResult.Rejected(
  ChoiceNotFound | SceneNotFound          ← structural bug, log
  | Blocked(blockers: List<Blocker>)      ← UI shows disabled + reasons
)
Blocker.ConditionNotMet | GateNotPassed
```

`Blocked` carries **all** blockers, in order: persistent (condition)
before temporary (gate). UI (N4 / Ф4) shows them together on a
disabled choice — "requires sword; walk 100 more steps".

## Evaluator and EffectsApplier

Two objects, each with **one job**:

- `Evaluator.evaluate(condition, state): Boolean` — the single source
  of truth for `Condition`. Used by `Engine.step`, the reader UI, the
  analyzer, and (future) the JS runtime.
- `EffectsApplier.apply(state, effects): PlayerState` — the single
  source of truth for `Effect`. Applies in list order.

Unknown variables default to their zero-value (`0` / `false`). The
type checker (Ф2.2) is what reports "unknown variable" to the author;
the evaluator is not the place for that.

`Evaluator.isDead(state)` — hardcoded `health <= 0`. Ф3.5 will promote
invariants (including this one) to first-class IR.

## Analyzer (`analyze/`)

### `Analyzer.reachable(compiled): ReachabilityReport`

BFS from `adventure.startScene` along `choice.nextScene` links.
Returns:

- `reachable: Set<SceneId>` — scenes the player can visit.
- `unreachable: List<SceneId>` — sorted; "written but never seen".
- `deadEnds: List<SceneId>` — reachable scenes with no choices.

### `Analyzer.lint(compiled): List<LintIssue>`

Each issue has `kind: LintKind`, `message`, `sceneId?`,
`severity: Severity`. Current checks (Ф1.6):

| Kind | Severity | Meaning |
|------|----------|---------|
| `UnreachableScene` | WARNING | Not reachable from `startScene`. |
| `RemovingUnestablishedItem(var)` | WARNING | Reducing an ITEM/HEALTH that is never established. |
| `DeathSceneWithoutHealth` | WARNING | `death_scene` present, `health` never initialized/modified. |
| `ConditionOnUnestablishedFlag(flag)` | WARNING | `has_X` references a name that never appears anywhere. |
| `DeathWithoutDeathScene` | WARNING | `health` used, no `death_scene`. |
| `DeadEnd` | WARNING | Reachable terminal scene not marked `is_ending`. |
| `EndingSceneWithChoices(sceneId)` | ERROR | `is_ending: true` + non-empty choices. |

Only `ERROR` blocks the reader from loading the file. Everything else
opens with a "N warnings — continue?" dialog once per fingerprint.

The analyzer returns issues sorted by (scene id, message) for stable
output. `test_adventure.json` is the reference: it must be lint-clean
under current rules (test `lint is clean on test_adventure.json`).

**Deferred to later phases:**
- **Ф2.2:** type checker — unknown variable, `has_X` on non-Bool.
- **Ф3.6:** dead conditions — `has_sword && !has_sword`.
- **Ф4.6:** merge conflict analysis.

## Error hierarchy

```
ParseError         (parse/:     JSON syntax, structural problems, condition syntax)
  ├─ Syntax
  ├─ Structure
  └─ ConditionError

CompileError       (CompileResult.kt:   indexes cannot be built)
  ├─ DuplicateSceneId
  ├─ MissingStartScene
  └─ MissingNextScene

LintIssue          (analyze/:   file plays, but is semantically wrong)
  ├─ Severity.ERROR   ← reader refuses to open
  ├─ Severity.WARNING ← reader warns, player decides
  └─ Severity.INFO
```

Three layers, three questions:

1. Can I turn this into IR? (ParseError)
2. Can I build runtime indexes? (CompileError)
3. Is the IR coherent? (LintIssue)

Same boundary fixed in Ф1.3.4.1: putting a semantic check in the
parser makes both harder to test and blurs the error message the
author sees.

## Testing strategy

- `RealAdventureTest` — parses the actual `test_adventure.json`
  shipped to every new user.
- `GoldenIrTest` — parses `test_adventure.json`, serializes the
  result, compares against `golden_ir.json` semantically. Regenerated
  via `./gradlew :engine:updateGolden` after an intentional IR change.
- `SchemaFingerprintTest` — pins the **names** of every serialized
  field. Catches added/removed fields with defaults (which
  `GoldenIrTest` misses due to `encodeDefaults = false`).
- `JsonToIrTest` — unit tests for the mapper: each error case, each
  migration, each boundary (`typo in flag name parses as Has — type
  check is F2`).
- `ConditionParserTest` — unit tests for the condition grammar.
- `EngineCompileTest` / `EngineStepTest` — runtime compile errors and
  transitions.
- `EvaluatorTest` / `EffectsApplierTest` — the two single-source-of-
  truth objects.
- `AnalyzerTest` — reachability and each `LintKind`.
- `AdventureDtoTest` — DTO deserialization edge cases.

CI runs `./gradlew :engine:test` on every push and PR. Test reports
are uploaded as `engine-test-reports`.

## See also

- [`architecture.md`](./architecture.md) — high-level layers and data
  flow.
- [`../README.md`](../README.md) — project overview.
- [`checklist-tracker.md`](./checklist-tracker.md) — phase plan,
  including Ф1.1–Ф1.10 (this document closes Ф1.10).
- [`roadmap.md`](./roadmap.md) — post-Ф10 plan.