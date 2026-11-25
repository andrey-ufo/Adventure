package com.myapp.adventure.engine.format.dto

import com.myapp.adventure.engine.ir.TagCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the adventure JSON.
 *
 * These classes mirror the on-disk JSON exactly. They are **internal** —
 * a detail of the [com.myapp.adventure.engine.format.JsonToIr] pipeline,
 * not part of `:engine`'s public API. The public API works with
 * [com.myapp.adventure.engine.ir.Adventure] and friends.
 *
 * All optional fields use non-null defaults (`emptyList()`, `emptyMap()`,
 * `null` for nullable types) so that the mapper does not have to write
 * `?: emptyList()` at every call site. `Json { coerceInputValues = true }`
 * (configured in `JsonToIr`) makes an explicit `null` in JSON behave the
 * same as a missing field.
 *
 * **Exception:** [AdventureDto.version] is nullable on purpose. `null`
 * means "the file has no version field" → the parser substitutes
 * [com.myapp.adventure.engine.ir.Adventure.CURRENT_VERSION]. We must be
 * able to distinguish "no version" from any explicit integer.
 *
 * **Why one file:** DTOs form a single JSON contract and change together;
 * splitting them across several files would add navigation noise without
 * any benefit. This is an intentional exception to the "one class per
 * file" convention, not an accident.
 */
