package com.myapp.adventure.utils

import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.ValidationIssue

object StoryValidator {

    fun validate(scenes: List<EditorScene>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (scenes.isEmpty()) return issues

        val uidById = mutableMapOf<String, String>()
        scenes.forEach { if (it.id.isNotEmpty()) uidById[it.id] = it.uid }

        // ---------- ERROR: битые ссылки и пустые выборы ----------
        for (scene in scenes) {
            val sceneName = scene.title.ifBlank { "(без названия)" }
            for ((choiceIdx, choice) in scene.choices.withIndex()) {
                val choiceLabel = choice.text.ifBlank { "(без текста)" }

                if (choice.text.isBlank()) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.ERROR,
                        sceneUid = scene.uid,
                        title = "Пустой текст выбора",
                        description = "Сцена «$sceneName», вариант #${choiceIdx + 1}: текст не заполнен"
                    )
                }
                if (choice.nextSceneId.isBlank()) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.ERROR,
                        sceneUid = scene.uid,
                        title = "Выбор без перехода",
                        description = "Сцена «$sceneName», вариант «$choiceLabel» никуда не ведёт"
                    )
                } else if (choice.nextSceneId !in uidById) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.ERROR,
                        sceneUid = scene.uid,
                        title = "Битая ссылка",
                        description = "Сцена «$sceneName», вариант «$choiceLabel» ведёт на несуществующую сцену «${choice.nextSceneId}»"
                    )
                }
            }
        }

        // ---------- WARNING: тупики ----------
        for ((idx, scene) in scenes.withIndex()) {
            if (scene.choices.isEmpty() && !scene.isChapterEnd && idx < scenes.size - 1) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    sceneUid = scene.uid,
                    title = "Тупик",
                    description = "Сцена «${scene.title.ifBlank { "(без названия)" }}» не имеет вариантов и не отмечена как конец главы. В читалке это завершит главу или историю."
                )
            }
        }

        // ---------- WARNING: шаговые шлюзы ----------
        for ((idx, scene) in scenes.withIndex()) {
            if (scene.isChapterEnd && scene.requiredSteps == 0 && idx < scenes.size - 1) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    sceneUid = scene.uid,
                    title = "Шаговый шлюз = 0",
                    description = "Сцена «${scene.title.ifBlank { "(без названия)" }}» — конец главы, но требует 0 шагов. Следующая глава откроется сразу."
                )
            }
            if (scene.isChapterEnd && scene.requiredSteps > 0 && idx == scenes.size - 1) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    sceneUid = scene.uid,
                    title = "Шаги на финальной сцене",
                    description = "Сцена «${scene.title.ifBlank { "(без названия)" }}» — последняя, но требует ${scene.requiredSteps} шагов. В читалке это не сработает — история закончится сразу."
                )
            }
        }

        // ---------- WARNING: недостижимые сцены ----------
        val reachable = computeReachable(scenes)
        for ((idx, scene) in scenes.withIndex()) {
            if (idx > 0 && scene.uid !in reachable) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    sceneUid = scene.uid,
                    title = "Недостижимая сцена",
                    description = "Сцена «${scene.title.ifBlank { "(без названия)" }}» недостижима ни по одной ветке из начала истории."
                )
            }
        }

        return issues
    }

    /**
     * Моделирует прохождение читалки: из каждой сцены доступны все choice-таргеты,
     * а если у сцены нет выборов — читалка переходит к первой сцене следующей главы.
     */
    private fun computeReachable(scenes: List<EditorScene>): Set<String> {
        if (scenes.isEmpty()) return emptySet()

        val chapterGroups = buildChapterGroups(scenes)
        val sceneToChapterIdx = mutableMapOf<String, Int>()
        chapterGroups.forEachIndexed { idx, group ->
            group.forEach { sceneToChapterIdx[it.uid] = idx }
        }

        val uidById = mutableMapOf<String, String>()
        scenes.forEach { if (it.id.isNotEmpty()) uidById[it.id] = it.uid }
        val sceneByUid = scenes.associateBy { it.uid }

        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        val startUid = scenes.first().uid
        reachable.add(startUid)
        queue.addLast(startUid)

        while (queue.isNotEmpty()) {
            val currentUid = queue.removeFirst()
            val current = sceneByUid[currentUid] ?: continue

            // Переходы по choices
            for (choice in current.choices) {
                val targetUid = uidById[choice.nextSceneId] ?: continue
                if (targetUid !in reachable) {
                    reachable.add(targetUid)
                    queue.addLast(targetUid)
                }
            }

            // Нет выборов → читалка переходит к первой сцене следующей главы
            if (current.choices.isEmpty()) {
                val currentChapterIdx = sceneToChapterIdx[currentUid] ?: continue
                val nextChapterIdx = currentChapterIdx + 1
                if (nextChapterIdx < chapterGroups.size) {
                    val nextUid = chapterGroups[nextChapterIdx].first().uid
                    if (nextUid !in reachable) {
                        reachable.add(nextUid)
                        queue.addLast(nextUid)
                    }
                }
            }
        }

        return reachable
    }

    /** Тот же алгоритм группировки, что и в buildAdventure(). */
    private fun buildChapterGroups(scenes: List<EditorScene>): List<List<EditorScene>> {
        val groups = mutableListOf<List<EditorScene>>()
        var current = mutableListOf<EditorScene>()
        for (scene in scenes) {
            current.add(scene)
            if (scene.isChapterEnd || scene == scenes.last()) {
                groups.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }
}