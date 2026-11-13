# Progress log — Ф2.8 onward

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Continuation from [`progress-log-2.md`](./progress-log-2.md) (Russian,
Ф2.4–Ф2.7). From Ф2.8 onward entries are in English — see
[`documentation-rules.md`](../documentation-rules.md) §1 and the project
convention "progress log entries are in English" (CONTRIBUTING.md).

---

- **2026-11-12 · Ф2.8.1 — JSON parser reads `variables`**

  **DoD.** "`JsonToIr` reads `variables` from JSON, symmetrically to
  Ф2.7's save path. `AdventureDto` and `JsonToIr` map `VariableDto` →
  `VarDecl`. `test_adventure.json` gains `variables` (declarations for
  all 11 tags) so `TypeChecker` runs in strict mode and
  `real test_adventure_json is type-clean` remains green."

  **What changed (5 files).**

    1. `engine/.../format/dto/Dto.kt` — new `VariableDto(type, min?, max?)`;
       `AdventureDto.variables: Map<String, VariableDto> = emptyMap()`.
       `VariableDto` is a DTO, not `VarType`, because `VarType` cannot
       express the "incomplete declaration" state (`IntMin(min = null)` is
       not constructible).
    2. `engine/.../format/JsonToIr.kt` — `buildAdventure` now maps
       `dto.variables` → `VarDecl(name, type)` via new private extension
       `VariableDto.toVarType()`. Permissive defaults: unknown `type` →
       `Integer` (forward-compat); `int_min` without `min` / `int_range`
       without `min`/`max` → `Integer` (matches `:app`
       `EditorVarDecl.toVarType()` from Ф2.7).
    3. `app/src/main/res/raw/test_adventure.json` — added `variables`
       block with declarations for all 11 tags. `health`, `stamina`,
       `mana` → `IntMin(0)`; `sword`, `potion` → `ItemCount`; the six
       JOURNAL flags → `Bool`. `version: 5` unchanged: the IR field
       `Adventure.variables` exists since Ф1.2; only its content changes.
       Old files (empty `variables`) continue to work via fallback mode.
    4. `engine/.../format/SchemaFingerprintTest.kt` — added
       `expectedFlatFields["VariableDto"] = setOf("type", "min", "max")`
       and `assertFlat("VariableDto", ...)`. `expectedFlatFields["Adventure"]`
       already contained `"variables"` (added in Ф1.2).
    5. `engine/.../format/JsonToIrTest.kt` — 5 new tests:
       `variables block parses into VarDecl map`,
       `variables absent falls back to empty map`,
       `unknown variable type downgrades to Integer`,
       `int_min without min downgrades to Integer`,
       `int_range without max downgrades to Integer`.

  **Decisions (see prior chat / Ф2.7 conventions).**

    - **D1/D2/D3 — permissive defaults for unknown/incomplete types.**
      Matches `:app` editor behaviour, keeps file playable, and does not
      mask the real authoring error (a typo in the *name*, which
      `TypeChecker` still catches as `UnknownVariable`).
    - **D5 — no version bump.** `variables` has existed in IR since Ф1.2;
      the parser is tolerant of empty `variables` (fallback mode). A
      version bump would have to be accompanied by a rewrite of every
      existing file, for zero user benefit.

  **User benefit.** (a) The `variables` block written by the editor
  (Ф2.7) is now read by the reader; author → save → player → open
  round-trips the type declarations. (b) `test_adventure.json` runs
  `TypeChecker` in strict mode: any typo in any reference (condition,
  effect, initial_state) now blocks opening the file — the "grey
  button, no error" failure mode from pre-Ф2 is structurally
  impossible. (c) The save path (`buildAdventure()`) and read path
  (`JsonToIr.parse`) are symmetric: whatever the editor writes is
  what the reader sees.

  **What was NOT done.** Type-aware clamping of `IntRange.max` /
  `IntMin(N > 0)` (deferred; documented in `engine-types.md` §"Runtime
  clamp"). Cross-field `TagCategory ↔ VarType` validation (e.g.
  `HEALTH + Bool` — semantically nonsense; deferred until fallback mode
  is rare enough that the check would not produce false positives).

  **Verified.** `./gradlew :engine:updateGolden` → wrote
  `golden_ir.json` (~+600 chars, the `variables` block). `git diff
  golden_ir.json` — only the `variables` block added, no other
  structural change. `./gradlew :engine:test --rerun-tasks` →
  **181/181 PASSED** (176 from Ф2.7 + 5 new). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings. Smoke (phone):
  open `test_adventure.json` in library — reader loads without type
  errors; open in editor — "Проверить" shows no "types are not
  declared" warning (the warning was correctly suppressed because
  `variables` is non-empty).

  **Next — Ф2.8.2** (`inferTypes`).

---

- **2026-11-12 · Ф2.8.2 — `inferTypes` utility**

  **DoD.** "Скрипт/утилита `inferTypes(ir)` предлагает типы по
  использованию; `test_adventure.json` обновлён с `variables` и
  проходит typecheck; ручной регресс читалки 1:1."

  **What changed (2 new files).**

    1. `engine/.../ir/types/TypeInference.kt` (new) — public
       `inferTypes(adventure: Adventure): Map<String, VarDecl>`. Pure
       function. Scans `tags[].category`, `initialState.flags`,
       `initialState.stats`, and every `Effect` / `Condition` in every
       scene / choice, collecting four signal sets:
       `healthSignals`, `itemSignals`, `numericSignals`, `boolSignals`.
       Resolves by priority `HEALTH > ITEM > numeric > Bool`. Returns
       `Bool` / `ItemCount` / `IntMin(0)` only — never `Integer` or
       `IntRange` (inference does not guess bounds).
    2. `engine/.../ir/types/TypeInferenceTest.kt` (new) — 21 tests:
       initial-state signals (2), tag signals (4: HEALTH, ITEM,
       JOURNAL-not-in-stats, JOURNAL-in-stats), usage signals (8:
       SetFlag, RemoveFlag, Has, ModifyStat, Compare, InRange, Not, And),
       priority resolution (3: HEALTH beats Bool, ITEM beats numeric,
       numeric beats Bool), edge cases (3: empty, name-in-both, VarDecl
       name), and one integration test
       `real test_adventure_json - inference matches declared variables`
       which asserts `inferTypes(adv) == adv.variables` on the reference
       sample.

  **Decision (D4) — priority order.**
  `HEALTH tag > ITEM tag > numeric usage > Bool usage`. Rationale in
  `inferTypes` KDoc: explicit `tags[].category` is deliberate and
  beats incidental usage; within usage, "used in `modify_stat` /
  `compare`" is a stronger indicator of "this is a number" than
  "used in `set_flag`" is of "this is a flag" (a Bool cannot appear
  in `modify_stat` except by mistake; a numeric can appear in
  `set_flag` only by mistake).

  **User benefit.** (a) An author with an old adventure (no
  `variables`) opens it in the editor, clicks "Suggest types"
  (Ф9.2, future), gets a `variables` block pre-filled with the right
  types, reviews, saves. Zero manual typing for the common case.
  (b) The tripwire test means `inferTypes` **cannot drift** from the
  reference sample: if the inference logic or the sample changes,
  CI fails, and the author sees `inferTypes(adv) != adv.variables`
  with a concrete diff. (c) No new state: `inferTypes` does not
  mutate, does not cache, does not touch disk. Callable from a
  future CLI tool as `inferTypes(JsonToIr.parse(readFile(f)).value)`
  in one line.

  **What was NOT done.** Realtime "Suggest types" UI in the editor —
  Ф9.2 (realtime linter). CLI tool that pipes `inferTypes` output into
  a migrated file — nice-to-have, not required by DoD. `ItemList`
  handling: `inferTypes` treats it same as `Bool` (both non-numeric),
  so it is essentially never inferred; authors who want `ItemList`
  declare it explicitly.

  **Verified.** `./gradlew :engine:test --rerun-tasks` →
  **202/202 PASSED** (181 from Ф2.8.1 + 21 new). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings. The new
  `inferTypes` API is `public` but has no `:app` consumer yet — it
  is `:engine`-internal-use-so-far, exposed for Ф9.2.

  **Next — Ф2.9 + Ф2.10.**

---

- **2026-11-12 · Ф2.9 — closed automatically**

  **DoD.** "`test_adventure.json` → эталонный IR включает типы; тест
  падает при изменении IR; CI зелёный."

  All three satisfied by Ф2.8.1 without additional code:

    - `updateGolden` rewrote `golden_ir.json` with the `variables`
      block. `GoldenIrTest > golden matches current IR` — green.
    - `SchemaFingerprintTest.expectedFlatFields["VariableDto"]` pins the
      schema so that adding/removing/renaming a field in
      `VariableDto` breaks the test. The existing
      `expectedFlatFields["Adventure"]` already pinned the
      `"variables"` field name (since Ф1.2).
    - CI green on the same PR.

  No code changes; this entry documents the auto-closure.

---

