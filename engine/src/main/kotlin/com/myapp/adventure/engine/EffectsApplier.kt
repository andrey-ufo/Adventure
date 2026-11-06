package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState

/**
 * Applies [Effect]s to a [PlayerState], returning a **new** state.
 *
 * **One source of truth.** Every effect application goes through [apply]:
 * `Engine.step` (transition), the reader UI (`:app`, preview — Ф9.3),
 * the debug panel (`:app`, "apply this effect by hand"), the analyzer
 * (`:app`, "what does the state look like after this branch"). As with
 * [Evaluator], a divergent second copy of this logic is exactly the N2
 * bug — prevented by funneling everything through one object.
 *
 * **Order.** Effects are applied **in list order** (as the parser produced
 * them: `set_flags`, `remove_flags`, `modify_stats`). Reordering effects
 * changes the result: `remove X` before `set X` leaves `X` set; `set X`
 * before `remove X` leaves `X` unset. This matches the current reader.
 *
 * **Clamp policy (temporary, until Ф2.8).** Every [Effect.ModifyStat]
 * clamps its result to `>= 0`. Once `Adventure.variables: Map<String,
 * VarDecl>` exists (Ф2.8), the clamp will depend on the variable's
 * declared type (`HEALTH`, `ITEM` → `>= 0`; `Integer`, `IntRange` →
 * per-type bounds; `Bool` → not applicable). Until then, "clamp all to
 * `>= 0`" is the safest choice:
 * - The reader never shows "-5 health" — user sees "❤️ 0" instead.
 * - An author who wants "stamina goes negative" is expressing an
 *   intention that will not be honoured in this release; if it matters,
 *   the analyzer (Ф2.2) will not flag it yet, but the clamp policy will
 *   be tightened in Ф2.8.
 *
 * **Not in scope here.** Setting a stat to an **absolute** value
 * (`SetStat`) — not needed in IR v2 and not requested yet. Effects are
 * additive (`ModifyStat(delta)`) or flag toggles.
 */
public object EffectsApplier {

    /**
     * Returns a new [PlayerState] with all [effects] applied in order.
     *
     * `state` itself is not mutated; the returned value is a fresh
     * instance. Callers that want to chain effects (`state = apply(state,
     * choice.effects); state = apply(state, scene.effects)`) can do so
     * without worrying about aliasing.
     */
    public fun apply(state: PlayerState, effects: List<Effect>): PlayerState {
        if (effects.isEmpty()) return state
        val flags = state.flags.toMutableSet()
        val stats = state.stats.toMutableMap()
        for (effect in effects) {
            when (effect) {
                is Effect.SetFlag -> flags.add(effect.name)
                is Effect.RemoveFlag -> flags.remove(effect.name)
                is Effect.ModifyStat -> {
                    val old = stats[effect.name] ?: 0
                    stats[effect.name] = (old + effect.delta).coerceAtLeast(0)
                }
            }
        }
        return PlayerState(flags = flags, stats = stats)
    }
}