package com.myapp.adventure.data

import androidx.annotation.StringRes
import com.myapp.adventure.R
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
     * is used by the editor's stats / validator to group scenes
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
    /**
     * Ф4.5 (N5): the author marked this scene as a merge point. Round-
     * trips with `:app/data/Scene.merge` (save/load). **No editor UI in
     * 4.5** — the checkbox is Ф4.10; the field exists so that a scene
     * with `"merge": true` in JSON survives open→save.
     */
    var isMerge: Boolean = false,
    /**
     * Ф4.5 (N5): declared incoming branches. Round-trips with
     * `:app/data/Scene.mergeSources`. Empty → `null` in JSON (Gson
     * omits). Same UI deferral as [isMerge].
     */
    var mergeSources: List<String> = emptyList(),
    val tags: MutableList<EditorTag> = mutableListOf(),
    val choices: MutableList<EditorChoice> = mutableListOf()
)

data class EditorChoice(
    val uid: String = UUID.randomUUID().toString(),
    var text: String = "",
    var nextSceneId: String = "",
    var conditionTag: String = "",
    /**
     * Ф4.3.2b: "show this choice?" condition — distinct from
     * [conditionTag], which is "enable this choice?". Populated by
     * `AdventureEditorViewModel.applyTemplateToChoice()` from a
     * template's `visibilityCondition`; persisted to JSON as
     * `choices[].visibility_condition`. The reader (Ф4.4 / N4) will use
     * it to distinguish hidden vs disabled; today it is stored but
     * dormant (reader gates only on [conditionTag] via IR `condition`).
     */
    var visibilityConditionTag: String = "",
    val tags: MutableList<EditorTag> = mutableListOf(),
    /**
     * Ф4.3.2b: if this choice was expanded from a `ChoiceTemplate`, the
     * template's id. `null` for hand-written choices. Editor-only
     * metadata — the runtime sees only the expanded [text] /
     * [conditionTag] / [visibilityConditionTag] / [tags] /
     * [nextSceneId]. Used by the "update all uses" flow (Ф4.3.3).
     */
    var templateUid: String? = null,
    /**
     * Ф4.3.2b: arguments used when this choice was expanded. Empty map
     * for hand-written choices. Persisted to JSON as
     * `template_ref.args` so Ф4.3.3 can re-apply with the same args.
     */
    var templateArgs: Map<String, String> = emptyMap(),
    /**
     * Ф4.7/Ф4.8: optional atomic subroutine call. Executed after [tags]
     * (effects) and before [nextSceneId] (the return target). `null` for
     * ordinary choices. Edited by the "Использовать подпрограмму" dialog;
     * round-trips through `:app/data/Choice.call` and the IR
     * `Choice.call`.
     */
    var subroutineCall: EditorSubroutineCall? = null,
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
    // Ф4.5: freeze `mergeSources` so undo/redo snapshots cannot alias
    // the live editor state (same principle as `templateArgs`).
    mergeSources = mergeSources.toList(),
    tags = tags.map { it.copy() }.toMutableList(),
    choices = choices.map { choice ->
        choice.copy(
            tags = choice.tags.map { it.copy() }.toMutableList(),
            // Ф4.3.2b: `templateArgs` is `Map` (immutable interface), but
            // the caller could have passed a `MutableMap`. `toMap()`
            // freezes it so that undo/redo snapshots cannot alias the
            // live editor state.
            templateArgs = choice.templateArgs.toMap(),
            subroutineCall = choice.subroutineCall?.deepCopy(),
        )
    }.toMutableList()
)

data class RebuildResult(
    val oldId: String,
    val newId: String,
    val updatedReferences: Int
)

/**
 * Ф2.7 — UI-форма объявления переменной.
 *
 * Не переиспользует `:engine/ir/types/VarType` напрямую, потому что
 * UI нужна «промежуточная» форма: автор печатает `0` в `min` и ещё
 * не ввёл `max`. `VarType.IntRange(min, max)` требует оба значения
 * сразу — промежуточного состояния нет. Поэтому `kind + min? + max?`
 * (см. [toVarType] для конверсии в IR, permissive-fallback при
 * недозаполненных полях).
 *
 * @property name имя переменной (совпадает с ключом в `variables`).
 * @property kind тип из ограниченного набора (см. [VarKind]).
 * @property min нижняя граница, `null` если не применимо / не введена.
 * @property max верхняя граница, `null` если не применимо / не введена.
 */
data class EditorVarDecl(
    var name: String = "",
    var kind: VarKind = VarKind.BOOL,
    var min: Int? = null,
    var max: Int? = null,
)

