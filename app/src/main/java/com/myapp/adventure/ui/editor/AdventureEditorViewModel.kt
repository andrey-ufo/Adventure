package com.myapp.adventure.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Chapter
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorSnapshot
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.Scene
import com.myapp.adventure.data.RebuildResult
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.utils.Logger

class AdventureEditorViewModel(application: Application) : AndroidViewModel(application) {

    val scenes = mutableListOf<EditorScene>()
    var title: String = ""
    var author: String = ""
    var description: String = ""

    private val expandedScenes = mutableSetOf<String>()

    private val history = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private val context get() = getApplication<Application>().applicationContext

    // ---------- Аккордеон ----------

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
    fun expandAllScenes() { scenes.forEach { expandedScenes.add(it.uid) } }

    private fun pruneExpandedScenes() {
        val currentUids = scenes.map { it.uid }.toSet()
        expandedScenes.retainAll(currentUids)
    }

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
        scenes = scenes.map { it.deepCopy() }
    )

    fun restoreSnapshot(snapshot: EditorSnapshot) {
        title = snapshot.title
        author = snapshot.author
        description = snapshot.description
        scenes.clear()
        scenes.addAll(snapshot.scenes.map { it.deepCopy() })
        pruneExpandedScenes()
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

    /**
     * Если ID сцены ещё "автоматический" и в названии что-то есть —
     * генерирует ID из полного названия, замораживает и обновляет все ссылки.
     * Возвращает true, если ID фактически изменился.
     */
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

    /**
     * Пересобрать ID по явному запросу пользователя (кнопка «🔄»).
     * Возвращает результат с количеством обновлённых ссылок или null,
     * если пересборка невозможна.
     */
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

    /** Заранее посчитать, каким будет новый ID, без применения. */
    fun previewRebuiltId(sceneUid: String): String? {
        val scene = scenes.find { it.uid == sceneUid } ?: return null
        if (scene.title.isBlank()) return null
        val oldId = scene.id
        scene.id = ""
        val preview = generateSceneId(scene.title)
        scene.id = oldId
        return preview
    }

    /** Замораживает все «висящие» ID перед сборкой JSON. */
    fun freezeAllPendingIds() {
        scenes.toList().forEach { scene ->
            if (scene.idIsAuto) {
                if (scene.title.isNotBlank()) {
                    freezeSceneId(scene.uid)
                } else {
                    scene.idIsAuto = false   // пустую сцену тоже замораживаем как есть
                }
            }
        }
    }

    /** Обновляет все ссылки со старого ID на новый. Возвращает количество изменённых выборов. */
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

    /**
     * Создаёт сцену с заданным названием. Если название непустое — генерирует и
     * сразу замораживает ID, чтобы ссылки работали сразу.
     */
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

    fun updateSceneTitle(index: Int, newTitle: String) {
        val scene = scenes.getOrNull(index) ?: return
        val oldTitle = scene.title
        scene.title = newTitle
        Logger.log(context, "Сцена '$oldTitle' переименована в '$newTitle' (ID: ${scene.id.ifEmpty { "пуст" }})")
    }

    fun addChoice(sceneIndex: Int) {
        val scene = scenes.getOrNull(sceneIndex) ?: return
        scene.choices.add(EditorChoice())
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

    fun getSceneOptions(excludeIndex: Int = -1): List<Pair<String, String>> {
        return scenes.mapIndexedNotNull { index, scene ->
            if (index == excludeIndex) null
            else (scene.title.ifEmpty { "(без названия)" } to scene.id)
        }
    }

    // ---------- Сборка Adventure ----------

    fun buildAdventure(): Adventure {
        Logger.log(context, "Сборка приключения '${title}' в JSON-модель")
        val chapters = mutableListOf<Chapter>()
        val sceneList = mutableListOf<Scene>()
        var chapterIndex = 1

        for (scene in scenes) {
            val sceneObj = Scene(
                id = scene.id.ifEmpty { "scene_${scenes.indexOf(scene)}" },
                text = scene.text,
                setFlags = scene.tags.filter { !it.isStatModifier && it.value == 0 }.map { it.name },
                removeFlags = scene.tags.filter { it.name.startsWith("!") }.map { it.name.removePrefix("!") },
                modifyStats = scene.tags.filter { it.isStatModifier || it.value != 0 }.associate { it.name to it.value },
                choices = scene.choices.map { choice ->
                    Choice(
                        text = choice.text,
                        nextScene = choice.nextSceneId,
                        condition = choice.conditionTag.ifEmpty { null },
                        setFlags = if (choice.conditionTag.isNotEmpty()) listOf(choice.conditionTag) else emptyList()
                    )
                }.ifEmpty { null }
            )
            sceneList.add(sceneObj)

            if (scene.isChapterEnd || scene == scenes.last()) {
                val chapterId = "chapter_$chapterIndex"
                val requiredSteps = if (scene.isChapterEnd) scene.requiredSteps else 0
                chapters.add(
                    Chapter(
                        id = chapterId,
                        title = "Глава $chapterIndex",
                        requiredSteps = requiredSteps,
                        scenes = sceneList.toList()
                    )
                )
                Logger.log(context, "Сформирована глава $chapterId (сцен: ${sceneList.size}, шагов: $requiredSteps)")
                sceneList.clear()
                chapterIndex++
            }
        }

        return Adventure(
            title = title,
            author = author,
            description = description,
            chapters = chapters
        )
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}