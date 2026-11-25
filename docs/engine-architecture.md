# Engine architecture

**Status:** draft. Describes the `:engine` module as of IR v5 (Ф4.7).
Ф2 (types), Ф3 (evaluator, invariants, dead conditions) and Ф4.1–Ф4.7
(choice templates, disabled choices, merge points/analysis, atomic
subroutines) are implemented;
sections marked "deferred" point at the phase that will pick each item
up. See [`roadmap.md`](./roadmap.md) for the phase plan.

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

- `Adventure` — root. `version: Int` (CURRENT_VERSION = 5), `title`, `author`, `description`, `variables`, `choiceTemplates`, `invariants`, `tags`, `initialState`, `deathScene`, `startScene: SceneId`, `chapters`.
- `Chapter` — pure narrative grouping. No mechanical meaning. `id`,
  `title`, `scenes`.
- `Scene` — `id: SceneId`, `text`, `effects: List<Effect>`,
  `choices: List<Choice>`, `gate: Gate?`, `isEnding: Boolean`,
  `isMerge: Boolean` + `mergeSources: List<SceneId>` (Ф4.5, see
  §"Merge points" below).
- `Choice` — `id: ChoiceId`, `text`, `nextScene: SceneId`, `condition: Condition?` (enable), `visibilityCondition: Condition?` (show, Ф4.2), `effects: List<Effect>`, `templateRef: TemplateRef?` (Ф4.2, editor metadata), `call: SubroutineCall?` (Ф4.7 — atomic call executed after `effects`, `nextScene` is the return target).
- `Subroutine` — `id: String`, typed `inputs` / `outputs: List<VarDecl>`, `body: List<SubroutineStep>` (Ф4.7). A call creates a private local frame; the body sees only its parameters, never global variables directly. See §"Subroutines" below.
- `SubroutineStep` — `condition: Condition?`, `effects: List<Effect>`, `call: SubroutineCall?` (Ф4.7). Flat guarded steps: if the condition is false the step is skipped; otherwise effects run, then the nested call (if any).
- `SubroutineCall` — `subroutineId: String`, `args: Map<String, String>`, `outputs: Map<String, String>` (Ф4.7). On a `Choice` the names are globals; inside a body they are the caller frame's locals.
- `Condition` — sealed: `Has`, `Not`, `And`, `Or`, `Compare`,
  `InRange`. AST, not string.
- `Effect` — sealed: `SetFlag`, `RemoveFlag`, `ModifyStat`. List order
  is applied order.
- `Gate` — `requiredSteps: Int`, `repeatable: Boolean`. Steps on the
  **scene**, not the chapter (Ф1.5.4).
- `PlayerState` — runtime state: `flags: Set<String>`,
  `stats: Map<String, Int>`. Immutable.
- `ChoiceTemplate` — reusable choice template. `id`, `parameters: List<VarDecl>`, `text`, `visibilityCondition`, `availabilityCondition`, `effects`, `nextScene`. See §"Choice templates" below.
- `Invariant` — global rule. `condition: Condition`, `outcome: Outcome`. Fires when `condition` holds after a step (Ф3.5).
- `Outcome` — sealed: `Death` today (Ф3.5).

Value classes (`@JvmInline`, serialized transparently):
`SceneId`, `ChoiceId`.

Version history of the on-disk format is in
`Adventure.CURRENT_VERSION` KDoc. The version stayed at `5` for the
additive optional fields introduced in Ф3.5 (`invariants`), Ф4.1
(`choice_templates`), Ф4.5 (`merge` / `merge_sources`) and Ф4.7
(`subroutines` / `Choice.call`); each has an empty/false/null default
and an identity migration.

**Type system:** `Adventure.variables: Map<String, VarDecl>` and the six
`VarType` variants (`Bool`, `Integer`, `IntMin`, `IntRange`, `ItemCount`,
`ItemList`) are documented in [`engine-types.md`](./engine-types.md).
They were introduced in Ф2.1 (declarations), populated by the parser in
Ф2.8 (`JsonToIr`), and validated by `TypeChecker` in Ф2.2 / Ф2.3.

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

**Two-stage pipeline (Ф2.3).** Since Ф2.3, `parse` runs two checks in
sequence: JSON parsing (`JsonToIr.buildAdventure`) and type checking
(`TypeChecker.check`, Ф2.2). The order is strict — type checking only
runs when the JSON parses cleanly. A partial IR (a choice whose
condition string failed to parse has `condition = null`) would produce
false negatives at best and false positives at worst, so the parser
short-circuits on the first error (B1 decision, 2026-11-12).