/**
 * Ф2.7 — тип переменной в UI-редакторе.
 *
 * 1:1 с `VarType` из `:engine` (Bool / Integer / IntMin / IntRange /
 * ItemCount / ItemList). Отдельный enum нужен, чтобы `Spinner` показывал
 * человекочитаемые лейблы и чтобы UI не тянул IR-классы.
 */
enum class VarKind(@StringRes val displayNameRes: Int) {
    BOOL(R.string.var_kind_bool),
    INTEGER(R.string.var_kind_integer),
    INT_MIN(R.string.var_kind_int_min),
    INT_RANGE(R.string.var_kind_int_range),
    ITEM_COUNT(R.string.var_kind_item_count),
    ITEM_LIST(R.string.var_kind_item_list);

    /** Whether `min`/`max` are shown in the UI for this type. */
    val needsMin: Boolean
        get() = this == INT_MIN || this == INT_RANGE
    val needsMax: Boolean
        get() = this == INT_RANGE
}

/**
 * Конверсия UI-модели в IR-тип.
 *
 * **Permissive default.** Если `IntMin`/`IntRange` выбраны, но
 * `min`/`max` не заполнены — возвращает `Integer` (самый широкий
 * числовой тип). Смысл: одна недозаполненная декларация не должна
 * каскадить `UnknownVariable` на все ссылки на переменную через
 * fallback-таблицу. Вместо этого автор получит **Warning** от
 * `StoryValidator` («min/max не заполнены — тип понижен до Integer»)
 * и сможет исправить.
 *
 * @return `VarType` или `null`, если имя пустое (объявление не
 *   используется, не попадает в IR).
 */
fun EditorVarDecl.toVarType(): com.myapp.adventure.engine.ir.types.VarType? {
    if (name.isBlank()) return null

    /**
     * Ф4.3.1 — choice template in the editor.
     *
     * Mirrors `:engine/ir/ChoiceTemplate`, but uses the editor's vocabulary:
     * conditions are raw strings (like `EditorChoice.conditionTag`), effects
     * are `EditorTag` (like `EditorScene.tags` / `EditorChoice.tags`).
     * Parameters are `EditorVarDecl` (shared with the variables section;
     * supports the intermediate state "min entered, max not yet").
     *
     * `uid` is the editor's stable id (survives rename), same role as
     * `EditorScene.uid` / `EditorChoice.uid`. `id` is the `ChoiceTemplate.id`
     * from the IR (used as `templateRef.templateId` when `applyTemplate`
     * runs). May be empty on creation — the author fills it in;
     * `buildAdventure` skips templates with empty `id` on save (validation
     * warns separately).
     */
    data class EditorTemplate(
        val uid: String = java.util.UUID.randomUUID().toString(),
        var id: String = "",
        var text: String = "",
        var visibilityCondition: String = "",
        var availabilityCondition: String = "",
        val parameters: MutableList<EditorVarDecl> = mutableListOf(),
        val effects: MutableList<EditorTag> = mutableListOf(),
        var nextSceneId: String = "",
    )

    fun EditorTemplate.deepCopy(): EditorTemplate = copy(
        parameters = parameters.map { it.copy() }.toMutableList(),
        effects = effects.map { it.copy() }.toMutableList(),
    )

    val minLocal = min
    val maxLocal = max
    return when (kind) {
        VarKind.BOOL -> com.myapp.adventure.engine.ir.types.VarType.Bool
        VarKind.INTEGER -> com.myapp.adventure.engine.ir.types.VarType.Integer
        VarKind.INT_MIN -> minLocal?.let {
            com.myapp.adventure.engine.ir.types.VarType.IntMin(it)
        } ?: com.myapp.adventure.engine.ir.types.VarType.Integer
        VarKind.INT_RANGE -> if (minLocal != null && maxLocal != null) {
            com.myapp.adventure.engine.ir.types.VarType.IntRange(minLocal, maxLocal)
        } else {
            com.myapp.adventure.engine.ir.types.VarType.Integer
        }
        VarKind.ITEM_COUNT -> com.myapp.adventure.engine.ir.types.VarType.ItemCount
        VarKind.ITEM_LIST -> com.myapp.adventure.engine.ir.types.VarType.ItemList
    }
}

