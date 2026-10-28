package com.myapp.adventure.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Chapter
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.Scene
import com.myapp.adventure.utils.Logger

class AdventureEditorViewModel(application: Application) : AndroidViewModel(application) {

    val scenes = mutableListOf<EditorScene>()
    var title: String = ""
    var author: String = ""
    var description: String = ""

    private val context get() = getApplication<Application>().applicationContext

    /**
     * Транслитерация кириллицы для создания латинских идентификаторов и имён файлов.
     */
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

    fun addScene() {
        val newScene = EditorScene()
        scenes.add(newScene)
        Logger.log(context, "Добавлена сцена, индекс: ${scenes.size - 1}")
    }

    fun removeScene(index: Int) {
        if (index in scenes.indices) {
            val removed = scenes.removeAt(index)
            Logger.log(context, "Удалена сцена: ${removed.title.ifEmpty { "(без названия)" }}, индекс: $index")
        }
    }

    fun updateSceneTitle(index: Int, newTitle: String) {
        val scene = scenes.getOrNull(index) ?: return
        val oldTitle = scene.title
        scene.title = newTitle
        if (scene.id.isEmpty() || scene.id.startsWith("scene_")) {
            val newId = generateSceneId(newTitle)
            scene.id = newId
            Logger.log(context, "Сцена '$oldTitle' переименована в '$newTitle', ID обновлён: $newId")
        } else {
            Logger.log(context, "Сцена '$oldTitle' переименована в '$newTitle' (ID не менялся: ${scene.id})")
        }
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
}