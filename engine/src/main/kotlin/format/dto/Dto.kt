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
     * Ф3.5: global invariants. Flat list of `{"condition": "...",
     * "outcome": "death"}` objects. The condition is a string (parsed
     * by `JsonToIr` into a `Condition` AST); the outcome is a flat
     * string (`"death"` today — future variants add more). Empty /
     * absent → `Engine.evaluateInvariants` falls back to the
     * pre-Ф3.5 hardcoded `health <= 0 => death` rule.
     */
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
    val text: String = "💀 Вы погибли.",
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