- **2026-11-12 · Ф2.10 — `docs/engine-types.md`**

  **DoD.** "Документ в статусе stable; ссылка из
  `docs/engine-architecture.md`; примеры всех 5 типов."

  **Note.** The checklist says "5 типов"; the actual `VarType` has
  **6** variants (`Bool`, `Integer`, `IntMin`, `IntRange`,
  `ItemCount`, `ItemList`). The document covers all 6 — `ItemList`
  was added in Ф2.1 as a forward-looking type for Ф4 (N3, choice
  templates) and today is only known to `TypeChecker` as
  "non-numeric".

  **What changed (3 files).**

    1. `docs/engine-types.md` (new, stable) — the reference for the
       variable type system. Covers: why the type system exists
       (before/after examples of the "grey button, no error" failure);
       where variables live (JSON + IR); the six types (table +
       per-type rationale + "choosing between numeric types" +
       "why three numeric variants"); type checking (table of all 9
       `TypeError` variants with trigger/example/fix-hint); error
       ordering; fallback mode (what/why/how); migration via
       `inferTypes` (priority order, idempotence, what it is not);
       runtime clamp (current state, table of "declared bound vs
       enforced", why deferred to Ф5, what the author should do
       today); common authoring mistakes (table); see-also. ~7 KB.
    2. `docs/engine-architecture.md` — three edits: (a) `## IR`
       "Deferred" section replaced with a pointer to `engine-types.md`
       and a one-line summary of the type system (Ф2.1–Ф2.3);
       (b) `## Analyzer` "Deferred" removes the "Ф2.2 type checker"
       item and adds a note distinguishing `TypeError` from
       `LintIssue`; (c) `## See also` gets a link to
       `engine-types.md`.
    3. `docs/index.md` — one line added to `### For developers`:
       `- [Engine types](./engine-types.md) — the variable type system
       (Ф2): six VarType variants, TypeChecker, inferTypes, runtime
       clamp.`

  **User benefit.** (a) A new contributor reading `engine-types.md`
  learns the type system in 15 minutes, instead of grepping the
  codebase for `VarType` / `TypeChecker` / `inferTypes`. (b) An
  author reading it learns the **six** types (not three), how to
  choose between them, what `has_X` vs `X > 0` means, and what the
  runtime clamp does **not** enforce today. This closes the biggest
  remaining source of author confusion: `has_sword` on an `ItemCount`
  variable. (c) The "Runtime clamp" section is the honest disclosure
  of a known gap — an author can decide whether the gap matters for
  their adventure, rather than discovering it mid-play.

  **What was NOT done.** `docs/authoring-guide.md` (Ф10.7, deferred
  until the feature set stabilises); `glossary.md` additions for
  `VarType`, `VarDecl`, `TypeChecker`, `inferTypes` — not required
  for Ф2.10 DoD and might duplicate `engine-types.md` §"The six types".

  **Verified.** All three files written. `engine-types.md` status is
  `stable`, reachable from `index.md` and `engine-architecture.md`.
  **No code changed** — this batch is documentation only. `./gradlew
  :engine:test --rerun-tasks` remains **202/202 PASSED**. `./gradlew
  :app:assembleDebug` remains BUILD SUCCESSFUL, 0 warnings.

  **Gate Ф2 closed.** All five gate items satisfied:

    - `test_adventure.json` compiles (parse → typecheck). ✅
    - Typo in a condition = **compile error**, not silent pass. ✅
    - `:app` has no path where a condition error becomes `true` (Ф2.5). ✅
    - `:app` and `:engine` have no hand-written condition parser. ✅
    - `StoryValidator` has no parsing, only `:engine` calls (Ф2.6). ✅

  **Next — Ф3.1.** AST conditions and effects in `:engine`:
  `Condition` (Literal, VarRef, Has, Not, And, Or, Compare, InRange),
  `Effect` (Set, Add, Sub, Flag). See checklist Ф3.1: "AST sealed-
  иерархия; парсер строки → `Condition`; обратная сериализация в
  строку (для редактора и PO); unit-тест round-trip."

  **Current status of Ф3.1 work** (for planning the next batch):
  most of it exists already (Ф1.2–Ф1.3): sealed `Condition` with
  `Has`, `Not`, `And`, `Or`, `Compare`, `InRange`; sealed `Effect`
  with `SetFlag`, `RemoveFlag`, `ModifyStat`; `ConditionParser`
  string → `Condition`. **Missing:**

    - `Condition.Literal` / `Condition.VarRef` — needed by Ф3.2 for
      `A && B` where `B` is a bare variable.
    - `Effect.Set` (set stat to absolute value) — not present; today
      `ModifyStat` only adds delta.
    - **Reverse serialization** `Condition → String` — needed by the
      editor (Ф9.2 will write a `Condition` back into `"condition": "..."`).
      Today only `String → Condition` exists.

  The reverse-serialization task is the bulk of Ф3.1. This is where
  the next batch starts.

---