Callers should look at both fields of `ParseResult.Failure`:

```kotlin
when (val result = JsonToIr.parse(json)) {
    is ParseResult.Success -> use(result.value)
    is ParseResult.Failure -> {
        if (result.typeErrors.isNotEmpty()) showTypeErrors(result.typeErrors)
        if (result.errors.isNotEmpty()) showParseErrors(result.errors)
    }
}
```

The two lists are mutually exclusive — exactly one is non-empty. This
is enforced in ParseResult.Failure.init, not just by convention.

### Two-stage parsing

1. **DTO layer** (`format/dto/Dto.kt`, `@Serializable`): mirrors the
   JSON exactly. Optional fields are non-null with defaults; `version`
   is the only nullable (to distinguish "no version" from an explicit
   version).
2. **Mapper** (`JsonToIr`): DTO → IR. Accumulates semantic errors
   (blank ids, empty chapters, unparseable conditions) into a
   `ParseResult.Failure` with **all** errors at once.

### Condition parser (`parse/ConditionParser`)

Syntactic only. Knows nothing about variable types; `TypeChecker`
validates the AST afterwards against `Adventure.variables`. The full
grammar shipped in Ф3.2 / N7.base:

| Input | Result |
|-------|--------|
| `has_X` | `Condition.Has("X")` |
| `!A`, `A && B`, `A \|\| B`, `(A)` | `Not`, `And`, `Or`, grouping |
| `X >= N` / `X > N` / `X <= N` / `X < N` / `X == N` / `X != N` | `Condition.Compare(X, op, N)` |
| `X in A..B` | `Condition.InRange(X, A, B)` |
| `true` / `false` | `Condition.Literal(...)` |

Precedence: `!` > `&&` > `\|\|`; `&&` / `\|\|` are left-associative.
The grammar, precedence table and `ConditionWriter` round-trip are
documented in [`engine-conditions.md`](./engine-conditions.md). Invalid
syntax returns a `ParseError.ConditionError` — **never** a silent pass.

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
- `MissingMergeSource(sceneId, mergeSourceId)` (Ф4.5 — a declared
  `merge_sources` entry names a scene that does not exist).
- `DuplicateSubroutineId(subroutineId)` (Ф4.7).
- `MissingSubroutine(subroutineId, caller… )` (Ф4.7 — a `Choice.call`
  or nested `SubroutineStep.call` names an unknown subroutine).
- `InvalidSubroutineCall(subroutineId, detail, caller… )` (Ф4.7 —
  missing/extra input arguments or unknown output names; type mismatches
  are `TypeError`s).
- `SubroutineCycle(path)` (Ф4.7 — `a -> b -> a` or self-call; recursion
  is not allowed in N6.base).

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
5. Apply `choice.effects`.
6. If `choice.call` is present, run the subroutine **atomically**
   (`SubroutineExecutor`): a stack of local frames, nested calls write
   their outputs into the parent frame, the top-level frame writes into
   globals. Then `Success(state, currentScene, outcome)` (Ф3.5 added
   `outcome`; Ф4.7 adds the call). A runtime call failure is
   `Rejected(RejectReason.SubroutineFailure)` — structural, so compile
   normally prevents it.

**Scene effects are not applied by `step`.** They are applied by the
reader on first entry (`appliedSceneEffects` in progress). Doing it in
`step` would re-drain health on every re-entry — a data-loss bug.
Consequence: `Success.outcome` reflects only the choice's effects; the
reader applies the target scene's effects and then re-runs
`Engine.evaluateInvariants`.

**`stepsInScene`:** `null` means "unknown" → treated as 0 walked (safe
default: a caller that forgets to pass steps cannot bypass a gate).
Ignored for scenes without a gate.

### `StepResult` and `RejectReason`

