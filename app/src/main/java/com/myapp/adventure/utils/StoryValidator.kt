package com.myapp.adventure.utils

import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.PlayerState
import com.myapp.adventure.data.ValidationIssue

object StoryValidator {

    fun validate(
        scenes: List<EditorScene>,
        initialState: PlayerState? = null
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (scenes.isEmpty()) return issues

        val uidById = mutableMapOf<String, String>()
        scenes.forEach { if (it.id.isNotEmpty()) uidById[it.id] = it.uid }

        // Собираем все имена меток, которые где-то устанавливаются:
        // - в scene.tags (положительные)
        // - в choice.tags (положительные)
        // - в initialState.flags (все имена)
        // - в initialState.stats (только != 0)
        val establishable = collectEstablishableNames(scenes, initialState)

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

        // ---------- WARNING: условие ссылается на неустанавливаемую метку ----------
        for (scene in scenes) {
            val sceneName = scene.title.ifBlank { "(без названия)" }
            for (choice in scene.choices) {
                val cond = choice.conditionTag.trim()
                if (cond.isEmpty()) continue
                val flagName = extractHasFlagName(cond) ?: continue
                if (flagName !in establishable) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.WARNING,
                        sceneUid = scene.uid,
                        title = "Условие на неустанавливаемую метку",
                        description = "Сцена «$sceneName», вариант «${choice.text.ifBlank { "(без текста)" }}»: " +
                                "условие «$cond» ссылается на метку «$flagName», которая нигде не устанавливается " +
                                "ни в сценах, ни в эффектах выборов, ни в начальном состоянии."
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
        val reachable = computeReachable(scenes, establishable)
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
     * Собирает имена меток, которые где-то устанавливаются:
     * - в scene.tags (положительные, не начинающиеся с «!»)
     * - в choice.tags всех выборов всех сцен
     * - в initialState.flags (все)
     * - в initialState.stats (только те, где значение != 0)
     * Имена с «!» (снятие флага) в множество не попадают.
     */
    private fun collectEstablishableNames(
        scenes: List<EditorScene>,
        initialState: PlayerState?
    ): Set<String> {
        val result = mutableSetOf<String>()

        fun registerName(rawName: String) {
            if (rawName.isBlank()) return
            if (rawName.startsWith("!")) return
            result.add(rawName)
        }

        for (scene in scenes) {
            for (tag in scene.tags) {
                registerName(tag.name)
            }
            for (choice in scene.choices) {
                for (tag in choice.tags) {
                    registerName(tag.name)
                }
            }
        }

        initialState?.flags?.forEach { registerName(it) }

        initialState?.stats?.forEach { (name, value) ->
            if (value != 0) registerName(name)
        }

        return result
    }

    /**
     * Извлекает имя флага из условия has_X / !has_X.
     * Возвращает null, если условие не про флаг (например, "health >= 50").
     */
    private fun extractHasFlagName(condition: String): String? {
        return when {
            condition.startsWith("!has_") -> condition.removePrefix("!has_").trim().ifEmpty { null }
            condition.startsWith("has_") -> condition.removePrefix("has_").trim().ifEmpty { null }
            else -> null
        }
    }

    /**
     * Моделирует прохождение читалки:
     * - из каждой сцены доступны все choice-таргеты, условие которых потенциально выполнимо:
     *   * null → выполнимо всегда
     *   * has_X → выполнимо, если X в establishable (где-то устанавливается)
     *   * !has_X → выполнимо, если X не в establishable (никогда не устанавливается)
     *   * health >= N / health < N → считаем потенциально выполнимым (значения могут измениться)
     * - если у сцены нет выборов → переход к первой сцене следующей главы
     */
    private fun computeReachable(
        scenes: List<EditorScene>,
        establishable: Set<String>
    ): Set<String> {
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

            for (choice in current.choices) {
                val targetUid = uidById[choice.nextSceneId] ?: continue

                val cond = choice.conditionTag.trim()
                if (!isConditionPotentiallySatisfiable(cond, establishable)) {
                    continue
                }

                if (targetUid !in reachable) {
                    reachable.add(targetUid)
                    queue.addLast(targetUid)
                }
            }

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

    /**
     * Пессимистичная проверка: может ли это условие когда-либо выполниться?
     * - пустое → да
     * - has_X → только если X где-то устанавливается
     * - !has_X → только если X нигде не устанавливается (никогда не появится)
     * - health >= N / < → да (значения могут измениться)
     */
    private fun isConditionPotentiallySatisfiable(
        condition: String,
        establishable: Set<String>
    ): Boolean {
        if (condition.isEmpty()) return true

        val flagName = extractHasFlagName(condition)
        if (flagName != null) {
            val isNegated = condition.startsWith("!has_")
            return if (isNegated) {
                flagName !in establishable
            } else {
                flagName in establishable
            }
        }

        // Условия на статы — оптимистично считаем выполнимыми
        return true
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