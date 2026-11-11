package com.myapp.adventure.utils

import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.PlayerState
import com.myapp.adventure.data.TagCategory
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

        // ---------- ERROR: снятие ITEM/HEALTH, которое нигде не устанавливается ----------
        val positiveEst = collectPositiveItemHealthNames(scenes, initialState)
        for (scene in scenes) {
            val sceneName = scene.title.ifBlank { "(без названия)" }

            for (tag in scene.tags) {
                if (tag.name.startsWith("!")) continue
                if (tag.value >= 0) continue
                if (tag.category != TagCategory.ITEM && tag.category != TagCategory.HEALTH) continue
                if (tag.name !in positiveEst) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.ERROR,
                        sceneUid = scene.uid,
                        title = "Снятие неустанавливаемого ресурса",
                        description = "Сцена «$sceneName», метка «${tag.name}» (${tag.value}): уменьшает значение, " +
                                "но «${tag.name}» нигде не устанавливается положительно " +
                                "(ни в начальном состоянии, ни в эффектах сцен/выборов). Игрок не сможет это использовать."
                    )
                }
            }

            for (choice in scene.choices) {
                val choiceLabel = choice.text.ifBlank { "(без текста)" }
                for (tag in choice.tags) {
                    if (tag.name.startsWith("!")) continue
                    if (tag.value >= 0) continue
                    if (tag.category != TagCategory.ITEM && tag.category != TagCategory.HEALTH) continue
                    if (tag.name !in positiveEst) {
                        issues += ValidationIssue(
                            severity = IssueSeverity.ERROR,
                            sceneUid = scene.uid,
                            title = "Снятие неустанавливаемого ресурса",
                            description = "Сцена «$sceneName», выбор «$choiceLabel», метка «${tag.name}» (${tag.value}): " +
                                    "уменьшает значение, но «${tag.name}» нигде не устанавливается положительно. " +
                                    "Игрок не сможет это использовать."
                        )
                    }
                }
            }
        }

        // ---------- WARNING: смерть и сцена смерти ----------
        val hasDeathScene = scenes.any { it.isDeathScene }
        val hasHealth = hasHealthInStats(scenes, initialState)

        if (hasHealth && !hasDeathScene) {
            issues += ValidationIssue(
                severity = IssueSeverity.WARNING,
                sceneUid = scenes.first().uid,
                title = "Смерть без сцены смерти",
                description = "В истории используется health, но сцена смерти не задана. " +
                        "При наступлении смерти будет показан стандартный экран."
            )
        }

        if (hasDeathScene && !hasHealth) {
            val deathScene = scenes.first { it.isDeathScene }
            issues += ValidationIssue(
                severity = IssueSeverity.WARNING,
                sceneUid = deathScene.uid,
                title = "Сцена смерти без health",
                description = "Сцена смерти задана, но health нигде не используется — " +
                        "смерть никогда не наступит."
            )
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
        // b2: gates are per-scene now. `isChapterEnd` is a narrative
        // boundary, not a gate marker — a chapter end without a gate
        // is a legitimate author choice ("this chapter is a natural
        // break, but the next opens immediately"). The old warning
        // "chapter end but 0 steps = auto-open" was only meaningful
        // when chapter end *implied* a gate; that implication is gone.
        // Replaced by: "gate on the very last scene, but the scene is
        // not marked as an ending" — the player will walk the gate,
        // see choices-less scene, and get "🎉 Конец истории" **only
        // if** it was also `is_ending`; otherwise it is a DeadEnd
        // (already a separate analyzer warning). So this warning is
        // now specifically "gate on a terminal scene that is also an
        // ending" — the gate can never be completed, because the
        // scene stops the story first.
        for ((idx, scene) in scenes.withIndex()) {
            if (scene.gateRequiredSteps > 0 && scene.isEnding) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    sceneUid = scene.uid,
                    title = "Шаговый гейт на сцене-конце истории",
                    description = "Сцена «${scene.title.ifBlank { "(без названия)" }}» " +
                            "помечена как конец игры и одновременно требует " +
                            "${scene.gateRequiredSteps} шагов. Гейт не сработает — " +
                            "читалка остановит историю до того, как шаги будут пройдены."
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

    /**
     * Собирает имена ITEM/HEALTH-меток, которые где-то устанавливаются в положительное значение:
     * - initialState.stats (значение > 0)
     * - scene.tags / choice.tags с категорией ITEM/HEALTH и value > 0
     */

    /** true, если health где-то встречается (initial_state, эффекты сцен или выборов). */
    private fun hasHealthInStats(
        scenes: List<EditorScene>,
        initialState: PlayerState?
    ): Boolean {
        if ((initialState?.stats?.get("health") ?: -1) >= 0) return true

        for (scene in scenes) {
            if (scene.tags.any { it.name == "health" && it.value != 0 }) return true
            for (choice in scene.choices) {
                if (choice.tags.any { it.name == "health" && it.value != 0 }) return true
            }
        }
        return false
    }


    private fun collectPositiveItemHealthNames(
        scenes: List<EditorScene>,
        initialState: PlayerState?
    ): Set<String> {
        val result = mutableSetOf<String>()

        initialState?.stats?.forEach { (name, value) ->
            if (value > 0) result.add(name)
        }

        fun registerTag(tag: com.myapp.adventure.data.EditorTag) {
            if (tag.name.isBlank()) return
            if (tag.name.startsWith("!")) return
            if (tag.category != TagCategory.ITEM && tag.category != TagCategory.HEALTH) return
            if (tag.value > 0) result.add(tag.name)
        }

        for (scene in scenes) {
            for (tag in scene.tags) registerTag(tag)
            for (choice in scene.choices) {
                for (tag in choice.tags) registerTag(tag)
            }
        }

        return result
    }

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