```
StepResult.Success(state, currentScene, outcome: Outcome?)  ← Ф3.5
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

`Evaluator.isDead(state)` — the legacy hardcoded `health <= 0` check.
Since Ф3.5 the runtime uses `Engine.evaluateInvariants(adventure, state)`
instead: a non-empty `invariants` list replaces the fallback, and an
empty list preserves the old `health <= 0` behaviour.

`Evaluator.choiceState(choice, state): ChoiceState` (Ф4.4) is the
single source of truth for "can the player pick this choice, and if
not, why". It returns `Visible`, `Disabled(reason: Reason)` or `Hidden`;
the reader and the editor preview both use it. `Hidden` wins over
`Disabled` when both `visibilityCondition` and `condition` are false.

## Analyzer (`analyze/`)

### `Analyzer.reachable(compiled): ReachabilityReport`

BFS from `adventure.startScene` along `choice.nextScene` links.
Returns:

- `reachable: Set<SceneId>` — scenes the player can visit.
- `unreachable: List<SceneId>` — sorted; "written but never seen".
- `deadEnds: List<SceneId>` — reachable scenes with no choices.

### `Analyzer.lint(compiled): List<LintIssue>`

Each issue has `kind: LintKind`, `message`, `sceneId?`,
`severity: Severity`. Current checks (Ф1.6–Ф4.6):

| Kind | Severity | Meaning |
|------|----------|---------|
| `UnreachableScene` | WARNING | Not reachable from `startScene`. |
| `RemovingUnestablishedItem(var)` | WARNING | Reducing an ITEM/HEALTH that is never established. |
| `DeathSceneWithoutHealth` | WARNING | `death_scene` present, `health` never initialized/modified. |
| `ConditionOnUnestablishedFlag(flag)` | WARNING | `has_X` references a name that never appears anywhere. |
| `DeathWithoutDeathScene` | WARNING | `health` used, no `death_scene`. |
| `DeadEnd` | WARNING | Reachable terminal scene not marked `is_ending`. |
| `UnreachableInvariant(reason)` | WARNING | An invariant references a flag that is never set (Ф3.5). |
| `DeadCondition(condition, reason)` | WARNING | Contradictory / out-of-bounds / combined-numeric condition (Ф3.6). |
| `MergeConflict(reason, source?)` | WARNING | Merge declaration does not match the graph (Ф4.6). |
| `EndingSceneWithChoices(sceneId)` | ERROR | `is_ending: true` + non-empty choices. |

`lint()` includes the dead-condition check and the merge-conflict
check. `Analyzer.deadConditions(compiled)` and
`Analyzer.checkMerges(compiled)` are also public so the editor's
realtime linter (Ф9.2) can call them on their own.

Only `ERROR` blocks the reader from loading the file. Everything else
opens with a "N warnings — continue?" dialog once per fingerprint.

The analyzer returns issues sorted by (scene id, message) for stable
output. `test_adventure.json` is the reference: it must be lint-clean
under current rules (test `lint is clean on test_adventure.json`).

**Deferred to later phases:**
- **Ф5:** state compatibility between merge branches — needs the
  per-branch `StateDelta` model, not just graph reachability.
- **Ф4.10:** merge editing UI (toggle, source picker, map join).

**Type errors are not lint issues.** `TypeChecker` (Ф2.2, wired into
`JsonToIr.parse` in Ф2.3) rejects unknown variables and wrong types
**before** `Analyzer.lint` runs. The two error classes have different
lifecycles: type errors block the parse pipeline
(`ParseResult.Failure.typeErrors`), lint issues are reported on a
successfully-parsed file. See [`engine-types.md`](./engine-types.md)
and the "Error hierarchy" section above.

## Error hierarchy

```
ParseResult.Failure  (parse/ParseResult.kt:  the file cannot be opened)
├─ errors:     List<ParseError>    ← JSON syntax / structure / condition syntax
└─ typeErrors: List<TypeError>     ← unknown variables, wrong types (Ф2.3)

CompileError       (CompileResult.kt:   indexes cannot be built)
├─ DuplicateSceneId
├─ MissingStartScene
├─ MissingNextScene
├─ MissingMergeSource        ← Ф4.5: declared merge branch does not exist
├─ DuplicateSubroutineId     ← Ф4.7
├─ MissingSubroutine         ← Ф4.7: call to unknown id
├─ InvalidSubroutineCall     ← Ф4.7: arity/name mismatch
└─ SubroutineCycle           ← Ф4.7: recursion is not allowed in N6.base

