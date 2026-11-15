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

---

- **2026-11-14 · Ф4.2 — `applyTemplate` + `Choice.visibilityCondition` + `TemplateRef`**

  **DoD** *(translated from `checklist-tracker.md` §Ф4.2; original is Russian)*.
  "Instantiation: `applyTemplate(templateId, args) → Choice`; in the IR
  — either expanded `Choice` or `TemplateRef` + args (decide and
  record). One template applied in two scenes with different args;
  the result is unambiguous; test: different args → different
  conditions / effects; decision recorded in
  `docs/engine-architecture.md`."

  **Scope decisions (agreed before coding).**

    - **A1 — expansion over live `TemplateRef`.** `applyTemplate` returns
      a fully-expanded `Choice`; runtime / analyzer see only expanded
      content. `Choice.templateRef` is editor metadata (Ф4.3).
    - **B1 — exact match in AST name slots + `{paramName}` in text.**
      Text uses braces; AST slots use exact name equality
      (`Has("itemId")` → `Has("potion")` if `args[itemId] = "potion"`;
      `Has("has_itemId")` stays as-is — prefix composition is **NOT**
      substituted, by design).
    - **C1 — API-only in Ф4.2.** No `ChoiceDto.template_ref` JSON parsing
      yet; that lands in Ф4.3 with the editor UI. DoD satisfied by
      `TemplateApplierTest`.
    - **D2 — `TypeChecker.checkChoiceTemplate` deferred to Ф4.2.2.**
      Requires `TypeError.templateId: String?` across all 9 variants;
      large enough for a follow-up.
    - **E2 — `Choice.visibilityCondition` added now.** Without it,
      `ChoiceTemplate.visibilityCondition` is lost on expansion. Costs one
      field + fingerprint update; benefit: AST for N4 / Ф4.4 is in place.

  **What changed (7 files).**

    1. `engine/.../ir/TemplateRef.kt` (new) — `data class
     TemplateRef(templateId: String, args: Map<String, String> =
     emptyMap())` with `@SerialName("template_id")`. KDoc: editor
       metadata, not touched by runtime or analyzer.
    2. `engine/.../ir/Choice.kt` (replaced) — two new optional fields:
        - `visibilityCondition: Condition? = null`
          (`@SerialName("visibility_condition")`) — "show this choice?"
          (distinct from `condition` = "enable"). Populated by
          `applyTemplate`; consumed by reader in Ф4.4.
        - `templateRef: TemplateRef? = null`
          (`@SerialName("template_ref")`) — source metadata for the
          editor (Ф4.3).
    3. `engine/.../ir/TemplateApplier.kt` (new) — `fun
     applyTemplate(template, args: Map<String, String>, choiceId):
     Choice` + `fun Adventure.applyTemplate(templateId, args,
     choiceId): Choice?` (sugar: lookup + apply) + private helpers
       `substituteText`, `substituteName`, `substituteCondition`,
       `substituteEffect`. Validates args completeness; throws
       `IllegalArgumentException` on mismatch (developer-facing
       invariant; JSON parsing / editor build pre-validate in Ф4.3).
    4. `engine/.../ir/ChoiceTemplate.kt` (KDoc edits) — `@property text`
       mentions `{paramName}` substitution; `@property nextScene` defers
       parameterization to "later"; TypeChecker extension is
       "Ф4.2.2 (follow-up)".
    5. `engine/.../format/SchemaFingerprintTest.kt` (replaced) —
       `expectedFlatFields["Choice"]` gains `"visibility_condition"` /
       `"template_ref"`; new `"TemplateRef" to setOf("template_id",
     "args")`; `assertFlat("TemplateRef", ...)`.
    6. `docs/engine-architecture.md` — `## IR` updated (Adventure line
       gains `choiceTemplates` / `invariants`; Choice line gains
       `visibilityCondition` / `templateRef`; three new bullets for
       `ChoiceTemplate` / `Invariant` / `Outcome`). New section
       `## Choice templates (Ф4.2)` before `## Testing strategy`:
       expansion design (A1), substitution semantics (B1), editor
       tracking via `templateRef`, deferred items (Ф4.3 / Ф4.2.2 / Ф4.4).
    7. `engine/src/test/.../ir/TemplateApplierTest.kt` (new) — 17 tests:
       text / condition / visibilityCondition / effects / nested `And` /
       `InRange` / `Effect.Set` / non-parameter names / exact-match
       pinning (prefix composition **not** substituted) / `templateRef` /
       `nextScene` / args validation (missing, extra) / no-params /
       `Adventure.applyTemplate` (found, missing).

  **Compiler fix during Ф4.2.** First `SchemaFingerprintTest.kt`
  replace put `import com.myapp.adventure.engine.ir.TemplateRef` at
  line 18 (inside class body) → `<N>` syntax errors, "imports are
  only allowed in the beginning of file". Fixed by replacing the whole
  file with the import at the top, alongside other `ir.*` imports.

  **Test-design correction during Ф4.2.** Two `TemplateApplierTest`
  tests initially failed because they expected **prefix substitution**
  (`Has("has_itemId")` → `Has("has_potion")`). The code correctly
  implements **exact match only** (B1). Fixed:
    - `effects substituted`: reduced to a single `ModifyStat("itemId",
    -1)` effect (proves DoD "different args → different effects"
      without prefix).
    - `nested condition substituted`: `Has("other_flag")` (global) +
      `Compare("itemId", …)` (parameter) — shows recursion into nested
      conditions without prefix.
    - **New** `prefix composition is not substituted - exact match only
    (B1)` pins the design: `Has("has_itemId")` stays as-is. A future
      "improvement" to prefix substitution will fail this test, forcing
      an explicit decision + log update.

  **User benefit.** (a) `applyTemplate` is a ready-to-use API for the
  editor (Ф4.3) and any CLI tooling. (b) Different args produce
  different `text` / `condition` / `visibilityCondition` / `effects`
  (DoD pin: `availability condition substituted`,
  `effects substituted`). (c) Exact-match semantics are documented and
  pinned — no accidental "improvement" without a decision. (d)
  `Choice.visibilityCondition` stores the template's visibility
  condition; Ф4.4 / N4 is a UI change, not an AST change. (e)
  `Adventure.applyTemplate(templateId, …)` sugar: the editor calls by
  id, no manual lookup.

  **What was NOT done (deliberately).** JSON `template_ref` parsing
  in `ChoiceDto` — Ф4.3. `TypeChecker.checkChoiceTemplate` — Ф4.2.2.
  `test_adventure.json` demo — Ф4.11 (bundled with disabled / merge /
  subroutine). Editor UI — Ф4.3. `Choice.visibilityCondition` reader
  behaviour — Ф4.4 (N4). `Choice.templateRef` consumption — Ф4.3
  ("update all uses").

  **Verified.** `./gradlew :engine:test --rerun-tasks` → **333/333
  PASSED** (was 332 total with 2 failed before fixes; after
  test-expectation corrections + 1 new pinning test). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 1 unrelated warning
  (`FontManager.scaledDensity`, pre-existing since Ф1). No smoke
  required — Ф4.2 is `:engine`-only, no `:app` consumer.

  **Next — Ф4.3.** Editor UI for templates. Split proposed:
  **4.3.1** (data foundation: `EditorTemplate`, draft / save / load
  round-trip — this fixes a **current data-loss bug**: files with
  `choice_templates` lose them on save because `:app/data/Adventure`
  has no field for them and `buildAdventure` does not write them);
  **4.3.2** (per-choice "Use template…" button + template picker + args
  dialog); **4.3.3** ("update all uses" when a template is edited).

