# Engine types

**Status:** stable. Describes the type system introduced in Ф2 (2.1–2.8) of
[`checklist-tracker.md`](./checklist-tracker.md). Changes here are breaking
for every adventure file — extend with care.

This document is the reference for the **variable type system**: how
variables are declared, how the parser and type checker use them, what the
author must know when writing conditions and effects, and what is
enforced at runtime versus checked only at parse time.

It is the companion to [`engine-architecture.md`](./engine-architecture.md)
(which describes the module at a structural level) and to
[`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md) (which
maps mechanics to phases, not types).

## Why a type system

Before Ф2, the author wrote:

```json
"choices": [{"text": "Enter", "condition": "has_sword", "next_scene": "b"}]
```

The parser accepted `has_sword`. At runtime, `has_sword` looked at the
player's flags. But `sword` was declared as an **item** — it lived in
`stats`, never in `flags`. So `has_sword` was always `false`, the button
was always disabled, and the author saw no error — just a "grey button
that should be active".

Ф2 closes this gap in two layers:

1. **Type checker** (parse time) — every reference is checked against
   the declared type: `has_sword` on `ItemCount` is a **blocking error**
   with a fix hint (`use 'sword > 0' instead`).
2. **Declarations** — the author states every variable's type in
   `"variables"`; there is no more "inferred from usage". Fallback mode
   exists for backward compatibility (see [Fallback](#fallback)).

## Where variables live

Variables are declared once in `"variables"` at the top of the adventure
file, and referenced by name from conditions (`"condition": "sword > 0"`)
and effects (`"modify_stats": {"sword": -1}`).

```json
{
  "title": "Example",
  "variables": {
    "health":    { "type": "int_range", "min": 0, "max": 100 },
    "sword":     { "type": "item_count" },
    "has_key":   { "type": "bool" },
    "gold":      { "type": "int_min", "min": 0 },
    "score":     { "type": "int" },
    "documents": { "type": "item_list" }
  },
  "initial_state": {
    "flags": ["has_key"],
    "stats": { "health": 100, "sword": 1, "gold": 10, "score": 0 }
  },
  "chapters": [  ]
}
```

In the IR, `"variables"` becomes `Adventure.variables: Map<String, VarDecl>`,
where each entry is `VarDecl(name, type)` with `type: VarType`.

`VarDecl.name` and the map key are identical by construction; the IR keeps
both so that `inferTypes` (see [Migration](#migration)) can return a
`Map<String, VarDecl>` directly comparable to the file's `variables`.

## The six types

| Type | JSON | IR | Default | Bounds (semantic) | When to use |
|---|---|---|---|---|---|
| **Bool** | `{"type": "bool"}` | `VarType.Bool` | `false` | n/a | Flags: "has sword", "met wizard". Referenced from `has_X` / `!has_X`, set with `set_flags` / `remove_flags`. |
| **Integer** | `{"type": "int"}` | `VarType.Integer` | `0` | none | Unbounded numbers: score, computed totals. Use when the variable genuinely has no min/max (rare). |
| **IntMin** | `{"type": "int_min", "min": N}` | `VarType.IntMin(N)` | `min` | `>= N` | Resources that cannot go negative but have no upper bound: gold, stamina, ammo, health (if the story never caps it). **Most common numeric type.** |
| **IntRange** | `{"type": "int_range", "min": A, "max": B}` | `VarType.IntRange(A, B)` | `min` | `[A, B]` | Bounded stats: health 0..100, skill 1..12, endurance 20..30, day 0..30. |
| **ItemCount** | `{"type": "item_count"}` | `VarType.ItemCount` | `0` | `>= 0` | Inventory items, stackable potions, arrows. Same runtime as `IntMin(0)`, kept distinct so the type checker can tell "how many" from "any number". |
| **ItemList** | `{"type": "item_list"}` | `VarType.ItemList` | `emptyList()` | n/a | Unordered collection of item identifiers. Reserved for Ф4 (N3, choice templates); today the type checker only knows "non-numeric". |

### Choosing between the numeric types

Ask two questions about the variable:

1. **Can it go below zero?** If no → `IntMin(0)` or `IntRange(0, N)`.
2. **Can it ever exceed some N?** If no → `IntMin(min)` (or `Integer` if
   the lower bound also doesn't apply). If yes → `IntRange(min, max)`.

For example:

| Variable | Question 1 | Question 2 | Type |
|---|---|---|---|
| `health` (0..100) | yes | yes (100) | `IntRange(0, 100)` |
| `gold` | yes | no | `IntMin(0)` |
| `score` | no | no | `Integer` |
| `skill` (1..12) | yes (1) | yes (12) | `IntRange(1, 12)` |
| `potion` (count) | yes | no | `ItemCount` |

**Rule of thumb: prefer `IntMin(0)` over `Integer`.** Most game variables
are resources that cannot go negative. `Integer` (no lower bound) should be
rare — it is useful for scores that may become negative after penalties,
and for variables whose bounds the author genuinely does not want to commit
to.

### Why three numeric variants, not one

- **Clarity at the JSON level.** `{"type": "int_range", "min": 0, "max": 100}`
  reads immediately as "health-like stat"; `{"type": "int"}` reads as
  "any integer". Six explicit variants make the intent visible.
- **Type-checker hints.** A `Compare` on `IntRange` can be validated
  against the range in a future phase (Ф3.6, `Analyzer.deadConditions`);
  this is impossible if all numerics collapse to one type.
- **Clamp policy.** Once type-aware clamping lands (see
  [Runtime clamp](#runtime-clamp)), the applier needs to know the bound.
  Today it clamps everything to `>= 0` regardless; the type is what will
  drive the policy.

## Type checking (parse time)

After the JSON parses cleanly, `JsonToIr.parse` runs
`TypeChecker.check(adventure)` (Ф2.3). The checker produces
`TypeError`s, not `ParseError`s: parse errors mean "the file cannot be
turned into IR"; type errors mean "the file is structurally sound, but its
variable references are wrong". Both are blocking — the reader refuses to
open the file, the editor shows the errors in the validation dialog.

### What is rejected

| Variant | Trigger | Example | Fix hint |
|---|---|---|---|
| `UnknownVariable` | A name in a condition or effect is not in `variables` | `condition: "has_swrod"` | Rename to `has_sword` |
| `HasOnNonBool` | `has_X` / `!has_X` where `X` is not `Bool` | `condition: "has_sword"` on `ItemCount` | Use `sword > 0` |
| `SetFlagOnNonBool` | `set_flags` on a non-`Bool` variable | `set_flags: ["gold"]` | Use `modify_stats: {"gold": 1}` |
| `RemoveFlagOnNonBool` | `remove_flags` on a non-`Bool` variable | `remove_flags: ["health"]` | Use `modify_stats: {"health": -health}` |
| `ModifyStatOnNonNumeric` | `modify_stats` on `Bool` or `ItemList` | `modify_stats: {"has_key": 1}` | Use `set_flags` / `remove_flags` |
| `CompareOnNonNumeric` | `X op N` where `X` is not numeric | `condition: "has_key > 0"` | Use `has_has_key` |
| `InRangeOnNonNumeric` | `X in a..b` where `X` is not numeric | (reserved for Ф3.2) | Use `has_X` or a `Compare` |
| `InitialFlagOnNonBool` | A non-`Bool` name in `initial_state.flags` | `"flags": ["health"]` | Move to `initial_state.stats` |
| `InitialStatOnNonNumeric` | A `Bool` / `ItemList` name in `initial_state.stats` | `"stats": {"has_key": 1}` | Move to `initial_state.flags` |

Every error is scoped to a scene (`sceneId`) except `InitialState` ones
which have `sceneId == null` and appear first in the sorted output.

### Error ordering

`TypeChecker.check` returns errors sorted by
`(sceneId == null, sceneId.value, message)`. This is stable across runs,
which matters for snapshot tests of the editor's validation dialog and
for `git diff` of golden outputs.

## Fallback

**What it is.** If `variables` is empty (or absent from the JSON — pre-Ф2.8
files), `TypeChecker` runs in **fallback mode**: it infers a provisional
type table from `tags` and `initialState`, and checks references against
that.

**Why keep it.** Two reasons:

1. **Backward compatibility.** Files written before Ф2.8 carry no
   `variables`. Without fallback, those files would fail parse with
   `UnknownVariable` on every reference. Fallback lets the reader open
   them; the editor shows a warning "types are not declared" and offers
   to declare them (Ф2.7).
2. **Incremental migration.** An author opening an old file in the editor
   sees the warning, clicks "Suggest types" (Ф9.2, future), gets a
   `variables` block pre-filled by `inferTypes`, reviews and saves.
   Fallback makes the file playable **while** the author migrates it.

**How the inferred table is built.** See `TypeChecker.buildTypeTable` KDoc;
summary:

- **`tags[].category`** — `HEALTH` → `IntMin(0)`, `ITEM` → `ItemCount`,
  `JOURNAL` → `Bool` (or `IntMin(0)` if the name appears in
  `initialState.stats`).
- **`initialState.flags`** → `Bool`.
- **`initialState.stats`** → `IntMin(0)`.

If the inferred table is **empty** (no tags, no initial state), the checker
returns no errors — there is nothing to check against, and pretending
otherwise would produce false positives. The "you have not declared
variables" signal is a **UI concern** (Ф2.7 warning, Ф9.2 realtime-linter),
not a `TypeError`.

**Fallback is not weaker.** Typo `has_swrod` still produces
`UnknownVariable` if `sword` is a tag. `has_sword` on `sword: ItemCount`
still produces `HasOnNonBool`. Fallback only loses the ability to check
**cross-field** consistency (e.g. "author declared `HEALTH` but used
`Bool`"), which strict mode sees.

## Migration

**Tool:** `inferTypes(adventure): Map<String, VarDecl>` — public in
`:engine/ir/types/TypeInference.kt` (Ф2.8.2).

Given a parsed `Adventure` with an empty or incomplete `variables`, it
proposes a `Map<String, VarDecl>` that the author can review and copy into
the JSON.

**Priority resolution** when signals conflict:
`HEALTH tag > ITEM tag > numeric usage > Bool usage`.

Rationale in `inferTypes` KDoc, summary:

- **`tags[].category`** is deliberate — the author wrote it. It beats
  incidental usage.
- **Numeric usage > Bool usage** — a Bool cannot appear in `modify_stats`
  (it is a type error), while a numeric can appear in `set_flags` only
  by mistake. When both signals are present, the type checker's
  subsequent error ("`SetFlagOnNonBool`") is a clearer fix-target than
  "`ModifyStatOnNonNumeric`".

**`inferTypes` never guesses bounds.** It emits only `Bool`,
`ItemCount`, or `IntMin(0)` — never `Integer` or `IntRange`. The author
who wants a bounded variable declares it explicitly.

**Idempotence.** On `test_adventure.json` (which now has both declarations
and usage), `inferTypes(adv) == adv.variables` — this is the tripwire test
`TypeInferenceTest > real test_adventure_json - inference matches declared variables`.
If it fails, either the file was edited inconsistently or the logic
drifted.

**What it is not.** `inferTypes` does not validate. It does not check
against an existing `variables` block; it does not resolve contradictions
in contradictory usage. It is a **suggestion engine**, not a validator.
Validation is `TypeChecker`.

## Runtime clamp

**Current state (as of Ф2).** `EffectsApplier.apply` clamps every
`Effect.ModifyStat` to `>= 0`, regardless of the variable's declared
type. This is the same policy as Ф1.5 (`EffectsApplier.kt` KDoc,
"Clamp policy (temporary, until Ф2.8)"). Ф2 did not change it — the DoD
for Ф2.8 was "inferTypes + test_adventure migration + type-clean
reference sample", not "type-aware clamp".

Consequence: a `IntRange(0, 100)` variable (`health`) can exceed its
`max` if the player walks through enough `+50` effects, because the
applier does not enforce the upper bound. The **type checker** sees the
range and will be able to flag out-of-range literals in a future phase
(Ф3.6, `Analyzer.deadConditions`), but the **runtime** today only knows
"not below zero".

**What is not enforced yet:**

| Type | Declared bound | Enforced at runtime | Notes |
|---|---|---|---|
| `Bool` | n/a | n/a | Flags are set/removed, not clamped |
| `Integer` | none | `>= 0` (over-eager) | No lower bound in type; applier clamps anyway |
| `IntMin(N)` | `>= N` | `>= 0` (**not `>= N`**) | `N > 0` (e.g. `skill: 1..12` but declared `IntMin(1)`) is not enforced |
| `IntRange(A, B)` | `[A, B]` | `>= 0` (**not `[A, B]`**) | Upper bound not enforced; lower bound only `>= 0` |
| `ItemCount` | `>= 0` | `>= 0` | Correct by coincidence (policy matches type) |
| `ItemList` | n/a | n/a | Applier does not touch lists |

**Why this is deferred.** Type-aware clamp needs a `VarType` lookup at
every `ModifyStat`, which means `EffectsApplier.apply` must take the
`Adventure` (or at least `variables`). That changes its signature and
every call site (`Engine.step`, the reader preview, the debug panel).
Worth doing — but tied to save/restore (Ф5.2, S5, delta model), because a
restored state must be clamped identically to a freshly-applied one.
Doing it now would double the work in Ф5.

**What the author should know today:**

- **Do not rely on `IntRange.max` being enforced.** If the story needs a
  hard cap, add an explicit `Compare` condition on the choice that
  raises the stat, or a scene effect that resets it.
- **Do not rely on `IntMin(N > 0)` being enforced.** A negative delta can
  bring a `skill: 1..12` variable down to `0`. Add an explicit guard if
  the range matters.
- **`ItemCount` is safe** — the applier's `>= 0` policy matches the type.
- **`Integer` will not go below 0** even though the type permits it.
  This is an over-eager policy, not a bug — it has the same explanation
  as above.

The clamp will be type-aware in a future phase; when it lands, this
section will change to "enforced" and the type declarations will become
fully authoritative.

## Common authoring mistakes

| Symptom | Likely cause | Fix |
|---|---|---|
| Choice is visible but always disabled; no error in editor | `condition: "has_sword"` on an `ItemCount` variable, but file still in fallback mode (no `variables`) | Add `variables` block; the checker will now reject it as `HasOnNonBool` |
| Choice is visible but always disabled; error in editor: "Variable 'swrod' is used but not declared" | Typo in condition | Rename to `sword` |
| Health rises indefinitely, upper bound ignored | `IntRange(0, 100)` declared, but applier does not enforce `max` (see [Runtime clamp](#runtime-clamp)) | Add explicit `Compare` guard, or wait for type-aware clamp |
| Stat goes to 0 but the story expected it to stay at `min: 1` | `IntMin(1)` declared, but applier clamps only `>= 0` | Same — explicit guard, or wait |
| `initial_state.flags` contains a numeric name; editor reports `InitialFlagOnNonBool` | Author put `health` in `flags` by mistake | Move to `initial_state.stats` |

## See also

- [`engine-architecture.md`](./engine-architecture.md) — the module's
  overall structure: IR, parser, runtime, analyzer.
- [`glossary.md`](./glossary.md) — domain terminology (`Tag`, `Flag`,
  `Stat`, `Health`, …).
- [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md) —
  which gamebook mechanics are supported today.
- [`checklist-tracker.md`](./checklist-tracker.md) — the phase plan;
  Ф2 is "Типы и предсказуемые ошибки".
- Source: `engine/src/main/kotlin/com/myapp/adventure/engine/ir/types/`
  (`VarType.kt`, `VarDecl.kt`, `TypeChecker.kt`, `TypeError.kt`,
  `TypeInference.kt`).