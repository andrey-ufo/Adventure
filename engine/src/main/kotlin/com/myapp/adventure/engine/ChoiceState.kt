package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Choice

/**
 * Ф4.4 (N4): the *display state* of a [Choice] from the player's point
 * of view — a three-way answer to "what does the UI do with this
 * button?".
 *
 * **Why a dedicated type, not just `Boolean`.** The reader used to render
 * a choice as either enabled or disabled, with no reason. The DoD for
 * N4 asks for a reason on disabled choices, and separately asks for
 * *hidden* choices to disappear entirely (the `visibilityCondition`
 * field added in Ф4.2.1). Three states cover both:
 *
 * - [Visible] — enabled; tapping the choice runs the normal
 *   transition (`Engine.step` or the reader's inline path).
 * - [Disabled] — shown as a greyed-out button with a plain
 *   "(недоступно)" suffix; the structured [Reason] is carried but not
 *   rendered by the reader today (see [Disabled] KDoc).
 * - [Hidden] — not shown at all. The choice's
 *   [com.myapp.adventure.engine.ir.Choice.visibilityCondition] is
 *   `false` — the author decided this option should not be presented to
 *   the player at all (as opposed to [Disabled], which says "you can
 *   see it but cannot pick it *yet*").
 *
 * **What this type is NOT.** Not a runtime gate — `Engine.step` still
 * rejects a choice whose [com.myapp.adventure.engine.ir.Choice.condition]
 * is `false` via `StepResult.Rejected(Blocked(...))`. [ChoiceState] is
 * the *pre-check* the UI runs to decide how to render the button before
 * the player taps it. The two layers are complements: `Engine.step` is
 * the final authority (the transition happens or does not), [choiceState]
 * is the display hint. If they disagree (a bug in the caller), the
 * transition still fails correctly — worst case, the button was shown
 * as enabled but nothing happens (same as today's silent-fail state,
 * now structurally impossible if the reader goes through [choiceState]).
 *
 * **Why in `:engine`, not `:app`.** The *state* (Visible / Disabled /
 * Hidden) is a pure function of `(Choice, PlayerState)` — no `Context`,
 * no strings, no `Resources`.  * Putting it in `:app` would deny reuse to
 *  * a future JS runtime (E1.base) or to an in-editor realtime linter
 *  * (Ф9.2), which also want to know "why is this choice disabled".
 *  * The rendering side (what string to show to the user) is an `:app`
 *  * concern; today the reader deliberately shows only
 *  * `(недоступно)` and does not render the specific [Reason] (see
 *  * `Disabled` KDoc).
 *
 * **`OrFailed` / `AndFailed`.** When the underlying [Reason] is
 * `AndFailed`, [Disabled.reason] carries the first failing sub-reason —
 * the wrapper adds no information ("A && B fails" == "A fails (and B is
 * not checked)"). `OrFailed` keeps both sides: the player cannot pick
 * this choice for two distinct reasons, and the UI may want to list
 * both ("requires `sword` or `magic`").
 */
public sealed class ChoiceState {

    /**
     * The choice is enabled. Tapping it does the normal transition.
     */
    public data object Visible : ChoiceState()

    /**
     * The choice is shown but cannot be picked. [reason] is the
     * structured cause — carried in the engine for potential future
     * display (debug panel, author tooling, "long-press for hint"), but
     * **not rendered by the reader today**. The reader shows a plain
     * `(недоступно)` suffix; rendering the specific reason was
     * deliberately removed in Ф4.4-fix — see `updateChoices` KDoc in
     * `AdventureReaderFragment` for the rationale (Russian morphology,
     * redundancy with choice text / HUD, gamebook convention).
     */
    public data class Disabled(public val reason: Reason) : ChoiceState()

    /**
     * The choice is not shown. Its
     * [com.myapp.adventure.engine.ir.Choice.visibilityCondition] is
     * `false` — the author decided this option should not exist in the
     * UI at all.
     *
     * **`Hidden` wins over `Disabled` (decision G1).** If both
     * `visibilityCondition` and `condition` are false, the state is
     * `Hidden`. Rationale: `visibilityCondition` is a stronger
     * statement — the author decided the player should not even see
     * this option; a reason for a hidden option is dead information
     * (never rendered). This mirrors the natural-language semantics:
     * "hide it" beats "show it disabled with a reason".
     */
    public data object Hidden : ChoiceState()
}