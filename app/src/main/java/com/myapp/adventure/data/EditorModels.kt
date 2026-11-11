package com.myapp.adventure.data

import java.util.UUID

data class EditorScene(
    val uid: String = UUID.randomUUID().toString(),
    var id: String = "",
    var idIsAuto: Boolean = true,
    var title: String = "",
    var text: String = "",
    /**
     * Narrative chapter boundary. The reader does not use this field
     * for gates (Ф1.5.4 — chapters are pure narrative grouping); it
     * is used by the editor's map / stats / validator to group scenes
     * into chapters and to show the "🏁 Конец главы" badge.
     *
     * **Replaces the old "конец главы + requiredSteps" pair.** Before
     * b2, `isChapterEnd` also wrote `chapter.required_steps = N` on
     * the *next* chapter — that was the legacy v1 gate, migrated by
     * `JsonToIr` to `Gate(N)` on this scene. From b2, the gate is a
     * **separate** per-scene concept ([gateRequiredSteps] /
     * [gateRepeatable]), so an author can express four combinations:
     * chapter end without a gate, gate without a chapter end, both,
     * neither. The old behaviour (chapter end implicitly means a
     * gate) is preserved on load via `loadFromUri`'s v1 migration,
     * but is no longer how the UI models things.
     */
    var isChapterEnd: Boolean = false,
    /**
     * b2: per-scene step gate, `0` = no gate. Written as
     * `"gate": N` on the scene. Distinct from [isChapterEnd]
     * (narrative boundary) and [isEnding] (adventure ending).
     * Mutually compatible with both: a scene can be the last of a
     * chapter, an ending, and have a gate, independently.
     */
    var gateRequiredSteps: Int = 0,
    /**
     * b2: whether the gate re-engages on every visit
     * (`"gate_repeatable": true`). Only meaningful when
     * [gateRequiredSteps] > 0. Default `false` ("once passed, stays
     * passed in this playthrough") matches `Gate.repeatable`
     * default in `:engine/ir/Gate.kt` and the "least surprising"
     * policy confirmed 2026-11-07.
     */
    var gateRepeatable: Boolean = false,
    var isDeathScene: Boolean = false,
    var deathAllowStepBack: Boolean = true,
    var deathAllowChapterRestart: Boolean = true,
    var deathAllowAdventureRestart: Boolean = true,
    var isEnding: Boolean = false,
    val tags: MutableList<EditorTag> = mutableListOf(),
    val choices: MutableList<EditorChoice> = mutableListOf()
)

data class EditorChoice(
    val uid: String = UUID.randomUUID().toString(),
    var text: String = "",
    var nextSceneId: String = "",
    var conditionTag: String = "",
    val tags: MutableList<EditorTag> = mutableListOf()
)

data class EditorTag(
    var name: String = "",
    var value: Int = 0,
    var isStatModifier: Boolean = false,
    var category: TagCategory = TagCategory.JOURNAL,
    var displayName: String = "",
    var visible: Boolean = false
)

fun EditorScene.deepCopy(): EditorScene = copy(
    tags = tags.map { it.copy() }.toMutableList(),
    choices = choices.map { choice ->
        choice.copy(tags = choice.tags.map { it.copy() }.toMutableList())
    }.toMutableList()
)

data class RebuildResult(
    val oldId: String,
    val newId: String,
    val updatedReferences: Int
)