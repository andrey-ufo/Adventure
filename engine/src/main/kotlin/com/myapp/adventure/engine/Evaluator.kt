package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.PlayerState

/**
 * Single evaluator of [Condition]s against a [PlayerState].
 *
 * **One source of truth.** Every check of a condition in the whole project
 * goes through [evaluate]: `Engine.step` (is the choice available?),
 * the reader UI (`:app`, show disabled with reason — N4), the editor
 * analyzer (`:app`, Ф1.6 / Ф9.2), the debug panel (`:app`). If two
 * callers interpreted a condition differently, the engine could allow a
 * choice the UI showed as disabled, or vice versa. Preventing that is
 * exactly what N2 (`Ф3`) exists for; this object is the starting point.
 *
 * **Semantics** (as of IR v2):
 * - [Condition.Has] — true if `name` is in [PlayerState.flags]. **Not**
 *   `stats` — `has_sword` is the syntactic form for boolean flags. Type
 *   checker in Ф2.2 rejects `has_sword` on non-boolean declarations; until
 *   then, a `Has` on a name that lives in `stats` simply returns `false`.
 * - [Condition.Not] — logical negation.
 * - [Condition.And], [Condition.Or] — short-circuit (`&&`, `||`).
 * - [Condition.Compare] — `stats[varName] ?: 0` compared to `value`
 *   by `op` (`lt`, `le`, `gt`, `ge`, `eq`, `ne`).
 * - [Condition.InRange] — `stats[varName] ?: 0` in `min..max` (inclusive).
 *
 * **Pure.** No I/O, no state mutation, no exceptions on user input:
 * unknown variables default to `0` (`Compare`/`InRange`) or `false`
 * (`Has`). The type checker (Ф2.2) is what reports "unknown variable"
 * to the author; the evaluator is not the place for that.
 *
 * **Deferred to Ф3.** `Reason?` for rejection, global invariants
 * (`health <= 0 => death` as first-class IR), `evaluate` returning a
 * structured cause. Today the method returns `Boolean`; callers that
 * need a reason construct it themselves from the condition they passed in.
 */
public object Evaluator {

    /**
     * Evaluates [condition] against [state].
     *
     * Returns `true` iff the condition holds. Unknown variable names
     * resolve to their zero-value ([PlayerState.stats] default `0`,
     * [PlayerState.flags] default `false`).
     */
    public fun evaluate(condition: Condition, state: PlayerState): Boolean {
        return when (condition) {
            is Condition.Has -> condition.name in state.flags
            is Condition.Not -> !evaluate(condition.inner, state)
            is Condition.And ->
                evaluate(condition.left, state) && evaluate(condition.right, state)
            is Condition.Or ->
                evaluate(condition.left, state) || evaluate(condition.right, state)
            is Condition.Compare -> compare(
                actual = state.stats[condition.varName] ?: 0,
                op = condition.op,
                expected = condition.value,
            )
            is Condition.InRange -> {
                val actual = state.stats[condition.varName] ?: 0
                actual in condition.min..condition.max
            }
        }
    }

    /**
     * Player death check.
     *
     * Currently "dead" is defined as `stats["health"] <= 0`. If `health`
     * is not in [PlayerState.stats], the player is alive (a default `1`
     * is used for comparison so that "no health declared" means "not
     * dead" — the author is simply not using health in this adventure).
     *
     * **Not part of [evaluate].** Death is not a [Condition] in IR v2;
     * it is a runtime outcome. Ф3.5 will promote invariants (including
     * `health <= 0 => death`) to first-class IR and this method will
     * become one of the invariant checks. For now, a single hardcoded
     * rule.
     */
    public fun isDead(state: PlayerState): Boolean {
        val health = state.stats["health"] ?: 1
        return health <= 0
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