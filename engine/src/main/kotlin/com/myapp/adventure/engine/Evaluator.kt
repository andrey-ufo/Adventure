package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.PlayerState

/**
 * Single evaluator of [Condition]s against a [PlayerState].
 *
 * **One source of truth.** Every check of a condition in the whole
 * project goes through this object: `Engine.step` (is the choice
 * available?), the reader UI (`:app`, show disabled with reason — N4),
 * the editor analyzer (`:app`, Ф1.6 / Ф9.2), the debug panel (`:app`).
 * If two callers interpreted a condition differently, the engine could
 * allow a choice the UI showed as disabled, or vice versa. Preventing
 * that is exactly what N2 (`Ф3`) exists for; this object is the single
 * implementation.
 *
 * **Two entry points (Ф3.3):**
 *
 * - [evaluate] returns `Boolean` — thin wrapper over
 *   [evaluateWithReason]. **Use when the reason is not needed.**
 *   `Engine.step` (blocker list), `Analyzer` (reachability checks),
 *   `TypeChecker` (irrelevant here) — none of them need to know
 *   *which* subexpression failed, only *whether* the whole holds.
 * - [evaluateWithReason] returns [EvaluationResult] — either
 *   [EvaluationResult.Success] or [EvaluationResult.Failure] with a
 *   structured [Reason]. **Use when the UI will show the cause** —
 *   N4 / Ф4 disabled-with-reason. The wrapper exists so that callers
 *   who only need `Boolean` do not pay for the `Reason` allocation
 *   (in practice negligible, but the API signals intent).
 *
 * **Semantics** (as of IR v5):
 * - [Condition.Has] — true if `name` is in [PlayerState.flags]. **Not**
 *   `stats` — `has_sword` is the syntactic form for boolean flags.
 *   Type checker in Ф2.2 rejects `has_sword` on non-boolean
 *   declarations; until then, a `Has` on a name that lives in `stats`
 *   simply returns `false` (and the [Reason.HasFailed] reason).
 * - [Condition.VarRef] — same runtime semantics as [Condition.Has]
 *   (reads `state.flags`). Currently not produced by the parser
 *   (see [Condition.VarRef] KDoc); supported so that hand-built IR
 *   works and Ф3.2-future parser changes land without a runtime gap.
 * - [Condition.Literal] — evaluates to its `value` directly.
 * - [Condition.Not] — logical negation. `Not(X)` **fails** iff `X`
 *   holds ([Reason.NotFailed]).
 * - [Condition.And], [Condition.Or] — short-circuit (`&&`, `||`). In
 *   [evaluateWithReason], the reason for `And(A, B)` is the reason of
 *   the **first** false side (left, if left is false; right, if left
 *   is true and right is false). For `Or(A, B)`, if both sides are
 *   false, **both** reasons are reported ([Reason.OrFailed]).
 * - [Condition.Compare] — `stats[varName] ?: 0` compared to `value`
 *   by `op`. `actualValue` in [Reason.CompareFailed] is what the
 *   evaluator actually used (0 if the variable is not in `stats`).
 * - [Condition.InRange] — `stats[varName] ?: 0` in `min..max`
 *   (inclusive). `actualValue` same convention.
 *
 * **Pure.** No I/O, no state mutation, no exceptions on user input:
 * unknown variables default to `0` (`Compare` / `InRange`) or `false`
 * (`Has` / `VarRef`). The type checker (Ф2.2) is what reports
 * "unknown variable" to the author; the evaluator is not the place
 * for that.
 *
 * **Deferred to Ф3.5.** Global invariants (`health <= 0 => death` as
 * first-class IR, `Invariant`). Today `isDead` is a single hardcoded
 * rule; Ф3.5 will promote the invariants and `isDead` will become one
 * of them.
 */
public object Evaluator {

    /**
     * Evaluates [condition] against [state].
     *
     * Returns `true` iff the condition holds. Unknown variable names
     * resolve to their zero-value ([PlayerState.stats] default `0`,
     * [PlayerState.flags] default `false`).
     *
     * **Thin wrapper** over [evaluateWithReason]: use when only the
     * answer matters, not the reason (e.g. `Engine.step` constructing
     * the blocker list). For the UI (N4 / Ф4) use [evaluateWithReason].
     */
    public fun evaluate(condition: Condition, state: PlayerState): Boolean =
        evaluateWithReason(condition, state) is EvaluationResult.Success