- **2026-11-13 · Ф3.1 — AST conditions and effects, `ConditionWriter`**

  **DoD.** "AST условий и эффектов в `:engine`: `Condition` (Literal,
  VarRef, Has, Not, And, Or, Compare, InRange), `Effect` (Set, Add,
  Sub, Flag). Парсер строки → `Condition`; обратная сериализация в
  строку (для редактора и PO); unit-тест round-trip."

  **Note on plan.** The checklist says "Effect (Set, Add, Sub, Flag)".
  Decisions (agreed with the user before coding):

    - **Я4** — do **not** split `ModifyStat` into `Add`/`Sub`. `delta:
    Int` (signed) is idiomatic, one class. Splitting would require
      rewriting `JsonToIr`, `EffectsApplier`, `TypeChecker`, `Analyzer`,
      `AdventureEditorViewModel` (`:app`), golden, dozens of tests — for
      cosmetic "positive delta" typing.
    - **Я5** — do **not** introduce generic `Effect.Flag(name, value:
    Boolean)`. `SetFlag` / `RemoveFlag` are `Flag(true)` /
      `Flag(false)`, split for readable JSON (`"set_flags"` vs
      `"remove_flags"`).
    - **Я1/Я2/Я3** — add `Condition.Literal`, `Condition.VarRef`,
      `Effect.Set` as **AST-only** nodes. Not produced by the parser
      today (see their KDoc); future use by Ф3.2 (`&&`, `||`, `!`,
      `A && B` where `B` is a bare variable) and Ф8 (`IrToJson`,
      absolute-set field). Adding them now costs ~50 lines and keeps
      every exhaustive `when` complete; adding them in Ф3.2 would force
      a second pass through the same `when`s.

  Split into **3.1.1** (AST + all consumers) and **3.1.2** (writer +
  tests), by analogy with 2.8.1/2.8.2. Both sub-steps below.

  ### 3.1.1 — AST + consumers

  **What changed (13 files).**

    1. `engine/.../ir/Condition.kt` — added `Condition.Literal(value:
     Boolean)` and `Condition.VarRef(name: String)`. KDoc on each
       explains: not produced by parser; `VarRef` becomes meaningful in
       Ф3.2 for `A && B`; `Literal` for future AST optimizations.
    2. `engine/.../ir/Effect.kt` — added `Effect.Set(name, value: Int)`
       (absolute, not delta). KDoc explains: not produced by parser (no
       JSON field yet); prepared for Ф8.
    3. `engine/.../ir/types/TypeError.kt` — KDoc of
       `ModifyStatOnNonNumeric` notes that `Set` shares the same variant
       (from the type checker's point of view, "add delta" and "set
       absolute" both require a numeric variable).
    4. `engine/.../ir/types/TypeChecker.kt` — `checkCondition` gets
       `is Literal -> Unit` (Bool by construction) and
       `is VarRef -> Unit` (no-op today; Ф3.2 adds the real check —
       `UnknownVariable` / `VarRefOnNonBool`). `checkEffects` gets
       `is Effect.Set -> ...` with the same rule as `ModifyStat`.
    5. `engine/.../EffectsApplier.kt` — `is Effect.Set -> stats[name] =
     value.coerceAtLeast(0)` (same `>= 0` clamp policy as
       `ModifyStat`; type-aware clamp is deferred to Ф5).
    6. `engine/.../Evaluator.kt` — `is Literal -> value` and
       `is VarRef -> name in state.flags` (same as `Has`).
    7. `engine/.../analyze/Analyzer.kt` — `collectHasFlags` adds `VarRef`
       to the "condition-on-this-flag" set (parallel to `Has`);
       `Literal` no-op. `lintRemovingUnestablishedItem` and
       `lintDeathSceneWithoutHealth` / `lintDeathWithoutDeathScene` now
       also count `Effect.Set` as "establishes a positive value" (for
       `Set(name, value > 0)`) / "uses health" (for `Set("health",
     any)`).
    8. `engine/.../ir/types/TypeInference.kt` — `collectEffectSignals`
       adds `Effect.Set → numericSignals`. `collectConditionSignals`
       adds `Literal → Unit`, `VarRef → boolSignals`.
    9. `engine/.../format/SchemaFingerprintTest.kt` —
       `expectedConditionVariants += {"literal" to {"value"}, "var_ref"
       to {"name"}}`. `expectedEffectVariants += {"set" to {"name",
     "value"}}`.
    10. `engine/.../EffectsApplierTest.kt` — 4 new tests: `Set` sets
        absolute, creates missing, clamps to 0, does not mutate state.
    11. `engine/.../ir/types/TypeCheckerTest.kt` — 3 new tests: `Set` on
        Bool → `ModifyStatOnNonNumeric`, on numeric → clean, on unknown →
        `UnknownVariable`.
    12. `engine/.../EvaluatorTest.kt` — 3 new tests: `Literal(true)`,
        `Literal(false)`, `VarRef` reads flags same as `Has`.
    13. `engine/.../analyze/AnalyzerTest.kt` — 2 new tests: `Set(name,
        >0)` establishes; `Set(name, 0)` does not establish.

  **Compiler-driven work.** The first `./gradlew :engine:test
  --rerun-tasks` after the AST additions produced **5 compile errors**
  in **3 files** we had not touched:

    - `Evaluator.kt:49` — non-exhaustive `when` (missing `Literal`,
      `VarRef`). `Evaluator.evaluate` is the only place that computes a
      `Condition` — adding variants to the AST must be mirrored there.
    - `TypeInference.kt:152, 165` — non-exhaustive `when` (missing `Set`,
      `Literal`, `VarRef`). `collectEffectSignals` /
      `collectConditionSignals`.
    - `Analyzer.kt:182, 185` — `Unresolved reference 'name' on receiver
    of type 'Effect'`. `Effect.establishesPositive(): Boolean` does
      not smart-cast `effect` after returning, so `effect.name` did not
      compile. Fixed by replacing the helper with an inline `when`
      (smart-cast at each branch).

  This is **the intended behaviour** of sealed ASTs: every consumer is
  forced to be explicit. The list of consumers (for future reference,
  also in `documentation-rules.md` after Ф3.1 closes):

  **Condition consumers:** `Evaluator.evaluate`,
  `TypeChecker.checkCondition`, `Analyzer.collectHasFlags`,
  `TypeInference.collectConditionSignals`, `ConditionWriter.write` (in
  3.1.2).

  **Effect consumers:** `EffectsApplier.apply`,
  `TypeChecker.checkEffects`, `Analyzer.lintRemovingUnestablishedItem`
  / `lintDeathSceneWithoutHealth` / `lintDeathWithoutDeathScene`,
  `TypeInference.collectEffectSignals`.

  ### 3.1.2 — `ConditionWriter` + tests

  **What changed (2 new files).**

    1. `engine/.../parse/ConditionWriter.kt` (new) — `object
     ConditionWriter { fun write(c: Condition): String }`. Produces
       canonical form with **minimal brackets** (precedence: `!` > `&&`
       > `||`). Associative: `And(And(a, b), c)` → `"a && b && c"`;
       `And(a, Or(b, c))` → `"a && (b || c)"`.
    2. `engine/.../parse/ConditionWriterTest.kt` (new) — 29 tests in
       three groups: (a) **true round-trip** for the variants the parser
       produces today (`Has`, `Not(Has)`, all 6 `Compare` ops); (b)
       **canonical string output** for `And` / `Or` / `Literal` /
       `VarRef` / `InRange` / `Not(non-Has)` / `!!` — writer produces
       the string, and the output is what Ф3.2 will accept; (c)
       **documented non-round-trip** — explicit `parseFails(...)` for
       the future variants; when Ф3.2 extends the parser, flip these to
       `roundTrip(c) == c`. Plus the known corner case
       `VarRef("has_x")` — writes as `"has_x"` and parses as `Has("x")`
       (naming collision in the author's file, not a writer bug).

  **Bug found and fixed during 3.1.2.** First run: 242/243 PASSED —
  `not compare needs brackets` failed with `expected:<!(health >= 50)>
  but was:<!health >= 50>`. Root cause: **precedence alone is not
  sufficient for prefix operators**. `!` is prefix; `!health >= 50`
  reads as `(!health) >= 50` in most languages, not `!(health >= 50)`.
  But `Compare` has `PREC_LEAF = 3` > `PREC_NOT = 2`, so the
  precedence-based writer skipped the brackets — producing an
  ambiguous string.

  Fix: new `writeNotChild(c)` — **structural**, not precedence-based.
  `Has` / `VarRef` / `Literal` / `Not` → no brackets (`!has_x`, `!x`,
  `!true`, `!!y`). `Compare` / `InRange` / `And` / `Or` → brackets
  (`!(x >= 5)`, `!(x in 0..10)`, `!(a && b)`, `!(a || b)`).

  **User benefit.** (a) The editor (Ф9.2) can now hold a `Condition`
  in memory and serialize it back to the `"condition"` field on save —
  the round-trip contract that makes "open a file, save without
  changes, get the same file" achievable once `JsonToIr` and
  `IrToJson` exist. For the three variants the parser produces today
  (`Has`, `Not(Has)`, `Compare`) this is already **strictly
  round-trip**: `parse(write(c)) == c`. (b) The writer's output for
  `&&`, `||`, `!`, `in`, `true`/`false`, `VarRef` is **canonical** —
  it is the syntax Ф3.2 will accept. When Ф3.2 lands, the 7 negative
  tests in `ConditionWriterTest` become positive with a one-line diff
  (remove `assertIs<Failure>`, use `assertEquals`), rather than a
  rewrite. (c) A future `EffectWriter` (Ф8, `IrToJson`) can follow the
  same pattern; the writer/precedence lesson is documented here, not
  lost. (d) `Evaluator`, `TypeInference`, `Analyzer` now handle
  `Literal` / `VarRef` / `Set` — when Ф3.2 starts producing these
  nodes, no consumer has a gap.

  **What was NOT done (deliberately).**

    - **No parser changes** — `ConditionParser` was not touched in
      Ф3.1. Adding `&&`, `||`, `!` (general), parentheses, `in a..b`,
      `VarRef`, `true`/`false` is **Ф3.2 (N7.base)**.
    - **No `VarRefOnNonBool`** — the new `TypeError` variant that will be
      raised by `TypeChecker.checkCondition`'s `VarRef` branch once the
      parser produces the node. Ф3.2.
    - **No `Add` / `Sub` / `Flag`** (Я4, Я5) — see "Note on plan" above.
    - **No effect writer** — `Effect` AST → JSON (the `"set_flags"` /
      `"modify_stats"` fields) is `IrToJson` (Ф8), not this batch.
    - **Golden and `test_adventure.json` unchanged** — the new variants
      do not appear in the current JSON, so `updateGolden` was not
      needed. `GoldenIrTest` stays green with the existing file.

  **Verified.** `./gradlew :engine:test --rerun-tasks` → **243/243
  PASSED**:

    - `EffectsApplierTest` — 4 new (Set).
    - `TypeCheckerTest` — 3 new (Set).
    - `EvaluatorTest` — 3 new (Literal / VarRef).
    - `AnalyzerTest` — 2 new (Set establishes / Set(0) does not).
    - `ConditionWriterTest` — 29 new (round-trip, canonical form,
      documented non-round-trip, corner case).
    - `SchemaFingerprintTest` — green after adding `literal` / `var_ref`
      / `set` to expected variants.
    - `GoldenIrTest`, `JsonToIrTest`, `RealAdventureTest`,
      `AdventureDtoTest`, `Engine*Test`, `TypeInferenceTest` — green
      (nothing in these paths was touched).

  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings.
  `:app` has no `when` over `Condition` or `Effect` directly — all
  reads go through `Evaluator.evaluate` / `EffectsApplier.apply`; all
  writes go through `serializeTags` → JSON (unchanged).

  **Next — Ф3.2** (N7.base): extend `ConditionParser` grammar with
  `&&`, `||`, `!` (general), parentheses, `X in a..b`, `true` /
  `false`, and `VarRef` (bare Bool reference). Add
  `TypeError.VarRefOnNonBool`. Flip the 7 negative tests in
  `ConditionWriterTest` to positive (round-trip). Extend
  `test_adventure.json` with examples of each; `updateGolden`;
  `docs/engine-conditions.md`. Split planned as 3.2.1 (parser +
  TypeChecker `VarRef` + test rewrites) and 3.2.2 (docs +
  `test_adventure.json` + golden).

---

- **2026-11-13 · Ф3.2 — condition grammar (`&&`, `||`, `!`, `in`,
  `Literal`)**

  **DoD.** "N7.base: расширить грамматику: `&&`, `||`, `!`, скобки,
  сравнения `>=, >, <=, <, ==, !=`, количественные (`gold >= 3`).
  Парсер принимает все конструкции; приоритеты (`!` > `&&` > `||` >
  сравнения); тесты; `test_adventure.json` расширен примерами и
  проходит typecheck."

  **Scope decision (revised after user feedback).** Initial plan
  included `VarRef` (bare Bool identifier) + `TypeError.VarRefOnNonBool`
    + `has_`-prefix reservation. **Dropped from Ф3.2** after re-evaluating
      against the two project goals (best app for authors/readers; solid
      base for future checklist/roadmap work). Reasons:

    - **Not in DoD.** `VarRef` is not mentioned in N7.base. Adding it
      would double the parser code (~150 → ~250 lines) and require
      `has_`-prefix reservation (breaking for any author with a variable
      named `has_key`).
    - **Ambiguous UX today.** Two syntaxes for the same thing
      (`has_sword` and `sword`, both Bool) confuses authors more than
      one canonical syntax.
    - **Not critical for any future phase.** `VarRef` is a
      nice-to-have for terseness, not a blocker for Ф3.3 (single
      evaluator), Ф3.5 (invariants), Ф4 (templates), Ф12 (RNG), or
      Ф13 (combat). The AST node is ready (Ф3.1.1) — adding the parser
      branch is a ~30-line change, at any future point.

  `Condition.VarRef` remains in the AST; `Evaluator`, `TypeChecker`
  (no-op), `TypeInference`, `Analyzer`, `ConditionWriter` all handle it.
  The parser branch + `VarRefOnNonBool` + `has_`-prefix reservation are
  tracked as a separate roadmap item (Appendix B).

  ### 3.2.1 — Recursive-descent parser

  **What changed (2 files).**

    1. `engine/.../parse/ConditionParser.kt` — full rewrite, recursive
       descent. Grammar:
       ```
       expr    = or
       or      = and ("||" and)*
       and     = not ("&&" not)*
       not     = "!" not | primary
       primary = "(" expr ")" | "true" | "false"
               | "has_" IDENT | IDENT "in" INT ".." INT
               | IDENT COMPARE_OP INT
       ```
       Precedence: `!` (right, prefix) > `&&` (left) > `||` (left).
       Parentheses override. Two-char operators (`>=`, `<=`, `==`, `!=`)
       parsed before one-char. `IN_RANGE_REGEX` for `X in A..B`
       (tolerates spaces around `..`). `splitTopLevel(input, op)` splits
       on `&&` / `||` at depth 0 (inside `(...)` does not split);
       `isMatchingParens(s)` requires the first `(` to match the last `)`
       (`(a) && (b)` → false, not a single group). Reserved chars (`&`,
       `|`, `!`, `<`, `>`, `=`, `(`, `)`) rejected inside flag/variable
       names.

    2. `engine/.../parse/ConditionParserTest.kt` — full rewrite. Removed
       the old "and-or not supported in F1_3" test (now supported);
       renamed `reserved char in comparison var name is rejected` into
       `not then compare parses as Not-Compare` (behaviour changed:
       `!health > 5` is now `Not(Compare(health, GT, 5))`, not parse
       error); renamed `has_sword && health is rejected - DoD Ф2_4` into
       `has_sword && health is rejected - DoD Ф2_4 - now at primary
       level` (still parse error — `health` bare identifier is not
       `VarRef`; DoD preserved); added `has_sword && has_health parses -
     DoD Ф2_4 via TypeChecker` (the well-formed grammar side).
       Plus ~24 new tests: literal, in-range, and/or/not precedence,
       parentheses, `!!`, associativity, error cases.

  **Flipped in `ConditionWriterTest.kt` (6 tests).** `And`, `Or`,
  `Literal`, `InRange`, `Not(Compare)`, `!!` — negative → round-trip
  positive. `VarRef` negative tests kept (parser still rejects).

  **Bug during 3.2.1.** First run: `trailing junk after condition
  fails` — `"has_a extra"` parsed as `Has("a extra")` (valid, not a
  failure). The test was **wrong**, not the parser: `has_` allows
  spaces in flag names (documented since Ф1.3). Replaced with two
  tests: `trailing junk after literal fails` (`true extra`,
  `false extra`), and `has_ allows spaces in flag name - documented
  behavior` (pins the design).

  ### 3.2.2 — docs + sample + golden

  **What changed (4 files + golden regen).**

    1. `docs/engine-conditions.md` (new, stable) — the reference for the
       condition grammar. Covers: grammar (BNF), each leaf (`has_X`,
       `Compare`, `InRange`, `Literal`), each operator (`&&`, `||`,
       `!` including the `!health > 5` = `Not(Compare(...))` semantics),
       precedence table, round-trip table with `ConditionWriter`, why
       `VarRef` is deferred (and how to add it later), reserved chars,
       common mistakes, see-also.
    2. `app/src/main/res/raw/test_adventure.json` — new scene
       `conditions_advanced_test` (in chapter 2, before `chapter_end_2`)
       demonstrating all four syntaxes: `has_initial_flag && health >=
     50` (Bool + Compare), `has_initial_flag || has_quest_started`
       (Bool + Bool, always true because the left is from
       `initial_state.flags`), `!has_quest_completed` (negation),
       `health in 50..100` (range), `!(has_quest_started &&
     has_quest_completed)` (negated group). Plus a new choice in
       `conditions_test` linking to the new scene. All 6 examples are
       lint-clean (verified by `AnalyzerTest > lint is clean on
     test_adventure_json`).
    3. `docs/index.md` — one line in `### For developers` for
       `Engine conditions`.
    4. `docs/engine-architecture.md` — one line in `## See also` for
       `engine-conditions.md`.
    5. `engine/src/test/resources/golden_ir.json` — regenerated via
       `./gradlew :engine:updateGolden`. The diff is exactly two
       additions: `conditions_test_choice_5` in `conditions_test` and
       the full `conditions_advanced_test` scene. No other changes.

  **User benefit.** (a) Authors can now write composite conditions:
  `has_sword && health > 50`, `!(has_quest_started &&
  has_quest_completed)`, `health in 50..100`. This closes the biggest
  remaining authoring gap before Ф4 (choice templates). (b) The
  writer/parser round-trip is complete for everything except
  `VarRef` — save-open cycle preserves semantics for 7 of 8 variants.
  (c) `conditions_advanced_test` in `test_adventure.json` gives every
  new user a working reference for `&&`, `||`, `!`, `in`. (d) `VarRef`
  is documented as "deliberately deferred" in `engine-conditions.md`,
  with a concrete roadmap item — no author or contributor will
  rediscover the question from scratch.

  **What was NOT done (deliberately).**

    - **`VarRef` in parser** — see scope decision above. AST node
      exists; parser branch + `TypeError.VarRefOnNonBool` +
      `has_`-prefix reservation deferred to a future phase (Appendix B
      of checklist).
    - **`TypeError.VarRefOnNonBool`** — depends on `VarRef` in parser.
    - **`Effect` grammar / syntax** — `ConditionParser` is condition-only.
      `Effect` AST (`SetFlag`, `RemoveFlag`, `ModifyStat`, `Set`) is
      built by `JsonToIr.buildEffects` from the three JSON fields
      (`set_flags` / `remove_flags` / `modify_stats`); no effect-string
      syntax today and none planned.
    - **`roll(...)` / `ExternalOutcome`** — Ф12 / Ф15. New `Condition`
      variants will follow the same pattern (AST-first, parser later).
    - **`docs/engine-types.md` update** — the type system is orthogonal
      to grammar. `has_X` and `Compare` were already typed in Ф2.2;
      `And`/`Or`/`Not` return Bool by AST shape; `Literal` is Bool by
      construction; `InRange` requires numeric (same rule as `Compare`).
      No new type-checker rules were needed for Ф3.2.

  **Verified.** `./gradlew :engine:updateGolden` → wrote
  `golden_ir.json` (+~700 chars). `git diff golden_ir.json` — only
  the two additions above. `./gradlew :engine:test --rerun-tasks` →
  **272/272 PASSED**:

    - `ConditionParserTest` — full rewrite (~40 tests, ~30 new).
    - `ConditionWriterTest` — 6 flips (negative → round-trip).
    - All other tests unchanged: `EffectsApplierTest` (15),
      `EngineCompileTest` (6), `EngineStepTest` (20), `EvaluatorTest`
      (17), `AnalyzerTest` (37), `GoldenIrTest` (3), `JsonToIrTest`
      (35), `RealAdventureTest` (14), `SchemaFingerprintTest` (5),
      `AdventureDtoTest` (6), `TypeCheckerTest` (28),
      `TypeInferenceTest` (21).

  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings
  (`:app` uses `Evaluator.evaluate` / `EffectsApplier.apply`, no
  direct `Condition` / `Effect` handling).

  **Smoke (phone).** Open `test_adventure.json` → chapter 2 →
  `conditions_test` → «Составные условия (демо)» →
  `conditions_advanced_test`. All 6 buttons display; clicking each
  transitions to `healthy`. `!has_quest_completed` becomes disabled
  after completing the quest in `journal_test` (documented dynamic
  behaviour). Open in editor → «Проверить» → «Проблем не найдено».

  **Next — Ф3.3.** Single evaluator: `evaluate(condition, state,
  decls) → Bool + Reason?`; `Reason` returns which subexpression
  failed (needed for N4 disabled + reason in Ф4). Dependencies:
  Ф3.1 (AST + writer) ✓, Ф3.2 (grammar) ✓, Ф2 (types) ✓. The
  `Condition` AST is exhaustive (`Has`, `VarRef`, `Literal`, `Not`,
  `And`, `Or`, `Compare`, `InRange`) and `Evaluator` handles all 8;
  Ф3.3's job is the `Reason`-returning wrapper around it, plus
  deleting the parallel `isConditionMet` in `:app` (Ф3.4).

---

- **2026-11-13 · Ф3.3 — `Evaluator.evaluateWithReason` + structured
  `Reason`**

  **DoD.** "N2: один evaluator: `evaluate(condition, state, decls) →
  Bool + Reason?`. Одна функция; `Reason` возвращается при `false`
  (какое подвыражение не выполнено) — это нужно для N4. Unit-тесты
  на все AST-узлы; **любая** проверка условия в `:app` и `:engine`
  идёт через неё."

  **DoD reconciliation.** The DoD mentions `decls`. **Not passed to
  `Evaluator`** (decision C1). Rationale: types are a parse-time
  concern (`TypeChecker`, Ф2.2); at runtime every variable in
  `PlayerState` is already the correct shape by construction (a file
  that fails typecheck never reaches the reader — Ф2.3). Adding
  `decls` would be a parameter that is always empty. If Ф5 (type-aware
  clamp) or Ф3.5 (global invariants) need `decls`, an overload
  `evaluateWithReason(condition, state, decls)` lands in ~5 lines
  without changing this API. The DoD `Bool + Reason?` intent is
  covered by [EvaluationResult] (`Success` / `Failure(reason)`) —
  a `Pair<Boolean, Reason?>` would allow the illegal state
  `(true, Some(reason))`, whereas the sealed type is structurally
  correct.

  **What changed (3 files).**

    1. `engine/.../EvaluationResult.kt` (new) — `sealed class
     EvaluationResult { Success / Failure(reason: Reason) }` and
       `sealed class Reason` with 8 variants, one per `Condition`
       variant plus boolean recursions:
        - `HasFailed(name)`, `VarRefFailed(name)`, `LiteralFailed`,
          `CompareFailed(varName, op, actualValue, expectedValue)`,
          `InRangeFailed(varName, actualValue, min, max)` — leaves.
        - `NotFailed(innerCondition: Condition)` — the inner condition
          **succeeded** (no reason to nest); keeping the AST node lets
          the UI format `!(...)` literally.
        - `AndFailed(failedReason: Reason)` — first false side only
          (short-circuit: the right side is not evaluated when the left
          is false).
        - `OrFailed(leftReason: Reason, rightReason: Reason)` — both
          sides, because `||` short-circuits only on `true`. When both
          are false, the player has two distinct blockers; the UI may
          want to show both ("requires `sword` or `magic`").
          Reasons are **structured data**, not formatted strings. Formatting
          is a UI concern (`:app`, `strings.xml`, Ф7). The same separation
          that `TypeError` (structured) vs `TypeError.message` (English,
          editor-only) already follows.
    2. `engine/.../Evaluator.kt` (replace) — new
       `evaluateWithReason(condition, state): EvaluationResult`;
       `evaluate(condition, state): Boolean` becomes a **thin wrapper**:
       `evaluateWithReason(...) is Success`. KDoc explains the split:
        - `evaluate` — used by `Engine.step` (blocker list), `Analyzer`
          (reachability checks) — callers that only need the answer.
        - `evaluateWithReason` — used by N4 / Ф4 (disabled-with-reason
          UI), where the *cause* matters.
          Short-circuit rules are documented per variant. `compare` and
          `inRange` extracted into private helpers; `actualValue` in
          `Reason.CompareFailed` / `Reason.InRangeFailed` is the value
          the evaluator actually compared (`state.stats[varName] ?: 0`),
          not "unknown" — meaningful even in fallback mode.
    3. `engine/.../EvaluatorTest.kt` (replace) — kept all 17 tests from
       Ф1.5 / Ф3.1 (Boolean `evaluate`, `isDead`, `Literal`, `VarRef`).
       Added 14 new tests for `evaluateWithReason`: `Success` for true
       condition; every `Reason` variant leaf; `Compare` carries actual
       and expected; missing stat reports actual `0`; `Not` reason
       when inner succeeds; `Not` succeeds when inner fails; `And` on
       left / right failure; `And` short-circuit on false left
       (right not evaluated); `Or` both-fail reports both reasons;
       `Or` short-circuit on true left / right; nested `Or(And, c)`
       reason shape; `!!` on true / false inner; `Success` carries no
       reason (compile-time pin via type usage).

  **What was NOT done (deliberately).**

    - **`isConditionMet` in `:app`** — still calls `Evaluator.evaluate`
      (Bool). Replacing it with `evaluateWithReason` and showing the
      reason in the UI is **Ф3.4** (removal) + **Ф3.8** (passing
      reason to the UI model). Ф3.3 is engine-only.
    - **`decls` parameter** — see "DoD reconciliation" above.
    - **Global invariants** (`health <= 0 => death` as first-class IR) —
      Ф3.5. `isDead` remains a hardcoded single rule.
    - **`Analyzer.deadConditions`** — Ф3.6.
    - **Sandbox check** (no `java.lang.reflect`, no IO inside
      `Evaluator`) — Ф3.7.
    - **`docs/engine-conditions.md` update** — the grammar and
      `Reason` structure are independent documents. `engine-conditions.md`
      describes the parser side; if a future reader wants the reason
      shape, the `Reason` KDoc in `EvaluationResult.kt` is the source.
      If a `docs/engine-reasons.md` becomes necessary (when N4 formats
      reasons in the UI), it lands with that feature, not here.

  **User benefit.** (a) The UI (N4 / Ф4) has everything it needs to
  show "requires `sword`, you have 0" or "requires `health >= 50`,
  you have 30" — without `:engine` knowing any user-facing string.
  (b) `Engine.step` and `Analyzer` keep using `evaluate(): Boolean`
  — no performance or behavioral regression. (c) The `Reason`
  hierarchy is exhaustive over `Condition`: adding a new variant in
  `:engine` breaks `:app` compilation until the UI teaches itself to
  render its reason — same "compiler makes you look at it" pattern
  as `TypeError` / `LintKind`. (d) Short-circuit semantics are
  **documented**, not guessed: `And` reports the first false side
  (right not evaluated when left is false), `Or` reports both
  (neither short-circuited). A UI author cannot accidentally show a
  stale right-side reason for an `And` where the left already failed.

  **Verified.** `./gradlew :engine:test --rerun-tasks` →
  **286/286 PASSED** (272 from Ф3.2 + 14 new in `EvaluatorTest`:
  reason variants + short-circuit + nested + `!!` + sanity).
  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings.
  `:app` not touched — `AdventureReaderFragment.isConditionMet` still
  calls `Evaluator.evaluate`; `Engine.step` also unchanged. The new
  API is **available** for `:app` but not yet consumed — Ф3.4 / Ф3.8.

  **Next — Ф3.4.** Remove `isConditionMet` from
  `AdventureReaderFragment`; replace with a thin wrapper over
  `Evaluator.evaluate` that takes an explicit `PlayerState` snapshot.
  The fragment is not the place for condition logic (N2). Note:
  DoD Ф3.4 mentions `updateChoices` uses `evaluate().reason` — that
  is Ф3.8 (passing `Reason` into the UI model). Ф3.4 uses
  `evaluate(): Boolean`; reason lands in Ф3.8.
  ### Ф3.4-fix2 — gate isolated from `health_test`

  **Problem (user feedback).** `health_test` carried the demo gate
  (`gate: 1, gate_repeatable: true`), so testing health/items/journal
  required walking. This was fine for the initial demo but became an
  obstacle for iterating on the reader (Ф3.4, N4/Ф4 disabled-reason).

  **Fix (2 files + golden regen).**

    1. `test_adventure.json`:
        - `health_test` — removed `gate` / `gate_repeatable`, text
          updated ("Шаговый гейт здесь не задан — сразу можно щёлкать
          урон / лечение / смерть").
        - New scene `gate_test` (chapter 1, between `health_test` and
          `items_test`) — `gate: 1, gate_repeatable: true`, one choice
          "Назад" → `start`. Text explains both per-visit and one-shot
          modes with reference to `chapter_end_*`.
        - `start` — 6th choice "Тест шагового гейта (демо)" →
          `gate_test`. Now `start` has 7 choices.
    2. `RealAdventureTest.kt`:
        - `start scene has six choices` → `seven choices` (6 → 7).
        - `gates on specific scenes` — `health_test` gate moved to
          `gate_test`; added assertion `gateOf(0, "health_test") == null`.
        - `health_test gate is repeatable, chapter ends are not` →
          `gate_test is repeatable, chapter ends are not`.
    3. `updateGolden` — new scene + new choice in start + removed gate
       fields from health_test.

  **User benefit.** Testing health/items/journal no longer requires
  walking. The gate demo moved to a dedicated scene, reachable from
  `start` in one tap. This is not a feature change for readers — it
  is a test-adventure ergonomics change. The **reference sample**
  still demonstrates both gate modes: `gate_test` (per-visit) and
  `chapter_end_1` / `chapter_end_2` (one-shot).

  **Verified.** `./gradlew :engine:updateGolden` → ~23,000 chars.
  `./gradlew :engine:test --rerun-tasks` → **286/286 PASSED**.
  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Smoke (phone):
  scenarios 1–4 (health without gate, gate separately, items/journal
  without gate, composite conditions) all pass.

---

- **2026-11-13 · Ф3.5 — global invariants (`Invariant`, `Outcome.Death`)**

  **DoD.** "N2: глобальные инварианты как first-class: `invariants:
  List<Invariant>` в IR, `Invariant(condition, outcome)` (например,
  `health <= 0 => death`). IR содержит инварианты; evaluator
  проверяет их после каждого шага; нарушение → `EngineEvent.Death` /
  `Outcome`; unit-тест: `health = -1` → death; редактор предупреждает
  о недостижимом инварианте."

  **Scope decisions.** Agreed before coding:

    - **A1 — only `Outcome.Death`.** A future `Ending(sceneId)` variant
      will land when a concrete need arrives (e.g. "invariant fires →
      jump to a specific ending scene, bypassing normal choice flow").
      `Scene.isEnding` today covers "the story ends here normally";
      `Outcome` covers "the story ends here because a rule fired".
      `Custom(name)` for Ф12/Ф13 — same rationale.
    - **B1 — semantics.** `Invariant(condition, outcome)` fires **when
      `condition` holds**. Same as the DoD notation `health <= 0 =>
    death`.
    - **C1 — fallback preserved.** `Engine.evaluateInvariants(adventure,
    state): Outcome?`:
        - `adventure.invariants.isEmpty()` → `if (Evaluator.isDead(state))
      Outcome.Death else null` (pre-Ф3.5 hardcoded `health <= 0`).
        - non-empty → first matching invariant, by list order.
          **A file with non-empty `invariants` replaces the fallback
          entirely** — explicit author rules are authoritative. Authors who
          want health-death plus another rule declare both.
    - **D1 — `StepResult.Success.outcome: Outcome?`, `isDead` computed.**
      `val isDead: Boolean get() = outcome == Outcome.Death`. Backward
      compatible with `if (success.isDead)` pattern; new code can
      `when(outcome)`. Two fields that could disagree (`isDead: Boolean`
        + `outcome`) were rejected — illegal state.
    - **E1 — `Evaluator.isDead` kept.** KDoc updated: "used as fallback
      inside `Engine.evaluateInvariants`". Tests from Ф1.5 that pin
      `isDead` behaviour remain (contract: "no invariants → health <= 0
      still kills").
    - **G1 — `Analyzer` scope limited to flag unreachability.**
      `LintKind.UnreachableInvariant` fires when an invariant references
      a Bool flag that is never established (not in `initialState.flags`,
      not in `initialState.stats.keys`, no `SetFlag` target in any
      scene/choice). Numeric unreachability (e.g. `health < -100` where
      `health` is `IntMin(0)`) — Ф3.6 (`Analyzer.deadConditions`), not
      now.
    - **H1 — reference sample declares `invariants` explicitly.**
      `test_adventure.json` gets `[{"condition": "health <= 0",
    "outcome": "death"}]` — semantically identical to the pre-Ф3.5
      fallback, but the format is now exercised.
    - **No version bump.** `invariants` is an optional list; empty
      defaults to fallback; old files unchanged. Same reasoning as
      `variables` (Ф2.8). `CURRENT_VERSION = 5`, with a "still 5 (Ф3.5)"
      entry in the version-history KDoc.

  Split into **3.5.1** (IR + DTO + parser) and **3.5.2** (runtime +
  reader + analyzer + docs).

  ### 3.5.1 — IR + DTO + parser

  **What changed (7 files, `:engine` only).**

    1. `engine/.../ir/Outcome.kt` (new) — `sealed class Outcome` with
       one variant `data object Death` (`@SerialName("death")`).
    2. `engine/.../ir/Invariant.kt` (new) — `data class
     Invariant(condition: Condition, outcome: Outcome)`.
    3. `engine/.../ir/Adventure.kt` — `val invariants: List<Invariant> =
     emptyList()` between `variables` and `tags`. Version-history
       KDoc gets a "Still `5` (Ф3.5)" entry.
    4. `engine/.../format/dto/Dto.kt` — new `InvariantDto(condition:
     String, outcome: String)` (flat, unlike the typed IR). Added
       `AdventureDto.invariants: List<InvariantDto> = emptyList()`.
    5. `engine/.../format/JsonToIr.kt` — `buildAdventure` maps
       `dto.invariants` via new private `buildInvariant(dto, index,
     errors)`. Condition string parsed by `ConditionParser` (errors
       re-anchored to `invariants[i].condition`); outcome string mapped
       (`"death"` → `Outcome.Death`; unknown → `ParseError.Structure` at
       `invariants[i].outcome`). Both stages must succeed or the
       invariant is skipped (parse fails overall).
    6. `engine/.../format/SchemaFingerprintTest.kt` —
       `expectedFlatFields["Adventure"]` gains `"invariants"`; new entry
       `"Invariant" to setOf("condition", "outcome")`; `assertFlat(...)`
       for `Invariant`; new `Outcome sealed variants match expected
     fingerprint` (`{"death" to emptySet()}`).
    7. `engine/.../format/JsonToIrTest.kt` — 4 new tests: `invariants
     parse into IR`, `invariants absent falls back to empty list`,
       `invalid invariant condition is ConditionError with path`,
       `unknown invariant outcome is Structure error`.

  `test_adventure.json` — added `"invariants": [{"condition":
  "health <= 0", "outcome": "death"}]` after `death_scene`.
  `updateGolden` — `golden_ir.json` ~22,400 chars.

  ### 3.5.2 — Runtime + reader + analyzer + docs

  **What changed (7 files: 5 replacements + 2 point edits + tests).**

    1. `engine/.../StepResult.kt` — `Success.outcome: Outcome? = null`
       added; `isDead` becomes computed (`outcome == Outcome.Death`).
       KDoc updated: "Death is not an error"; "outcome reflects
       choice effects only — scene effects are reader's job".
    2. `engine/.../Engine.kt` — new public
       `evaluateInvariants(adventure, state): Outcome?`. Fallback when
       `invariants.isEmpty()`; otherwise first matching rule. `step`
       passes `outcome = evaluateInvariants(...)` in `Success`.
    3. `engine/.../Evaluator.kt` — `isDead` KDoc rewritten: "now used
       **only** as fallback inside `Engine.evaluateInvariants`; new code
       should call `evaluateInvariants`".
    4. `engine/.../analyze/LintIssue.kt` — new
       `LintKind.UnreachableInvariant(reason: String)`. KDoc explains:
       subset of Ф3.6's broader `deadConditions` (flag-only today);
       `reason` field for future numeric causes.
    5. `engine/.../analyze/Analyzer.kt` — `lint()` now calls
       `lintUnreachableInvariants`; new private function shares
       `collectHasFlags` helper with `lintConditionOnUnestablishedFlag`.
       `sceneId = null` for invariant issues (not tied to a scene).
    6. `app/src/main/java/com/myapp/adventure/ui/reader/
     AdventureReaderFragment.kt` — `checkDeath()` rewritten to use
       `Engine.evaluateInvariants(adventure, PlayerState(flags, stats))`;
       `outcome == Outcome.Death` sets `isDead = true`. Behaviour for
       pre-Ф3.5 files identical (fallback).
    7. `docs/engine-conditions.md` — new §Global invariants (semantics,
       fallback contract, "why a list", limits).
    8. Tests:
        - `EngineStepTest` — 1 renamed (`choice effects kill player -
       isDead true` → `...via fallback`, asserts `outcome ==
       Outcome.Death`), 2 new (`explicit invariant fires - death`,
          `explicit invariants replace fallback - no death when health
          zero but no matching invariant`).
        - `AnalyzerTest` — 2 new (`lint reports invariant on
       unestablished flag`, `lint does not report invariant on
       established flag`).
        - `RealAdventureTest` — 1 new (`invariants has health rule`,
          pins format + `CompareOp.LE`).

  **Compiler-driven fixes during 3.5.2.**

    - `EngineStepTest.kt:426` — `Unresolved reference 'assertFalse'`.
      My prior batch used `assertEquals(false, ...)` in that file
      instead of `assertFalse`; I assumed the import was there and it
      wasn't. Fixed: `import kotlin.test.assertFalse`.
    - `StoryValidator.kt:200` — `'when' expression must be exhaustive.
    Add the 'is UnreachableInvariant' branch`. **`StoryValidator` in
      `:app` has a `when(lintIssue.kind)` for Russian UI titles**;
      adding a `LintKind` variant broke it. This is the intended
      "compiler makes you look at it" behaviour of sealed classes —
      documented in `LintKind` KDoc as a feature. Fixed: added `is
    LintKind.UnreachableInvariant -> "Инвариант не сработает"` to
      `titleFor(LintIssue)`. Same rule will apply to any future
      `LintKind` / `TypeError` / `CompileError` addition — see
      "Compiler consumers of sealed types" note below.

  **Compiler consumers of sealed types (reference for future
  batches).** When adding a variant to one of these `:engine` sealed
  types, **grep `:app` first** and update every `when` in the same
  batch — otherwise `:app:assembleDebug` fails:

  | Sealed class | Consumers in `:app` that `when`-exhaust |
    |---|---|
  | `LintKind` | `StoryValidator.titleFor(LintIssue)` |
  | `TypeError` | `StoryValidator.titleFor(TypeError)` |
  | `CompileError` | `StoryValidator.titleFor(CompileError)` |
  | `Condition` | — (all reads via `Evaluator`) |
  | `Effect` | — (all writes via `EffectsApplier`) |
  | `Outcome` | — (reader compares `== Outcome.Death`, no `when`) |
  | `ParseError` | — (`:app` groups via `is` checks) |
  | `VarType` | — (`EditorModels.toVarType` constructs, not matches) |

  **Known unrelated warning (not from Ф3.5).**
  `FontManager.kt:99` — `'field scaledDensity: Float' is deprecated`.
  Pre-existing since Ф1; out of scope for Ф3.5. **Tracked as a known
  debt for Ф6/U7 (Unicode/deprecations)** — replacement needs
  `displayMetrics.density * configuration.fontScale` (or
  `applyDimension`), but the exact approach depends on min SDK and
  Android version semantics. Not a regression; `:app:assembleDebug`
  succeeds with 1 warning (same as before Ф3.5).

  **User benefit.** (a) The author can express **any** death rule,
  not just `health <= 0`: poison timers, stamina limits, "died of
  fright when `has_seen_ghost && sanity < 10`". All modelled by the
  same `Invariant(condition, outcome)`. (b) The reader's death screen
  becomes a **consequence** of the rules, not a hardcoded check — the
  path from "player entered a violated state" to "death screen shown"
  is now one uniform `Engine.evaluateInvariants`. (c) Future phases
  (Ф12 RNG, Ф13 combat, Ф15 external outcomes) plug into the same
  mechanism: a new `Outcome` variant, not a new hardcoded check.
  (d) Author-facing analyzer catches the most common authoring
  mistake: `has_helth` typo in an invariant produces
  `UnreachableInvariant` warning with a concrete message ("references
  flag(s) that are never established"), instead of silent dead code.
  (e) `StepResult.Success.isDead` is preserved as a fast-path —
  no reader regression; `outcome` is the new expressive field for
  future UI.

  **What was NOT done (deliberately).**

    - **`Outcome.Ending` / `Outcome.Custom`** — see scope decision A1.
    - **Numeric unreachability in invariants** — Ф3.6
      (`Analyzer.deadConditions`): `health < -100` where `health` is
      `IntMin(0)` requires bounds reasoning; flag-only check is
      sufficient to catch the common typo class.
    - **UI for `Reason`** (invariant reason text in the death screen) —
      the `LintKind` message is author-facing; player-facing reason
      display for **invariants** is out of scope (N4/Ф4 covers
      disabled choices, not death reasons). `Outcome.Death` today
      ignores the reason entirely — a future `Outcome.Custom(message)`
      variant will carry player-facing data.
    - **`docs/engine-invariants.md`** — small enough to live in
      `engine-conditions.md` §Global invariants.
    - **Replacing `Evaluator.isDead`** — kept as fallback helper,
      KDoc clarified; deletion would break backward-compat tests and
      offer no benefit.

  **Verified.** `./gradlew :engine:updateGolden` → `golden_ir.json`
  ~22,400 chars (adds one `invariants` block in `Adventure`).
  `./gradlew :engine:test --rerun-tasks` → **296/296 PASSED** (291
  from Ф3.5.1 + 5 new: 3 in `EngineStepTest`, 2 in `AnalyzerTest`,
  1 in `RealAdventureTest`, minus 1 renamed). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 1 unrelated warning
  (`FontManager.scaledDensity`, pre-existing). Smoke (phone):

    - A. `test_adventure.json` → `start` → `health_test` → «Погибнуть»
      → death screen with "↩ Один шаг назад" / "🏠 Начать приключение
      заново". ✅
    - B. Four «Получить урон (-30)» in a row → death on 4th tap. ✅
    - C. Editor «Проверить» on `test_adventure.json` → «Проблем не
      найдено» (invariant `health <= 0`, `health` in `initialState.stats`
      → established). ✅
    - D. (negative) `"health <= 0"` replaced with `"has_helth"` →
      editor reports «Предупреждений: 1 • Invariant #0 references
      flag(s) that are never established…». Reverted. ✅

  **Gate Ф3 — status after 3.5:**

  | Item | Status |
    |---|---|
  | Single evaluator for all conditions | ✅ Ф3.4 |
  | `isConditionMet` / `extractHasFlagName` removed | ✅ Ф2.6 + Ф3.4 |
  | `&&`, `\|\|`, `!`, parentheses, quantitative | ✅ Ф3.2 |
  | Global invariants as first-class IR | ✅ Ф3.5 |
  | Analyzer detects unreachable conditions | ⏳ Ф3.6 (flag-часть в Ф3.5; numeric — Ф3.6) |
  | Sandbox criterion (no reflection/IO in `evaluate`) | ⏳ Ф3.7 |

  **Next — Ф3.6.** `Analyzer.deadConditions`: unreachable / dead
  conditions, including the numeric-reachability half that Ф3.5
  deliberately deferred. Uses `computeReachable` (Ф1.6.1) and adds:
    - `LintKind.DeadCondition(condition, reason)` — an invariant or a
      choice condition whose `Compare` / `InRange` bounds cannot be
      satisfied given the declared `VarType` (e.g. `health > 100` on
      `IntRange(0, 100)`, `health < -100` on `IntMin(0)`).
    - `LintKind.ContradictoryCondition(condition)` — `has_x &&
    !has_x` (a small boolean tautology check, no solver — just pattern
      on the AST).
    - `StoryValidator.titleFor(LintKind)` needs two more Russian titles
      (compiler will force it).

---

- **2026-11-13 · Ф3.6 — dead conditions (`Analyzer.deadConditions`)**

  **DoD.** "N2: анализ недостижимых условий: `Analyzer.deadConditions(ir)`;
  анализатор находит условия, которые не могут быть true ни в одном
  достижимом состоянии (пересечение с `computeReachable`); отчёт в UI
  редактора; тест: `has_sword && !has_sword` → предупреждение."

  **DoD reconciliation.** The DoD mentions "пересечение с
  `computeReachable`" — i.e. path-sensitive reachability. **Not
  implemented in Ф3.6** (decision A1). Path-sensitive deadness requires
  flow analysis (intersection of "where is this flag set" × "where is
  the condition checked" × "is that path reachable"), which is close to
  full dataflow. The DoD example (`has_sword && !has_sword`) is a
  **boolean tautology** — statically provable from the AST alone. Ф3.6
  detects the two most-common classes (boolean contradiction, numeric
  out-of-bounds); path-sensitive analysis is a future
  `Analyzer.deadConditions`-advanced entry, not a blocker for the
  current phase gate.

  Three classes of "dead condition" were evaluated:

    - **Boolean tautology** (`A && !A`) — detected. ✅
    - **Numeric out-of-bounds** (`health > 100` on `IntRange(0, 100)`) —
      detected. ✅
    - **Path-sensitive** (`has_x` set only in an unreachable scene) —
      deferred. The **existential** half (`has_x` never set anywhere) is
      already covered by `ConditionOnUnestablishedFlag` (Ф1.6.2.3).

  **Scope decisions (agreed before coding).**

    - **A1** — classes 1 + 2 only. Path-sensitive → roadmap.
    - **B1** — one `LintKind.DeadCondition(condition, reason)`, not
      three sub-variants. `reason` distinguishes cause ("contains both
      `X` and its negation", "requires `health` in `101..` but `health`
      is `IntRange(0..100)`"). Adding a new detection sub-type does not
      add a new `LintKind`.
    - **C1** — public `Analyzer.deadConditions(compiled): List<LintIssue>`,
      parallel to `Analyzer.reachable` / `Analyzer.lint`. `lint()`
      invokes it (single integration point).
    - **D1** — applies to both `Choice.condition` and `Invariant.condition`.
    - **E1** — boolean tautology by flattening the `And`-tree: positives
      (non-`Not` conjuncts, including nested `And` nodes) vs negatives
      (inner of every `Not` conjunct). Intersection → contradiction.
      `Or`, `Not(Or)`, etc. treated as opaque; no SAT solving.
    - **F1** — numeric bounds: `IntRange(min, max)`, `IntMin(min)` →
      `[min, ∞)`, `ItemCount` → `[0, ∞)`. `Integer` skipped (no bounds).
      For each variable in the `And`-tree, collect `Compare` / `InRange`
      constraints, reduce to `(lower, upper)`, intersect with the
      declared type. Empty intersection → dead.
    - **G1** — combined-numeric contradictions (`health >= 50 && health
    <= 30`) fall out of the same interval reduction — no extra code.
    - **H1** — `reason` is **English** (matches `TypeError.message`,
      `CompileError.message`); `StoryValidator.titleFor` maps the issue
      kind to a Russian title, `message` embeds `reason` verbatim. Same
      separation as everywhere else.
    - **I1** — single batch. `:engine`-only + one line in `:app`
      (`StoryValidator.titleFor`).

  **What changed (5 files).**

    1. `engine/.../analyze/LintIssue.kt` — new `LintKind.DeadCondition
     (condition: Condition, reason: String)`. KDoc covers:
       three sub-detections; `WARNING` severity; what is **not**
       detected (Or-tautologies, cross-variable arithmetic,
       path-sensitive, `Integer`); relationship to
       `ConditionOnUnestablishedFlag` (existential half already
       covered). Import `Condition`.
    2. `engine/.../analyze/Analyzer.kt` — new
       `public fun deadConditions(compiled): List<LintIssue>` + 5
       private helpers (`flattenAndNodes`, `collectNotInners`,
       `checkBooleanTautology`, `collectNumericConstraints`,
       `checkNumericBounds`, `formatInterval`, `NumericConstraints`
       data class). `lint()` now calls `deadConditions`. Imports:
       `VarType`, `VarType.displayName`, `ConditionWriter`. Walks
       every choice + every invariant; result sorted by
       `(sceneId == null, sceneId.value, message)` — same convention
       as `lint`.
    3. `app/src/main/java/com/myapp/adventure/utils/StoryValidator.kt`
       — `titleFor(LintKind.DeadCondition) -> "Недостижимое условие"`.
       Compiler forced this (sealed `when` in `StoryValidator`), same
       pattern as `UnreachableInvariant` in Ф3.5.
    4. `engine/.../analyze/AnalyzerTest.kt` — 10 new tests:
        - Boolean: contradictory flag (DoD example), nested
          contradiction, live (no contradiction).
        - Numeric: `IntRange` upper bound (`health > 100`), `IntMin`
          lower bound (`health < -100`), combined contradiction
          (`health >= 50 && health <= 30`), live (`30..50`),
          `Integer` skipped.
        - Invariant: dead condition in `invariants` (no sceneId).
          Helper `adventureWithLintSetup` gains `variables` parameter.
    5. `docs/engine-conditions.md` — new §Dead conditions: what is
       detected (3 classes), what is not (4 deferred), where it
       applies, severity, worked example with the editor's Russian
       dialog. Precedes §"What is not supported (`VarRef`)".

  **No changes:** `test_adventure.json`, `golden_ir.json`,
  `JsonToIr`, `Engine`, `Evaluator`, `StepResult`, `Condition`,
  `Outcome`, `Invariant` — the check is purely additive on the
  analyzer side.

  **Compiler-driven fix.** `StoryValidator.kt:200` (once) — see
  file 3. Same rule as Ф3.5: adding a `LintKind` variant breaks
  `:app:compileDebugKotlin` because `StoryValidator.titleFor(LintKind)`
  is exhaustive. Recorded as "compiler consumers of sealed types"
  table in Ф3.5 record; Ф3.6 adds one line, the table does not
  grow (same consumer, new branch).

  **User benefit.** (a) The two most common authoring bugs are caught
  before the reader ever opens the file:
    - `has_sword && !has_sword` (contradiction) — the choice could
      **never** be visible; today the author sees "why is the button
      always hidden?".
    - `health > 100` on `IntRange(0, 100)` — the invariant / choice
      is dead code; today the author sees "why doesn't the death rule
      fire?".
      (b) The warning lands in the editor's "Проверить" dialog **now** —
      no new UI, no new plumbing (Ф2.6 `StoryValidator` bridge already
      routes `LintIssue` into the dialog). (c) The pattern extends
      cleanly: `LintKind.DeadCondition.reason` is a string, so a future
      `Or`-tautology check or path-sensitive deadness adds a new `reason`
      branch, **not** a new `LintKind` — one title in
      `StoryValidator.titleFor` covers the whole family.

  **What was NOT done (deliberately).**

    - **Path-sensitive deadness** — decision A1. Requires dataflow;
      roadmap "Ф3.6-advanced".
    - **`Or`-tautologies** — `!(a || b) && a` needs normalization of
      `Or` into `And` of negations. Deferred; treated as opaque.
    - **Cross-variable arithmetic** — `gold + health > 1000`.
      `Condition` has no arithmetic today (Ф3.2 deliberately kept the
      grammar to `Compare` on a single variable). When arithmetic is
      added (future), the analyzer will need extension.
    - **Numeric deadness on `Integer`** — no declared bounds, any
      `Int` representable, false-negative is fine (conservative
      analyzer).
    - **`Not(Compare(...))` constraints** — `!(health >= 50)` is not
      extracted as `health < 50`; the interval collector skips `Not`.
      Could be added cheaply (`Not` of `Compare` → opposite op), but
      is not required for any DoD example. Deferred.

  **Verified.** `./gradlew :engine:test --rerun-tasks` →
  **306/306 PASSED** (296 from Ф3.5 + 10 new in `AnalyzerTest`).
  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, 1 unrelated
  warning (`FontManager.scaledDensity`, pre-existing). Smoke
  (phone): open `test_adventure.json` in editor → «Проверить» →
  «Проблем не найдено» (sample has no dead conditions). Added a
  manual `"condition": "has_initial_flag && !has_initial_flag"` to
  `conditions_advanced_test` → «Проверить» → «Предупреждений: 1 •
  Недостижимое условие» with the exact message. Reverted. ✅

  **Gate Ф3 — status after 3.6:**

  | Item | Status |
    |---|---|
  | Single evaluator for all conditions | ✅ Ф3.4 |
  | `isConditionMet` / `extractHasFlagName` removed | ✅ Ф2.6 + Ф3.4 |
  | `&&`, `\|\|`, `!`, parentheses, quantitative | ✅ Ф3.2 |
  | Global invariants as first-class IR | ✅ Ф3.5 |
  | Analyzer detects unreachable conditions | ✅ Ф3.5 (flag-side) + Ф3.6 (boolean + numeric) |
  | Sandbox criterion (no reflection/IO in `:engine` main) | ⏳ Ф3.7 |

  Five of six gate items closed. Ф3.7 is the last, then the phase
  gate closes.

  **Next — Ф3.7.** Sandbox criterion (задел под Q1): `evaluate()` /
  `:engine` main has no access to reflection, file IO, network,
  process execution, wall-clock randomness. Static test (Kotlin
  unit test, no ArchUnit — see Я1 in plan) verifies via import
  scanning. Not full Q1 (HTML escaping — Ф10); but sandbox is ready.

---

- **2026-11-13 · Ф3.7 — sandbox criterion (static test)**

  **DoD.** "Sandbox-evaluator (задел под Q1): без доступа к
  рефлексии/IO/DOM, только `State` + `decls`. `evaluate()` в
  `:engine` не имеет `java.lang.reflect`, `System.*`, файлового IO;
  статический тест (grep или ArchUnit) подтверждает; это **не**
  полный Q1 (экранирование — Ф10), но sandbox готов."

  **Scope decisions (agreed before coding).**

    - **A1 — Kotlin unit test, not a CI bash script, not ArchUnit.**
      A test in `:engine/src/test/kotlin/com/myapp/adventure/engine/SandboxTest.kt`
      runs locally with `./gradlew :engine:test` — the same command a
      contributor runs anyway. A CI-only bash check would be invisible
      until push; ArchUnit adds a dependency for a 100-line scan.
    - **B1 — both import patterns and identifier patterns.** Import-only
      would miss `System.exit` / `Runtime.getRuntime` /
      `ProcessBuilder`, all of which live in `java.lang` and are
      auto-imported. Identifier patterns are applied to non-comment
      lines (see KDoc for the "comments are approximated" limitation —
      documented, not a bug).
    - **C1 — only `:engine/src/main/kotlin`.** Test sources use
      `File`, `Random`, `System.currentTimeMillis` legitimately
      (reading source files, generating data, measuring); the sandbox
      criterion is about production code.
    - **D1 — `System.currentTimeMillis` / `System.nanoTime`
      forbidden.** The engine must be pure; tests must be
      deterministic; save/restore (`Ф5`) must behave identically
      across runs; future JS runtime (`E1.base`) has no `System`.
      Future features needing time (e.g. "night-only invariants")
      will inject a `Clock`, same pattern as future `RandomSource`
      for `Ф12`.
    - **E1 — `System.getenv` / `System.getProperties` forbidden.**
      The engine must not depend on the host environment — tests
      would be non-deterministic. Injection, not global read.
    - **F1 — `java.util.UUID` allowed.** Uses `SecureRandom`
      internally, but UUIDs are not used in `evaluate` /
      `Engine.step` today, and forbidding `UUID` without forbidding
      `SecureRandom` (which is in `java.security`, ubiquitous,
      never imported) would be inconsistent. If `UUID.randomUUID()`
      appears inside `Evaluator`/`Engine` later, code review will
      catch it, and the pattern list can be extended.
    - **G1 — `java.time.*` allowed, with `TODO`.** `LocalDate`,
      `Duration`, `Instant` as **value types** are deterministic.
      `LocalDate.now()` / `Instant.now()` / etc. call
      `System.currentTimeMillis()` indirectly; today `:engine` uses
      none of the `*.now()` variants, so the sandbox is clean.
      `TODO` in `SandboxTest` KDoc: when time enters the engine,
      add `LocalDate.now` etc. to `forbiddenIdentifiers` and inject
      a `Clock`.
    - **H1 — `kotlinx.serialization` allowed.** Reflection inside the
      library is a dependency concern, not ours; the `@Serializable`
      compiler plugin generates code without runtime reflection for
      all common cases. A JS-runtime build (`E1.base`) that lacks
      reflection will surface any library-level reflection as a
      build/test failure there, not here.
    - **I1 — no `docs/security.md`.** That file is `Ф10.9` (X3.base
      docs). `documentation-rules.md §7` — do not document what has
      not stabilized. The test's KDoc is the human-readable side
      for now.
    - **J1 — single batch.** One new file, one test. Nothing to split.

  **What changed (1 file).**

  `engine/src/test/kotlin/com/myapp/adventure/engine/SandboxTest.kt`
  (new, ~290 lines with KDoc). Two tests:

    1. `main sources have no forbidden imports or identifiers` —
       walks every `.kt` under `engine/src/main/kotlin`, line-scans:
        - `import`-lines against 7 forbidden patterns (reflection,
          java.io, java.nio.file, java.net, android, kotlin.random.Random,
          java.util.Random);
        - non-comment lines against 7 forbidden identifiers
          (`System.exit`, `System.getenv`, `System.getProperties`,
          `System.currentTimeMillis`, `System.nanoTime`,
          `Runtime.getRuntime`, `ProcessBuilder`).
          Collects all violations with file:line before failing — one
          `:engine:test` run shows every offender, not just the first.
    2. `main sources exist and are discoverable` — sanity check
       against a silently-empty scan: source root is discoverable,
       ≥ 10 `.kt` files (currently ~20), `Evaluator.kt` present at
       the expected package root.

  `locateMainSources()` — two candidates (`src/main/kotlin` for
  Gradle's module working directory; `engine/src/main/kotlin` for
  IDE "Run" from repo root), validated by
  `com/myapp/adventure/engine` subdirectory presence.

  **Compiler-driven bug during 3.7.** First run:
  `SandboxTest.kt:289 — Unclosed comment`. Root cause: the KDoc
  of the test documented the line-scan approach with the literal
  characters for a block-comment opener inside a `/** ... */`.
  Kotlin **nests** block comments: `/**` opens one, a literal
  `/*` inside opens a **second**, and the final `*/` closes only
  the inner — the outer runs to EOF. Fixed by replacing the
  literal characters with the phrase "a block-comment opener".
  Ironic: the bug was in the comment **documenting** the
  limitation "comments are approximated, not parsed". Rule now
  recorded in `documentation-rules.md` (see next item — the
  "Documenting comment delimiters" addition, part of the
  documentation discipline batch).

  **User benefit.** (a) The engine's purity contract is
  **machine-checked**, not aspirational: `./gradlew :engine:test`
  fails if any `:engine` main source ever imports IO, network,
  reflection, or reads the wall clock. (b) The criterion is
  **documented** in the test KDoc, with per-pattern rationale and
  explicit "what is allowed and why" (UUID, java.time, kotlinx.
  serialization) — a future contributor knows the intent, not just
  the rule. (c) The `TODO` for `java.time.*.now()` is where a
  future time-dependent feature will land — when `:engine` starts
  using time, the pattern list needs one line added, not a
  discussion. (d) `E1.base` (JS runtime) inherits the same
  property: everything that runs in the engine today is
  representable in a JS sandbox (no `System`, no `File`, no
  reflection) — the DoD for `E1.base` (`Ф8.9`) becomes a matter of
  checking the JS code, not redesigning the engine.

  **What was NOT done (deliberately).**

    - **`docs/security.md`** — `Ф10.9`.
    - **AST-based analysis** (parsing with `kotlin-compiler-embeddable`)
      — line-based scan is sufficient today. Upgrade path documented
      in the test KDoc: switch to AST when false positives appear.
    - **`LocalDate.now` / `Instant.now` etc. in the forbidden list**
      — `TODO`, not a current rule, because `:engine` does not use
      them today and pinning them would freeze design decisions not
      yet made (when time enters the engine, the mechanism will be
      `Clock` injection, not `LocalDate.now` in `evaluate`).
    - **Dependency-level reflection** — out of scope (see `H1`).
    - **HTML escaping / XSS** — `Ф10.2` (Q1 proper, `:app`/exporter
      concern).

  **Verified.** `./gradlew :engine:test --rerun-tasks` →
  **308/308 PASSED** (306 from `Ф3.6` + 2 new in `SandboxTest`).
  `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (unchanged,
  `:app` was not touched). No new warnings. Manual negative
  verification (documented, not kept in the tree): a temporary
  `import java.io.File` in an `:engine` main file produced
  `Sandbox violated: ... Violations (1): • ui/Foo.kt:5 — forbidden
  import (java.io): import java.io.File`; reverted. First-run
  self-check (`main sources exist and are discoverable`) passes
  against the current ~20 `.kt` files.

  **Gate Ф3 — closed (see the next entry).**

  **Next — Ф4.1.** `ChoiceTemplate` in IR: id, typed parameters,
  body (visibility / availability conditions, effects, target),
  no scene binding. Followed by `Ф4.2` (instantiation) — see plan
  in the next message.

---

- **2026-11-13 · Gate Ф3 — closed**

  **DoD (checklist-tracker, Ф3 gate):** all six items. Status of
  each:

  | Item | Status | Where |
    |---|---|---|
  | Любая проверка условия в приложении проходит через **один** evaluator (grep подтверждает) | ✅ | `Ф3.4` — `isConditionMet` удалён; `conditionHolds` — тонкий враппер над `Evaluator.evaluate` |
  | `isConditionMet` и `extractHasFlagName` удалены | ✅ | `Ф2.6` (`extractHasFlagName`) + `Ф3.4` (`isConditionMet`). Grep `:app` = 0 для обоих |
  | AND/OR, скобки, количественные сравнения работают и покрыты тестами | ✅ | `Ф3.2` — recursive-descent parser; `ConditionParserTest` ~30 тестов; `ConditionWriterTest` round-trip |
  | Глобальные инварианты — first-class в IR, проверяются после каждого шага | ✅ | `Ф3.5` — `Invariant(condition, outcome)`, `Outcome.Death`, `Engine.evaluateInvariants`, reader `checkDeath()` через invariants |
  | Анализатор умеет находить недостижимые условия | ✅ | `Ф3.5` (flag-side `UnreachableInvariant`) + `Ф3.6` (boolean tautology + numeric bounds `DeadCondition`) |
  | Sandbox-критерий: `evaluate()` не имеет доступа к reflection/IO/DOM | ✅ | `Ф3.7` — `SandboxTest` сканирует `:engine/src/main/kotlin` на 7 forbidden import-паттернов + 7 forbidden identifiers |

  **Six of six. Phase Ф3 accepted.**

  **What this phase delivered, in one paragraph.**
  Before Ф3, conditions were checked in two places (`:app` reader
  and a `catch → true` fallback in `:app`), there was no global
  invariant mechanism (just a hardcoded `health <= 0`), and the
  parser supported only the simplest `has_X` / `X op N` syntax.
  After Ф3: one evaluator (`Evaluator.evaluateWithReason`), one AST
  (eight `Condition` variants, all handled exhaustively in every
  `:engine` consumer), one grammar (`has_X`, `!has_X`, `X op N`,
  `X in A..B`, `true`/`false`, `&&`, `||`, `!`, parentheses — with
  canonical round-trip via `ConditionWriter`), global invariants as
  first-class IR (`Invariant` + `Outcome`), and three analyzer
  checks (`ConditionOnUnestablishedFlag`, `UnreachableInvariant`,
  `DeadCondition`) — plus a machine-checked purity criterion
  (`SandboxTest`). The reader has no local condition logic. The
  editor has no local condition parsing (`StoryValidator` is a
  bridge to `:engine`). The engine has no access to reflection,
  IO, network, or wall clock.

  **Phase-spanning decisions recorded during Ф3** (do not re-debate
  in Ф4+):

    - **One evaluator, two entry points.** `evaluate(condition, state):
    Boolean` (fast path for `Engine.step` / `Analyzer`) and
      `evaluateWithReason(condition, state): EvaluationResult`
      (structured reason for N4 / Ф4). Both funnel through the same
      `when(condition)` — no second implementation.
    - **`Reason` is structured data, not formatted strings.**
      Formatting is a `:app` / `strings.xml` / Ф7 concern (i18n).
      `:engine` never builds a user-facing sentence.
    - **`VarRef` AST exists but is not produced by the parser yet.**
      Deferred from `Ф3.2` (see record there); tracked as a roadmap
      item ("VarRef + `has_X` deprecation"). Adding the parser branch
      later is ~30 lines; the AST consumer work (`Evaluator`,
      `TypeChecker`, `TypeInference`, `Analyzer`, `ConditionWriter`)
      is already done.
    - **Explicit replaces implicit.** Non-empty `Adventure.invariants`
      replaces the `health <= 0` fallback — the author's rules are
      authoritative. Same contract for `Adventure.variables`
      (non-empty → strict typecheck; empty → fallback to `tags` +
      `initialState`).
    - **`has_X` is the canonical Bool syntax.** No bare `sword` /
      `health` today. `has_` prefix disambiguation is a future
      `VarRef` decision (`VarRef + has_X deprecation`, roadmap).
    - **`LintKind` / `TypeError` / `CompileError` sealed → `:app`
      `when`-consumers must be updated in the same batch.** Compiler
      forces this; table in `Ф3.5` record. Ф3.6 added one line
      (`DeadCondition`); the table does not grow.

  **What is not closed by this phase (by design — these are Ф4+).**

    - **N3** — reusable `ChoiceTemplate` (Ф4.1 + Ф4.2 + Ф4.3).
    - **N4** — disabled choice with reason (Ф4.4). The `Reason`
      structure landed in Ф3.3; the UI display is `Ф3.8` / N4 / Ф4.
    - **N5** — explicit merge node (Ф4.5 + Ф4.6).
    - **N6.base** — subroutines (Ф4.7 + Ф4.8).
    - **`Or`-tautologies, cross-variable arithmetic, path-sensitive
      deadness** in `Analyzer.deadConditions` — see `Ф3.6` record
      ("What was NOT done"). Roadmap item "Ф3.6-advanced".
    - **`VarRef` in the parser** — roadmap item, see `Ф3.2` record.
    - **Q1 proper (HTML escaping, XSS)** — Ф10.2.
    - **Full threat model doc** — Ф10.9 (`docs/security.md`).

  **What this file covers.**

  `progress-log-3.md` (this file) begins at **Ф2.8.1** (2026-11-12)
  and ends here, at **Gate Ф3 (2026-11-13)**. It covers:

    - **Ф2.8** — `JsonToIr` reads `variables` from JSON (2.8.1),
      `inferTypes` (2.8.2);
    - **Ф2.9** — golden auto-closure (2.9);
    - **Ф2.10** — `docs/engine-types.md` (2.10);
    - **Ф3.1** — AST additions + `ConditionWriter` (3.1.1 + 3.1.2);
    - **Ф3.2** — condition grammar (`&&`, `||`, `!`, `in`,
      `Literal`) (3.2.1 + 3.2.2);
    - **Ф3.3** — `Evaluator.evaluateWithReason` + `Reason`;
    - **Ф3.4** — `isConditionMet` removal (3.4 + fix + fix2);
    - **Ф3.5** — global invariants (3.5.1 + 3.5.2);
    - **Ф3.6** — dead conditions;
    - **Ф3.7** — sandbox criterion.

  **This file is now sealed.** New entries from `Ф4.1` onward go
  into [`progress-log-4.md`](./progress-log-4.md) — see the
  one-phase-per-file rule in
  [`../documentation-rules.md §2`](../documentation-rules.md#2-file-format-and-naming).

  **Next — Ф4.1** (`ChoiceTemplate` in IR). See
  `progress-log-4.md`.