package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Outcome
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
 * **Death is not an error.** [Success.outcome] is `Outcome.Death` when
 * an **invariant** fired after the choice's effects were applied (Ф3.5,
 * via [Engine.evaluateInvariants]). The target scene's entry effects
 * are **not** applied here — that is the reader's responsibility (it
 * owns idempotency via `appliedSceneEffects` in progress, because
 * re-entering a scene must not re-apply `ModifyStat`). The reader can
 * use `outcome` in this result as a fast-path: if it is
 * `Outcome.Death`, show the death screen immediately and skip entering
 * the scene. If `null`, the reader enters the scene, applies
 * `scene.effects` (if first time), and re-checks invariants with
 * [Engine.evaluateInvariants] — **scene** effects can fire an
 * invariant too, and the engine cannot know whether they have already
 * been applied.
 */
public sealed class StepResult {

    /**
     * The step succeeded: [state] is the new player state,
     * [currentScene] is the scene the player is now in, and [outcome]
     * is the invariant outcome (if any) that fired after the choice
     * effects.
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
     *
     * @property state new player state after choice effects.
     * @property currentScene the scene the player is now in.
     * @property outcome invariant outcome that fired (Ф3.5). `null` =
     *   no invariant matched — the player is alive and nothing special
     *   happened. `Outcome.Death` = the player died (an invariant or
     *   the fallback `health <= 0` rule fired). Future variants
     *   (`Ending`, `Custom`) will be checked by the reader's
     *   `when(outcome)`.
     */
    public data class Success(
        public val state: PlayerState,
        public val currentScene: Scene,
        public val outcome: Outcome? = null,
        /**
         * Ф4.9 (N4): choices in [currentScene] that are visible but
         * disabled by their `condition`, with the structured reason from
         * [Evaluator.choiceState].
         *
         * Hidden choices are excluded (`visibilityCondition == false`);
         * enabled choices are excluded. A scene-level gate is **not**
         * duplicated here — the reader disables all choices uniformly
         * while the gate is active (see `Scene.gate` / `Blocker.GateNotPassed`).
         *
         * Computed against the **post-step** state, so a subroutine call
         * that changes a flag is reflected immediately. Defaults to an
         * empty list for callers that only need the old fields.
         */
        public val disabledChoices: List<DisabledChoice> = emptyList(),
        /**
         * Ф5.2 (S5): the player-state operation log applied by this step —
         * choice effects first, then top-level subroutine output writes.
         *
         * The scene transition / gate / item order are not player-state
         * deltas; they stay in the save cursor. Callers that only need
         * the new state can ignore this field (empty default for old
         * call sites/tests).
         */
        public val appliedDeltas: List<StateDelta> = emptyList(),
    ) : StepResult() {

        /**
         * Convenience for the pre-Ф3.5 callsite pattern
         * (`if (success.isDead) showDeathScreen()`).
         *
         * **Do not use as the sole check for death.** The reader must
         * re-run [Engine.evaluateInvariants] after applying
         * `currentScene.effects` — a scene effect can bring the player
         * into a violated invariant, and `outcome` here only reflects
         * **choice** effects. `isDead` is a fast-path: `true` → show
         * death screen immediately; `false` → enter the scene, apply
         * effects, re-check.
         */
        public val isDead: Boolean get() = outcome == Outcome.Death
    }

    /**
     * The step was rejected; see [reason] for the cause.
     */
    public data class Rejected(public val reason: RejectReason) : StepResult()
}

/**
 * Ф4.9 (N4): a visible-but-disabled choice in the **target** scene after a
 * successful step.
 *
 * `reason` is the structured [ChoiceState.Disabled.reason]; formatting the
 * player-facing text is an `:app` concern (same split as
 * [com.myapp.adventure.engine.ir.types.TypeError]).
 */
public data class DisabledChoice(
    public val choiceId: ChoiceId,
    public val reason: Reason,
)

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
     * Ф4.7: an atomic subroutine call could not be executed — unknown
     * callee, missing argument, unsupported parameter type, or nesting
     * deeper than the safety limit.
     *
     * **Defensive.** `Engine.compile` rejects unknown ids, signature
     * mismatches and cycles before the reader can open the file; this
     * variant covers hand-built `CompiledAdventure`s and corrupted IR. It
     * is a structural bug, not a "disabled choice": log it, do not render
     * it as a player-facing reason.
     */
    public data class SubroutineFailure(
        public val subroutineId: String,
        public val reason: String,
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