---

- **2026-11-14 · Ф4.3.1 — Template data foundation (round-trip fix)**

  **DoD context.** Ф4.3 ("editor of templates") is split into three
  sub-steps; this entry is the first. **4.3.1 is not the full DoD** —
  it closes the **data-loss bug** that a file with `choice_templates`
  loses them on save (`:app/data/Adventure` had no field, so the
  editor's Gson save dropped the block). UI (create/edit/apply) is
  4.3.2; "update all uses" is 4.3.3. Splitting is deliberate: the
  round-trip fix is a **one-sitting** change (7 files, no new
  fragments, no new layouts); the UI is 3× that volume.

  **Scope decisions (agreed before coding).**

    - **A — `EditorTemplate` as a new editor model.** Mirrors
      `:engine/ir/ChoiceTemplate`, but with editor vocabulary: raw-string
      conditions (`String`, like `EditorChoice.conditionTag`), effects as
      `EditorTag` (like `EditorScene.tags`), parameters as
      `EditorVarDecl` (reuse `VariablesAdapter` UI). Not reuse the IR
      model directly — the editor needs intermediate states (`min`
      entered, `max` not yet), same reason `EditorVarDecl` exists.
    - **B — store `templates` in `EditorDraft` / `EditorSnapshot` /
      `AdventureEditorViewModel`.** Same treatment as `variables`: undo
      / redo / draft / autosave all cover templates.
    - **C — `:app/data/Adventure` gains `choiceTemplates:
    List<ChoiceTemplateDto>?`.** Same "empty → omit" policy as
      `variables` / `tags`: `null` when the author has no templates, so
      clean files stay clean.
    - **D — new `ChoiceTemplateDto` + `VarDeclDto` in `:app/data`.**
      `VarDeclDto` differs from `VariableDto` by carrying `name` (list
      form, not map form). Rationale: `ChoiceTemplate.parameters` is a
      list — the parameter name is a field, not a map key; order matters
      for the UI.
    - **E — no UI yet.** No `TemplatesAdapter`, no "📦 Шаблоны" button,
      no per-choice "Use template…" in this batch. The templates are
      **invisible** to the author but **preserved** on save/load.

  **What changed (8 files).**

    1. `app/.../data/EditorModels.kt` — new `data class EditorTemplate`
       (`uid`, `id`, `text`, `visibilityCondition`,
       `availabilityCondition`, `parameters: MutableList<EditorVarDecl>`,
       `effects: MutableList<EditorTag>`, `nextSceneId`) + its
       `deepCopy()`. `uid` is the editor's stable id (survives rename);
       `id` is `ChoiceTemplate.id` from the IR.
    2. `app/.../data/EditorDraft.kt` — new `templates:
     List<EditorTemplate> = emptyList()` between `variables` and
       `initialStateTags`.
    3. `app/.../data/EditorSnapshot.kt` — same, new `templates` field.
    4. `app/.../data/Adventure.kt` — `Adventure` gains
       `@SerializedName("choice_templates") choiceTemplates:
       List<ChoiceTemplateDto>? = null` between `variables` and `tags`;
       new `ChoiceTemplateDto` (flat conditions, three effect fields,
       `next_scene`) and `VarDeclDto` (`name`, `type`, `min`, `max`).
    5. `app/.../utils/EditorDraftManager.kt` — `saveDraft(...)` gains
       `templates: List<EditorTemplate>` param (no default — forces all
       call-sites to pass explicitly, same reasoning as Ф2.7 for
       `variables`); `EditorDraft(templates = ...)`.
    6. `app/.../ui/editor/AdventureEditorViewModel.kt` —
       `val templates: MutableList<EditorTemplate> = mutableListOf()`;
       `addTemplate` / `removeTemplate`; `editorTemplateFromDto(dto)`
       (unknown param type → `VarKind.INTEGER`, permissive default);
       `takeSnapshot` / `restoreSnapshot` cover `templates`;
       `buildAdventure` passes `choiceTemplates =
     buildChoiceTemplatesDto()`; new private `buildChoiceTemplatesDto()`
       returns `null` on empty (Gson omit) and **skips templates with
       empty `id`** (a template without id cannot be referenced by
       `Choice.templateRef`; validation will warn in 4.3.2).
    7. `app/.../ui/editor/AdventureEditorFragment.kt` —
       `loadFromUri` restores `viewModel.templates` from
       `adventure.choiceTemplates` (nullable, `?.forEach`);
       `saveDraftImmediate` / `onDestroyView` pass `viewModel.templates`
       to `EditorDraftManager.saveDraft`; `maybeRestoreDraft` restores
       `templates` from `draft.templates` (`deepCopy()` per entry).
    8. `app/src/main/res/raw/test_adventure.json` — **smoke-only** temporary
       edit adding a `choice_templates` block (one `use_item` template
       with `itemId` parameter); reverted after smoke (see Verified).

  **Compiler fix during Ф4.3.1.** First `:app:assembleDebug` after the
  batch: 50+ cascading "Unresolved reference 'EditorTemplate'" errors.
  Root cause: `EditorModels.kt` step was written as "append to end of
  file", but was applied as find/replace on a **different** file or
  not applied — the class was missing. Fixed by an **anchored**
  find/replace on the tail of `EditorVarDecl.toVarType()` (last two
  `VarKind.ITEM_*` lines + closing brace) with the new `EditorTemplate`
    + `deepCopy()` inserted after. **Discipline lesson (recorded in
      Ф4.1's list):** "append to end of file" instructions must be
      expressed as find/replace with at least one unchanged anchor line —
      same rule as Ф4.1's `Dto.kt` / `SchemaFingerprintTest` lessons.

  **User benefit.** (a) **Data-loss bug closed:** before Ф4.3.1, an
  author opening a JSON file that already had `choice_templates` and
  pressing "Save" would silently lose the whole block — files round-trip
  now preserve templates. (b) `viewModel.templates` is the single
  in-memory source of truth for both undo/redo (`takeSnapshot` /
  `restoreSnapshot`) and draft persistence (`EditorDraftManager`) —
  the same lifecycle as `variables`, so no surprise state loss between
  autosave and restart. (c) `:app/data/Adventure.choiceTemplates` and
  `ChoiceTemplateDto` / `VarDeclDto` mirror the JSON shape exactly, so
  the writer (`buildChoiceTemplatesDto`) needs no per-field
  transformations for the common case (id, text, conditions,
  next_scene are 1:1; effects reuse the existing `serializeTags`).
  (d) Templates with empty `id` are skipped on save (not an error,
  just excluded) — a placeholder template the author is still drafting
  cannot pollute the file; the author will see it missing from the
  saved file only after deliberate inspection, not as a mystery diff.

  **What was NOT done (deliberately).**

    - **Template UI** — no `TemplatesAdapter`, no "📦 Шаблоны" button,
      no per-choice "Use template…", no edit dialog. Ф4.3.2.
    - **`EditorChoice.templateRefUid` / `templateRefArgs`** — adding
      these fields happens in Ф4.3.2 alongside the "apply" flow.
    - **JSON persistence of `Choice.templateRef`** — `ChoiceDto` has no
      `template_ref` field; expanded choices save as ordinary choices.
      Ф4.3.3 (or C2 follow-up) when editor's "update all uses" needs
      the link to survive save.
    - **`buildIrAdventure` does not include `choiceTemplates`** — the
      IR passed to `StoryValidator` in "Проверить" has
      `choiceTemplates = emptyList()`. Intentional: `TypeChecker` does
      not yet validate templates (Ф4.2.2); adding them now would produce
      false `UnknownVariable` on parameter names.
    - **`TypeChecker.checkChoiceTemplate`** — Ф4.2.2.
    - **Template `text` placeholders are not validated** — an author can
      write `{typo}` with no matching parameter; `applyTemplate` will
      leave it as-is in the expanded choice. Validation (or runtime
      warning) is a future consideration.

  **Verified.** `./gradlew :engine:test --rerun-tasks` →
  **333/333 PASSED** (`:engine` not touched). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings.

  Smoke (phone, with the temporary `test_adventure.json` edit):

    - **S1** — reader opens the file with `choice_templates` and plays
      normally. ✅
    - **S2** — editor loads the file; `debug_log.txt` shows
      "Приключение загружено из файла: …" with no errors. ✅
    - **S3** — "Сохранить" produces `NewAdventures/Testovoe_priklyuchenie.json`
      with the `"choice_templates"` block preserved (id `use_item`,
      parameter `itemId`, conditions, effects, `next_scene`). ✅
    - **S4** — reader opens the saved file; plays normally. ✅
    - **S5** — second save of the saved file is idempotent (same
      `choice_templates` block, no drift). ✅

  After smoke: `git checkout -- app/src/main/res/raw/test_adventure.json`
  restored the reference sample. The permanent demo
  (`test_adventure.json` with a template applied in two scenes) is
  **Ф4.11**, bundled with disabled / merge / subroutine — not duplicated
  here.

  **Next — Ф4.3.2.** Template UI. Split proposed:
  **4.3.2a** (separate `TemplateListFragment` + edit dialog + CRUD
  on `viewModel.templates`); **4.3.2b** (per-scene
  "📦+ Use template…" → pick template + args → append expanded
  `EditorChoice` with `templateRefUid`); **4.3.3** ("update all uses"
  when a template changes). Развилки для 4.3.2a — в следующем
  сообщении.

---

- **2026-11-14 · Ф4.3.2a — Template UI (list + edit dialog) + smoke fixes**

  **DoD context.** Ф4.3 ("editor of templates") is split into three
  sub-steps. **4.3.2a** closes the first two halves of the DoD: "there
  is a templates screen in `AdventureEditorFragment`" and "a template
  is created and stored in the JSON". The third half — "a template is
  **applied** to two scenes with different args" — is **4.3.2b**
  (per-choice "📦 Use template…" + args picker). 4.3.3 ("update all
  uses") remains the third sub-step.

  **Scope decisions (agreed before coding).**

    - **A2 — separate `TemplateListFragment`**, not an inline section in
      the editor's `ConcatAdapter`. Templates are denser than scenes
      (text + 2 conditions + parameters list + effects list +
      next-scene); inlining would push `jumpToScene`'s offset arithmetic
      to `+3` and make the editor a 4-section flat list. A dedicated
      screen mirrors `AdventureMapFragment` (focused list with a back
      button) and keeps the editor's scroll untouched.
    - **B1 — "📦 Шаблоны" toolbar button** in
      `fragment_adventure_editor.xml`'s `menu_actions`. Not `MenuFragment`
      (templates belong to the current adventure, not to the app), not a
      separate global menu.
    - **C2 — compact rows** in the list (id mono, text bold, "N
      параметров · M эффектов" summary); click opens the edit dialog.
      Inline fields (like `EditorSceneAdapter`) would be too tall per
      template.
    - **D2 — one edit dialog** with all fields, parameters and effects as
      sub-lists. Parameters reuse `item_editor_variable.xml` (same
      layout as `VariablesAdapter`); effects reuse `renderTagRow` from
      `EditorHelpers`.
    - **E2 — `ScenePickerDialog`** for next-scene, not a `Spinner`.
      Search by `id` or `title` (case-insensitive, debounced 200ms); empty
      query hides the list (500 scenes never render). Special rows at
      the top: `@self` (template-only, `allowSelf = true`) and "create
      new scene". Reusable — a future refactor of
      `EditorChoice.nextSceneId` (separate batch) can use the same dialog.
    - **F2 — positive button disabled until `id`, `text`, `nextSceneId`
      are non-empty** — and (fix1, problem 3) `id` matches `[a-z0-9_]+`.
      A template with no id would be skipped silently by
      `buildChoiceTemplatesDto`; a template with cyrillic id would
      produce a JSON key that cannot be referenced.

  **What changed (9 new + 3 edits + docs).**

    1. `app/.../res/layout/item_scene_picker_row.xml` (new) — two-line
       picker row: bold title, mono grey id.
    2. `app/.../res/layout/dialog_scene_picker.xml` (new) — search field,
       special rows (`@self`, "create new scene"), scrollable results.
    3. `app/.../ui/editor/ScenePickerDialog.kt` (new) — object with
       `show(context, scenes, initialSceneId, allowSelf, onPicked,
       onCreateNew)`. Debounced search (200ms), `MAX_RESULTS = 50`,
       empty query hides the list. Reusable by any future scene picker.
    4. `app/.../res/layout/item_template.xml` (new) — template row: id
       (mono grey), text (bold), "N параметров · M эффектов" summary,
       `✕` delete button.
    5. `app/.../res/layout/item_empty_template_hint.xml` (new) — empty
       state ("Пока нет шаблонов…").
    6. `app/.../res/layout/fragment_template_list.xml` (new) — toolbar
       ("← Назад", title, "+ Шаблон"), RecyclerView.
    7. `app/.../res/layout/dialog_edit_template.xml` (new) — edit dialog.
       **fix1 (problem 2):** labels *over* fields (11sp grey), short
       hints (`use_item`, `Выпить {itemId}`), descriptions *under* fields
       (10sp grey). Long prompts that used to run off-screen are gone.
    8. `app/.../ui/editor/TemplateListAdapter.kt` (new) — compact row
       adapter; `TYPE_TEMPLATE` / `TYPE_EMPTY`; `pluralParams` /
       `pluralEffects` (ru) for the summary.
    9. `app/.../ui/editor/TemplateListFragment.kt` (new) — the list
       screen + edit dialog. **fix1 (problem 1, rotation):** the working
       copy lives in `viewModel.editingTemplate` (added in file 10), which
       survives config change; `onViewCreated` re-opens the dialog if
       `editingTemplate != null`. `editingTemplate` is cleared only on
       Save / Cancel / system-back — **not** on config change. **fix1
       (problem 3, cyrillic id):** `idRegex = Regex("[a-z0-9_]+")`;
       `tv_template_id_hint` turns grey / green (`✓`) / red
       (`Недопустимые символы…`); positive button disabled while the
       regex fails.
    10. `app/.../ui/editor/AdventureEditorViewModel.kt` — new
        `var editingTemplate: EditorTemplate?`. KDoc explains the
        rotation-survival mechanism and why `DialogFragment` was
        deliberately not chosen for pre-alpha (custom `Gson` adapter for
        `EditorTemplate` + ~250 lines of refactor; `ViewModel` state is
        ~15 lines and covers the actual user case).
    11. `app/.../res/layout/fragment_adventure_editor.xml` — new
        `btn_templates` ("📦 Шаблоны") in `menu_actions`, after `btn_map`.
    12. `app/.../ui/editor/AdventureEditorFragment.kt` — `btn_templates`
        click → `replace(R.id.fragment_container,
      TemplateListFragment())`.
    13. `docs/engine-architecture.md` — new `### Editor UI (Ф4.3.2a)`
        sub-section under `## Choice templates (Ф4.2)`: list screen,
        edit dialog, `ScenePickerDialog`, `@self`, F2 validation,
        explicit "not in this batch" for 4.3.2b / 4.3.3.

  **What was NOT done (deliberately).**

    - **Apply template to a choice** — `EditorChoice.templateUid` /
      `templateArgs`, per-choice "📦 Use template…", args picker dialog.
      **Ф4.3.2b.**
    - **Update all uses** — when the author edits a template, existing
      expanded choices do not auto-update. **Ф4.3.3.**
    - **`ChoiceDto.template_ref`** — engine-side parsing of expanded
      choices' `template_ref` metadata. **Ф4.3.2b** (paired with
      `EditorChoice.templateUid`).
    - **`ScenePickerDialog` rotation survival** — the picker dialog is
      still lost on rotation (the template's working copy is safe in
      `viewModel.editingTemplate`; the author re-opens the picker). This
      is a smaller annoyance than losing the whole template dialog; the
      same `ViewModel`-state pattern would close it in a future batch.
    - **`TemplateListFragment` process death** (low-memory kill) — the
      `ViewModel` state is lost, same limitation as the rest of the
      editor; `DialogFragment` would fix it but is deferred (same
      reasoning as rotation in file 10).
    - **`TypeChecker.checkChoiceTemplate`** — Ф4.2.2 (still deferred).

  **Compiler fix during Ф4.3.2a.** No compile errors after the batch —
  all 9 new files and 3 edits compiled on the first try. The reason is
  that `:app`'s dependencies on `:engine` (`EditorHelpers`,
  `renderTagRow`, `bindAfterTextChanged`, `setTextIfDifferentSilently`,
  `EditorVarDecl`, `VarKind`, `EditorTag`) were all already in place
  from Ф2.7 / Ф4.3.1.

  **User benefit.** (a) The author now has a **dedicated template
  screen** ("📦 Шаблоны"), can create a template with parameters and
  effects, edit it, delete it, and it round-trips through the file.
  (b) Creating a template with a **non-empty `id`**, **non-empty
  `text`**, and **a valid next-scene** is enforced by the dialog — a
  template that would be silently skipped by `buildChoiceTemplatesDto`
  (empty `id`) or produce an empty button in the reader (empty `text`)
  cannot be saved. (c) The id regex `[a-z0-9_]+` matches the editor's
  `transliterate` output, so author-written ids stay compatible with
  scene-id-style keys in JSON, and `templateRef.templateId` will
  resolve. (d) **Rotation no longer discards the template being
  edited** — the working copy lives in the `ViewModel`, so an author who
  rotates the phone mid-edit keeps every field. (e) The **scene picker**
  scales to 500+ scenes: search by id or title, `@self` special row,
  "create new scene" — the giant `Spinner` problem is gone for the
  template case. (f) **Long prompts no longer run off-screen** — labels
  over fields, short hints, descriptions under. The id/text fields are
  readable and usable on a small portrait phone.

  **Verified.** `./gradlew :engine:test --rerun-tasks` → **337/337
  PASSED** (`:engine` not touched in phase B). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings.

  Smoke (phone, all 6 scenarios from the phase B plan + 3 fixes):

    - **Empty list** — "📦 Шаблоны" on a file with no templates shows
      "Пока нет шаблонов…". ✅
    - **Create + round-trip** — id `use_item`, text `Use {itemId}`,
      visibility/availability `itemId > 0`, next-scene `start`,
      parameter `itemId: ItemCount`, effect `modify_stats: itemId = -1`
      → "Сохранить" → "← Назад" → "Сохранить" (toolbar) → `NewAdventures/…json`
      contains `"choice_templates"` with the template. ✅
    - **Edit** — click row → dialog, change text → save → card updated
      → save file → block updated. ✅
    - **Delete** — `✕` → confirm → card gone → save → block
      removed. ✅
    - **ScenePickerDialog** — search by id (`start` shows the row),
      search by title (case-insensitive), empty query hides the list,
      `zzz` shows "Ничего не найдено", `🔁 @self` sets label to
      "🔁 @self — текущая сцена", "➕ Создать новую сцену" creates the
      scene and sets label. ✅
    - **@self round-trip** — template with `@self` → save → JSON has
      `"next_scene": "@self"` → load → label reads "🔁 @self — текущая
      сцена". ✅
    - **fix1 (problem 1)** — rotate during edit (new and existing) →
      dialog and fields survive → save works. ✅
    - **fix1 (problem 2)** — long hints no longer run off-screen; labels
      over fields, short hints inside, descriptions under. ✅
    - **fix1 (problem 3)** — cyrillic id `Тест` → red hint
      "Недопустимые символы…" + Save disabled; replace with `test` →
      green `✓` + Save enabled; empty id → grey hint + Save
      disabled. ✅

  **Next — Ф4.3.2b.** Apply a template to a choice. Scope: `EditorChoice`
  gets `templateUid: String?` + `templateArgs: Map<String, String>`;
  `:app/data/ChoiceDto` gets `template_ref` (`TemplateRefDto`); editor
  builds an expanded `EditorChoice` via the engine's `applyTemplate` (so
  the substitution semantics are **the same** in editor and reader, no
  drift); a "📦 Из шаблона" button per scene opens
  `TemplatePickerDialog` → `TemplateArgsDialog` → adds the expanded
  `EditorChoice` with `templateUid` / `templateArgs` set;
  `loadFromUri` / `buildAdventure` round-trip the metadata through
  `template_ref` in JSON. Engine untouched (its `ChoiceDto`
  `ignoreUnknownKeys = true` ignores the new field; the reader keeps
  playing expanded choices as before). Развилки для 4.3.2b — в
  следующем сообщении.

---

- **2026-11-15 · 3b-6 — i18n infrastructure and user-facing string
  extraction (batches 2a, 2b, 3a, 3b-1…3, 4a–b, 5, 6a–e, plus fixes)**

  **Context.** The i18n infrastructure was introduced mid-Ф4 because of
  two pressures: (a) every new UI batch adds 15–30 Russian `setText("…")`
  / `Toast.makeText(…, "…")` lines in `.kt` and `android:text="…"` in
  layouts, which (i) degrades `gitingest` / some diff viewers (Cyrillic
  bulk), (ii) accumulates work for Ф7.3 linearly with each future batch;
  (b) doing the extraction now is 1.5 days; waiting until Ф7.3 is 4–5
  days plus merge-conflict risk against concurrent 4.3.2b / 4.4 / 4.5
  work.

  The scope was deliberately kept to **infrastructure + extraction**;
  **translation to English is deferred to Ф7.3**, as is the transfer of
  `Logger.log(…)` and debug-panel strings. See
  [`i18n-policy.md`](../i18n-policy.md) for the policy that governs all
  future batches.

  **Scope decisions (agreed before coding).**

    - **A — `strings.xml` is the single source of truth** for user-facing
      strings. Naming: `snake_case`, prefix by area
      (`app_*`, `menu_*`, `library_*`, `editor_*`, `template_*`,
      `reader_*`, `stats_*`, `settings_*`, `setup_*`, `logs_*`,
      `search_*`, `validation_*`, `dialog_*`, `action_*`, `icon_*`,
      `word_*`, `label_*`, `map_*`, `import_*`, `step_service_*`).
    - **B — `values-en/` is deliberately NOT created** until Ф7.3. An
      empty `values-en/` with copies of Russian would make an English
      system show Russian UI with the false appearance of translation; an
      empty `values-en/` with no copies would fall back to `values/` and
      show Russian anyway. Either way, no value until real English
      strings exist.
    - **C — Log and debug-panel strings stay in Russian.** `Logger.log(…)`
      is diagnostic; the debug panel (`showDebugPanel` and friends) is
      developer-only. Translating ~80 keys would add no reader benefit.
      Both are documented as "kept RU" in `i18n-policy.md`.
    - **D — Plural forms use `ruQuantityString` workaround until Ф7.3.**
      Android's `<plurals>` rules come from the **device** locale, not
      the source `values/` directory. On a non-`ru` device the Russian
      strings get wrong forms ("4882 шагов" instead of "4882 шага",
      "4 слов" instead of "4 слова"). `PluralHelper.kt` builds a
      `Context` forced to `ru` and calls `getQuantityString` on it; every
      plural call goes through it. **Deleted in Ф7.3** when
      `values-en/` lands.
    - **E — `FontManager` and `EditorDraftManager` are deliberately not
      touched.** `FontManager` returns display strings
      (`"Мелкий"/"Обычный"/"Крупный"/"Очень крупный"`, `"По умолчанию"`)
      used as both display and **keys** in `SharedPreferences`. Changing
      that requires a data migration; Ф7.3 owns it together with the
      whole font-list rewrite. `EditorDraftManager` has only
      `Logger.log(…)` messages, already covered by C.
    - **F — `SearchResult.detail` / `ValidationIssue.title` become
      `@StringRes Int` (3b-6e-1).** `:utils` (`SearchService`,
      `StoryValidator`) is a plain Kotlin module — no `Context`, no
      `getString`. Making these two fields `@StringRes` keeps `:utils`
      clean of user-facing strings and gives the caller (`AdventureEditorFragment`,
      which has `Context`) full control over the resolved text.

  **What changed (by batch).**

    - **Batch 1 — policy + `strings.xml` header + shared dialog buttons.**
        - `docs/i18n-policy.md` (new): where strings live, naming
          convention, what NOT to do, migration status.
        - `app/src/main/res/values/strings.xml`: header comment block,
          `dialog_save` / `dialog_cancel` / `dialog_delete` / `dialog_close`
          / `dialog_apply` / `dialog_ok` / `dialog_yes` / `dialog_no` /
          `dialog_clear` / `dialog_restore` / `dialog_create` / `dialog_done`.
        - `docs/documentation-rules.md §1`: one line pointing at
          `i18n-policy.md` for UI strings.
        - `docs/index.md`: `For developers` section gets a link to
          `i18n-policy.md`.

    - **Batch 2a — fragment layouts.**
        - `fragment_menu.xml`, `fragment_adventure_list.xml`,
          `fragment_adventure_editor.xml`, `fragment_adventure_map.xml`,
          `fragment_adventure_read.xml`, `fragment_log_viewer.xml`,
          `fragment_settings.xml`, `fragment_setup.xml`,
          `fragment_template_list.xml`: every `android:text` /
          `android:hint` / `android:contentDescription` → `@string/…`.
        - `action_*` shared button prefix (`action_menu`, `action_back`,
          `action_undo`, `action_redo`, `action_debug`).
        - ~25 keys in `strings.xml`.

    - **Batch 2b — dialog + item layouts.**
        - `dialog_chapter_steps.xml`, `dialog_edit_choice_tags.xml`,
          `dialog_edit_meta.xml`, `dialog_item_list.xml`,
          `dialog_new_scene.xml`, `dialog_search.xml`,
          `dialog_story_stats.xml`, `item_editor_choice.xml`,
          `item_editor_scene.xml`, `item_editor_tag.xml`,
          `item_initial_state.xml`, `item_empty_hint.xml`,
          `item_empty_template_hint.xml`, `item_load_adventure.xml`,
          `item_map_chapter_header.xml`, `item_map_scene_row.xml`,
          `item_map_steps_gate.xml`, `item_search_result.xml`,
          `item_template.xml`, `item_validation_issue.xml` → `@string/…`.
        - Debug layouts (`dialog_debug_*`, `item_debug_*`) **skipped** per C.
        - ~60 keys in `strings.xml`.
        - **Bug during batch 2b:** `item_editor_tag.xml` lost a `/>` on the
          `cb_tag_visible` CheckBox (my find/replace used `"👁"` which is
          actually 2 codepoints — `U+1F441 U+FE0F` — and the exact match
          failed; the manual fix dropped the self-closing slash). Fixed by
          replacing the whole file; learned: **emoji must not be used in
          find/replace `find` blocks** — either copy from the file or
          replace larger anchor text.

    - **Batch 3a — editor dialogs (AlertDialog titles + buttons).**
        - `AdventureEditorFragment`: `showEditMetaDialog`,
          `maybeRestoreDraft` (title + buttons),
          `loadFromUri` (types-missing dialog),
          `showValidationDialog` (title / close),
          `showSearchDialog` (title / close),
          `showStatsDialog` (title / close).
        - `EditorSceneAdapter`: delete scene, replace death scene,
          delete choice, choice-effects dialog, steps gate,
          new scene, rebuild ID.
        - New `dialog_replace`, `dialog_declare`, `dialog_later` keys.

    - **Batch 3b-1 — editor toasts and save status.**
        - `AdventureEditorFragment`: 5 `tvSaveStatus` assignments,
          12 toasts.
        - New `editor_save_status_*` and `editor_toast_*` keys.

    - **Batch 3b-2 — `EditorSceneAdapter` toasts + effects-dialog
      setText.**
        - 4 toasts (`steps_positive`, `steps_integer_positive`,
          `title_first`, `id_matches`).
        - `tvInfo.text` (parametrized) and `tvEmpty.text` in
          `showChoiceEffectsDialog`.

    - **Batch 3b-3a — reader menu, HUD, permissions, error toasts.**
        - `AdventureReaderFragment`: 6-item `showReaderMenuDialog`,
          `confirmRestartAdventure`, `updateHud` (3 HUD buttons),
          3 `showXDialog` titles, 2 permission toasts, 3 parse/compile
          error toasts, launch error toast, adventure-restarted toast.
        - New `reader_menu_*`, `reader_hud_*_fmt`, `reader_perm_*`,
          `reader_error_*`, `reader_restart_dialog_*` keys.

    - **Batch 3b-3b — reader end/death/gate/warnings.**
        - `showEnd` (text wrapper + button), `showDeathScreen` (3 buttons),
          `onGatePassed` (end/preview), `loadCurrentScene` (end/preview),
          `showGateWaiting` (waiting + HC hint), `updateStepsProgressUi`,
          `showWarningDialog`, `showWarningInfoDialog`,
          `showBrokenAdventureDialog`, `showTypeErrorsDialog`.
        - New `reader_end_*`, `reader_death_*`, `reader_gate_*`,
          `reader_warning_dialog_*`, `reader_broken_dialog_*`,
          `reader_type_errors_dialog_*` keys.

    - **Batch 3b-4a — `LogViewerFragment` + `SettingsFragment`.**
        - `LogViewerFragment`: loading text, empty / copied / cleared
          toasts, clear-log dialog.
        - `SettingsFragment`: font-added toast, HC toasts (3), logging
          toasts (2), HC status texts (4), font-change dialog.
        - **Bug during batch 3b-4a:** both files lacked `import
      com.myapp.adventure.R` — they had no `R.*` references before
          this batch. 34 compile errors, all `Unresolved reference 'R'`.
          Fixed by adding the import. Learned: **every batch that
          introduces `R.string.*` into a file must include the `R` import
          in the instruction** if the file did not already have it.

    - **Batch 3b-4b — `SetupFragment` + `AdventureImportHelper`.**
        - `SetupFragment`: 7 toasts (notifications, activity, folder,
          error, rationale ×2).
        - `AdventureImportHelper`: 4 toasts (folder, success,
          create-failed, error) + new `R` import.

    - **Batch 3b-5 — plurals + format helpers + search/stats dialogs.**
        - `AdventureEditorFragment`: `formatMatchesCount`,
          `formatScenesPrepositional`, `formatScenes` / `formatWords` /
          `formatChoices` / `formatSteps` / `formatReadingTime` via
          `<plurals>`; `showSearchDialog` summary;
          `showStatsDialog` strings; `addChapterStatsRow` strings.
        - `<plurals name="word_scenes|word_words|word_choices|word_minutes|word_matches|word_scenes_in">`.
        - New `editor_stats_*`, `editor_chapter_*`, `editor_search_matches_fmt` keys.

    - **Batch 3b-5-fix — `ruQuantityString` workaround.**
        - `app/.../utils/PluralHelper.kt` (new): `ruResources(context)` and
          `ruQuantityString(context, pluralsResId, quantity)`.
        - `AdventureEditorFragment.pluralWithNumber` and
          `StatisticsFragment.formatSteps` switched to `ruQuantityString`.
        - `i18n-policy.md` §Migration status updated to note the workaround
          and its removal in Ф7.3.
        - **Root cause fixed:** on an `en` device, `4882 шагов` and
          `4 слов` were displayed; after fix — `4882 шага` and `4 слова`.

    - **Batch 3b-6a — shared helpers + `AdventureMap*` + `TemplateListAdapter`.**
        - `EditorHelpers.kt`: top-level `formatInt` and `pluralWithNumber`
          (consolidating duplicates from `AdventureEditorFragment` and
          `StatisticsFragment`).
        - `AdventureMapAdapter`: delete local `formatInt` / `formatScenes`;
          `ChapterHeader` uses `pluralWithNumber` + `word_words_short`.
        - `AdventureMapFragment`: `tvMapTitle` fallback, chapter title
          via `editor_chapter_title_fmt`.
        - `TemplateListAdapter`: summary via
          `pluralWithNumber(ctx, R.plurals.word_parameters|word_effects, n)`.
        - New `word_words_short`, `word_parameters`, `word_effects`,
          `editor_search_enter_query`, `editor_search_nothing_found` keys.

    - **Batch 3b-6b — map SceneRow/StepsGate + fallbacks.**
        - `AdventureMapAdapter`: `label_without_title`,
          `map_no_transitions`, `map_gate_opens_immediately`, gate time
          strings (`map_gate_approximate_minutes_fmt` etc.),
          `map_gate_text_fmt` with `stepsText` from `pluralWithNumber`.
        - `AdventureMapFragment`: `label_without_title`,
          `map_choice_target_unset`.
        - `TemplateListAdapter`: `template_no_id`,
          `item_editor_choice_no_text`, KDoc revised.
        - **Bug during batch 3b-6b:** duplicate `val ctx` in
          `TemplateListAdapter.onBindViewHolder` (one from 3b-6a, one
          added in 3b-6b), then removing the "duplicate" removed the only
          one in scope. Fixed by replacing the whole file. Learned: **`ctx`
          is declared exactly once at the top of `onBindViewHolder`,
          period**.

    - **Batch 3b-6c — `EditorHelpers` / `ScenePickerDialog` /
      `TemplateListFragment` / `VariablesAdapter` / `InitialStateAdapter`.**
        - `EditorHelpers.renderTagRow`: spinner display and value hint
          via `getString(R.string.editor_tag_*)`.
        - `ScenePickerDialog`: title / cancel / nothing-found /
          new-scene / create / cancel / `(без id)`.
        - `TemplateListFragment`: delete dialog + message + buttons,
          edit/new title, id-hints, next-scene label, default-scene
          title, save toast.
        - `VariablesAdapter`: summary via `pluralWithNumber` +
          `word_variables`, `label_empty`; deleted `pluralVariables`.
        - `InitialStateAdapter`: summary via `pluralWithNumber` +
          `word_tags`, `label_empty`; `// Строки тегов` → `// Tag rows`.
        - New `editor_tag_*`, `scene_picker_*`, `scene_no_id`,
          `template_*`, `editor_new_scene_default_title_fmt`,
          `word_variables`, `word_tags`, `label_empty` keys.

    - **Batch 3b-6d — `EditorSceneAdapter` dialogs + spinners + steps
      display.**
        - 5 dialog messages via `context.getString(R.string.editor_*_message_fmt, …)`.
        - `applyChoiceState` / `btnDeleteChoice` / `etChoiceText` fallbacks
          via `item_editor_choice_no_text`.
        - `formatStepsDisplay` / `formatSceneIdLabel` /
          `formatChoicesCount` rewritten with `Context` param (no
          `binding` in helper).
        - Spinner display items (`editor_choice_spinner_create_new` /
          `_choose`).
        - New `editor_scene_delete_message_fmt`,
          `editor_scene_replace_death_message_fmt`,
          `editor_choice_delete_message_fmt`,
          `editor_rebuild_id_message_fmt`, `_result_fmt`, `_result_no_refs`,
          `editor_choice_spinner_*`, `editor_scene_id_label_*`,
          `editor_steps_display_*`, `word_choices_count`, `word_references`,
          `editor_choices_count_none` keys.
        - **Bug during batch 3b-6d:** `formatStepsDisplay`,
          `formatSceneIdLabel`, `formatChoicesCount` used
          `binding.root.context`, but `binding` is not a field of
          `EditorSceneAdapter` — it is a local `val` inside
          `onBindViewHolder`. 6 errors, all `Unresolved reference 'binding'`.
          Fixed by passing `Context` parameter to each helper and updating
          5 call sites. Learned: **`RecyclerView.Adapter` helpers take
          `Context` as a parameter, never `binding`**.

    - **Batch 3b-6e-1 — data classes + `SearchService` +
      `StoryValidator`.**
        - `SearchResult.detail: String` → `detailRes: @StringRes Int` +
          `detailArg: Int?` (for the one parametrized detail, "Выбор #N").
        - `ValidationIssue.title: String` → `titleRes: @StringRes Int`.
        - `SearchService.search(…, titleFallback: String)` new parameter.
        - `StoryValidator.titleFor(TypeError|CompileError|LintIssue)` now
          returns `@StringRes Int`.
        - `AdventureEditorFragment`: 3 places updated to `getString(...)`.
        - New `search_detail_*`, `editor_search_more_fmt`,
          `validation_title_*` (~19 keys), `validation_desc_types_not_declared`,
          `validation_summary_*` keys.
        - **Bug during batch 3b-6e-1:** the `showValidationDialog` block
          that iterates `conditionParseErrors` was mixed (`for` retained,
          `map` added, closing `}` of `for` dropped). 34 compile errors —
          everything after `showValidationDialog` looked like local
          functions inside it (`Modifier 'private' is not applicable to
      'local function'`, `Unresolved reference 'openPreview'` from
          `onCreate`, `Missing '}'` at the file end). Fixed by replacing
          just the broken block. Learned: **when the `find` for a
          structural construct (`for`, `if`, `buildString`) does not match
          the file, the fix must include the whole construct
          (open + body + close), not the body only.** This joins the
          cumulative Kotlin edit discipline list (alongside the
          `EditorHelpers` / `SandboxTest` / `ConditionWriterTest` /
          `SchemaFingerprintTest` / `TemplateApplierTest` / `item_editor_tag.xml`
          / `strings.xml:210` lessons).

    - **Batch 3b-6e-2 — `SearchService`/`StoryStatsService` fallback
      params + `StepCounterService` + remaining toasts.**
        - `StoryStatsService.calculate(…, titleFallback: String)` new
          parameter (same pattern as `SearchService`).
        - `StepCounterService`: 4 notification strings via `getString`;
          today-steps text built with `ruQuantityString`.
        - `AdventureListFragment`: 2 toasts.
        - `AdventureReaderFragment`: preview back button
          (`reader_preview_back`), default death text
          (`reader_default_death_text`).
        - `AdventureEditorFragment`: draft-restore message
          (`editor_draft_restore_message_fmt`), `"Сцена смерти"` default
          title via `item_editor_scene_death_scene`, both `SearchService.search`
          and `StoryStatsService.calculate` call sites pass
          `titleFallback = getString(R.string.label_without_title)`.
        - New `step_service_*`, `library_toast_*`, `reader_preview_back`,
          `reader_default_death_text`, `editor_draft_restore_message_fmt`
          keys.

    - **Post-batch fix (P1)** — `StoryStatsService.kt` 3 RU `//`
      comments translated (`// Longest and shortest scenes`,
      `// Chapter grouping — same algorithm as in buildAdventure()`,
      `// Reading time: words + deliberation on choices`). Other RU
      comments and `/** … */` KDoc remain; tracked as **C1** in
      `docs/roadmap.md` (deferred to Ф7.3).

  **Total.** ~180 new keys in `strings.xml`; ~110 layout attribute
  replacements; ~150 `.kt` replacements across ~25 files; 2 data-class
  field changes (`SearchResult.detail`, `ValidationIssue.title`); 3
  utility signature changes (`SearchService.search`,
  `StoryStatsService.calculate`, `PluralHelper`); 2 new files
  (`PluralHelper.kt`, and the doc `i18n-policy.md`).

  **User benefit.** (a) The reader, the editor, the menu, the settings,
  the library, the log viewer, the scene picker, the template editor,
  and the step-service notification now display consistent Russian UI
  text driven from one place. (b) **Plural forms are correct on any
  device locale** — `4 слов` / `4882 шагов` are fixed to `4 слова` /
  `4882 шага` regardless of the system language. (c) `HardcodedText`
  is 0 in user-facing layouts; the `lint-baseline.xml` entry for it
  becomes obsolete and can be removed. (d) The `.kt` files are
  ~100% ASCII, which fixes `gitingest` / digest readability for the
  source tree. (e) `values-en/` in Ф7.3 is now a **lookup step**, not
  a rewrite: every key is already named and grouped. (f) Future UI
  batches (4.3.2b, 4.4, 4.5, …) start with `strings.xml` ready, so
  Russian text in `.kt` will not reappear.

  **What was NOT done (deliberately).**

    - **`values-en/`** — Ф7.3 (translation proper).
    - **RU comments + KDoc in ~15 `:app` files** — **C1** in
      `docs/roadmap.md`, deferred to Ф7.3 (translation + `values-en/` in
      one pass to keep terminology aligned).
    - **`FontManager` display strings / `EditorDraftManager` log strings**
      — decision A / E above; Ф7.3.
    - **`Logger.log(…)` and debug-panel strings** — policy decision C;
      remain RU indefinitely (not user-facing).
    - **`AdventureEditorViewModel.kt` "Глава N" / "Здоровье"** — these
      are **JSON defaults** written into `chapters[].title` and
      `TagDefinition.display_name` when the author does not provide
      values; they are **data**, not UI. Left as-is; a future "authoring
      defaults" task could localize them, but they are not part of the
      user-facing UI contract.

  **Verified.** Every batch: `./gradlew :app:assembleDebug` →
  BUILD SUCCESSFUL, 0 warnings; `./gradlew :engine:test --rerun-tasks`
  → **337/337 PASSED** (engine untouched throughout). Per-batch smoke
  on the phone:

    - after 2a/2b — every screen renders the same Russian text as before
      (purely mechanical extraction);
    - after 3a/3b-1/3b-2 — editor: create / load / edit / save, delete
      scene / choice / template, steps gate dialog, choice-effects dialog,
      search / stats / validation dialogs — all text on the correct
      place, all buttons do what they did before;
    - after 3b-3a/3b-3b — reader: menu, HUD, death screen, end screen,
      gate progress, warning dialog, broken / type-error dialogs — all
      shown with the same Russian text;
    - after 3b-4a/3b-4b — settings (logging, HC, font), log viewer
      (loading / copy / clear), setup (folder / permissions), import —
      text and toasts intact;
    - after 3b-5/3b-5-fix — editor stats dialog shows correct plurals
      on an `en` phone (`4 слова`, `4 882 шага`) — the workaround works;
    - after 3b-6a…e-2 + P1 — map screen, template list / edit dialog,
      scene picker, search results (`Заголовок` / `Текст сцены` /
      `Выбор #1` / `Метка`), validation dialog (all ~19 titles, summary
      `Ошибок: 1` / `Предупреждений: 1`), step-service notification
      (`Шагомер Adventure` / `Сегодня: N шагов`), reader preview
      back button (`Назад`), draft-restore dialog message — all intact.

  Final grep on `:app/src/main/java`:
  `Select-String -Pattern '"[^"]*[А-Яа-я]|//\s*[А-Яа-я]'` — only
  `Logger.log(…)`, debug-panel strings, `Ф`-prefix (project term), and
  KDoc quotations remain, all consistent with
  [`i18n-policy.md`](../i18n-policy.md). **User-facing surface is
  clean.**

  **Next — Ф4.3.2b.** Apply templates to choices. Decisions were agreed
  before 3b-6 started: `A1 B2 C2 D1 E1 F1 G1 H1 I1` — per-scene
  "📦 Из шаблона" button, `TemplatePickerDialog` (card list, not
  `setItems`), `TemplateArgsDialog` (label + hint per parameter, with
  type-driven examples), no preview (apply and see in the scene),
  `currentSceneId` always passed (`@self` resolution), F1 disabled
  button when no templates exist, `Choice.id` left to `JsonToIr`,
  `ChoiceDto.templateRef` written (ignored by `:engine` via
  `ignoreUnknownKeys`), flat `template_ref` shape. After 4.3.2b →
  4.3.3 ("update all uses").
