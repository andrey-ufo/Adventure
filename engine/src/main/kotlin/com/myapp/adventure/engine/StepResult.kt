package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId

/**
 * Outcome of [Engine.step].
 *
 * **Why a sealed result and not just `PlayerState`.** A transition can
 * fail for two different classes of reason that the reader must
 * distinguish in the UI:
 *
 * 1. **Structural error** — the choice or the scene does not exist. This
 *    is a bug (wrong `ChoiceId` from the UI, or the adventure is missing
 *    a referenced scene). The reader logs it; the analyzer (Ф1.6) should
 *    have caught it. Modelled as [Rejected] with
 *    [RejectReason.ChoiceNotFound] / [RejectReason.SceneNotFound].
 *
 * 2. **Blockers** — the choice exists but is not currently available.
 *    There may be **one or several** blockers at the same time (for
 *    example: "requires `sword` flag" **and** "walk 100 more steps").
 *    Modelled as [Rejected] with [RejectReason.Blocked], which carries
 *    **all** blockers in one list so the reader can show them together
 *    on a disabled choice (N4 / Ф4).
 *
 * Collapsing these two classes into a single "rejected" boolean would
 * lose information the UI needs: a bug must be visible in logs, not as a
 * "disabled" choice; a blocked choice must show a reason, not a generic
 * "недоступно".
 *
 * **Death is not an error.** [Success.isDead] is `true` when the
 * **choice's** effects reduced the player's health to `<= 0`. The
 * target scene's entry effects are **not** applied here — that is
 * the reader's responsibility (it owns idempotency via
 * `appliedSceneEffects` in progress, because re-entering a scene must
 * not re-apply `ModifyStat`). The reader can use `isDead` in this
 * result as a fast-path: if `true`, show the death screen
 * immediately and skip entering the scene. If `false`, the reader
 * enters the scene, applies `scene.effects` (if first time), and
 * re-checks death with [Evaluator.isDead] — **scene** effects can
 * kill too, and the engine cannot know whether they have already
 * been applied.
 */
public sealed class StepResult {

    /**
     * The step succeeded: [state] is the new player state,
     * [currentScene] is the scene the player is now in.
     *
     * **Effects already applied.** Scene effects (from
     * `currentScene.effects`) are applied to [state] before this result
     * is returned. Choice effects were applied before the transition.
     * The reader should not reapply them.
     *
     * **`appliedSceneEffects` is not tracked by the engine.** The
     * decision "should I apply this scene's effects again, or have I
     * already?" belongs to the reader (it owns progress persistence and
     * the "first enter" semantics). The engine is idempotent per call,
     * not per scene.
     */
    public data class Success(
        public val state: PlayerState,
        public val currentScene: Scene,
        public val isDead: Boolean,
    ) : StepResult()

    /**
     * The step was rejected; see [reason] for the cause.
     */
    public data class Rejected(public val reason: RejectReason) : StepResult()
}

/**
 * Why a step was rejected.
 *
 * Two unrelated classes of cause share this type because both are
 * "step could not proceed" from the caller's point of view — but the UI
 * treats them differently:
 *
 * - **[ChoiceNotFound] / [SceneNotFound]** — bug in the data or the
 *   caller. Not shown as a "disabled" choice; logged as an error.
 * - **[Blocked]** — the choice exists but the player cannot take it
 *   right now. Shown as a disabled choice with **all** [Blocker]s listed
 *   as reasons (N4 / Ф4). Example: "requires `sword`; walk 100 more
 *   steps".
 */
public sealed class RejectReason {

    /** No choice with this id exists in the current scene. */
    public data class ChoiceNotFound(
        public val choiceId: ChoiceId,
    ) : RejectReason()

    /** No scene with this id exists in the compiled adventure. */
    public data class SceneNotFound(
        public val sceneId: SceneId,
    ) : RejectReason()

    /**
     * The choice exists but is blocked by one or more conditions.
     *
     * **Invariant:** [blockers] is never empty. `Engine.step` returns
     * [Blocked] only when at least one blocker is present; a choice with
     * no blockers produces [StepResult.Success].
     *
     * **Order.** Blockers are ordered with **persistent** blockers
     * first (conditions the player cannot fix by walking), then
     * **temporary** blockers (gates). The reader should render them in
     * this order so the player sees the harder-to-fix reason first.
     */
    public data class Blocked(
        public val blockers: List<Blocker>,
    ) : RejectReason()
}

/**
 * A single reason a choice is not currently available.
 *
 * Every variant is **specific to a choice attempt** — the reader shows
 * these as reasons on a disabled button. Structural problems (missing
 * scene, missing choice) are **not** blockers: they are
 * [RejectReason.ChoiceNotFound] / [RejectReason.SceneNotFound], because
 * a UI cannot show "this button is disabled because it does not exist"
 * meaningfully.
 */
public sealed class Blocker {

    /**
     * `choice.condition` evaluated to `false` against the state at the
     * moment of the step.
     *
     * **Persistent blocker.** Walking more steps does not fix it; the
     * player must do something else (find an item, complete a quest).
     */
    public data class ConditionNotMet(
        public val condition: Condition,
    ) : Blocker()

    /**
     * The current scene has a `gate` and the player has not yet walked
     * `gate.requiredSteps` steps since entering it.
     *
     * **Temporary blocker.** Walking `remainingSteps` more steps will
     * clear it.
     *
     * @property remainingSteps steps still needed. Always `> 0`.
     */
    public data class GateNotPassed(
        public val remainingSteps: Int,
    ) : Blocker()
}