package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory

/**
 * Semantic analysis of an adventure that **compiles successfully**.
 *
 * **Scope.** The analyzer works on a
 * [com.myapp.adventure.engine.CompiledAdventure] — that is, after
 * [com.myapp.adventure.engine.Engine.compile] has built the indexes.
 * Anything that prevents compilation is
 * [com.myapp.adventure.engine.CompileError], not a [LintIssue]. Anything
 * that prevents **parsing** is
 * [com.myapp.adventure.engine.parse.ParseError]. The analyzer is the third
 * and last layer: "the file opens and plays, but is it coherent?" — same
 * boundary fixed in Ф1.3.4.1.
 *
 * **What the analyzer does not check (yet).**
 * - `nextScene` pointing to a scene that does not exist. It is checked
 *   implicitly by [reachable]: a `nextScene` to a missing scene is
 *   added to [reachable] (the id is recorded), but the scene is not in
 *   [CompiledAdventure.scenesById], so it is not further traversed. This
 *   is the same model as the current `:app` reader: it fails at the
 *   moment of transition, not at open time. A dedicated check
 *   ([LintKind.MissingNextScene]) will be added in Ф1.6.2 together with
 *   the `StoryValidator` port.
 * - `has_X` on numeric variable, unknown variable — type checker, Ф2.2.
 * - Duplicate `ChoiceId` within a scene — the reader looks choices up by
 *   id; a duplicate would be resolved to the first one. Will be a
 *   `LintKind.DuplicateChoiceId` in Ф1.6.2.
 *
 * **Determinism.** Both methods return values sorted by scene id, so
 * repeated runs on the same input produce identical output. This is a
 * prerequisite for snapshot tests of analyzer output in a later phase.
 */
public object Analyzer {

    /**
     * Builds a [ReachabilityReport] for [compiled].
     *
     * **BFS** from [com.myapp.adventure.engine.ir.Adventure.startScene]
     * along `choice.nextScene` links. Every entry in
     * [ReachabilityReport.reachable] is guaranteed to exist in
     * [CompiledAdventure.scenesById] — so the UI can safely jump to any
     * scene id it finds there.
     *
     * **Missing references are not reachability.** A `nextScene` that
     * points to a scene not present in [CompiledAdventure.scenesById] is
     * **not** added to [ReachabilityReport.reachable] — "reachable" means
     * "the player can visit this scene", and a scene that does not exist
     * cannot be visited. The missing reference itself is a separate
     * problem ([LintKind.MissingNextScene], coming in Ф1.6.2), not a
     * reachability question.
     *
     * **Missing start scene.** If
     * [com.myapp.adventure.engine.ir.Adventure.startScene] itself is not
     * in [CompiledAdventure.scenesById] — a bug in the file, not caught
     * by `JsonToIr` because reference-integrity belongs to the analyzer —
     * [ReachabilityReport.reachable] is empty and every existing scene is
     * reported as unreachable. The missing start scene will surface as a
     * separate lint issue ([LintKind.MissingStartScene], Ф1.6.2).
     *
     * **Termination.** The BFS visits each scene at most once; `nextScene`
     * cycles (self-loops, `A → B → A`) are safe.
     */
    public fun reachable(compiled: CompiledAdventure): ReachabilityReport {
        val start = compiled.adventure.startScene
        val reachable = mutableSetOf<SceneId>()
        val queue = ArrayDeque<SceneId>()
        // Only seed the BFS if the start scene actually exists. If it does
        // not, `reachable` stays empty and every existing scene is reported
        // as unreachable — which is exactly the author's situation: nothing
        // can be visited. The missing start scene itself will be a lint
        // issue (`LintKind.MissingStartScene`, Ф1.6.2), not a reachability
        // answer.
        if (start in compiled.scenesById) {
            reachable.add(start)
            queue.addLast(start)
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val scene = compiled.scenesById[current] ?: continue
            for (choice in scene.choices) {
                // Only follow edges to scenes that actually exist. A
                // `nextScene` pointing to a missing scene is a separate
                // problem (missing-reference), not a reachability one; it
                // will be a lint issue in Ф1.6.2. Keeping missing ids out
                // of `reachable` means UI can safely jump to anything in
                // the set.
                if (choice.nextScene in compiled.scenesById &&
                    reachable.add(choice.nextScene)
                ) {
                    queue.addLast(choice.nextScene)
                }
            }
        }

        val allSceneIds = compiled.scenesById.keys
        val unreachable = (allSceneIds - reachable)
            .sortedBy { it.value }

        val deadEnds = reachable
            .filter { compiled.scenesById[it]?.choices?.isEmpty() == true }
            .sortedBy { it.value }

        return ReachabilityReport(
            reachable = reachable,
            unreachable = unreachable,
            deadEnds = deadEnds,
        )
    }