    /**
     * Evaluates [condition] against [state], with a structured [Reason]
     * for the failure case.
     *
     * **Short-circuit rules for [Reason]**:
     * - `And(A, B)` where `A` is false → `AndFailed(A.reason)`, `B`
     *   not evaluated.
     * - `And(A, B)` where `A` is true, `B` is false →
     *   `AndFailed(B.reason)`.
     * - `Or(A, B)` where `A` is true → [EvaluationResult.Success], `B`
     *   not evaluated.
     * - `Or(A, B)` where both are false → `OrFailed(A.reason,
     *   B.reason)`.
     * - `Not(A)` where `A` is true → `NotFailed(A)` (the AST node, not
     *   a `Reason` — `A` succeeded, there is no `Reason` to nest).
     * - `Not(A)` where `A` is false → [EvaluationResult.Success].
     *
     * The `Reason` tree mirrors the `Condition` tree **only at the
     * branch points that failed**. A successful subtree produces
     * `Success`, not a "success reason".
     */
    public fun evaluateWithReason(
        condition: Condition,
        state: PlayerState,
    ): EvaluationResult {
        return when (condition) {
            is Condition.Literal -> if (condition.value) {
                EvaluationResult.Success
            } else {
                EvaluationResult.Failure(Reason.LiteralFailed)
            }
            is Condition.VarRef -> if (condition.name in state.flags) {
                EvaluationResult.Success
            } else {
                EvaluationResult.Failure(Reason.VarRefFailed(condition.name))
            }
            is Condition.Has -> if (condition.name in state.flags) {
                EvaluationResult.Success
            } else {
                EvaluationResult.Failure(Reason.HasFailed(condition.name))
            }
            is Condition.Not -> when (
                val innerResult = evaluateWithReason(condition.inner, state)
            ) {
                is EvaluationResult.Success ->
                    EvaluationResult.Failure(Reason.NotFailed(condition.inner))
                is EvaluationResult.Failure -> EvaluationResult.Success
            }
            is Condition.And -> {
                val leftResult = evaluateWithReason(condition.left, state)
                when (leftResult) {
                    is EvaluationResult.Failure ->
                        EvaluationResult.Failure(Reason.AndFailed(leftResult.reason))
                    is EvaluationResult.Success -> {
                        val rightResult = evaluateWithReason(condition.right, state)
                        when (rightResult) {
                            is EvaluationResult.Failure ->
                                EvaluationResult.Failure(Reason.AndFailed(rightResult.reason))
                            is EvaluationResult.Success -> EvaluationResult.Success
                        }
                    }
                }
            }
            is Condition.Or -> {
                val leftResult = evaluateWithReason(condition.left, state)
                when (leftResult) {
                    is EvaluationResult.Success -> EvaluationResult.Success
                    is EvaluationResult.Failure -> {
                        val rightResult = evaluateWithReason(condition.right, state)
                        when (rightResult) {
                            is EvaluationResult.Success -> EvaluationResult.Success
                            is EvaluationResult.Failure -> EvaluationResult.Failure(
                                Reason.OrFailed(
                                    leftReason = leftResult.reason,
                                    rightReason = rightResult.reason,
                                )
                            )
                        }
                    }
                }
            }
            is Condition.Compare -> compareWithReason(condition, state)
            is Condition.InRange -> inRangeWithReason(condition, state)
        }
    }

    /**
     * Legacy death check: `stats["health"] <= 0`. If `health` is not
     * in [PlayerState.stats], the player is alive (a default `1` is
     * used for comparison so that "no health declared" means "not
     * dead" — the author is simply not using health in this adventure).
     *
     * **Status in Ф3.5.** This method is now used **only** as the
     * fallback inside [Engine.evaluateInvariants]: when
     * [com.myapp.adventure.engine.ir.Adventure.invariants] is empty,
     * `evaluateInvariants` calls `isDead(state)` and returns
     * [com.myapp.adventure.engine.ir.Outcome.Death] iff it returns
     * `true`. Once an adventure declares explicit invariants
     * (`invariants: [{"condition": "health <= 0", "outcome":
     * "death"}, ...]`), the fallback is not consulted — the author's
     * rules fully replace it.
     *
     * **Not part of [evaluate].** Death is not a [Condition] in the
     * generic sense; it is an outcome. Pre-Ф3.5 this was hardcoded;
     * Ф3.5 made it a fallback so that existing files (no `invariants`)
     * behave identically. **New code should call
     * [Engine.evaluateInvariants], not this method** — invariants are
     * the future-proof API.
     *
     * The method is kept as `public` (not deprecated) because:
     * - `Engine.evaluateInvariants` uses it as a fallback;
     * - removing it in Ф3.5 would break tests that pin the fallback
     *   behaviour ("no invariants declared → health <= 0 still kills"),
     *   which is an important contract for backward compatibility.
     */
    public fun isDead(state: PlayerState): Boolean {
        val health = state.stats["health"] ?: 1
        return health <= 0
    }

    // ---------- Private helpers ----------

    private fun compareWithReason(
        condition: Condition.Compare,
        state: PlayerState,
    ): EvaluationResult {
        val actual = state.stats[condition.varName] ?: 0
        return if (compare(actual, condition.op, condition.value)) {
            EvaluationResult.Success
        } else {
            EvaluationResult.Failure(
                Reason.CompareFailed(
                    varName = condition.varName,
                    op = condition.op,
                    actualValue = actual,
                    expectedValue = condition.value,
                )
            )
        }
    }

    private fun inRangeWithReason(
        condition: Condition.InRange,
        state: PlayerState,
    ): EvaluationResult {
        val actual = state.stats[condition.varName] ?: 0
        return if (actual in condition.min..condition.max) {
            EvaluationResult.Success
        } else {
            EvaluationResult.Failure(
                Reason.InRangeFailed(
                    varName = condition.varName,
                    actualValue = actual,
                    min = condition.min,
                    max = condition.max,
                )
            )
        }
    }

    private fun compare(actual: Int, op: CompareOp, expected: Int): Boolean {
        return when (op) {
            CompareOp.LT -> actual < expected
            CompareOp.LE -> actual <= expected
            CompareOp.GT -> actual > expected
            CompareOp.GE -> actual >= expected
            CompareOp.EQ -> actual == expected
            CompareOp.NE -> actual != expected
        }
    }
}