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
 * **Deferred to Ф2.8:** `variables` (`Map<String, VarDeclDto>`). Not
 * present in `test_adventure.json` yet; add when the file gains it.
 *
 * **Why one file:** DTOs form a single JSON contract and change together;
 * splitting them across seven files would add navigation noise without any
 * benefit. This is an intentional exception to the "one class per file"
 * convention, not an accident.
 */
@Serializable
internal data class AdventureDto(
    val version: Int? = null,
    val title: String,
    val author: String = "",
    val description: String = "",
    @SerialName("initial_state")
    val initialState: InitialStateDto = InitialStateDto(),
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