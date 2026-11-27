package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.engine.applyDeltas
import com.myapp.adventure.engine.ir.PlayerState

internal data class ReplayedProgress(
    val state: PlayerState,
    val deltas: List<StateDelta>,
)

/**
 * Ф5.2b/Ф5.9: pure replay of `baseSnapshot + delta`.
 *
 * Returns `null` when the save has no base or contains an unknown delta
 * variant (future schema), so the caller can fall back to the legacy
 * current-state fields instead of crashing or silently skipping an
 * operation.
 */
internal object SavedProgressApplier {

    fun replay(saved: ProgressData): ReplayedProgress? {
        val base = saved.baseSnapshot ?: return null
        val dtos = saved.delta ?: return null
        val deltas = dtos.mapNotNull { it.toDomainOrNull() }
        if (deltas.size != dtos.size) return null

        val state = applyDeltas(
            PlayerState(flags = base.flags.toSet(), stats = base.stats),
            deltas,
        )
        return ReplayedProgress(state, deltas)
    }
}
