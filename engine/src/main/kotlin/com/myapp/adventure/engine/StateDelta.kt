package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ф5.2 (S5): one **player-state operation** in the save operation log.
 *
 * This is deliberately narrower than a full progress log:
 * - it changes flags/stats only;
 * - cursor/flow fields (current scene, gate, applied scene effects, item
 *   order, warnings, passed gates) live in the save snapshot/cursor, not
 *   in `StateDelta`. A separate cursor/step log can be layered on for
 *   S3/undo without changing this model.
 *
 * `Add`/`Sub` are operations, not resulting diffs: replaying the log once
 * in order is exact; replaying it twice is not intended. `Set*` and flag
 * deltas are idempotent, `Add`/`Sub` are deterministic one-shot ops.
 *
 * Numeric results are clamped at `0` on apply, matching
 * [EffectsApplier] (`health` can never become negative).
 */
@Serializable
public sealed class StateDelta {

    @Serializable
    @SerialName("set_flag")
    public data class SetFlag(public val name: String) : StateDelta()

    @Serializable
    @SerialName("remove_flag")
    public data class RemoveFlag(public val name: String) : StateDelta()

    @Serializable
    @SerialName("set_stat")
    public data class SetStat(
        public val name: String,
        public val value: Int,
    ) : StateDelta()

    /** Adds a signed delta; negative values subtract after the clamp. */
    @Serializable
    @SerialName("add_stat")
    public data class AddStat(
        public val name: String,
        public val delta: Int,
    ) : StateDelta()

    /** Subtracts a positive delta (or a signed one, as a convenience). */
    @Serializable
    @SerialName("sub_stat")
    public data class SubStat(
        public val name: String,
        public val delta: Int,
    ) : StateDelta()
}

/**
 * Applies one [StateDelta] to [state]. Pure; never mutates the input.
 *
 * This is the only place that knows the clamping rule, so the operation
 * log and the live `EffectsApplier` cannot drift.
 */
public fun applyDelta(state: PlayerState, delta: StateDelta): PlayerState =
    when (delta) {
        is StateDelta.SetFlag -> state.copy(
            flags = state.flags + delta.name,
        )
        is StateDelta.RemoveFlag -> state.copy(
            flags = state.flags - delta.name,
        )
        is StateDelta.SetStat -> state.copy(
            stats = state.stats + (delta.name to delta.value.coerceAtLeast(0)),
        )
        is StateDelta.AddStat -> state.copy(
            stats = state.stats + (
                    delta.name to
                            ((state.stats[delta.name] ?: 0) + delta.delta)
                                .coerceAtLeast(0)
                    ),
        )
        is StateDelta.SubStat -> state.copy(
            stats = state.stats + (
                    delta.name to
                            ((state.stats[delta.name] ?: 0) - delta.delta)
                                .coerceAtLeast(0)
                    ),
        )
    }

/** `applyAll(base, deltas)` — a pure fold over the operation log. */
public fun applyDeltas(
    base: PlayerState,
    deltas: List<StateDelta>,
): PlayerState = deltas.fold(base, ::applyDelta)

/**
 * Maps the IR effect list to the operation log. `ModifyStat` becomes
 * `AddStat` or `SubStat`; `Set` becomes `SetStat`; flags map directly.
 */
public fun effectsToDeltas(effects: List<Effect>): List<StateDelta> =
    ArrayList<StateDelta>(effects.size).also { out ->
        appendEffectsToDeltas(effects, out)
    }

/**
 * Append form of [effectsToDeltas], used by `Engine.step` so the hot path
 * can write directly into its result list instead of allocating a second
 * copy (`map(...).toMutableList()`).
 */
public fun appendEffectsToDeltas(
    effects: List<Effect>,
    out: MutableList<StateDelta>,
) {
    for (effect in effects) {
        out += when (effect) {
            is Effect.SetFlag -> StateDelta.SetFlag(effect.name)
            is Effect.RemoveFlag -> StateDelta.RemoveFlag(effect.name)
            is Effect.Set -> StateDelta.SetStat(effect.name, effect.value)
            is Effect.ModifyStat -> if (effect.delta >= 0) {
                StateDelta.AddStat(effect.name, effect.delta)
            } else {
                StateDelta.SubStat(effect.name, -effect.delta)
            }
        }
    }
}
