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
enum class VarKind(val displayName: String) {
    BOOL("Bool (флаг)"),
    INTEGER("Integer (любое целое)"),
    INT_MIN("IntMin (>= min, без верха)"),
    INT_RANGE("IntRange (min..max)"),
    ITEM_COUNT("ItemCount (счётчик предмета)"),
    ITEM_LIST("ItemList (список)");

    /** Нужны ли `min`/`max` в UI для этого типа. */
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
    // Копируем `var`-поля в локальные `val` перед `when`: smart cast
    // на mutable property невозможен (компилятор не гарантирует, что
    // между проверкой и использованием значение не изменилось).
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