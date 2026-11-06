package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.PlayerState

/**
 * Entry point of the runtime: compiles an [Adventure] into a
 * [CompiledAdventure] and steps through scenes.
 *
 * **Phase status.** Ф1.5.5 step 1: only [compile] is implemented.
 * [step] (and its supporting logic) comes in step 2.
 *
 * **Stateless.** `Engine` is an `object` with no fields. Every operation
 * takes all inputs explicitly. This makes:
 * - `Engine.step` trivially thread-safe (no shared mutable state);
 * - unit tests independent (no setup, no teardown, no ordering);
 * - the reader (`:app`) able to drop and recreate `CompiledAdventure`
 *   without worrying about `Engine` lifecycle.
 *
 * **O(1) per step.** All per-scene lookups go through the indexes in
 * [CompiledAdventure], never through a linear scan of `adventure.chapters`.
 * This keeps `P1.base` (1–2 thousand scenes, Must) and `P1.advanced`
 * (10k+ scenes, Nice) both free of stutter on transitions.
 */
public object Engine {

    /**
     * Builds the runtime indexes for [adventure].
     *
     * Call this **once** per adventure, at load time (reader) or per
     * authoring session (editor preview, analyzer). Never call
     * [compile] from inside [step]: that would rebuild the indexes on
     * every transition.
     *
     * O(n) in the number of scenes; n is small (thousands at most) and
     * the cost is amortized over many steps.
     *
     * @return [CompileResult.Success] with the compiled form, or
     *   [CompileResult.Failure] with one or more [CompileError]s. Only
     *   errors that prevent building the indexes are reported; semantic
     *   issues belong to `Analyzer` (Ф1.6).
     */
    public fun compile(adventure: Adventure): CompileResult {
        val errors = mutableListOf<CompileError>()
        val scenesById = mutableMapOf<SceneId, Scene>()
        val chapterIndexBySceneId = mutableMapOf<SceneId, Int>()
        // Track where each duplicated id was seen, so the error message
        // points at the actual chapters, not just "somewhere".
        val chaptersBySceneId = mutableMapOf<SceneId, MutableList<Int>>()

        adventure.chapters.forEachIndexed { chapterIndex, chapter ->
            chapter.scenes.forEach { scene ->
                chaptersBySceneId
                    .getOrPut(scene.id) { mutableListOf() }
                    .add(chapterIndex)
                // First occurrence wins in the index. The author sees the
                // duplicate-id error and decides which scene is the
                // canonical one; we do not silently pick.
                scenesById.putIfAbsent(scene.id, scene)
                chapterIndexBySceneId.putIfAbsent(scene.id, chapterIndex)
            }
        }

        chaptersBySceneId.forEach { (sceneId, chapterIndices) ->
            if (chapterIndices.size > 1) {
                errors += CompileError.DuplicateSceneId(
                    sceneId = sceneId,
                    chapterIndices = chapterIndices.toList(),
                )
            }
        }

        return if (errors.isEmpty()) {
            CompileResult.Success(
                CompiledAdventure(
                    adventure = adventure,
                    scenesById = scenesById,
                    chapterIndexBySceneId = chapterIndexBySceneId,
                ),
            )
        } else {
            CompileResult.Failure(errors)
        }
    }

    /**
     * Takes a step: applies the chosen [Choice]'s effects, moves to its
     * `next_scene`, and returns the new state.
     *
     * **What this method does and does not apply.**
     * - Applies **`choice.effects`** (in list order).
     * - Does **not** apply the target scene's `effects`. That is the
     *   reader's job: it tracks `appliedSceneEffects` in progress and
     *   applies a scene's entry effects **once**, on first entry. Doing
     *   it here would re-drain health or re-add items on every
     *   re-entry — a data-loss bug.
     *
     * Consequence: [StepResult.Success.isDead] reflects **only** the
     * choice's effects. The reader must re-check
     * [Evaluator.isDead] after applying the target scene's effects.
     *
     * **Blocker semantics.**
     * - [RejectReason.SceneNotFound] / [RejectReason.ChoiceNotFound] —
     *   **structural bugs** (wrong id, or the adventure is incomplete).
     *   Reader logs; the analyzer (Ф1.6) should not have allowed it.
     * - [RejectReason.Blocked] — the choice exists but the player cannot
     *   take it **right now**. Carries **all** blockers (condition
     *   failure, gate not passed) so the reader can show them together
     *   on a disabled choice (N4 / Ф4).
     *
     * **Order of checks.**
     * 1. Current scene exists. → [RejectReason.SceneNotFound]
     * 2. Choice exists in that scene. → [RejectReason.ChoiceNotFound]
     * 3. All blockers collected (condition, gate). If any →
     *    [RejectReason.Blocked], in order: [Blocker.ConditionNotMet]
     *    before [Blocker.GateNotPassed] (persistent before temporary).
     * 4. Target scene exists. If not → [RejectReason.SceneNotFound]
     *    (`choice.nextScene`). **Checked before applying effects** so
     *    a missing target scene does not silently apply effects into a
     *    void.
     * 5. `choice.effects` applied. → [StepResult.Success].
     *
     * **Gate and `stepsInScene`.**
     * The engine does not count steps. The reader passes how many steps
     * the player has walked **since entering the current scene**:
     * - `null` — "unknown". Treated as **0 walked** → gate blocks. Safe
     *   default: a caller that forgets to pass steps must not bypass a
     *   gate.
     * - A non-negative integer — the current count. Gate blocks iff
     *   `count < gate.requiredSteps`.
     *
     * `stepsInScene` is ignored for scenes without a gate.
     *
     * @return [StepResult.Success] or [StepResult.Rejected]; never null.
     */
    public fun step(
        compiled: CompiledAdventure,
        state: PlayerState,
        sceneId: SceneId,
        choiceId: ChoiceId,
        stepsInScene: Int? = null,
    ): StepResult {
        // 1. Current scene.
        val currentScene = compiled.scenesById[sceneId]
            ?: return StepResult.Rejected(RejectReason.SceneNotFound(sceneId))

        // 2. Choice within the current scene.
        val choice = currentScene.choices.firstOrNull { it.id == choiceId }
            ?: return StepResult.Rejected(RejectReason.ChoiceNotFound(choiceId))

        // 3. Collect all blockers, in order: persistent (condition)
        //    before temporary (gate).
        val blockers = mutableListOf<Blocker>()
        choice.condition?.let { condition ->
            if (!Evaluator.evaluate(condition, state)) {
                blockers += Blocker.ConditionNotMet(condition)
            }
        }
        currentScene.gate?.let { gate ->
            val walked = stepsInScene ?: 0
            if (walked < gate.requiredSteps) {
                blockers += Blocker.GateNotPassed(gate.requiredSteps - walked)
            }
        }
        if (blockers.isNotEmpty()) {
            return StepResult.Rejected(RejectReason.Blocked(blockers))
        }

        // 4. Target scene must exist before we apply effects.
        val nextScene = compiled.scenesById[choice.nextScene]
            ?: return StepResult.Rejected(RejectReason.SceneNotFound(choice.nextScene))

        // 5. Apply choice effects (target scene's effects are reader's job).
        val stateAfterChoice = EffectsApplier.apply(state, choice.effects)

        return StepResult.Success(
            state = stateAfterChoice,
            currentScene = nextScene,
            isDead = Evaluator.isDead(stateAfterChoice),
        )
    }
}