# Progress log — Ф4 onward

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Continuation from [`progress-log-3.md`](./progress-log-3.md) (Ф2.8–Ф3.7,
English). File structure follows the **one-phase-per-file** rule in
[`../documentation-rules.md §2`](../documentation-rules.md#2-file-format-and-naming):
`progress-log-4.md` covers **Ф4 (model of choices)** and only Ф4. When
the Ф4 gate closes, this file is sealed and `progress-log-5.md` starts
with Ф5.

**Phases in this file:**

- **Ф4 — Model of choices** (N3 ChoiceTemplate, N4 disabled+reason, N5
  merge, N6.base subroutines). All sub-steps 4.1–4.12.

## File boundary

Read this file **from the bottom** to see what is currently in progress.
Sub-step numbering follows `checklist-tracker.md`: `Ф4.1`, `Ф4.2`, …
`Ф4.12`, plus any `Ф4.X-fix` entries discovered during smoke.

## Entry format

```
- **YYYY-MM-DD · ФX.Y — short title**

  **DoD** *(translated from `checklist-tracker.md` §ФX.Y; original is
  Russian)*. "<English translation of the Russian DoD>"

  **What changed (N files).**

  1. `path/to/file.kt` — description.
  2. ...

  **Decisions.** Agreed-before-coding forks (D1–Dn), one line each.

  **User benefit.** What the author or reader can do now that they
  could not before.

  **What was NOT done.** Explicit deferrals, with the phase that will
  pick each one up.

  **Verified.** `./gradlew …` commands + result counts. Smoke
  scenarios (phone) with ✅ marks.

  **Next — ФX.Y+1.** What the next batch starts with.
```

---

*Ф4 entries begin below. The first will be `Ф4.1` — `ChoiceTemplate`
in IR.*

---

- **2026-11-13 · Ф4.1 — `ChoiceTemplate` in IR (AST-only)**

  **DoD** *(translated from `checklist-tracker.md` §Ф4.1; original is
  Russian)*. "N3: `ChoiceTemplate` in IR: id, typed parameters, body
  (visibility condition, availability condition, effects, target), no
  scene binding. Parameters are typed (`VarDecl`); unit test: a
  template with a parameter `itemId: ItemCount` is instantiated with
  different values."

  **DoD reconciliation — split.** The DoD bundles two things:
  (a) the `ChoiceTemplate` AST, (b) a unit test that instantiates a
  template with different parameter values. **Only (a) is in Ф4.1.**
  Instantiation (`applyTemplate(templateId, args) → Choice`) is
  **Ф4.2** by the checklist plan. Same shape as Ф3.1 (`Literal` /
  `VarRef` AST existed but the parser did not produce them until
  Ф3.2) and Ф3.5.1 (IR parsed, runtime integration in Ф3.5.2). The
  "instantiated with different values" half of the DoD is tracked
  under Ф4.2 (`test: different args → different conditions /
  effects`).

  **Scope decisions (agreed before coding).**

  - **A — `nextScene: SceneId` fixed in the template.** If Ф4.2
    needs parameterization ("return to the scene the choice was
    picked in"), it will add `targetParam: String?` as a second,
    backward-compatible field. Not speculating now.
  - **B — `visibilityCondition` + `availabilityCondition` both
    present as separate nullable fields.** Cost: one more field, one
    more test. Benefit: AST stable; Ф4.4 (N4 disabled-with-reason)
    only implements the UI behaviour, not the AST. The reader today
    splits neither (Ф3.4 `conditionHolds` uses only the single
    `Choice.condition`); Ф4.4 will split both.
  - **C — `parameters: List<VarDecl>`.** Same type as
    `Adventure.variables`' values, readable, serializable. Not
    `Map<String, VarType>` — order is meaningful for docs and
    duplicates must be checked (parser-level in Ф4.2 if desired).
  - **D — no placeholder substitution syntax in Ф4.1.** `text` is a
    plain string. `{itemId}` etc. is a Ф4.2 decision (once
    `applyTemplate` defines the semantics).
  - **E — `Adventure.choiceTemplates: List<ChoiceTemplate>`.**
    Not `Map` — the list form serializes cleanly to a JSON array,
    and duplicate-id check lives in `JsonToIr` (one pass over the
    whole list, single error). A `Map` would silently dedup, hiding
    the error.
  - **F — `Choice` is untouched in Ф4.1.** No `templateRef` field;
    `Choice` remains flat (`text`, `next_scene`, `condition`,
    `effects`). Instantiation / `TemplateRef` is Ф4.2.
  - **No version bump.** `choice_templates` optional, empty default,
    identity migration. Same reasoning as `variables` (Ф2.8) and
    `invariants` (Ф3.5). `CURRENT_VERSION = 5`, "Still 5 (Ф4.1)"
    entry in the version-history KDoc.

  **What changed (6 files + tests).**

  1. `engine/.../ir/ChoiceTemplate.kt` (new) — `data class
     ChoiceTemplate(id, parameters: List<VarDecl>, text,
     visibilityCondition: Condition?, availabilityCondition:
     Condition?, effects: List<Effect>, nextScene: SceneId)`.
     KDoc: status "AST-only in Ф4.1", semantics of each field,
     "not checked by TypeChecker today" (Ф4.2 extension),
     "no placeholder substitution in Ф4.1".
  2. `engine/.../ir/Adventure.kt` — new
     `@SerialName("choice_templates") choiceTemplates:
     List<ChoiceTemplate> = emptyList()` between `variables` and
     `invariants`. Version-history KDoc "Still 5 (Ф4.1)".
  3. `engine/.../format/dto/Dto.kt` — new
     `AdventureDto.choiceTemplates: List<ChoiceTemplateDto> =
     emptyList()`; new `ChoiceTemplateDto` and `VarDeclDto` classes
     (flat condition strings, three effect fields, `next_scene`).
  4. `engine/.../format/JsonToIr.kt` —
    - `buildAdventure` maps `dto.choiceTemplates` via
      `buildChoiceTemplate(dto, path, errors)` + a single
      duplicate-id check (`ParseError.Structure` if two templates
      share `id`);
    - `buildChoice` and `buildInvariant` refactored to call a
      shared `parseCondition(raw, path, errors)` helper (was
      15-line duplicated block in each; Ф4.1 adds two more callers
      via `ChoiceTemplate`, four copies would be worse than one);
    - `VariableDto.toVarType` → delegates to a shared
      `typeStringToVarType(type, min, max)` helper, also used by
      new `VarDeclDto.toVarType` — same permissive-default
      downgrade (unknown type → `Integer`, `int_min` /
      `int_range` without bounds → `Integer`) as Ф2.8.
  5. `engine/.../format/SchemaFingerprintTest.kt` —
     `expectedFlatFields["Adventure"]` gains `"choice_templates"`;
     new entries `"ChoiceTemplate" to setOf("id", "parameters",
     "text", "visibility_condition", "availability_condition",
     "effects", "next_scene")` and `"VarDeclDto" to setOf("name",
     "type", "min", "max")`; `assertFlat` for both.
  6. `engine/.../format/JsonToIrTest.kt` — 4 new tests:
     `choice_templates block parses` (id, typed parameters,
     `next_scene`), `choice template visibility and availability
     conditions parse` (both conditions parsed to AST),
     `choice_templates absent falls back to empty list`,
     `duplicate choice template id is an error`.

  `test_adventure.json` — **not touched**. Ф4.1 adds AST only; the
  reference sample does not gain templates yet. That lands in Ф4.2,
  when `Choice.templateRef` (or expand-at-parse in `ChoiceDto`)
  provides a consumer.

  **Compiler-driven fixes during Ф4.1.**

  1. `Dto.kt` lost `tags` from `AdventureDto`. Root cause: my "find"
     block in step 3a was two adjacent `val` lines, without the
     unchanged anchor line after the change. The natural off-by-one
     selection dropped `tags`. Reverted with one line: `val tags:
     List<TagDefinitionDto> = emptyList()` restored between
     `invariants` and `death_scene`. **Recorded as a discipline
     rule** (see "documentation discipline" below).
  2. `SchemaFingerprintTest.kt` lost `"VariableDto"` from
     `expectedFlatFields`. Same root cause: my "add after
     `"VariableDto" to setOf(...)`" instruction was expressed as
     "replace `"Gate" to setOf(...), "ChoiceTemplate" ...`" without
     including `"VariableDto"` as an anchor. Reverted with one line.

  **Documentation-discipline lessons (accumulated).** This batch was
  the fifth time my "find / replace" instructions caused an off-by-N
  problem. All five are captured for one consolidated update to
  `documentation-rules.md §2` **at the end of Ф4** (not per-batch;
  one rule file update, one record):

  - 4-backtick outer fence (from `progress-log-2.md` /
    `engine-conditions.md`).
  - No `.` or `..` in Kotlin backticked test names
    (`ConditionWriterTest`).
  - No `// ... (long test)` placeholders in "find" (`TypeCheckerTest`).
  - Anchor the "find" block with at least one unchanged line before
    and after the change (`Dto.kt` `tags`, `SchemaFingerprintTest`
    `"VariableDto"`).
  - No literal block-comment delimiters inside a KDoc/block comment
    (`SandboxTest`).

  **User benefit.** (a) The author can already **declare** templates
  in JSON (`"choice_templates": [...]`), and the parser stores them
  in `Adventure.choiceTemplates` — the infrastructure for "write it
  once, use it in many scenes" exists. (b) The AST is stable:
  changing `Choice` in Ф4.2 (adding `templateRef` or expand-at-parse
  in the DTO) will not touch `ChoiceTemplate`. (c) `visibilityCondition`
  / `availabilityCondition` are already separate fields — Ф4.4
  (N4 disabled-with-reason) is a UI change, not an AST change.
  (d) Shared helpers (`parseCondition`, `typeStringToVarType`)
  reduce the next steps: four call-sites for conditions, two for
  type-string downgrade, one entry point each. (e) `VarDeclDto` and
  `VariableDto` share `typeStringToVarType` — adding a type (e.g.
  `float` in some future) is a one-line change, not two.

  **What was NOT done (deliberately).**

  - **Instantiation** (`applyTemplate(templateId, args) → Choice`)
    — Ф4.2.
  - **`Choice.templateRef`** or expand-at-parse in `ChoiceDto` —
    Ф4.2. Ф4.1 does not touch `Choice` or `ChoiceDto`.
  - **Type-checking of templates** — `TypeChecker.checkChoiceTemplate`
    (parameters + global vars in scope) — Ф4.2.
  - **Duplicate parameter names within one template** — not checked
    in Ф4.1. Ф4.2 `TypeChecker` extension will catch it (ambiguous
    scope), or a `ParseError.Structure` can be added if it becomes a
    source of bugs.
  - **`ChoiceTemplate.visibilityCondition` / `availabilityCondition`
    consumers** — no reader / analyzer reads them yet (Ф4.4 for
    visibility, Ф4.4 / N4 for availability).
  - **`docs/engine-architecture.md` "Decision: expand vs TemplateRef"**
    record — Ф4.2 (per DoD Ф4.2).
  - **Placeholder substitution** in `ChoiceTemplate.text` — Ф4.2.

  **Verified.** `./gradlew :engine:test --rerun-tasks` → **317/317
  PASSED** (308 from Ф3.7 + 4 new in `JsonToIrTest` + 5 net from
  refactoring-driven test churn). `./gradlew :app:assembleDebug` →
  BUILD SUCCESSFUL, 1 unrelated warning
  (`FontManager.scaledDensity`, pre-existing since Ф1). No smoke
  required — Ф4.1 is AST-only, no `:app` consumer, no change to
  reader's or editor's behaviour.

  **Next — Ф4.2.** `applyTemplate(templateId, args) → Choice`;
  decide and record in `docs/engine-architecture.md`:
  **expand-at-parse vs `TemplateRef`**. That decision drives the
  rest of Ф4.2 — `ChoiceDto` shape, `TypeChecker` extension,
  `test_adventure.json` demo.