    /**
     * Returns all semantic issues found in [compiled].
     *
     * Today this is exactly [LintKind.UnreachableScene] for each scene in
     * [ReachabilityReport.unreachable]. Ф1.6.2 will add the existing
     * `StoryValidator` checks (dead ends without `isChapterEnd`, missing
     * `nextScene`, `set_flags` on `ITEM`/`HEALTH`, etc.) as additional
     * [LintKind] variants.
     *
     * **No false positives.** An empty list means the adventure is
     * semantically clean (by the current set of checks). The analyzer
     * never returns `WARNING`s for things the author cannot control.
     */
    public fun lint(compiled: CompiledAdventure): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        issues += lintUnreachable(compiled)
        issues += lintRemovingUnestablishedItem(compiled)
        issues += lintDeathSceneWithoutHealth(compiled)
        issues += lintConditionOnUnestablishedFlag(compiled)
        issues += lintDeathWithoutDeathScene(compiled)
        issues += lintDeadEnd(compiled)
        issues += lintEndingSceneWithChoices(compiled)
        issues += lintUnreachableInvariants(compiled)
        return issues.sortedWith(
            compareBy<LintIssue> { it.sceneId == null }
                .thenBy { it.sceneId?.value ?: "" }
                .thenBy { it.message },
        )
    }

    private fun lintUnreachable(compiled: CompiledAdventure): List<LintIssue> {
        val report = reachable(compiled)
        val startId = compiled.adventure.startScene.value
        return report.unreachable.map { sceneId ->
            LintIssue(
                kind = LintKind.UnreachableScene,
                message = "Scene '${sceneId.value}' is not reachable from " +
                        "start scene '$startId'",
                sceneId = sceneId,
                severity = Severity.WARNING,
            )
        }
    }

    private fun lintRemovingUnestablishedItem(compiled: CompiledAdventure): List<LintIssue> {
        val tags = compiled.adventure.tags.associateBy { it.name }
        val allScenes = compiled.adventure.chapters.flatMap { it.scenes }
        val allChoices = allScenes.flatMap { it.choices }

        // Establish what is "given": positive initial stats and positive
        // ModifyStat effects anywhere. If an ITEM/HEALTH variable is in
        // this set, reducing it is legitimate.
        val positiveSet = mutableSetOf<String>()
        compiled.adventure.initialState.stats.forEach { (name, value) ->
            if (value > 0) positiveSet.add(name)
        }
        // Ф3.1: `Set(name, N > 0)` establishes a positive value just as
        // `ModifyStat(name, +N)` does. `Set(name, 0)` and `Set(name, -N)`
        // do not establish — they reduce, not grant.
        //
        // Written as an inline `when` rather than a `Boolean`-returning
        // helper (`Effect.establishesPositive(): Boolean`): after
        // `establishesPositive()` returns true, the compiler does not
        // smart-cast `effect` to `ModifyStat` / `Set`, so `effect.name`
        // would not compile. An inline `when` performs the smart-cast at
        // each branch.
        for (effect in allScenes.flatMap { it.effects }) {
            when (effect) {
                is Effect.ModifyStat -> if (effect.delta > 0) positiveSet.add(effect.name)
                is Effect.Set -> if (effect.value > 0) positiveSet.add(effect.name)
                is Effect.SetFlag, is Effect.RemoveFlag -> Unit
            }
        }
        for (effect in allChoices.flatMap { it.effects }) {
            when (effect) {
                is Effect.ModifyStat -> if (effect.delta > 0) positiveSet.add(effect.name)
                is Effect.Set -> if (effect.value > 0) positiveSet.add(effect.name)
                is Effect.SetFlag, is Effect.RemoveFlag -> Unit
            }
        }

        fun isItemOrHealth(name: String): Boolean {
            val tag = tags[name] ?: return false
            return tag.category == TagCategory.ITEM || tag.category == TagCategory.HEALTH
        }

        val reported = mutableSetOf<Pair<SceneId, String>>()
        val issues = mutableListOf<LintIssue>()

        fun check(sceneId: SceneId, effect: Effect) {
            if (effect !is Effect.ModifyStat) return
            if (effect.delta >= 0) return
            if (!isItemOrHealth(effect.name)) return
            if (effect.name in positiveSet) return
            val key = sceneId to effect.name
            if (!reported.add(key)) return
            issues += LintIssue(
                kind = LintKind.RemovingUnestablishedItem(effect.name),
                message = "Scene '${sceneId.value}' reduces '${effect.name}' " +
                        "by ${-effect.delta}, but '${effect.name}' is never established " +
                        "(not in initial_state with a positive value, no positive change " +
                        "in any scene or choice). The player cannot use it.",
                sceneId = sceneId,
                severity = Severity.WARNING,
            )
        }

        for (scene in allScenes) {
            for (effect in scene.effects) check(scene.id, effect)
            for (choice in scene.choices) {
                for (effect in choice.effects) check(scene.id, effect)
            }
        }
        return issues
    }

    private fun lintDeathSceneWithoutHealth(compiled: CompiledAdventure): List<LintIssue> {
        if (compiled.adventure.deathScene == null) return emptyList()
        if (compiled.adventure.initialState.stats.containsKey("health")) return emptyList()
        val allScenes = compiled.adventure.chapters.flatMap { it.scenes }
        // Ф3.1: `Effect.Set("health", N)` uses health just as
        // `Effect.ModifyStat("health", ±N)` does. Both variants count.
        fun Effect.touchesHealth(): Boolean = when (this) {
            is Effect.ModifyStat -> name == "health"
            is Effect.Set -> name == "health"
            is Effect.SetFlag, is Effect.RemoveFlag -> false
        }
        val hasHealthEffect = allScenes.any { scene ->
            scene.effects.any { it.touchesHealth() } ||
                    scene.choices.any { choice ->
                        choice.effects.any { it.touchesHealth() }
                    }
        }
        if (hasHealthEffect) return emptyList()
        return listOf(
            LintIssue(
                kind = LintKind.DeathSceneWithoutHealth,
                message = "Adventure has a death scene, but 'health' is never " +
                        "initialized (not in initial_state.stats) and never modified " +
                        "(no ModifyStat for 'health' in any scene or choice). " +
                        "Death will never occur.",
                sceneId = null,
                severity = Severity.WARNING,
            ),
        )
    }

    // ----- Ф1.6.2.3: condition on unestablished flag (port of :app check #4) -----

    private fun lintConditionOnUnestablishedFlag(
        compiled: CompiledAdventure,
    ): List<LintIssue> {
        // "Established" = the name appears somewhere the runtime can see it:
        //   - initial_state.flags (Bool flag set from the start)
        //   - initial_state.stats keys (numeric value set from the start;
        //     shim until Ф2.2, see LintKind.ConditionOnUnestablishedFlag KDoc)
        //   - any Effect.SetFlag in any scene or choice
        val establishable = mutableSetOf<String>()
        establishable += compiled.adventure.initialState.flags
        establishable += compiled.adventure.initialState.stats.keys
        val allScenes = compiled.adventure.chapters.flatMap { it.scenes }
        for (scene in allScenes) {
            for (effect in scene.effects) {
                if (effect is Effect.SetFlag) establishable.add(effect.name)
            }
            for (choice in scene.choices) {
                for (effect in choice.effects) {
                    if (effect is Effect.SetFlag) establishable.add(effect.name)
                }
            }
        }

        val issues = mutableListOf<LintIssue>()
        for (scene in allScenes) {
            for (choice in scene.choices) {
                val condition = choice.condition ?: continue
                val flags = collectHasFlags(condition)
                for (flag in flags.sorted()) {
                    if (flag in establishable) continue
                    issues += LintIssue(
                        kind = LintKind.ConditionOnUnestablishedFlag(flag),
                        message = "Choice '${choice.text}' in scene '${scene.id.value}' " +
                                "has condition referencing flag '$flag', but '$flag' never " +
                                "appears anywhere in the adventure (not in initial_state, " +
                                "no SetFlag effect in any scene or choice). The condition " +
                                "cannot behave as intended.",
                        sceneId = scene.id,
                        severity = Severity.WARNING,
                    )
                }
            }
        }
        return issues
    }

    /** Recursively collects every [Condition.Has] name (also inside Not/And/Or). */
    private fun collectHasFlags(condition: Condition): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(c: Condition) {
            when (c) {
                is Condition.Has -> result.add(c.name)
                is Condition.VarRef -> {
                    // Ф3.1: `VarRef` semantically reads like `Has` (bare
                    // Bool reference), so it establishes the same
                    // "condition-on-this-flag" signal. Currently
                    // unreachable — the parser does not produce
                    // `VarRef` until Ф3.2 — but keeping the parallel
                    // branch here means Ф3.2 does not need to remember
                    // to update this function.
                    result.add(c.name)
                }
                is Condition.Literal -> Unit // no variable reference
                is Condition.Not -> visit(c.inner)
                is Condition.And -> { visit(c.left); visit(c.right) }
                is Condition.Or -> { visit(c.left); visit(c.right) }
                is Condition.Compare -> Unit // numeric, not a flag
                is Condition.InRange -> Unit // numeric, not a flag
            }
        }
        visit(condition)
        return result
    }

    // ----- Ф1.6.2.3: death without death scene (port of :app check #6) -----

    private fun lintDeathWithoutDeathScene(compiled: CompiledAdventure): List<LintIssue> {
        if (compiled.adventure.deathScene != null) return emptyList()
        val hasHealthInInitial = compiled.adventure.initialState.stats.containsKey("health")
        // Ф3.1: same `touchesHealth` treatment as in
        // `lintDeathSceneWithoutHealth` — `Set("health", N)` counts.
        fun Effect.touchesHealth(): Boolean = when (this) {
            is Effect.ModifyStat -> name == "health"
            is Effect.Set -> name == "health"
            is Effect.SetFlag, is Effect.RemoveFlag -> false
        }
        val hasHealthEffect = compiled.adventure.chapters.flatMap { it.scenes }.any { scene ->
            scene.effects.any { it.touchesHealth() } ||
                    scene.choices.any { choice ->
                        choice.effects.any { it.touchesHealth() }
                    }
        }
        if (!hasHealthInInitial && !hasHealthEffect) return emptyList()
        return listOf(
            LintIssue(
                kind = LintKind.DeathWithoutDeathScene,
                message = "Adventure uses 'health' but does not define a death_scene. " +
                        "When the player's health reaches 0, a default death screen will be " +
                        "shown. Add a death_scene to customize the message and the recovery " +
                        "options.",
                sceneId = null,
                severity = Severity.WARNING,
            ),
        )
    }

    // ----- Ф1.6.2.3: dead end (port of :app check #8, adapted to IR v3) -----

    private fun lintDeadEnd(compiled: CompiledAdventure): List<LintIssue> {
        val reachable = reachable(compiled).reachable
        val issues = mutableListOf<LintIssue>()
        for (chapter in compiled.adventure.chapters) {
            for (scene in chapter.scenes) {
                if (scene.choices.isNotEmpty()) continue
                if (scene.isEnding) continue // explicitly marked as an ending — ok
                if (scene.id !in reachable) continue // UnreachableScene covers this
                issues += LintIssue(
                    kind = LintKind.DeadEnd,
                    message = "Scene '${scene.id.value}' has no choices and is not marked " +
                            "as an ending. The player will see \"the end\" here and will not " +
                            "reach the remaining scenes. Either add choices or set " +
                            "\"is_ending\": true on this scene.",
                    sceneId = scene.id,
                    severity = Severity.WARNING,
                )
            }
        }
        return issues
    }

    // ----- Ф1.7.0: ending scene with choices -----

    private fun lintEndingSceneWithChoices(compiled: CompiledAdventure): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        for (chapter in compiled.adventure.chapters) {
            for (scene in chapter.scenes) {
                if (!scene.isEnding) continue
                if (scene.choices.isEmpty()) continue
                issues += LintIssue(
                    kind = LintKind.EndingSceneWithChoices(scene.id),
                    message = "Scene '${scene.id.value}' is marked as an ending " +
                            "(\"is_ending\": true) but has ${scene.choices.size} choice(s). " +
                            "The reader shows the 'the end' screen and ignores the choices, " +
                            "so the player sees dead buttons. Either remove the choices or " +
                            "clear \"is_ending\".",
                    sceneId = scene.id,
                    severity = Severity.ERROR,
                )
            }
        }
        return issues
    }


    // ----- Ф3.5: invariant on unestablished flag -----

    /**
     * Checks every [com.myapp.adventure.engine.ir.Invariant] for
     * Bool flag references (`Has` / `VarRef`) that are never set
     * anywhere — see [LintKind.UnreachableInvariant] KDoc.
     *
     * **Not the same as `lintConditionOnUnestablishedFlag`.** That
     * function checks **choices** (per-scene conditions on the player's
     * path); this one checks **global invariants** (always evaluated
     * after every step). Both share the same "establishable" set
     * (initial flags/stats + every `SetFlag` in any scene/choice), but
     * the issue is scoped differently: a choice issue is navigable to a
     * scene, an invariant issue is not (`sceneId = null`).
     *
     * **Numeric conditions are not checked here** — Ф3.6
     * (`Analyzer.deadConditions`) will handle `health < -100` and
     * similar reachability questions, which require more nuance than
     * "is the name anywhere?".
     */
    private fun lintUnreachableInvariants(
        compiled: CompiledAdventure,
    ): List<LintIssue> {
        val invariants = compiled.adventure.invariants
        if (invariants.isEmpty()) return emptyList()

        // Establishable set: same convention as
        // lintConditionOnUnestablishedFlag (initial flags, initial
        // stats keys, every SetFlag target in any scene or choice).
        val establishable = mutableSetOf<String>()
        establishable += compiled.adventure.initialState.flags
        establishable += compiled.adventure.initialState.stats.keys
        val allScenes = compiled.adventure.chapters.flatMap { it.scenes }
        for (scene in allScenes) {
            for (effect in scene.effects) {
                if (effect is Effect.SetFlag) establishable.add(effect.name)
            }
            for (choice in scene.choices) {
                for (effect in choice.effects) {
                    if (effect is Effect.SetFlag) establishable.add(effect.name)
                }
            }
        }

        val issues = mutableListOf<LintIssue>()
        for ((index, invariant) in invariants.withIndex()) {
            val flags = collectHasFlags(invariant.condition)
            val missing = flags.filter { it !in establishable }.sorted()
            if (missing.isEmpty()) continue
            val names = missing.joinToString(", ") { "'$it'" }
            issues += LintIssue(
                kind = LintKind.UnreachableInvariant(
                    reason = "references flag(s) never set: $names",
                ),
                message = "Invariant #$index references flag(s) that are never " +
                        "established (not in initial_state, no SetFlag effect in any " +
                        "scene or choice): $names. The invariant cannot fire as written. " +
                        "Check the flag name for a typo, or add a SetFlag that sets it.",
                sceneId = null,
                severity = Severity.WARNING,
            )
        }
        return issues
    }
}