@Serializable
internal data class AdventureDto(
    val version: Int? = null,
    val title: String,
    val author: String = "",
    val description: String = "",
    @SerialName("initial_state")
    val initialState: InitialStateDto = InitialStateDto(),
    /**
     * Ф2.8: declared variable types. `null`/absent → empty map → the
     * reader's `TypeChecker` runs in **fallback** mode (infers types
     * from `tags` + `initialState`). Non-empty → strict mode (every
     * reference must be declared here).
     *
     * `Map<String, VariableDto>`, not `Map<String, VarType>`. See
     * [VariableDto] KDoc: `VarType` cannot express the "incomplete
     * declaration" state (`IntMin(min = null)` is not constructible),
     * which the parser must accept and downgrade.
     */
    val variables: Map<String, VariableDto> = emptyMap(),
    /**
     * Ф4.1: reusable choice templates. Flat list of
     * `ChoiceTemplateDto` — see [ChoiceTemplateDto] KDoc. Empty /
     * absent → `Adventure.choiceTemplates = emptyList()`.
     * **No consumer in Ф4.1**: parsed infrastructure only. Ф4.2
     * adds `Choice.templateRef` + `applyTemplate`.
     */
    @SerialName("choice_templates")
    val choiceTemplates: List<ChoiceTemplateDto> = emptyList(),
    /**
     * Ф4.7: reusable atomic procedures. Empty / absent → no subroutines.
     * Calls are referenced from [ChoiceDto.call] and nested
     * [SubroutineStepDto.call].
     */
    val subroutines: List<SubroutineDto> = emptyList(),
    val invariants: List<InvariantDto> = emptyList(),
    val tags: List<TagDefinitionDto> = emptyList(),
    @SerialName("death_scene")
    val deathScene: DeathSceneDto? = null,
    /** v3+; absent in v1/v2 files (migrated in `JsonToIr`). */
    @SerialName("start_scene")
    val startScene: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
internal data class ChapterDto(
    val id: String,
    val title: String = "",
    /**
     * **Legacy (v1).** In adventure files of version 1 this field carried
     * a step gate on the chapter. Since v2 the gate lives on the scene
     * ([SceneDto.gate]). Kept here so v1 files still parse; `JsonToIr`
     * migrates a non-zero value onto the last scene of the previous
     * chapter as `Gate(requiredSteps)`.
     */
    @SerialName("required_steps")
    val requiredSteps: Int = 0,
    val scenes: List<SceneDto> = emptyList(),
)

@Serializable
internal data class SceneDto(
    val id: String,
    val text: String,
    @SerialName("set_flags")
    val setFlags: List<String> = emptyList(),
    @SerialName("remove_flags")
    val removeFlags: List<String> = emptyList(),
    @SerialName("modify_stats")
    val modifyStats: Map<String, Int> = emptyMap(),
    val choices: List<ChoiceDto> = emptyList(),
    val gate: Int? = null,
    /**
     * v5+; absent in v1–v4 files. `null` means "field was not in the
     * source file". `JsonToIr` maps `null` to `false` (identity
     * migration v4 → v5). Adventure file format keeps the gate flat:
     * `"gate": N` + optional `"gate_repeatable": true`, so a simple
     * gate stays one key.
     */
    @SerialName("gate_repeatable")
    val gateRepeatable: Boolean? = null,
    /**
     * v4+; absent in v1–v3 files. `null` means "field was not in the
     * source file". `JsonToIr` maps `null` to `false` (identity
     * migration v3 → v4). Nullable rather than `Boolean = false` for
     * consistency with [AdventureDto.version] and [SceneDto.gate], and
     * so that a future migration can distinguish "author explicitly
     * wrote `is_ending: false`" from "author did not write the field".
     * Today both collapse to `Scene.isEnding = false`, so the
     * distinction is not observed.
     */
    @SerialName("is_ending")
    val isEnding: Boolean? = null,
    /**
     * Ф4.5: `"merge": true` marks this scene as a merge point.
     * `null` (field absent) → false (identity migration). See
     * `:engine/ir/Scene.isMerge`.
     */
    val merge: Boolean? = null,
    /**
     * Ф4.5: `"merge_sources": ["s1", "s2"]` — declared incoming
     * branches. Empty list (field absent) → no sources declared; the
     * analyzer (Ф4.6) will collect them from the `nextScene` graph.
     */
    @SerialName("merge_sources")
    val mergeSources: List<String> = emptyList(),
)

@Serializable
internal data class ChoiceDto(
    val id: String? = null,
    val text: String,
    @SerialName("next_scene")
    val nextScene: String,
    val condition: String? = null,
    @SerialName("set_flags")
    val setFlags: List<String> = emptyList(),
    @SerialName("remove_flags")
    val removeFlags: List<String> = emptyList(),
    @SerialName("modify_stats")
    val modifyStats: Map<String, Int> = emptyMap(),
    /**
     * Ф4.7: optional atomic subroutine call. The choice keeps
     * `next_scene` as the **return target**; the call runs before the
     * transition.
     */
    val call: SubroutineCallDto? = null,
)

@Serializable
internal data class TagDefinitionDto(
    val name: String,
    @SerialName("display_name")
    val displayName: String = "",
    val category: TagCategory = TagCategory.JOURNAL,
    val visible: Boolean = false,
)

@Serializable
internal data class InitialStateDto(
    val flags: List<String> = emptyList(),
    val stats: Map<String, Int> = emptyMap(),
)

@Serializable
internal data class DeathSceneDto(
    val id: String = "death_scene",
    /**
     * Ф4-infra (3b-6h): empty default — see `:engine/ir/DeathScene.text`
     * KDoc. Reader falls back to `R.string.reader_default_death_text`
     * in `AdventureReaderFragment.showDeathScreen` when this is empty.
     */
    val text: String = "",
    @SerialName("allow_step_back")
    val allowStepBack: Boolean = true,
    @SerialName("allow_chapter_restart")
    val allowChapterRestart: Boolean = true,
    @SerialName("allow_adventure_restart")
    val allowAdventureRestart: Boolean = true,
)

/**
 * Ф2.8: wire-format for a single variable declaration.
 *
 * [type] is a **string discriminator** (`"bool"`, `"int"`, `"int_min"`,
 * `"int_range"`, `"item_count"`, `"item_list"`), matching the
 * `@SerialName` values on
 * [com.myapp.adventure.engine.ir.types.VarType]. `min` / `max` are only
 * meaningful for `int_min` / `int_range`, `null` for the others.
 *
 * **Why not reuse `VarType` directly.** `VarType` is a `sealed class`
 * with polymorphic serialization, which produces
 * `{"type": "int_min", "min": 0}` — the same JSON shape as
 * `VariableDto`. But `VarType` cannot express the "incomplete
 * declaration" state: `IntMin(min = null)` is not constructible
 * (`min` is required). The parser must accept
 * `{"type": "int_min"}` (no `min`) from a partially-filled file and
 * downgrade to `Integer`. `VariableDto` can hold that state
 * (`min = null`); `VarType` cannot. See `JsonToIr.VariableDto.toVarType`
 * for the downgrade logic.
 */
@Serializable
internal data class VariableDto(
    val type: String,
    val min: Int? = null,
    val max: Int? = null,
)


/**
 * Ф4.1: wire-format for a single reusable choice template.
 *
 * The adventure file uses `choice_templates` (plural, snake_case) at
 * the top level and `next_scene` / `visibility_condition` /
 * `availability_condition` (snake_case) inside each entry, matching
 * `ChoiceDto.nextScene` and the general snake_case convention of
 * multi-word fields.
 *
 * **Flat-vs-typed distinction** (same as everywhere else): `text` is
 * a plain string; `visibilityCondition` / `availabilityCondition` are
 * condition **strings** parsed by `ConditionParser`; effects are the
 * three parallel fields `set_flags` / `remove_flags` /
 * `modify_stats`. The typed IR counterpart is `ChoiceTemplate`.
 *
 * **Why `parameters: List<VarDeclDto>`, not `Map<String, VarType>`.**
 * Parameters are an ordered list of typed declarations; `VarDeclDto`
 * has `name` inside the object (`{"name": "itemId", "type":
 * "item_count"}`), not as the map key. This mirrors how
 * `Adventure.variables` looks in JSON (`{"health": {"type": ...}}` —
 * name is the key), except that template parameters use list form
 * because order is meaningful for documentation and duplicates must
 * be an error (checked by `JsonToIr`).
 */
@Serializable
internal data class ChoiceTemplateDto(
    val id: String,
    val parameters: List<VarDeclDto> = emptyList(),
    val text: String,
    @SerialName("visibility_condition")
    val visibilityCondition: String? = null,
    @SerialName("availability_condition")
    val availabilityCondition: String? = null,
    @SerialName("set_flags")
    val setFlags: List<String> = emptyList(),
    @SerialName("remove_flags")
    val removeFlags: List<String> = emptyList(),
    @SerialName("modify_stats")
    val modifyStats: Map<String, Int> = emptyMap(),
    @SerialName("next_scene")
    val nextScene: String,
)

/**
 * Ф4.1: wire-format for a single typed parameter declaration.
 *
 * `name` + `type` + optional `min` / `max` (for `int_min` /
 * `int_range`). Field set is identical to `VariableDto` plus `name`;
 * the two types share the `type` / `min` / `max` downgrade logic
 * (see `JsonToIr.typeStringToVarType`).
 */
@Serializable
internal data class VarDeclDto(
    val name: String,
    val type: String,
    val min: Int? = null,
    val max: Int? = null,
)

/**
 * Ф4.7: wire-format for a reusable atomic subroutine.
 *
 * Flat body: each step is a [SubroutineStepDto] with an optional
 * condition string, the same three effect fields as scenes/choices, and an
 * optional nested call. This mirrors the IR `Subroutine` one-to-one except
 * for conditions (string → AST) and parameter types (string → `VarType`).
 */
@Serializable
internal data class SubroutineDto(
    val id: String,
    val inputs: List<VarDeclDto> = emptyList(),
    val outputs: List<VarDeclDto> = emptyList(),
    val body: List<SubroutineStepDto> = emptyList(),
)

@Serializable
internal data class SubroutineStepDto(
    val condition: String? = null,
    @SerialName("set_flags")
    val setFlags: List<String> = emptyList(),
    @SerialName("remove_flags")
    val removeFlags: List<String> = emptyList(),
    @SerialName("modify_stats")
    val modifyStats: Map<String, Int> = emptyMap(),
    val call: SubroutineCallDto? = null,
)

/**
 * Ф4.7: wire-format for a subroutine call.
 *
 * Same shape in both contexts: on a [ChoiceDto] the names refer to global
 * variables; inside a subroutine body they refer to the enclosing frame's
 * local parameters. `args` maps callee input → source name; `outputs` maps
 * callee output → target name. See `:engine/ir/SubroutineCall` KDoc.
 */
@Serializable
internal data class SubroutineCallDto(
    @SerialName("subroutine_id")
    val subroutineId: String,
    val args: Map<String, String> = emptyMap(),
    val outputs: Map<String, String> = emptyMap(),
)

/**
 * Ф3.5: wire-format for a single invariant.
 *
 * **Flat shape** (unlike the IR `Invariant`, which has typed
 * `condition: Condition` and sealed `outcome: Outcome`):
 * - `condition` is a condition string — same syntax as
 *   `ChoiceDto.condition`, parsed by `ConditionParser`.
 * - `outcome` is a flat string (`"death"` today). Future variants
 *   (`"ending"`, `"custom"`) extend the allowed set — `JsonToIr`
 *   maps them into `Outcome`.
 *
 * The flat-vs-typed distinction is intentional, same as with
 * `ChoiceDto.condition` (string) vs. `Choice.condition` (AST): the
 * author's file is a string, the runtime works on the AST.
 */
@Serializable
internal data class InvariantDto(
    val condition: String,
    val outcome: String,
)