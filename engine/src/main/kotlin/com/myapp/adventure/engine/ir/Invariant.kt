package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable

/**
 * A global rule that fires whenever its [condition] holds, regardless
 * of which scene the player is in.
 *
 * **Semantics** (Ф3.5, N2):
 *
 * > `Invariant(condition, outcome)` — evaluated **after every step**
 * > (choice pick + scene entry). If `condition` holds, `outcome` is
 * > applied immediately.
 *
 * The canonical example is `Invariant(Compare("health", LE, 0),
 * Outcome.Death)`: whenever the player's health is zero or below,
 * they die — no matter which scene's `modify_stats` pushed it there.
 *
 * **Where the invariants are checked.** `Engine.evaluateInvariants`
 * (Ф3.5.2), called by `Engine.step` after applying choice effects and
 * by the reader after applying scene effects. Whichever path first
 * brings the player into a violated state, the outcome fires there.
 *
 * **Fallback when `Adventure.invariants` is empty.** To preserve
 * behaviour for pre-Ф3.5 files (which have no `invariants` field),
 * `Engine.evaluateInvariants` falls back to the hardcoded
 * `health <= 0 => Death` rule (see `Evaluator.isDead`). A file with a
 * non-empty `invariants` list **replaces** the fallback entirely — if
 * the author wants health-death plus another rule, they declare both.
 *
 * **Why per-invariant outcomes, not a single `Outcome` for the
 * whole list.** The list is heterogeneous: `Invariant(health <= 0,
 * Death)` and (in a future phase) `Invariant(roll(1d6) == 1,
 * Custom("teleport"))` coexist. The `Outcome` belongs to the rule
 * that fires.
 *
 * **Determinism.** `Engine.evaluateInvariants` returns the outcome of
 * the **first** matching invariant, in list order. An author who
 * declares `[A, B]` where both hold at the same time sees `A`'s
 * outcome — the "specific rules first, generic rules last" pattern is
 * the author's responsibility (same as in `Guard`-style systems).
 *
 * **Serialized** as `{"condition": <Condition AST>, "outcome": <Outcome>}`.
 * In the adventure file format (`JsonToIr` reads / `:app` writes) the
 * condition is a plain string and the outcome is a flat string — see
 * `InvariantDto`.
 *
 * @property condition evaluated after every step. When it holds,
 *   [outcome] fires.
 * @property outcome what fires. Today the only variant is
 *   [Outcome.Death].
 */
@Serializable
public data class Invariant(
    public val condition: Condition,
    public val outcome: Outcome,
)