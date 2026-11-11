package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A step gate on a [Scene]: the player must walk at least [requiredSteps]
 * real-world steps after entering this scene before any exit is allowed
 * (picking a choice, or auto-advancing to the next scene/chapter).
 *
 * **Where gates live.** A gate belongs to the **scene it gates the exit
 * of**, not to the chapter it opens. A chapter is pure narrative
 * grouping; a gate is a mechanical requirement on a single scene. A gate
 * may sit anywhere in a chapter, several gates may sit in one chapter,
 * and a chapter may have none.
 *
 * **Two modes** ([repeatable]):
 *
 * - **`repeatable = false` (default).** "Once walked, always open **in
 *   this playthrough**". The gate blocks the first time the player
 *   enters the scene; after [requiredSteps] are accumulated, the gate is
 *   marked as passed for this playthrough and **does not re-engage** if
 *   the player returns to this scene. Resetting the adventure
 *   (`restart`) clears the pass. Use this for the common case: "walk
 *   N steps to unlock the next chapter" — once unlocked, the player
 *   never re-walks.
 * - **`repeatable = true`.** Per-visit. Each time the player enters
 *   this scene (including by picking a choice that self-loops back to
 *   it), they must walk [requiredSteps] again. Use this for the
 *   "travelled a path" case: the player physically walks from location
 *   A to location B; returning to A and travelling to B again requires
 *   the same walk. Also useful for time-gated scenes: "you must wait
 *   N steps before you can pick anything here" — every visit.
 *
 * The author picks the mode in the editor. Default is `false` because
 * the "re-walked" surprise is worse than "unlocked but stays unlocked":
 * a player who unlocked a chapter once does not expect to unlock it
 * again by accident.
 *
 * **In JSON (adventure file format)** the gate is a flat pair:
 * `"gate": 4000` (required steps) and, optionally,
 * `"gate_repeatable": true`. `gate_repeatable` defaults to `false`, so
 * simple gates stay one-key.
 *
 * **In the IR** `Gate` is a data class with two fields
 * ([requiredSteps] and [repeatable]). The reason it is not a
 * `value class` anymore: `value class` requires exactly one field, and
 * we need two. IR serialization therefore wraps the gate as
 * `{"required_steps": N, "repeatable": bool}`. This is **IR
 * serialization**, not adventure file format — the two are different
 * by design (`golden_ir.json` is IR, not adventure).
 *
 * **Positive by construction.** `requiredSteps <= 0` is meaningless
 * ("walk at least 0 steps" = "no gate"). The parser (`JsonToIr`)
 * rejects `gate <= 0` as a structural error; `Gate(0)` should never be
 * constructed. "No gate" is `Scene.gate == null`.
 *
 * **Not checked by `Engine.step`.** Step counting is a reader concern
 * (a foreground service in `:app`). The engine does not know how many
 * steps the player has walked, nor whether a gate was already passed;
 * it only knows the mechanical shape of the gate. The reader checks the
 * gate, tracks which gates have been passed (per playthrough for
 * `repeatable = false`, per-visit for `repeatable = true`), and only
 * calls `Engine.step` when the current gate is open. This keeps the
 * engine free of wall-clock and sensor dependencies.
 *
 * @property requiredSteps number of steps to accumulate before the
 *   scene becomes exitable. Must be strictly positive.
 * @property repeatable whether the gate re-engages on every visit
 *   (`true`) or once passed stays passed for this playthrough
 *   (`false`, default).
 */
@Serializable
public data class Gate(
    @SerialName("required_steps")
    public val requiredSteps: Int,
    public val repeatable: Boolean = false,
)