/**
 * Ф4.3.1 — choice template in the editor.
 *
 * Mirrors `:engine/ir/ChoiceTemplate`, but uses the editor's vocabulary:
 * conditions are raw strings (like `EditorChoice.conditionTag`), effects
 * are `EditorTag` (like `EditorScene.tags` / `EditorChoice.tags`).
 * Parameters are `EditorVarDecl` (shared with the variables section;
 * supports the intermediate state "min entered, max not yet").
 *
 * `uid` is the editor's stable id (survives rename), same role as
 * `EditorScene.uid` / `EditorChoice.uid`. `id` is the `ChoiceTemplate.id`
 * from the IR (used as `templateRef.templateId` when `applyTemplate`
 * runs). May be empty on creation — the author fills it in;
 * `buildAdventure` skips templates with empty `id` on save (validation
 * warns separately).
 */
data class EditorTemplate(
    val uid: String = UUID.randomUUID().toString(),
    var id: String = "",
    var text: String = "",
    var visibilityCondition: String = "",
    var availabilityCondition: String = "",
    val parameters: MutableList<EditorVarDecl> = mutableListOf(),
    val effects: MutableList<EditorTag> = mutableListOf(),
    var nextSceneId: String = "",
)

fun EditorTemplate.deepCopy(): EditorTemplate = copy(
    parameters = parameters.map { it.copy() }.toMutableList(),
    effects = effects.map { it.copy() }.toMutableList(),
)

/**
 * Ф4.7/Ф4.8: editor model of an atomic subroutine.
 *
 * Mirrors `:engine/ir/Subroutine`: typed [inputs] / [outputs] and a flat
 * [body] of guarded steps. [uid] is the editor identity (survives id
 * rename); [id] is the JSON/IR id used by calls. The body sees only
 * inputs/outputs — the editor UI shows that local scope, and the engine
 * type checker enforces it.
 */
data class EditorSubroutine(
    val uid: String = UUID.randomUUID().toString(),
    var id: String = "",
    val inputs: MutableList<EditorVarDecl> = mutableListOf(),
    val outputs: MutableList<EditorVarDecl> = mutableListOf(),
    val body: MutableList<EditorSubroutineStep> = mutableListOf(),
)

fun EditorSubroutine.deepCopy(): EditorSubroutine = copy(
    inputs = inputs.map { it.copy() }.toMutableList(),
    outputs = outputs.map { it.copy() }.toMutableList(),
    body = body.map { it.deepCopy() }.toMutableList(),
)

/**
 * Ф4.7/Ф4.8: one flat guarded step of [EditorSubroutine].
 *
 * `condition` is a raw string (same as `EditorChoice.conditionTag`);
 * [effects] are [EditorTag]s serialized to `set_flags` / `remove_flags` /
 * `modify_stats`; [call] is an optional nested call binding local names.
 * No nested `if` tree — an `else` is a following step with a negated
 * condition (decision A1).
 */
data class EditorSubroutineStep(
    val uid: String = UUID.randomUUID().toString(),
    var condition: String = "",
    val effects: MutableList<EditorTag> = mutableListOf(),
    var call: EditorSubroutineCall? = null,
)

fun EditorSubroutineStep.deepCopy(): EditorSubroutineStep = copy(
    effects = effects.map { it.copy() }.toMutableList(),
    call = call?.deepCopy(),
)

/**
 * Ф4.7/Ф4.8: editor model of a subroutine call.
 *
 * [subroutineId] is the callee's JSON/IR id. On a choice, [args] /
 * [outputs] values are global variable names; inside a subroutine body
 * they are names of the enclosing frame's inputs/outputs.
 */
data class EditorSubroutineCall(
    var subroutineId: String = "",
    var args: Map<String, String> = emptyMap(),
    var outputs: Map<String, String> = emptyMap(),
)

fun EditorSubroutineCall.deepCopy(): EditorSubroutineCall = copy(
    args = args.toMap(),
    outputs = outputs.toMap(),
)

/**
 * Ф4.8c: a stable id for a duplicated subroutine.
 *
 * Keeps the source id when it is free; otherwise appends `_2`, `_3`, …
 * (or `subroutine_2` for a blank source). If the source already ends in a
 * numeric suffix (`consume_2`), the suffix is replaced rather than stacked
 * (`consume_3`). This is deliberately predictable for non-programmers —
 * the new card is immediately visible and can be renamed.
 */
fun uniqueSubroutineId(baseId: String, existingIds: Set<String>): String {
    val clean = baseId.trim()
    if (clean.isNotEmpty() && clean !in existingIds) return clean
    val root = clean.replace(Regex("_\\d+$"), "").ifEmpty { "subroutine" }
    if (root !in existingIds) return root
    var n = 2
    while ("${root}_$n" in existingIds) n++
    return "${root}_$n"
}