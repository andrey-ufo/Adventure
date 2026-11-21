package com.myapp.adventure.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Chapter
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.DeathScene
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorSnapshot
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.EditorUiState
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.data.PlayerState
import com.myapp.adventure.data.VariableDto
import com.myapp.adventure.data.VarKind
import com.myapp.adventure.data.toVarType
import com.myapp.adventure.R
import com.myapp.adventure.data.RebuildResult
import com.myapp.adventure.data.Scene
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.data.TemplateRefDto
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.data.ChoiceTemplateDto
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.VarDeclDto
import com.myapp.adventure.engine.ir.Adventure as IrAdventure
import com.myapp.adventure.engine.ir.Chapter as IrChapter
import com.myapp.adventure.engine.ir.Choice as IrChoice
import com.myapp.adventure.engine.ir.ChoiceId as IrChoiceId
import com.myapp.adventure.engine.ir.Condition as IrCondition
import com.myapp.adventure.engine.ir.DeathScene as IrDeathScene
import com.myapp.adventure.engine.ir.Effect as IrEffect
import com.myapp.adventure.engine.ir.Gate as IrGate
import com.myapp.adventure.engine.ir.InitialState as IrInitialState
import com.myapp.adventure.engine.ir.Scene as IrScene
import com.myapp.adventure.engine.ir.SceneId as IrSceneId
import com.myapp.adventure.engine.ir.TagCategory as IrTagCategory
import com.myapp.adventure.engine.ir.TagDefinition as IrTagDefinition
import com.myapp.adventure.engine.ir.ChoiceTemplate as IrChoiceTemplate
import com.myapp.adventure.engine.ir.applyTemplate as irApplyTemplate
import com.myapp.adventure.engine.ir.types.VarDecl as IrVarDecl
import com.myapp.adventure.engine.parse.ConditionParser
import com.myapp.adventure.engine.parse.ConditionWriter
import com.myapp.adventure.engine.parse.ParseError
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdventureEditorViewModel(application: Application) : AndroidViewModel(application) {

    val scenes = mutableListOf<EditorScene>()
    var title: String = ""
    var author: String = ""
    var description: String = ""

    // ------------------------------------------------------------------
    // Compose state (Phase 3.1a)
    //
    // `uiState` is an immutable snapshot of the editor. Compose screens
    // read it; the View-based adapters keep reading the mutable lists
    // below. Every mutating entry point calls `commitState()` so the
    // two stay in sync. See EditorUiState KDoc for the full rationale.
    // ------------------------------------------------------------------

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun commitState() {
        // UI-only fields (saveStatus, hasUnsavedChanges, menuExpanded) are
        // owned by the setters below — commitState must not reset them,
        // otherwise a "Saving…" flicker would be lost on every keystroke.
        val prev = _uiState.value
        val rawCanUndo = canUndo()
        val undoEnabled = rawCanUndo || (prev.hasUnsavedChanges && isHistoryInitialized())
        val redoEnabled = canRedo()
        _uiState.value = EditorUiState(
            title = title,
            author = author,
            description = description,
            variables = variables.map { it.copy() },
            templates = templates.map { it.deepCopy() },
            initialStateTags = initialStateTags.map { it.copy() },
            scenes = scenes.map { it.deepCopy() },
            expandedScenes = expandedScenes.toSet(),
            expandedSceneSettings = expandedSceneSettings.toSet(),
            expandedChoices = expandedChoices.toSet(),
            expandedMapChapters = expandedMapChapters.toSet(),
            isHistoryInitialized = isHistoryInitialized(),
            pendingJumpToSceneUid = pendingJumpToSceneUid,
            saveStatus = prev.saveStatus,
            hasUnsavedChanges = prev.hasUnsavedChanges,
            menuExpanded = prev.menuExpanded,
            variablesExpanded = prev.variablesExpanded,
            initialStateExpanded = prev.initialStateExpanded,
            undoEnabled = undoEnabled,
            redoEnabled = redoEnabled,
        )
    }

    /** Updates the toolbar's save-status text; never touches list contents. */
    fun setSaveStatus(status: SaveStatus) {
        _uiState.value = _uiState.value.copy(saveStatus = status)
    }

    /**
     * Updates the "has unsaved changes" flag, which feeds both the
     * debounce-save pipeline (in the Fragment) and the toolbar's undo
     * button (an "editing" adventure can be undone back to its saved
     * snapshot even if `history.size == 1`).
     */
    fun setHasUnsavedChanges(value: Boolean) {
        val undoEnabled = canUndo() || (value && isHistoryInitialized())
        _uiState.value = _uiState.value.copy(
            hasUnsavedChanges = value,
            undoEnabled = undoEnabled,
        )
    }

    /** Toggles the collapsible actions menu (undo/redo/save/load/…). */
    fun toggleMenuExpanded() {
        _uiState.value = _uiState.value.copy(menuExpanded = !_uiState.value.menuExpanded)
    }

    val initialStateTags: MutableList<EditorTag> = mutableListOf()
    var initialStateExpanded: Boolean = false

    /**
     * Ф2.7: объявления переменных. Источник истины для UI
     * ([VariablesAdapter]) и для IR/JSON-сборки
     * ([buildIrAdventure] / [buildAdventure]).
     * Пока пусто — TypeChecker работает в fallback-режиме.
     */
    val variables: MutableList<EditorVarDecl> = mutableListOf()
    var variablesExpanded: Boolean = false

    /**
     * Ф4.3.1: reusable choice templates. Editor-model mirrors of
     * `Adventure.choiceTemplates` (IR / JSON). Stored in editor state so
     * that `loadFromUri` → edit → `saveAdventure` preserves them. Before
     * Ф4.3.1, opening a file with `choice_templates` and saving silently
     * lost the templates — that data-loss bug is fixed by this field +
     * the load / save round-trip.
     */
    val templates: MutableList<EditorTemplate> = mutableListOf()

    /**
     * Ф4.3.2a-fix1: working copy of the template currently being edited,
     * or `null` if no dialog is open.
     *
     * **Why in the ViewModel.** `AlertDialog` (built via
     * `AlertDialog.Builder`) is dismissed on configuration change
     * (rotation, theme switch, font-scale). Its content is lost with
     * the Activity. `ViewModel` survives config change, so storing the
     * working copy here lets `TemplateListFragment.onViewCreated`
     * detect "a dialog was open" and re-create it with all fields
     * (including unsaved edits) intact.
     *
     * **Lifecycle.** Set by `TemplateListFragment.showEditTemplateDialog`
     * when the dialog opens; cleared on "Сохранить" / "Отмена" /
     * system back. **Not** cleared by config change — that is what
     * makes the restore path work.
     *
     * **Why not `DialogFragment`.** `DialogFragment` is the canonical
     * Android solution and would also work, but it would move
     * `showEditTemplateDialog` (~200 lines) into a separate class and
     * require `onSaveInstanceState` plumbing for `EditorTemplate` (which
     * has no `Parcelable` / no `@Serializable`, so state saving would
     * need a custom `Gson` adapter). `ViewModel` state is ~15 lines and
     * covers the same case (rotation), which is the one the author
     * actually hits in practice. Process death (low-memory kill) still
     * loses the dialog — acceptable for pre-alpha; would be closed by
     * `DialogFragment` later if it becomes a real problem.
     */
    var editingTemplate: EditorTemplate? = null

    private val expandedScenes = mutableSetOf<String>()
    private val expandedChoices = mutableSetOf<String>()
    private val expandedMapChapters = mutableSetOf<Int>()

    var pendingJumpToSceneUid: String? = null

    private val history = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private val context get() = getApplication<Application>().applicationContext

    // ---------- Аккордеон сцен ----------

    fun isSceneExpanded(uid: String): Boolean = uid in expandedScenes
    fun expandScene(uid: String) {
        expandedScenes.add(uid)
        commitState()
    }

    fun toggleSceneExpanded(uid: String): Boolean {
        val result = if (uid in expandedScenes) {
            expandedScenes.remove(uid)
            false
        } else {
            expandedScenes.add(uid)
            true
        }
        commitState()
        return result
    }

    fun collapseAllScenes() {
        expandedScenes.clear()
        commitState()
    }

    private fun pruneExpandedScenes() {
        val currentUids = scenes.map { it.uid }.toSet()
        expandedScenes.retainAll(currentUids)
        expandedSceneSettings.retainAll(currentUids)
    }

    // ---------- Аккордеон: настройки сцены (2d-ii) ----------
    //
    // "Настройки сцены" — collapsed sub-block of a scene that holds
    // the authoring checkboxes (chapter end / step gate / ending /
    // death scene + their sub-options). Collapsed by default: a
    // scene with all four flags would otherwise be 6 checkboxes tall
    // and push choices off-screen. State is per-scene (per uid) and
    // survives scrolling; it is pruned on scene deletion alongside
    // `expandedScenes`.
    private val expandedSceneSettings = mutableSetOf<String>()

    fun isSceneSettingsExpanded(uid: String): Boolean = uid in expandedSceneSettings

    fun toggleSceneSettings(uid: String): Boolean {
        val result = if (uid in expandedSceneSettings) {
            expandedSceneSettings.remove(uid)
            false
        } else {
            expandedSceneSettings.add(uid)
            true
        }
        commitState()
        return result
    }

    // ---------- Аккордеон выборов ----------

    fun isChoiceExpanded(uid: String): Boolean = uid in expandedChoices

    fun toggleChoiceExpanded(uid: String): Boolean {
        val result = if (uid in expandedChoices) {
            expandedChoices.remove(uid)
            false
        } else {
            expandedChoices.add(uid)
            true
        }
        commitState()
        return result
    }

    fun collapseAllChoices() {
        expandedChoices.clear()
        commitState()
    }

    private fun pruneExpandedChoices() {
        val currentUids = scenes.flatMap { it.choices.map { c -> c.uid } }.toSet()
        expandedChoices.retainAll(currentUids)
    }

    // ---------- Карта: аккордеон глав ----------

    fun isMapChapterExpanded(index: Int): Boolean = index in expandedMapChapters

    fun toggleMapChapterExpanded(index: Int) {
        if (index in expandedMapChapters) expandedMapChapters.remove(index)
        else expandedMapChapters.add(index)
        commitState()
    }

    fun ensureFirstMapChapterExpanded(totalChapters: Int) {
        if (expandedMapChapters.isEmpty() && totalChapters > 0) {
            expandedMapChapters.add(0)
            commitState()
        }
    }

    fun collapseAllMapChapters() {
        expandedMapChapters.clear()
        commitState()
    }

    // ---------- История Undo/Redo ----------

    fun isHistoryInitialized(): Boolean = history.isNotEmpty()

    fun initHistory() {
        history.clear()
        redoStack.clear()
        history.addLast(takeSnapshot())
        commitState()
    }

    fun takeSnapshot(): EditorSnapshot = EditorSnapshot(
        title = title,
        author = author,
        description = description,
        variables = variables.map { it.copy() },
        templates = templates.map { it.deepCopy() },
        initialStateTags = initialStateTags.map { it.copy() },
        scenes = scenes.map { it.deepCopy() }
    )

    fun restoreSnapshot(snapshot: EditorSnapshot) {
        title = snapshot.title
        author = snapshot.author
        description = snapshot.description
        variables.clear()
        variables.addAll(snapshot.variables.map { it.copy() })
        templates.clear()
        templates.addAll(snapshot.templates.map { it.deepCopy() })
        initialStateTags.clear()
        initialStateTags.addAll(snapshot.initialStateTags.map { it.copy() })
        scenes.clear()
        scenes.addAll(snapshot.scenes.map { it.deepCopy() })
        pruneExpandedScenes()
        pruneExpandedChoices()
        commitState()
    }

    fun commitSnapshot() {
        val current = takeSnapshot()
        if (current == history.lastOrNull()) return
        history.addLast(current)
        while (history.size > MAX_HISTORY) history.removeFirst()
        redoStack.clear()
        commitState()
    }

    fun undo(): Boolean {
        if (history.size < 2) return false
        val current = history.removeLast()
        redoStack.addLast(current)
        restoreSnapshot(history.last())
        commitState()
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        history.addLast(next)
        while (history.size > MAX_HISTORY) history.removeFirst()
        restoreSnapshot(next)
        commitState()
        return true
    }

    fun canUndo(): Boolean = history.size >= 2
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ---------- Транслитерация и ID ----------

    fun transliterate(text: String): String {
        val translitMap = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D",
            'Е' to "E", 'Ё' to "Yo", 'Ж' to "Zh", 'З' to "Z", 'И' to "I",
            'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
            'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
            'У' to "U", 'Ф' to "F", 'Х' to "H", 'Ц' to "Ts", 'Ч' to "Ch",
            'Ш' to "Sh", 'Щ' to "Sch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "",
            'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
        )
        return text.map { translitMap[it] ?: it.toString() }.joinToString("")
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim().replace(" ", "_").ifEmpty { "adventure" }
    }

    fun generateSceneId(title: String, excludeIds: Set<String> = emptySet()): String {
        val base = transliterate(title).lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trimEnd('_')
            .ifEmpty { "scene" }

        var id = base
        var counter = 2
        while (id in excludeIds || scenes.any { it.id == id }) {
            id = "${base}_$counter"
            counter++
        }
        return id
    }

    // ---------- ID-заморозка и пересборка ----------

    fun freezeSceneId(sceneUid: String): Boolean {
        val scene = scenes.find { it.uid == sceneUid } ?: return false
        if (!scene.idIsAuto) return false
        if (scene.title.isBlank()) return false

        val oldId = scene.id
        scene.id = ""
        val newId = generateSceneId(scene.title)
        scene.id = newId
        scene.idIsAuto = false

        if (newId == oldId) {
            commitState()
            return false
        }
        val updatedRefs = updateReferences(oldId, newId)
        Logger.log(context, "ID сцены '${scene.title}' заморожен: '$oldId' → '$newId', обновлено ссылок: $updatedRefs")
        commitState()
        return true
    }

    fun rebuildSceneId(sceneUid: String): RebuildResult? {
        val scene = scenes.find { it.uid == sceneUid } ?: return null
        if (scene.title.isBlank()) return null

        val oldId = scene.id
        scene.id = ""
        val newId = generateSceneId(scene.title)
        scene.id = newId
        scene.idIsAuto = false

        val updatedRefs = if (newId != oldId) updateReferences(oldId, newId) else 0
        Logger.log(context, "ID сцены пересобран: '$oldId' → '$newId', обновлено ссылок: $updatedRefs")
        commitState()
        return RebuildResult(oldId, newId, updatedRefs)
    }

    fun previewRebuiltId(sceneUid: String): String? {
        val scene = scenes.find { it.uid == sceneUid } ?: return null
        if (scene.title.isBlank()) return null
        val oldId = scene.id
        scene.id = ""
        val preview = generateSceneId(scene.title)
        scene.id = oldId
        return preview
    }

    fun freezeAllPendingIds() {
        scenes.toList().forEach { scene ->
            if (scene.idIsAuto) {
                if (scene.title.isNotBlank()) {
                    freezeSceneId(scene.uid)
                } else {
                    scene.idIsAuto = false
                }
            }
        }
        commitState()
    }

    private fun updateReferences(oldId: String, newId: String): Int {
        if (oldId.isEmpty() || oldId == newId) return 0
        var count = 0
        scenes.forEach { other ->
            other.choices.forEach { c ->
                if (c.nextSceneId == oldId) {
                    c.nextSceneId = newId
                    count++
                }
            }
        }
        return count
    }

    // ---------- Изменения структуры ----------

    fun addScene() {
        val newScene = EditorScene()
        scenes.add(newScene)
        expandedScenes.add(newScene.uid)
        Logger.log(context, "Добавлена сцена, индекс: ${scenes.size - 1}")
        commitState()
    }

    fun addSceneWithTitle(title: String): EditorScene {
        val trimmed = title.trim()
        val newScene = EditorScene(
            title = trimmed,
            idIsAuto = trimmed.isEmpty()
        )
        if (trimmed.isNotEmpty()) {
            newScene.id = generateSceneId(trimmed)
        }
        scenes.add(newScene)
        expandedScenes.add(newScene.uid)
        Logger.log(context, "Добавлена сцена '${trimmed.ifEmpty { "(без названия)" }}', индекс: ${scenes.size - 1}")
        commitState()
        return newScene
    }

    fun removeScene(index: Int) {
        if (index in scenes.indices) {
            val removed = scenes.removeAt(index)
            expandedScenes.remove(removed.uid)
            Logger.log(context, "Удалена сцена: ${removed.title.ifEmpty { "(без названия)" }}, индекс: $index")
            commitState()
        }
    }

    fun setDeathScene(sceneUid: String, enabled: Boolean) {
        if (enabled) {
            scenes.forEach { if (it.uid != sceneUid) it.isDeathScene = false }
        }
        scenes.find { it.uid == sceneUid }?.isDeathScene = enabled
        commitState()
    }

    fun addChoice(sceneIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        val choice = EditorChoice()
        scene.choices.add(choice)
        expandedChoices.add(choice.uid)
        Logger.log(context, "Добавлен выбор в сцену '${scene.title}' (индекс $sceneIndex)")
        commitState()
    }

    fun removeChoice(sceneIndex: Int, choiceIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        if (choiceIndex in scene.choices.indices) {
            scene.choices.removeAt(choiceIndex)
            Logger.log(context, "Удалён выбор из сцены '${scene.title}' (индекс сцены $sceneIndex, выбор $choiceIndex)")
            commitState()
        }
    }

    fun addTag(sceneIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        scene.tags.add(EditorTag())
        Logger.log(context, "Добавлена метка в сцену '${scene.title}' (индекс $sceneIndex)")
        commitState()
    }

    fun removeTag(sceneIndex: Int, tagIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        if (tagIndex in scene.tags.indices) {
            val removedTag = scene.tags.removeAt(tagIndex)
            Logger.log(context, "Удалена метка '${removedTag.name}' из сцены '${scene.title}' (индекс сцены $sceneIndex)")
            commitState()
        }
    }

    fun getSceneOptions(): List<Pair<String, String>> {
        val fallback = context.getString(R.string.label_without_title)
        return scenes.map { scene ->
            scene.title.ifEmpty { fallback } to scene.id
        }
    }

    // ---------- Начальное состояние ----------

    fun addInitialStateTag() {
        initialStateTags.add(EditorTag(category = TagCategory.JOURNAL))
        commitState()
    }

    fun updateInitialStateExpanded(expanded: Boolean) {
        if (initialStateExpanded == expanded) return
        initialStateExpanded = expanded
        _uiState.value = _uiState.value.copy(initialStateExpanded = expanded)
    }

    fun updateInitialStateTagName(index: Int, name: String) {
        initialStateTags.getOrNull(index)?.let { if (it.name != name) it.name = name }
    }

    fun updateInitialStateTagValue(index: Int, value: Int) {
        initialStateTags.getOrNull(index)?.let { if (it.value != value) it.value = value }
    }

    fun updateInitialStateTagDisplay(index: Int, display: String) {
        initialStateTags.getOrNull(index)?.let { if (it.displayName != display) it.displayName = display }
    }

    fun updateInitialStateTagCategory(index: Int, category: TagCategory) {
        val tag = initialStateTags.getOrNull(index) ?: return
        if (tag.category == category) return
        tag.category = category
        // Matches `renderTagRow` semantics: switching to ITEM with a
        // zero value sets it to 1 (an item counter without a count is
        // meaningless).
        if (category == TagCategory.ITEM && tag.value == 0) tag.value = 1
    }

    fun updateInitialStateTagVisible(index: Int, visible: Boolean) {
        initialStateTags.getOrNull(index)?.let { if (it.visible != visible) it.visible = visible }
    }

    // ---------- Ф2.7: переменные ----------


    fun updateVariablesExpanded(expanded: Boolean) {
        if (variablesExpanded == expanded) return
        variablesExpanded = expanded
        _uiState.value = _uiState.value.copy(variablesExpanded = expanded)
    }

    fun toggleVariablesExpanded() {
        updateVariablesExpanded(!variablesExpanded)
    }

    /**
     * Mutates a variable's name in place. No `commitState()` — the caller
     * (Fragment `markDirty()`) does one commit per keystroke; see
     * Phase 3.1b KDoc for the rationale. `commitState()` would deep-copy
     * every scene on each character.
     */
    fun updateVariableName(index: Int, name: String) {
        val v = variables.getOrNull(index) ?: return
        if (v.name == name) return
        v.name = name
    }

    fun updateVariableKind(index: Int, kind: VarKind) {
        val v = variables.getOrNull(index) ?: return
        if (v.kind == kind) return
        v.kind = kind
    }

    fun updateVariableMin(index: Int, min: Int?) {
        val v = variables.getOrNull(index) ?: return
        if (v.min == min) return
        v.min = min
    }

    fun updateVariableMax(index: Int, max: Int?) {
        val v = variables.getOrNull(index) ?: return
        if (v.max == max) return
        v.max = max
    }

    fun addVariable() {
        variables.add(EditorVarDecl())
        Logger.log(context, "Добавлена переменная, индекс: ${variables.size - 1}")
        commitState()
    }

    fun removeVariable(index: Int) {
        if (index in variables.indices) {
            val removed = variables.removeAt(index)
            Logger.log(
                context,
                "Удалена переменная '${removed.name.ifEmpty { "(без имени)" }}', индекс: $index",
            )
            commitState()
        }
    }

    // ---------- Ф4.3.1: choice templates ----------

    fun addTemplate() {
        templates.add(EditorTemplate())
        Logger.log(context, "Добавлен шаблон, индекс: ${templates.size - 1}")
        commitState()
    }

    fun removeTemplate(index: Int) {
        if (index in templates.indices) {
            val removed = templates.removeAt(index)
            Logger.log(
                context,
                "Удалён шаблон '${removed.id.ifEmpty { "(без id)" }}', индекс: $index",
            )
            commitState()
        }
    }

    /**
     * Ф4.3.1: `ChoiceTemplateDto` (loaded from JSON) → `EditorTemplate`
     * (UI form).
     *
     * Effects are converted to `EditorTag` with the default `JOURNAL`
     * category — same as for scene / choice when loading without tag
     * definitions (see `AdventureEditorFragment.buildTagList`). The
     * category is restored on the next edit via `VariablesAdapter` /
     * `renderTagRow`; on save, `serializeTags` does not depend on the
     * category — `isStatModifier` + `value` reconstruct
     * `set_flags` / `remove_flags` / `modify_stats`.
     *
     * Unknown parameter `type` → `VarKind.INTEGER` (permissive default,
     * same as `EditorVarDecl.toVarType()`).
     */
    fun editorTemplateFromDto(dto: ChoiceTemplateDto): EditorTemplate {
        val parameters = dto.parameters.orEmpty().map { p ->
            val kind = when (p.type) {
                "bool" -> VarKind.BOOL
                "int" -> VarKind.INTEGER
                "int_min" -> VarKind.INT_MIN
                "int_range" -> VarKind.INT_RANGE
                "item_count" -> VarKind.ITEM_COUNT
                "item_list" -> VarKind.ITEM_LIST
                else -> VarKind.INTEGER
            }
            EditorVarDecl(name = p.name, kind = kind, min = p.min, max = p.max)
        }.toMutableList()
        val effects = mutableListOf<EditorTag>()
        for (flag in dto.setFlags.orEmpty()) {
            effects.add(EditorTag(name = flag, value = 0, isStatModifier = false, category = TagCategory.JOURNAL))
        }
        for (flag in dto.removeFlags.orEmpty()) {
            effects.add(EditorTag(name = "!$flag", value = 0, isStatModifier = false, category = TagCategory.JOURNAL))
        }
        for ((stat, value) in dto.modifyStats.orEmpty()) {
            effects.add(EditorTag(name = stat, value = value, isStatModifier = true, category = TagCategory.JOURNAL))
        }
        return EditorTemplate(
            id = dto.id,
            text = dto.text,
            visibilityCondition = dto.visibilityCondition ?: "",
            availabilityCondition = dto.availabilityCondition ?: "",
            parameters = parameters,
            effects = effects,
            nextSceneId = dto.nextScene,
        )
    }

    /**
     * Ф2.7: конверсия `VariableDto` (загруженная из JSON) → `EditorVarDecl`
     * (UI-форма). Unknown `type` → `Integer` (permissive default — не
     * терять переменную, даже если в JSON оказалось незнакомое значение;
     * fallback не каскадит `UnknownVariable`).
     */
    fun editorVarDeclFromDto(name: String, dto: VariableDto): EditorVarDecl {
        return when (dto.type) {
            "bool" -> EditorVarDecl(name, VarKind.BOOL)
            "int" -> EditorVarDecl(name, VarKind.INTEGER)
            "int_min" -> EditorVarDecl(name, VarKind.INT_MIN, min = dto.min)
            "int_range" -> EditorVarDecl(name, VarKind.INT_RANGE, min = dto.min, max = dto.max)
            "item_count" -> EditorVarDecl(name, VarKind.ITEM_COUNT)
            "item_list" -> EditorVarDecl(name, VarKind.ITEM_LIST)
            else -> EditorVarDecl(name, VarKind.INTEGER)
        }
    }

    /**
     * Ф2.7: `MutableList<EditorVarDecl>` → JSON-map для Gson-save.
     * Пустая коллекция → `null` (Gson опускает поле, JSON чистый).
     * Имена с пустыми `name` игнорируются — валидация покажет warning.
     */
    private fun buildVariablesDto(): Map<String, VariableDto>? {
        if (variables.isEmpty()) return null
        val result = mutableMapOf<String, VariableDto>()
        for (v in variables) {
            val clean = v.name.trim()
            if (clean.isEmpty()) continue
            val dto = when (v.kind) {
                VarKind.BOOL -> VariableDto("bool")
                VarKind.INTEGER -> VariableDto("int")
                VarKind.INT_MIN -> VariableDto("int_min", min = v.min)
                VarKind.INT_RANGE -> VariableDto("int_range", min = v.min, max = v.max)
                VarKind.ITEM_COUNT -> VariableDto("item_count")
                VarKind.ITEM_LIST -> VariableDto("item_list")
            }
            result[clean] = dto
        }
        return result.ifEmpty { null }
    }

    fun removeInitialStateTag(index: Int) {
        if (index in initialStateTags.indices) {
            initialStateTags.removeAt(index)
            commitState()
        }
    }

    fun buildInitialState(): PlayerState? {
        if (initialStateTags.isEmpty()) return null

        val flags = mutableListOf<String>()
        val stats = mutableMapOf<String, Int>()

        for (tag in initialStateTags) {
            if (tag.name.isBlank() || tag.name.startsWith("!")) continue
            val effectiveCategory = if (tag.name == "health") TagCategory.HEALTH else tag.category
            when (effectiveCategory) {
                TagCategory.HEALTH, TagCategory.ITEM -> {
                    stats[tag.name] = tag.value
                }
                TagCategory.JOURNAL -> {
                    if (tag.isStatModifier || tag.value != 0) {
                        stats[tag.name] = tag.value
                    } else {
                        if (!flags.contains(tag.name)) flags.add(tag.name)
                    }
                }
            }
        }

        if (flags.isEmpty() && stats.isEmpty()) return null
        return PlayerState(
            flags = flags.ifEmpty { null },
            stats = stats.ifEmpty { null }
        )
    }

    // ---------- Сборка Adventure ----------

    private fun serializeTags(tags: List<EditorTag>): Triple<List<String>?, List<String>?, Map<String, Int>?> {
        val setFlagsSet = linkedSetOf<String>()
        val removeFlagsSet = linkedSetOf<String>()
        val modifyStatsMap = mutableMapOf<String, Int>()

        for (tag in tags) {
            val rawName = tag.name
            if (rawName.isBlank()) continue

            if (rawName.startsWith("!")) {
                val cleanName = rawName.removePrefix("!")
                if (tag.category == TagCategory.JOURNAL) {
                    removeFlagsSet.add(cleanName)
                }
                continue
            }

            val effectiveCategory = if (rawName == "health") TagCategory.HEALTH else tag.category
            when (effectiveCategory) {
                TagCategory.HEALTH, TagCategory.ITEM -> {
                    if (tag.value != 0) {
                        modifyStatsMap[rawName] = (modifyStatsMap[rawName] ?: 0) + tag.value
                    }
                }
                TagCategory.JOURNAL -> {
                    if (tag.isStatModifier || tag.value != 0) {
                        modifyStatsMap[rawName] = (modifyStatsMap[rawName] ?: 0) + tag.value
                    } else {
                        setFlagsSet.add(rawName)
                    }
                }
            }
        }

        return Triple(
            setFlagsSet.toList().ifEmpty { null },
            removeFlagsSet.toList().ifEmpty { null },
            modifyStatsMap.ifEmpty { null }
        )
    }

    fun buildAdventure(): Adventure {
        Logger.log(context, "Сборка приключения '${title}' в JSON-модель")
        val chapters = mutableListOf<Chapter>()
        val sceneList = mutableListOf<Scene>()
        var chapterIndex = 1

        // Ищем сцену смерти и исключаем её из обычного обхода
        val deathSceneEditor = scenes.firstOrNull { it.isDeathScene }

        for (scene in scenes) {
            if (scene.isDeathScene) continue

            val (sceneSetFlags, sceneRemoveFlags, sceneModifyStats) = serializeTags(scene.tags)

            val sceneObj = Scene(
                id = scene.id.ifEmpty { "scene_${scenes.indexOf(scene)}" },
                text = scene.text,
                setFlags = sceneSetFlags,
                removeFlags = sceneRemoveFlags,
                modifyStats = sceneModifyStats,
                choices = scene.choices.map { choice ->
                    val (choiceSetFlags, choiceRemoveFlags, choiceModifyStats) =
                        serializeTags(choice.tags)
                    Choice(
                        text = choice.text,
                        nextScene = choice.nextSceneId,
                        condition = choice.conditionTag.ifEmpty { null },
                        // Ф4.3.2b: "show" condition (distinct from
                        // `condition`). `:engine` ignores it today;
                        // Ф4.4 / N4 will gate on it in the reader.
                        visibilityCondition = choice.visibilityConditionTag.ifEmpty { null },
                        setFlags = choiceSetFlags,
                        removeFlags = choiceRemoveFlags,
                        modifyStats = choiceModifyStats,
                        // Ф4.3.2b: template ref survives save/load.
                        // `templateUid == null` for hand-written choices →
                        // `templateRef = null` → Gson omits the field.
                        templateRef = choice.templateUid?.let { uid ->
                            TemplateRefDto(
                                templateId = uid,
                                args = choice.templateArgs,
                            )
                        },
                    )
                }.ifEmpty { null },
                // Ф1.7.5: emit `"is_ending": true` only when the flag is
                // set. `null` → Gson omits the field, parser defaults to
                // false (identity migration).
                isEnding = if (scene.isEnding) true else null,
                // b2: per-scene gate. Omit `"gate"` when 0; omit
                // `"gate_repeatable"` when false (default).
                gate = if (scene.gateRequiredSteps > 0) scene.gateRequiredSteps else null,
                gateRepeatable =
                    if (scene.gateRequiredSteps > 0 && scene.gateRepeatable) true else null,
                // Ф4.5: emit `"merge": true` + `"merge_sources": [...]`
                // only when the author declared them. `null` for the
                // common case — Gson omits the fields, JSON stays clean.
                merge = if (scene.isMerge) true else null,
                mergeSources = scene.mergeSources.ifEmpty { null },
            )
            sceneList.add(sceneObj)

            if (scene.isChapterEnd || scene == scenes.lastOrNull { !it.isDeathScene }) {
                val chapterId = "chapter_$chapterIndex"
                chapters.add(
                    Chapter(
                        id = chapterId,
                        title = "Глава $chapterIndex",
                        // b2: chapter-level `required_steps` is legacy
                        // (v1). The editor writes per-scene `gate` now;
                        // `requiredSteps` stays `null` so Gson omits the
                        // field entirely (`:app/data/Chapter.requiredSteps`
                        // is `Int?` after b2). `JsonToIr.parse` handles
                        // both v1 files (migrates `required_steps`) and
                        // v5 files (reads per-scene `gate`).
                        requiredSteps = null,
                        scenes = sceneList.toList()
                    )
                )
                Logger.log(context, "Сформирована глава $chapterId (сцен: ${sceneList.size})")
                sceneList.clear()
                chapterIndex++
            }
        }

        // Собираем реестр меток из tags сцен, tags выборов и initial_state
        val tagRegistry = LinkedHashMap<String, TagDefinition>()

        fun registerTag(tag: EditorTag) {
            val cleanName = tag.name.removePrefix("!")
            if (cleanName.isBlank()) return

            // Хардкод: health всегда HEALTH
            val effectiveCategory = if (cleanName == "health") TagCategory.HEALTH else tag.category
            val effectiveDisplay = if (cleanName == "health" && tag.displayName.isBlank()) {
                context.getString(R.string.editor_tag_category_health)
            } else {
                tag.displayName.ifBlank { cleanName }
            }

            tagRegistry[cleanName] = TagDefinition(
                name = cleanName,
                displayName = effectiveDisplay,
                category = effectiveCategory,
                visible = tag.visible
            )
        }

        for (tag in initialStateTags) registerTag(tag)
        for (scene in scenes) {
            for (tag in scene.tags) registerTag(tag)
            for (choice in scene.choices) {
                for (tag in choice.tags) registerTag(tag)
            }
        }

        // Метки из условий, которые нигде не объявлены
        for (scene in scenes) {
            for (choice in scene.choices) {
                val cond = choice.conditionTag.trim()
                if (cond.isEmpty()) continue
                val flagName = when {
                    cond.startsWith("!has_") -> cond.removePrefix("!has_").trim()
                    cond.startsWith("has_") -> cond.removePrefix("has_").trim()
                    else -> null
                }
                if (flagName != null && flagName !in tagRegistry) {
                    tagRegistry[flagName] = TagDefinition(
                        name = flagName,
                        displayName = flagName,
                        category = TagCategory.JOURNAL,
                        visible = false
                    )
                }
            }
        }

        // Собираем DeathScene из помеченной сцены
        val deathScene = deathSceneEditor?.let {
            DeathScene(
                id = it.id.ifEmpty { "death_scene" },
                text = it.text,
                allowStepBack = it.deathAllowStepBack,
                allowChapterRestart = it.deathAllowChapterRestart,
                allowAdventureRestart = it.deathAllowAdventureRestart
            )
        }

        return Adventure(
            title = title,
            author = author,
            description = description,
            initialState = buildInitialState(),
            variables = buildVariablesDto(),
            choiceTemplates = buildChoiceTemplatesDto(),
            tags = tagRegistry.values.toList(),
            deathScene = deathScene,
            chapters = chapters
        )
    }

    /**
     * Ф2.6-fix: builds a `:engine/ir/Adventure` from the current editor
     * state.
     *
     * **Why this is a separate method from [buildAdventure].**
     * [buildAdventure] returns `:app/data/Adventure` — the Gson POJO
     * used to **serialize to disk**. That model is intentionally narrow
     * (only fields Gson needs to write the file) and predates the IR
     * (Ф1.7.5 documented the trade-off). `StoryValidator` (Ф2.6) and
     * the engine pipeline (`TypeChecker.check`, `Engine.compile`,
     * `Analyzer.lint`) work on `:engine/ir/Adventure`, which is the
     * **richer** model the reader sees. Before Ф2.6, "Проверить" ran on
     * a hand-rolled `StoryValidator` that re-implemented nine of the
     * engine's checks — and the two would drift. This method gives the
     * validation path the actual IR, so the author sees exactly what
     * the reader would.
     *
     * **What is built from what.**
     * - scenes / chapters: the same grouping as [buildAdventure]
     *   (`isChapterEnd` closes a chapter; last non-death scene closes
     *   the last chapter).
     * - effects: `EditorTag` list → `List<Effect>` in the same order
     *   as `JsonToIr.buildEffects` (`set_flag` → `remove_flag` →
     *   `modify_stat`), derived from the existing [serializeTags].
     * - gate: `gateRequiredSteps > 0` → `Gate(requiredSteps,
     *   repeatable)`. Omitted otherwise.
     * - `is_ending`: from `EditorScene.isEnding`.
     * - `startScene`: first non-death scene's id. If there is no such
     *   scene, `SceneId("")` — that is a structural error that
     *   `Engine.compile` will report as `MissingStartScene`.
     * - `death_scene`: from the `EditorScene` flagged
     *   `isDeathScene`.
     * - `tags`: `TagDefinition` registry built the same way as
     *   [buildAdventure].
     * - `initial_state`: from [buildInitialState] (already nullable;
     *   `null` → empty `InitialState`).
     * - **`variables`: empty**. Ф2.8 populates it; until then,
     *   `TypeChecker` falls back to `tags` + `initial_state`
     *   (documented behaviour, C4 in Ф2.2). This is deliberate: a
     *   file that the editor has not yet learned to declare types for
     *   still gets **some** type checking (unknown variables, wrong
     *   has/compare/effect), which is more than it got before Ф2.6.
     *
     * **Conditions are parsed here, not in `StoryValidator`.**
     * `EditorChoice.conditionTag` is a raw string; `IrChoice.condition`
     * is a typed AST. We parse it with [ConditionParser] — the same
     * parser the reader uses — and on `ParseResult.Failure` we (a) put
     * `null` into the IR (so type checking of the rest of the
     * adventure still runs) and (b) collect the error into the second
     * element of the returned Pair. The caller (`showValidationDialog`)
     * prepends those parse errors to the issue list, so the author
     * sees "condition does not parse" **before** validation runs on
     * the rest. `StoryValidator` itself stays free of condition
     * parsing — that is the Ф2.6 DoD ("no `extractHasFlagName`").
     *
     * **What this method does NOT do.**
     * - It does not serialize to JSON. Serialization is [buildAdventure]
     *   (Gson) and will be replaced by `IrToJson` in Ф8.
     * - It does not mutate `scenes`, `initialStateTags`, or any other
     *   editor state. Calling it twice produces an equal Pair.
     * - It does not touch `pendingJumpToSceneUid` or any UI state.
     *
     * @return Pair of the built IR and any parse errors encountered
     *   while converting conditions. The IR is always non-null; parse
     *   errors are empty when all conditions parsed cleanly.
     */
    fun buildIrAdventure(): Pair<IrAdventure, List<ParseError>> {
        Logger.log(context, "Сборка IR-модели приключения '${title.ifBlank { "(без названия)" }}'")
        val parseErrors = mutableListOf<ParseError>()

        fun convertCategory(c: TagCategory): IrTagCategory = when (c) {
            TagCategory.HEALTH -> IrTagCategory.HEALTH
            TagCategory.ITEM -> IrTagCategory.ITEM
            TagCategory.JOURNAL -> IrTagCategory.JOURNAL
        }

        fun effectsOf(tags: List<EditorTag>): List<IrEffect> {
            val (setFlags, removeFlags, modifyStats) = serializeTags(tags)
            val result = mutableListOf<IrEffect>()
            setFlags.orEmpty().forEach { result += IrEffect.SetFlag(it) }
            removeFlags.orEmpty().forEach { result += IrEffect.RemoveFlag(it) }
            modifyStats.orEmpty().forEach { (name, delta) -> result += IrEffect.ModifyStat(name, delta) }
            return result
        }

        fun conditionOf(
            raw: String,
            fallbackSceneId: String,
            choiceIndex: Int,
            field: String = "condition",
        ): IrCondition? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return when (val r = ConditionParser.parse(trimmed)) {
                is ParseResult.Success -> r.value
                is ParseResult.Failure -> {
                    // Re-anchor the error with a JSON-like path so the
                    // author gets "which scene / which choice" — same
                    // convention as JsonToIr's path strings. Ф4.3.2b:
                    // `field` distinguishes `condition` from
                    // `visibility_condition` on the same choice.
                    r.errors.forEach { err ->
                        parseErrors += if (err is ParseError.ConditionError) {
                            err.copy(path = "scene[$fallbackSceneId].choices[$choiceIndex].$field")
                        } else {
                            err
                        }
                    }
                    null
                }
            }
        }

        val deathSceneEditor = scenes.firstOrNull { it.isDeathScene }

        // Chapters + scenes — same grouping algorithm as buildAdventure().
        val irChapters = mutableListOf<IrChapter>()
        val currentScenes = mutableListOf<IrScene>()
        var chapterIndex = 1

        fun flushChapter() {
            if (currentScenes.isEmpty()) return
            irChapters += IrChapter(
                id = "chapter_$chapterIndex",
                title = "Глава $chapterIndex",
                scenes = currentScenes.toList(),
            )
            currentScenes.clear()
            chapterIndex++
        }

        val lastNonDeathScene = scenes.lastOrNull { !it.isDeathScene }
        for (scene in scenes) {
            if (scene.isDeathScene) continue
            if (scene.id.isBlank()) {
                // Skip blank-id scenes — validation will report them
                // separately via Engine.compile (duplicate "" ids are
                // also caught).
                continue
            }
            val sceneGate: IrGate? = if (scene.gateRequiredSteps > 0) {
                IrGate(requiredSteps = scene.gateRequiredSteps, repeatable = scene.gateRepeatable)
            } else null
            val irChoices = scene.choices.mapIndexed { idx, choice ->
                IrChoice(
                    id = IrChoiceId("${scene.id}_choice_$idx"),
                    text = choice.text,
                    nextScene = IrSceneId(choice.nextSceneId),
                    condition = conditionOf(choice.conditionTag, scene.id, idx),
                    // Ф4.3.2b: propagate the "show" condition into the IR.
                    // `:engine`'s Choice.visibilityCondition exists since
                    // Ф4.2.1 but is dormant (reader gates only on
                    // `condition`); populating it now closes the editor
                    // round-trip and prepares for Ф4.4 / N4. Parse errors
                    // land in `parseErrors` with `field = "visibility_condition"`.
                    visibilityCondition = conditionOf(
                        choice.visibilityConditionTag,
                        scene.id,
                        idx,
                        field = "visibility_condition",
                    ),
                    effects = effectsOf(choice.tags),
                )
            }
            currentScenes += IrScene(
                id = IrSceneId(scene.id),
                text = scene.text,
                effects = effectsOf(scene.tags),
                choices = irChoices,
                gate = sceneGate,
                isEnding = scene.isEnding,
                // Ф4.5: propagate merge metadata into the IR so
                // `StoryValidator` (Ф4.6) and the editor's map view can
                // see the declared convergence.
                isMerge = scene.isMerge,
                mergeSources = scene.mergeSources.map { IrSceneId(it) },
            )
            if (scene.isChapterEnd || scene === lastNonDeathScene) {
                flushChapter()
            }
        }
        flushChapter()

        // Tag registry — same logic as buildAdventure().
        val tagRegistry = LinkedHashMap<String, IrTagDefinition>()
        fun registerTag(tag: EditorTag) {
            val cleanName = tag.name.removePrefix("!").trim()
            if (cleanName.isEmpty()) return
            val effectiveCategory = if (cleanName == "health") TagCategory.HEALTH else tag.category
            val effectiveDisplay = if (cleanName == "health" && tag.displayName.isBlank()) {
                context.getString(R.string.editor_tag_category_health)
            } else {
                tag.displayName.ifBlank { cleanName }
            }
            tagRegistry[cleanName] = IrTagDefinition(
                name = cleanName,
                displayName = effectiveDisplay,
                category = convertCategory(effectiveCategory),
                visible = tag.visible,
            )
        }
        initialStateTags.forEach { registerTag(it) }
        for (scene in scenes) {
            scene.tags.forEach { registerTag(it) }
            scene.choices.forEach { c -> c.tags.forEach { registerTag(it) } }
        }

        val irInitial: IrInitialState = buildInitialState()?.let {
            IrInitialState(
                flags = it.flags ?: emptyList(),
                stats = it.stats ?: emptyMap(),
            )
        } ?: IrInitialState()

        val irDeath: IrDeathScene? = deathSceneEditor?.let {
            IrDeathScene(
                id = IrSceneId(it.id.ifEmpty { "death_scene" }),
                text = it.text,
                allowStepBack = it.deathAllowStepBack,
                allowChapterRestart = it.deathAllowChapterRestart,
                allowAdventureRestart = it.deathAllowAdventureRestart,
            )
        }

        val startScene: IrSceneId = scenes
            .firstOrNull { !it.isDeathScene && it.id.isNotBlank() }
            ?.let { IrSceneId(it.id) }
            ?: IrSceneId("")

        // Ф2.7: реальные объявления переменных из editor-модели. Пустая
        // коллекция → `emptyMap()` → TypeChecker fallback (C4) работает
        // через tags + initialState. Недозаполненные IntMin/IntRange
        // (без min/max) даунгрейдятся до Integer через
        // `EditorVarDecl.toVarType()` — permissive default, чтобы одна
        // недозаполненная декларация не каскадила `UnknownVariable`
        // на все ссылки.
        val irVariables: Map<String, com.myapp.adventure.engine.ir.types.VarDecl> =
            variables.mapNotNull { decl ->
                val clean = decl.name.trim()
                if (clean.isEmpty()) return@mapNotNull null
                val type = decl.toVarType() ?: return@mapNotNull null
                clean to com.myapp.adventure.engine.ir.types.VarDecl(clean, type)
            }.toMap()
        val adventure = IrAdventure(
            title = title,
            author = author,
            description = description,
            variables = irVariables,
            tags = tagRegistry.values.toList(),
            initialState = irInitial,
            deathScene = irDeath,
            startScene = startScene,
            chapters = irChapters,
        )
        return adventure to parseErrors
    }

    /**
     * Ф4.3.1: editor `templates` → `List<ChoiceTemplateDto>?` for
     * Gson-save.
     *
     * `null` on an empty list — Gson **omits** the field, JSON stays
     * clean (same policy as `buildVariablesDto` / `buildInitialState`).
     * Templates with an empty `id` are **skipped** — a
     * `ChoiceTemplate` without an id cannot be referenced by any
     * `Choice.templateRef`, so it is meaningless; the validator will
     * warn separately in Ф4.3.2.
     */
    private fun buildChoiceTemplatesDto(): List<ChoiceTemplateDto>? {
        if (templates.isEmpty()) return null
        val list = mutableListOf<ChoiceTemplateDto>()
        for (t in templates) {
            val cleanId = t.id.trim()
            if (cleanId.isEmpty()) continue
            val (setFlags, removeFlags, modifyStats) = serializeTags(t.effects)
            val params = t.parameters.mapNotNull { p ->
                val name = p.name.trim()
                if (name.isEmpty()) return@mapNotNull null
                when (p.kind) {
                    VarKind.BOOL -> VarDeclDto(name, "bool")
                    VarKind.INTEGER -> VarDeclDto(name, "int")
                    VarKind.INT_MIN -> VarDeclDto(name, "int_min", min = p.min)
                    VarKind.INT_RANGE -> VarDeclDto(name, "int_range", min = p.min, max = p.max)
                    VarKind.ITEM_COUNT -> VarDeclDto(name, "item_count")
                    VarKind.ITEM_LIST -> VarDeclDto(name, "item_list")
                }
            }
            list.add(
                ChoiceTemplateDto(
                    id = cleanId,
                    parameters = params.ifEmpty { null },
                    text = t.text,
                    visibilityCondition = t.visibilityCondition.trim().ifEmpty { null },
                    availabilityCondition = t.availabilityCondition.trim().ifEmpty { null },
                    setFlags = setFlags,
                    removeFlags = removeFlags,
                    modifyStats = modifyStats,
                    nextScene = t.nextSceneId,
                )
            )
        }
        return list.ifEmpty { null }
    }


    // ---------- Ф4.3.2b: template → editor choice ----------

    /**
     * Ф4.3.2b: `EditorTemplate` → `:engine/ir/ChoiceTemplate`.
     *
     * Returns `null` if any condition fails to parse (the author is
     * mid-edit); the caller propagates `null` and the UI shows a generic
     * "cannot apply" message. Parameters with blank names or
     * unresolvable types are silently skipped — same permissive default
     * as `buildIrAdventure`: a half-declared parameter must not block
     * the whole template.
     *
     * Effects go through `serializeTags` — the same three-field
     * (`set_flags` / `remove_flags` / `modify_stats`) decomposition
     * used everywhere else in the editor, so `applyTemplate` sees
     * exactly the same effect list it would get if the author had
     * written the choice by hand.
     */
    private fun editorTemplateToIr(template: EditorTemplate): IrChoiceTemplate? {
        val params = template.parameters.mapNotNull { decl ->
            val clean = decl.name.trim()
            if (clean.isEmpty()) return@mapNotNull null
            val type = decl.toVarType() ?: return@mapNotNull null
            IrVarDecl(clean, type)
        }
        val visibility = template.visibilityCondition.trim().takeIf { it.isNotEmpty() }?.let {
            when (val r = ConditionParser.parse(it)) {
                is ParseResult.Success -> r.value
                is ParseResult.Failure -> return null
            }
        }
        val availability = template.availabilityCondition.trim().takeIf { it.isNotEmpty() }?.let {
            when (val r = ConditionParser.parse(it)) {
                is ParseResult.Success -> r.value
                is ParseResult.Failure -> return null
            }
        }
        val (setFlags, removeFlags, modifyStats) = serializeTags(template.effects)
        val effects = mutableListOf<IrEffect>()
        setFlags.orEmpty().forEach { effects += IrEffect.SetFlag(it) }
        removeFlags.orEmpty().forEach { effects += IrEffect.RemoveFlag(it) }
        modifyStats.orEmpty().forEach { (name, delta) -> effects += IrEffect.ModifyStat(name, delta) }
        return IrChoiceTemplate(
            id = template.id,
            parameters = params,
            text = template.text,
            visibilityCondition = visibility,
            availabilityCondition = availability,
            effects = effects,
            nextScene = IrSceneId(template.nextSceneId),
        )
    }

    /**
     * Ф4.3.2b: `:engine/ir/Choice` (already expanded) → `EditorChoice`.
     *
     * **Discarded fields.** `irChoice.id` and `irChoice.templateRef` are
     * not used — the editor's `EditorChoice.uid` is the identity
     * (`buildAdventure` regenerates JSON `choice.id` from scene order),
     * and `templateUid` / `templateArgs` are passed explicitly by the
     * caller so that `EditorTemplate.id` (author-editable) is the source
     * of truth for `template_ref`.
     *
     * **Converted fields.** `irChoice.condition` /
     * `visibilityCondition` → strings via `ConditionWriter.write` (the
     * same canonical form the editor uses for hand-written conditions).
     * `irChoice.effects` → `EditorTag`s with default `JOURNAL`
     * category; the category is restored on the next edit through
     * `renderTagRow` (the tag registry in `Adventure` may know the
     * true category; the editor does not).
     */
    private fun irChoiceToEditorChoice(
        irChoice: IrChoice,
        templateUid: String,
        templateArgs: Map<String, String>,
    ): EditorChoice {
        val conditionTag = irChoice.condition?.let { ConditionWriter.write(it) } ?: ""
        val visibilityTag = irChoice.visibilityCondition?.let { ConditionWriter.write(it) } ?: ""
        val editorTags = mutableListOf<EditorTag>()
        for (effect in irChoice.effects) {
            when (effect) {
                is IrEffect.SetFlag -> editorTags += EditorTag(
                    name = effect.name, value = 0, isStatModifier = false,
                    category = TagCategory.JOURNAL,
                )
                is IrEffect.RemoveFlag -> editorTags += EditorTag(
                    name = "!${effect.name}", value = 0, isStatModifier = false,
                    category = TagCategory.JOURNAL,
                )
                is IrEffect.ModifyStat -> editorTags += EditorTag(
                    name = effect.name, value = effect.delta, isStatModifier = true,
                    category = TagCategory.JOURNAL,
                )
                is IrEffect.Set -> editorTags += EditorTag(
                    name = effect.name, value = effect.value, isStatModifier = true,
                    category = TagCategory.JOURNAL,
                )
            }
        }
        return EditorChoice(
            text = irChoice.text,
            nextSceneId = irChoice.nextScene.value,
            conditionTag = conditionTag,
            visibilityConditionTag = visibilityTag,
            tags = editorTags,
            templateUid = templateUid,
            templateArgs = templateArgs.toMap(),
        )
    }

    /**
     * Ф4.3.2b: apply a template to a scene, producing a new
     * `EditorChoice`.
     *
     * **Expansion (decision A1, Ф4.2).** The returned choice is fully
     * expanded — `text`, `conditionTag`, `visibilityConditionTag`,
     * `tags`, `nextSceneId` are filled in. `templateUid` +
     * `templateArgs` are kept as metadata for Ф4.3.3 ("update all uses").
     *
     * **`@self` resolution.** `currentSceneId` is the id of the scene
     * where the choice is being inserted. If the template's `nextScene`
     * is `ChoiceTemplate.SELF` (`SceneId("@self")`), the resulting
     * `EditorChoice.nextSceneId` is `currentSceneId`. Otherwise it is
     * the template's fixed target.
     *
     * **Failure modes.** Returns `null` if (a) no template with
     * `templateUid` exists (author deleted it mid-dialog); (b)
     * `editorTemplateToIr` returns `null` (a condition failed to parse —
     * author is mid-edit); (c) `applyTemplate` throws
     * `IllegalArgumentException` (missing/extra args — race if the
     * template was edited between picker and apply). The UI shows a
     * generic toast on `null`; no partial state is created.
     *
     * **Idempotence.** Does not mutate `templates`. Calling twice with
     * the same args produces structurally equal `EditorChoice`
     * (different `uid`).
     */
    fun applyTemplateToChoice(
        templateUid: String,
        args: Map<String, String>,
        currentSceneId: String,
    ): EditorChoice? {
        val template = templates.firstOrNull { it.id == templateUid } ?: return null
        val irTemplate = editorTemplateToIr(template) ?: return null
        // Placeholder ChoiceId — discarded by irChoiceToEditorChoice;
        // `EditorChoice.uid` is the real identity.
        val placeholderId = IrChoiceId("editor_apply")
        return try {
            val irChoice = irApplyTemplate(
                template = irTemplate,
                args = args,
                choiceId = placeholderId,
                currentSceneId = IrSceneId(currentSceneId),
            )
            irChoiceToEditorChoice(irChoice, templateUid, args)
        } catch (e: IllegalArgumentException) {
            Logger.log(context, "applyTemplateToChoice failed: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Phase 3.1c-iii: per-scene update methods for Compose UI.
    // These mutate the scene in place (deepCopy stays only in commitState,
    // called from Fragment.markDirty → commitSnapshot path). Each method
    // is intentionally narrow so the composable does not have to touch
    // mutable EditorScene fields directly.
    // ------------------------------------------------------------------

    fun updateSceneTitle(uid: String, title: String) {
        scenes.find { it.uid == uid }?.let { if (it.title != title) it.title = title }
    }

    fun updateSceneText(uid: String, text: String) {
        scenes.find { it.uid == uid }?.let { if (it.text != text) it.text = text }
    }

    fun setSceneChapterEnd(uid: String, value: Boolean) {
        val s = scenes.find { it.uid == uid } ?: return
        if (s.isChapterEnd != value) {
            s.isChapterEnd = value
            _uiState.value = _uiState.value.copy(
                scenes = scenes.map { it.deepCopy() },
            )
        }
    }

    fun setSceneEnding(uid: String, value: Boolean) {
        val s = scenes.find { it.uid == uid } ?: return
        if (s.isEnding != value) s.isEnding = value
    }

    fun setSceneDeathAllowStepBack(uid: String, value: Boolean) {
        scenes.find { it.uid == uid }?.let { if (it.deathAllowStepBack != value) it.deathAllowStepBack = value }
    }

    fun setSceneDeathAllowChapterRestart(uid: String, value: Boolean) {
        scenes.find { it.uid == uid }?.let { if (it.deathAllowChapterRestart != value) it.deathAllowChapterRestart = value }
    }

    fun setSceneDeathAllowAdventureRestart(uid: String, value: Boolean) {
        scenes.find { it.uid == uid }?.let { if (it.deathAllowAdventureRestart != value) it.deathAllowAdventureRestart = value }
    }

    fun setSceneGate(uid: String, steps: Int, repeatable: Boolean) {
        val s = scenes.find { it.uid == uid } ?: return
        if (s.gateRequiredSteps == steps && s.gateRepeatable == repeatable) return
        s.gateRequiredSteps = steps
        s.gateRepeatable = repeatable
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
        )
    }

    fun addSceneTag(uid: String) {
        val s = scenes.find { it.uid == uid } ?: return
        s.tags.add(EditorTag())
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
        )
    }

    fun removeSceneTag(uid: String, tagIdx: Int) {
        val s = scenes.find { it.uid == uid } ?: return
        if (tagIdx in s.tags.indices) {
            s.tags.removeAt(tagIdx)
            _uiState.value = _uiState.value.copy(
                scenes = scenes.map { it.deepCopy() },
            )
        }
    }

    fun updateSceneTag(
        uid: String,
        tagIdx: Int,
        name: String,
        value: Int,
        displayName: String,
        category: TagCategory,
        visible: Boolean,
    ) {
        val tag = scenes.find { it.uid == uid }?.tags?.getOrNull(tagIdx) ?: return
        if (tag.name != name) tag.name = name
        if (tag.value != value) tag.value = value
        if (tag.displayName != displayName) tag.displayName = displayName
        if (tag.category != category) tag.category = category
        if (tag.visible != visible) tag.visible = visible
    }

    /** Scene removal by uid — the composable knows the uid, not the index. */
    fun removeSceneByUid(uid: String) {
        val idx = scenes.indexOfFirst { it.uid == uid }
        if (idx < 0) return
        removeScene(idx)
    }

    /**
     * Create a scene with the given title, append it, and return it. Mirrors
     * `addSceneWithTitle` but returns `EditorScene` so Compose callers can
     * (eventually) link it from a choice. Scene ids are frozen after
     * insertion, same as `addSceneWithTitle`.
     */
    fun addSceneAndReturn(title: String): EditorScene = addSceneWithTitle(title)

    /**
     * Phase 3.1c-iii-c: request the Compose scene list to scroll to the
     * scene with [uid] and highlight it for ~2.5s. Sets `pendingJumpToSceneUid`
     * on the state; `EditorScenesList` observes it and calls
     * [clearPendingJumpToScene] after the scroll completes.
     *
     * Expands the scene atomically with the jump request — avoids a double
     * `commitState()` (one for expand, one for pending uid).
     */
    fun requestJumpToScene(uid: String) {
        expandedScenes.add(uid)
        pendingJumpToSceneUid = uid
        _uiState.value = _uiState.value.copy(
            expandedScenes = expandedScenes.toSet(),
            pendingJumpToSceneUid = uid,
        )
    }

    /** Called by `EditorScenesList` after the jump animation has started. */
    fun clearPendingJumpToScene() {
        if (pendingJumpToSceneUid == null) return
        pendingJumpToSceneUid = null
        _uiState.value = _uiState.value.copy(pendingJumpToSceneUid = null)
    }

    // ------------------------------------------------------------------
    // Phase 3.1c-iii-b-2: per-choice update methods for Compose UI.
    // ------------------------------------------------------------------

    fun updateChoiceText(sceneUid: String, choiceUid: String, text: String) {
        val scene = scenes.find { it.uid == sceneUid } ?: return
        scene.choices.find { it.uid == choiceUid }?.let { if (it.text != text) it.text = text }
    }

    fun updateChoiceCondition(sceneUid: String, choiceUid: String, condition: String) {
        val scene = scenes.find { it.uid == sceneUid } ?: return
        scene.choices.find { it.uid == choiceUid }?.let {
            if (it.conditionTag != condition) it.conditionTag = condition
        }
    }

    fun setChoiceNextSceneId(sceneUid: String, choiceUid: String, nextSceneId: String) {
        val scene = scenes.find { it.uid == sceneUid } ?: return
        val choice = scene.choices.find { it.uid == choiceUid } ?: return
        if (choice.nextSceneId == nextSceneId) return
        choice.nextSceneId = nextSceneId
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
        )
    }

    fun addChoiceToScene(sceneUid: String): EditorChoice? {
        val scene = scenes.find { it.uid == sceneUid } ?: return null
        val choice = EditorChoice()
        scene.choices.add(choice)
        expandedChoices.add(choice.uid)
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
            expandedChoices = expandedChoices.toSet(),
        )
        return choice
    }

    fun removeChoiceByUid(sceneUid: String, choiceUid: String) {
        val scene = scenes.find { it.uid == sceneUid } ?: return
        val idx = scene.choices.indexOfFirst { it.uid == choiceUid }
        if (idx < 0) return
        scene.choices.removeAt(idx)
        expandedChoices.remove(choiceUid)
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
            expandedChoices = expandedChoices.toSet(),
        )
    }

    fun applyChoiceTags(sceneUid: String, choiceUid: String, newTags: List<EditorTag>) {
        val scene = scenes.find { it.uid == sceneUid } ?: return
        val choice = scene.choices.find { it.uid == choiceUid } ?: return
        choice.tags.clear()
        choice.tags.addAll(newTags.map { it.copy() })
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
        )
    }

    /**
     * Phase 3.1c-iii-b-3-ii-a: apply a template to [sceneUid] and append the
     * resulting fully-expanded `EditorChoice`. Returns `true` on success.
     *
     * Mirrors the old `EditorSceneAdapter.applyTemplateAndInsert`: template
     * lookup + condition parse + `applyTemplate` expansion all happen inside
     * `applyTemplateToChoice`. `@self` is resolved against the scene id.
     * On failure (`applyTemplateToChoice` returns null — deleted template,
     * unparseable condition, or args mismatch) the scene is unchanged.
     */
    fun addChoiceFromTemplate(
        sceneUid: String,
        templateUid: String,
        args: Map<String, String>,
    ): Boolean {
        val scene = scenes.find { it.uid == sceneUid } ?: return false
        val newChoice = applyTemplateToChoice(
            templateUid = templateUid,
            args = args,
            currentSceneId = scene.id,
        ) ?: return false
        scene.choices.add(newChoice)
        expandedChoices.add(newChoice.uid)
        _uiState.value = _uiState.value.copy(
            scenes = scenes.map { it.deepCopy() },
            expandedChoices = expandedChoices.toSet(),
        )
        return true
    }

    init {
        // Placed after all property declarations so that `variables`,
        // `templates`, `expandedScenes`, etc. are initialized before
        // the first snapshot is taken.
        commitState()
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}