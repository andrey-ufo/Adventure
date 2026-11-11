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
import com.myapp.adventure.data.PlayerState
import com.myapp.adventure.data.RebuildResult
import com.myapp.adventure.data.Scene
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.utils.Logger

class AdventureEditorViewModel(application: Application) : AndroidViewModel(application) {

    val scenes = mutableListOf<EditorScene>()
    var title: String = ""
    var author: String = ""
    var description: String = ""

    val initialStateTags: MutableList<EditorTag> = mutableListOf()
    var initialStateExpanded: Boolean = false

    private val expandedScenes = mutableSetOf<String>()
    private val expandedChoices = mutableSetOf<String>()
    private val expandedMapChapters = mutableSetOf<Int>()

    var pendingJumpToSceneUid: String? = null

    private val history = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private val context get() = getApplication<Application>().applicationContext

    // ---------- Аккордеон сцен ----------

    fun isSceneExpanded(uid: String): Boolean = uid in expandedScenes
    fun expandScene(uid: String) { expandedScenes.add(uid) }

    fun toggleSceneExpanded(uid: String): Boolean {
        return if (uid in expandedScenes) {
            expandedScenes.remove(uid)
            false
        } else {
            expandedScenes.add(uid)
            true
        }
    }

    fun collapseAllScenes() { expandedScenes.clear() }

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
        return if (uid in expandedSceneSettings) {
            expandedSceneSettings.remove(uid)
            false
        } else {
            expandedSceneSettings.add(uid)
            true
        }
    }

    // ---------- Аккордеон выборов ----------

    fun isChoiceExpanded(uid: String): Boolean = uid in expandedChoices

    fun toggleChoiceExpanded(uid: String): Boolean {
        return if (uid in expandedChoices) {
            expandedChoices.remove(uid)
            false
        } else {
            expandedChoices.add(uid)
            true
        }
    }

    fun collapseAllChoices() { expandedChoices.clear() }

    private fun pruneExpandedChoices() {
        val currentUids = scenes.flatMap { it.choices.map { c -> c.uid } }.toSet()
        expandedChoices.retainAll(currentUids)
    }

    // ---------- Карта: аккордеон глав ----------

    fun isMapChapterExpanded(index: Int): Boolean = index in expandedMapChapters

    fun toggleMapChapterExpanded(index: Int) {
        if (index in expandedMapChapters) expandedMapChapters.remove(index)
        else expandedMapChapters.add(index)
    }

    fun ensureFirstMapChapterExpanded(totalChapters: Int) {
        if (expandedMapChapters.isEmpty() && totalChapters > 0) {
            expandedMapChapters.add(0)
        }
    }

    fun collapseAllMapChapters() { expandedMapChapters.clear() }

    // ---------- История Undo/Redo ----------

    fun isHistoryInitialized(): Boolean = history.isNotEmpty()

    fun initHistory() {
        history.clear()
        redoStack.clear()
        history.addLast(takeSnapshot())
    }

    fun takeSnapshot(): EditorSnapshot = EditorSnapshot(
        title = title,
        author = author,
        description = description,
        initialStateTags = initialStateTags.map { it.copy() },
        scenes = scenes.map { it.deepCopy() }
    )

    fun restoreSnapshot(snapshot: EditorSnapshot) {
        title = snapshot.title
        author = snapshot.author
        description = snapshot.description
        initialStateTags.clear()
        initialStateTags.addAll(snapshot.initialStateTags.map { it.copy() })
        scenes.clear()
        scenes.addAll(snapshot.scenes.map { it.deepCopy() })
        pruneExpandedScenes()
        pruneExpandedChoices()
    }

    fun commitSnapshot() {
        val current = takeSnapshot()
        if (current == history.lastOrNull()) return
        history.addLast(current)
        while (history.size > MAX_HISTORY) history.removeFirst()
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (history.size < 2) return false
        val current = history.removeLast()
        redoStack.addLast(current)
        restoreSnapshot(history.last())
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        history.addLast(next)
        while (history.size > MAX_HISTORY) history.removeFirst()
        restoreSnapshot(next)
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

        if (newId == oldId) return false
        val updatedRefs = updateReferences(oldId, newId)
        Logger.log(context, "ID сцены '${scene.title}' заморожен: '$oldId' → '$newId', обновлено ссылок: $updatedRefs")
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
        return newScene
    }

    fun removeScene(index: Int) {
        if (index in scenes.indices) {
            val removed = scenes.removeAt(index)
            expandedScenes.remove(removed.uid)
            Logger.log(context, "Удалена сцена: ${removed.title.ifEmpty { "(без названия)" }}, индекс: $index")
        }
    }

    fun setDeathScene(sceneUid: String, enabled: Boolean) {
        if (enabled) {
            scenes.forEach { if (it.uid != sceneUid) it.isDeathScene = false }
        }
        scenes.find { it.uid == sceneUid }?.isDeathScene = enabled
    }

    fun addChoice(sceneIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        val choice = EditorChoice()
        scene.choices.add(choice)
        expandedChoices.add(choice.uid)
        Logger.log(context, "Добавлен выбор в сцену '${scene.title}' (индекс $sceneIndex)")
    }

    fun removeChoice(sceneIndex: Int, choiceIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        if (choiceIndex in scene.choices.indices) {
            scene.choices.removeAt(choiceIndex)
            Logger.log(context, "Удалён выбор из сцены '${scene.title}' (индекс сцены $sceneIndex, выбор $choiceIndex)")
        }
    }

    fun addTag(sceneIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        scene.tags.add(EditorTag())
        Logger.log(context, "Добавлена метка в сцену '${scene.title}' (индекс $sceneIndex)")
    }

    fun removeTag(sceneIndex: Int, tagIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        if (tagIndex in scene.tags.indices) {
            val removedTag = scene.tags.removeAt(tagIndex)
            Logger.log(context, "Удалена метка '${removedTag.name}' из сцены '${scene.title}' (индекс сцены $sceneIndex)")
        }
    }

    fun getSceneOptions(): List<Pair<String, String>> {
        return scenes.map { scene ->
            scene.title.ifEmpty { "(без названия)" } to scene.id
        }
    }

    // ---------- Начальное состояние ----------

    fun addInitialStateTag() {
        initialStateTags.add(EditorTag(category = TagCategory.JOURNAL))
    }

    fun removeInitialStateTag(index: Int) {
        if (index in initialStateTags.indices) {
            initialStateTags.removeAt(index)
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
                        setFlags = choiceSetFlags,
                        removeFlags = choiceRemoveFlags,
                        modifyStats = choiceModifyStats
                    )
                }.ifEmpty { null },
                // Ф1.7.5: emit `"is_ending": true` only when the flag is
                // set. Gson with a non-null `false` would write
                // `"is_ending": false` on every scene, cluttering the
                // JSON and making the field appear on files where the
                // author never touched it. `null` → Gson omits the
                // field → on load, `JsonToIr` defaults it to `false`
                // (identity migration). This matches the style of
                // `setFlags` / `choices` above (empty → omit).
                isEnding = if (scene.isEnding) true else null,
                // b2: per-scene gate. Omit `"gate"` when 0 (no gate);
                // omit `"gate_repeatable"` when `false` (default).
                // Same "empty → omit" style as `setFlags` above.
                gate = if (scene.gateRequiredSteps > 0) scene.gateRequiredSteps else null,
                gateRepeatable =
                    if (scene.gateRequiredSteps > 0 && scene.gateRepeatable) true else null
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
                "Здоровье"
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
            tags = tagRegistry.values.toList(),
            deathScene = deathScene,
            chapters = chapters
        )
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}