LintIssue          (analyze/:   file plays, but is semantically wrong)
├─ Severity.ERROR   ← reader refuses to open
├─ Severity.WARNING ← reader warns, player decides
└─ Severity.INFO
```
Four layers, four questions:

Can I turn this into IR? (ParseError)

Do all variable references make sense? (TypeError)

Can I build runtime indexes? (CompileError)

Is the IR coherent? (LintIssue)

Two fields in ParseResult.Failure, not one. Parse errors and type
errors come from different layers of the pipeline (parser vs type
checker) and carry different structured data: ParseError has JSON
paths and condition fragments; TypeError has variable names and
types. Merging them would blur the I3 boundary between "the file
cannot be parsed" and "the file parses, but its variable references
are wrong". Two fields keep the boundary visible in the type system,
and they are mutually exclusive (enforced in Failure.init): the
parser stops at the first failing stage — type checking runs only on
a fully parsed IR (Ф2.3 / B1 decision, 2026-11-12).

Same boundary fixed in Ф1.3.4.1: putting a semantic check in the
parser makes both harder to test and blurs the error message the
author sees.

## Merge points (Ф4.5)

**Status:** shipped in IR + runtime + compile checks + analyzer (Ф4.6);
editor UI follows in Ф4.10.

A **merge point** is a scene where two or more narrative branches
deliberately converge. The IR represents it as an **explicit scene
attribute**, not a separate `MergeNode` (the checklist allows either):

```json
{
  "id": "after_fork",
  "text": "...",
  "merge": true,
  "merge_sources": ["left_path", "right_path"]
}
```

- `Scene.isMerge: Boolean` (JSON `merge`, default `false`) — "this scene
  is a declared convergence point". It is **metadata**; it does not
  change how the scene is rendered or stepped.
- `Scene.mergeSources: List<SceneId>` (JSON `merge_sources`, default
  empty) — the branches the author says lead here. Empty is allowed: a
  merge scene without a declaration still has incoming edges in the
  `nextScene` graph; the analyzer (Ф4.6) can collect them.

### Runtime: delta is transferred implicitly (decision B1 / C1)

There is **no runtime "merge operation"**. `Engine.step` applies the
chosen branch's `choice.effects` before the transition; the reader
applies the branch scene's `scene.effects` on first entry. The
`PlayerState` that arrives at the merge scene is therefore already the
pre-branch state plus the branch's delta. Nothing is copied, diffed or
merged at the merge point itself.

This is deliberate: the current state model is a single `PlayerState`,
not per-branch deltas. True branch tracking belongs to the delta/save
model in Ф5; introducing it here would force a save-format rewrite for
no gameplay benefit today. The DoD property "two branches with different
state changes → merge transfers delta" is pinned by
`EngineStepTest.merge point receives accumulated delta from either
branch`.

### Validation split

- **Compile (`Engine.compile`)** — every id in `merge_sources` must
  exist (`CompileError.MissingMergeSource`). This is a cross-scene
  reference, same layer as `MissingNextScene`; the parser cannot check
  it while building one scene.
- **Analyzer (Ф4.6)** — `Analyzer.checkMerges` reports three
  `MergeConflict` warnings: `NoIncomingBranches` (`merge: true`, but no
  choice leads here), `SourceDoesNotLeadHere` (a declared source has no
  choice pointing at the merge), and `SourceNotReachable` (a declared
  source exists and leads here but cannot be reached from
  `start_scene`; reported only when the merge itself is reachable, so
  `UnreachableScene` is not duplicated). State compatibility
  ("incompatible states") needs Ф5 deltas and is not analysed yet.
- **Editor (Ф4.10)** — visual join on the map; toggle / source editing.
  Until then `merge` / `mergeSources` only round-trip through
  `:app/data/Scene` and `EditorScene` so a hand-edited file is not lost
  on save; the editor's validation dialog already shows the three
  `MergeConflict` warnings through `Analyzer.lint`.
- **Reference sample (Ф4.11)** — `test_adventure.json` gains a real
  merge example together with templates, disabled choices and a
  subroutine.

No version bump: both fields are optional with defaults `false` /
`[]`, so the migration is the identity (same reasoning as `variables`,
`invariants`, `choice_templates`).

## Subroutines (Ф4.7)

**Status:** engine shipped — IR, JSON parsing, compile checks, type
checking and `Engine.step` execution; editor UI + JSON/draft/snapshot
round-trip shipped in Ф4.8. Reader integration (Ф4.9) and the
full N6 integration/sample (Ф4.10 / Ф4.11) remain. The Compose reader
currently applies effects directly instead of calling `Engine.step`, so
a call declared today is validated and saved but does not run in the
reader until Ф4.9 wires it in.

**Editor (Ф4.8).** The toolbar opens a dedicated "Подпрограммы"
destination: a list of subroutines plus a form for `id`, typed
inputs/outputs and a flat table of guarded body steps. Each step edits
its condition, effects (reusing the tag dialog) and optional nested call.
From a choice, "Подпрограмма:" opens the shared
`SubroutineCallDialog`; from a step, the same dialog binds local names.
The dialog hides blank-input mappings and empty ids, but deliberately
does not hide type errors — those appear in "Проверить" through the
engine. `subroutines` and `Choice.call` round-trip through
`:app/data/Adventure`, editor drafts and undo snapshots, so open → save
does not lose them.

A **subroutine** is a reusable, **atomic** procedure: it runs to
completion inside one `Engine.step` call. The player never sees
intermediate screens, saves cannot happen mid-call, and undo returns to
the state before the invoking choice. This preserves reading pace and
keeps the save format unchanged. Player-visible multi-step content is a
different feature (reusable scenes).

```json
{
  "id": "consume",
  "inputs":  [{"name": "item", "type": "item_count"},
              {"name": "health", "type": "int_min", "min": 0}],
  "outputs": [{"name": "item", "type": "item_count"},
              {"name": "health", "type": "int_min", "min": 0}],
  "body": [
    {
      "condition": "item > 0",
      "modify_stats": {"item": -1, "health": 5}
    }
  ]
}
```

Call from a choice (`next_scene` is the **return target**, use the same
scene id to stay in place):

```json
{
  "text": "Выпить зелье",
  "next_scene": "start",
  "call": {
    "subroutine_id": "consume",
    "args":    {"item": "potion", "health": "health"},
    "outputs": {"item": "potion", "health": "health"}
  }
}
```

### Local frame

- A call creates a private `PlayerState`-backed frame. `inputs` are
  copied **into** it; `outputs` start at their type defaults and are
  copied **out** on return. A name in both lists is an in-out parameter:
  it starts with the caller's value and is written back (types must
  match).
- The body sees **only** its parameters. It cannot read or write
  `Adventure.variables` directly; anything needed must be an input,
  anything changed must be an output. This is what makes a subroutine a
  predictable contract.
- On a `Choice`, argument sources and output targets are global variable
  names. Inside a body, they are the enclosing frame's local names.
  Nested calls therefore compose frames without reaching into globals.

### Flat guarded steps

`SubroutineStep` is a flat list, not a nested `if` tree:

1. If `condition` is present and false, skip the step.
2. Apply `effects` to the frame in order.
3. If `call` is present, push a child frame. On return, the child's
   outputs are written into the parent's locals; the parent resumes at
   the next step.

An `else` branch is a following step with the negated condition. This is
deliberately table-friendly for the Ф4.8 editor. No loops, nested
control flow or arbitrary local variables — those belong to Ф13
`Procedure`. Effect deltas are literals: an input parameter can drive a
condition, but there is no "add the value of X" effect yet. The common
in-out pattern (`item > 0` → `item -= 1`, `health += 5`) is fully
supported.

### Execution and validation split

- **Parser (`JsonToIr`)** — structural JSON: blank ids/names,
  duplicate parameter names, in-out type mismatch, unparseable
  conditions, blank call ids/argument names.
- **Compiler (`Engine.compile`)** — cross references and arity:
  unknown subroutine ids, missing/extra arguments, unknown output
  names, duplicate ids, and call-graph cycles
  (`CompileError.SubroutineCycle`). Recursion is forbidden in N6.base.
- **TypeChecker** — parameter/call types: top-level args/targets against
  the global table, nested args/targets against the local frame,
  writes to input-only parameters, `ItemList` parameters (unsupported in
  N6.base). Body errors are wrapped in `TypeError.InSubroutine`; call
  type mismatches are `TypeError.InvalidSubroutineSignature`.
- **Runtime (`SubroutineExecutor`)** — an explicit frame stack
  (`ArrayDeque<Frame>`) with a hard `MAX_DEPTH = 64` guard. Failures are
  `RejectReason.SubroutineFailure` (structural bug; compile should have
  caught it).

No version bump: `Adventure.subroutines` defaults to `[]` and
`Choice.call` to `null`, so existing files are unchanged (same reasoning
as `variables`, `invariants`, `choice_templates`, `merge`).

**User benefit.** Authors get reusable mechanical blocks (consume an
item, apply damage, run a common check) with explicit typed inputs and
outputs, conditions and nested composition — without adding screens for
the player, without changing saves, and with compile/type errors before
the adventure can be played. The local-frame contract prevents a shared
procedure from silently mutating unrelated global state.

## Choice templates (Ф4.2)

**Status:** shipped (Ф4.1–Ф4.3.2b); deferred items listed below.

An author can define a **choice template** once and reuse it in any number
of scenes with different arguments. The template is declared in
`Adventure.choiceTemplates` (JSON `choice_templates`); instantiation is
done by `applyTemplate`.

### Design: expansion

When a template is instantiated — programmatically via `applyTemplate`,
or by the editor (Ф4.3.2b), which writes the expanded choice plus
`template_ref` metadata — the resulting `Choice` is **fully expanded**.
Its `text`, `condition`, `visibilityCondition`, and `effects` are filled
in with substituted values. The runtime (`Engine.step`, `Evaluator`,
`EffectsApplier`) and the analyzer (`Analyzer.lint`) see only expanded
choices; they never look at `Choice.templateRef` or
`Adventure.choiceTemplates`.

`Choice.templateRef: TemplateRef?` records the source (template id + args)
for the editor's benefit (Ф4.3). It is metadata; the gameplay-relevant
content is already in the expanded fields.

**Why expansion over a live `TemplateRef` (decision A1).** A
`TemplateRef`-based `Choice` would force every consumer (evaluator,
effects applier, analyzer, save/load) to be template-aware. That is a large
surface for a feature whose main value is authoring convenience. Expansion
keeps the runtime simple and the save format flat. The trade-off: when an
author edits a template, existing expanded choices do not auto-update.
An explicit "update all uses" action would use `templateRef` to find the
affected choices; it is **not implemented** and not tracked as a
follow-up (it was not part of the Ф4.3 DoD).

### Substitution semantics (decision B1)

`applyTemplate(template, args, choiceId)` performs two kinds of
substitution. Args are `Map<String, String>` — parameter name → actual
variable name.

1. **Text** — `{paramName}` placeholders in `template.text` are replaced
   with the corresponding arg value. Example: `"Use {itemId}"` +
   `args = {itemId: "potion"}` → `"Use potion"`. Braces make parameter
   references unambiguous against ordinary prose.
2. **AST name slots** — `Condition.Has(name)`, `Condition.VarRef(name)`,
   `Condition.Compare(varName)`, `Condition.InRange(varName)`,
   `Effect.SetFlag(name)`, `Effect.RemoveFlag(name)`,
   `Effect.ModifyStat(name)`, `Effect.Set(name)` are checked for **exact**
   parameter-name matches. If the string equals a parameter name, it is
   replaced with the arg value; otherwise it is left as-is (a reference to
   a global variable). No braces here — an AST name slot is either a
   parameter or a global, and exact match is unambiguous.

`value` / `delta` / `min` / `max` slots (all `Int`) are **not**
parameterizable. Integer parameterization requires a placeholder syntax for
ints and is out of scope for Ф4.2; it can be added later if authors ask.

`nextScene` has two modes:

- **Normal `SceneId`** — copied verbatim. Parameterizing the target
  through args is out of scope.
- **`@self` sentinel** (`ChoiceTemplate.SELF` = `SceneId("@self")`) —
  "the scene where this template is applied". Resolved by
  `applyTemplate` from its `currentSceneId` argument. The canonical case
  is "use item" / "drink potion": the choice applies effects in place
  without moving the player. `@` is stripped by the editor's
  `transliterate`, so no real scene id can collide with `@self`. A
  non-template `Choice` with `next_scene: "@self"` is an ordinary
  dangling reference (`CompileError.MissingNextScene`) — `@self` is a
  template-only marker.
### Editor tracking

`Choice.templateRef` is preserved on the expanded choice and
round-trips through `:app/data/Choice.template_ref` (Ф4.3.2b). Without
it, the editor could not distinguish "this choice was written by hand to
look like the template" from "this choice came from the template".

Planned uses (not implemented today):

- Show "this choice uses template X" on the choice card — currently the
  metadata is stored but not rendered.
- **"Update all uses"** when a template is edited: find every choice with
  `templateRef.templateId == X`, re-run `applyTemplate`, replace. This
  was proposed as 4.3.3 but is **not** in the Ф4.3 DoD and has no
  follow-up tracked.

### Editor UI (Ф4.3.2a)

**Status:** first slice shipped.

The template list screen (`TemplateListFragment`) is opened from the
editor's "📦 Шаблоны" toolbar button. It lists templates as compact
rows (id mono, text bold, "N параметров · M эффектов" summary). Row
click opens the edit dialog; the `✕` button deletes with confirmation.

The edit dialog holds all template fields in one place:
`id`, `text`, two conditions, `nextScene`, the parameter sub-list,
and the effect sub-list. Parameters reuse `item_editor_variable.xml`
(same layout as `VariablesAdapter`); effects reuse `renderTagRow` from
`EditorHelpers`. The positive button is disabled until `id`, `text`,
and `nextSceneId` are non-empty (`F2` validation).

`nextScene` is picked via a search dialog (`ScenePickerDialog`) — not
a `Spinner`. Search matches `id` or `title`, case-insensitive,
debounced 200ms; empty query hides the list (never renders 500
scenes at once). Special rows at the top: `@self` (only for the
template editor, `allowSelf = true`) and "create new scene".
`ScenePickerDialog` is reusable — a future refactor of
`EditorChoice.nextSceneId` (separate batch) can use the same dialog.

**Apply to a scene (Ф4.3.2b).** Each scene card has "📦 Из шаблона".
The flow is: `TemplatePickerDialog` (card list, empty state) →
`TemplateArgsDialog` (one row per parameter, typed hint, positive button
disabled until all are filled) → `AdventureEditorViewModel.applyTemplateToChoice`
(engine `applyTemplate`) → the expanded `EditorChoice` is appended with
`templateUid` / `templateArgs` metadata. The reader plays the expanded
text/condition/effects; it never sees the template.

**Not implemented.** "Update all uses" on template edit (no follow-up
tracked), and the template badge on a choice card.

### Deferred

- **`TypeChecker.checkChoiceTemplate`** (Ф4.2.2): validate templates
  against a combined scope (`parameters + globalVariables`). Until then
  a template's own conditions/effects are not type-checked, only the
  expanded choices are.
- **Placeholder validation** in `template.text`: `{foo}` with no declared
  `foo` parameter survives expansion literally. The realtime linter
  (Ф9.2) is the intended home.
- **JSON-level `template_ref` re-expansion**: the editor and the reader
  treat `template_ref` as metadata; `:engine` does not re-apply the
  template on load. A hand-edited expanded choice is played as written.

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
  transitions, including `MissingMergeSource`, the Ф4.5 delta test
  (two branches with different effects arrive at the same merge scene
  with their respective state), and Ф4.7 (one subroutine called from two
  scenes, nested frames, guarded step, invariants evaluated after
  outputs).
- `EvaluatorTest` / `EffectsApplierTest` — the two single-source-of-
  truth objects, plus `Evaluator.choiceState` (Ф4.4).
- `AnalyzerTest` — reachability and each `LintKind`, including the
  Ф4.6 merge-conflict reasons.
- `TypeCheckerTest` — global type errors plus Ф4.7 subroutine scopes:
  inputs/outputs are local, globals are invisible inside a body,
  input-only writes are rejected, top-level and nested call types are
  checked.
- `AdventureDtoTest` — DTO deserialization edge cases.

CI runs `./gradlew :engine:test` on every push and PR. Test reports
are uploaded as `engine-test-reports`.

## See also

- [`architecture.md`](./architecture.md) — high-level layers and data
  flow.
- [`engine-types.md`](./engine-types.md) — the variable type system
  (Ф2): six `VarType` variants, `TypeChecker`, `inferTypes`, runtime
  clamp policy.
- [`engine-conditions.md`](./engine-conditions.md) — the condition
  grammar (Ф3.2): `has_X`, `Compare`, `InRange`, `&&`, `||`, `!`,
  parentheses; round-trip with `ConditionWriter`.
- [`../README.md`](../README.md) — project overview.
- [`checklist-tracker.md`](./checklist-tracker.md) — phase plan,
  including Ф1.1–Ф1.10 (this document closes Ф1.10) and Ф2.1–Ф2.10.
- [`roadmap.md`](./roadmap.md) — post-Ф10 plan.