package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta

/** Base + operation log pair persisted in `ProgressData`. */
internal data class SaveOperationLog(
    val base: ProgressSnapshot,
    val deltas: List<StateDelta>,
)

/**
 * Ф5.8: keeps the persisted operation log bounded.
 *
 * The log is an exact replay from [base], but appending forever would make
 * every step copy an ever-growing list and every save grow without bound.
 * Once more than [maxDeltas] operations are pending, the caller provides a
 * snapshot of the **current** state and the log restarts from zero:
 *
 * ```
 * base' = currentSnapshot; deltas' = []
 * ```
 *
 * The result is still exact (current state is the new base); only the
 * intermediate history inside this save representation is dropped. Undo
 * is unaffected: it uses `previousStepSnapshot`/`chapterStartSnapshot`,
 * not this log.
 *
 * Bounded work per step: at most [maxDeltas] deltas are copied.
 * Bounded saved state: at most [maxDeltas] deltas + one snapshot.
 */
internal class OperationLogBuffer(
    private val maxDeltas: Int = MAX_DELTAS,
) {

    init {
        require(maxDeltas > 0) { "maxDeltas must be positive" }
    }

    /**
     * Appends [newDeltas]. Returns a compacted pair when the log would
     * exceed [maxDeltas]; [compactedBase] is only invoked then.
     */
    fun append(
        base: ProgressSnapshot,
        current: List<StateDelta>,
        newDeltas: List<StateDelta>,
        compactedBase: () -> ProgressSnapshot,
    ): SaveOperationLog {
        if (newDeltas.isEmpty()) return SaveOperationLog(base, current)

        val combined = ArrayList<StateDelta>(current.size + newDeltas.size)
        combined.addAll(current)
        combined.addAll(newDeltas)
        return if (combined.size <= maxDeltas) {
            SaveOperationLog(base, combined)
        } else {
            SaveOperationLog(compactedBase(), emptyList())
        }
    }

    /**
     * Used when loading a save written by an older/other run: an oversized
     * log is folded into the current snapshot once instead of being kept
     * in memory for the rest of the session.
     */
    fun compactIfNeeded(
        base: ProgressSnapshot,
        deltas: List<StateDelta>,
        compactedBase: () -> ProgressSnapshot,
    ): SaveOperationLog =
        if (deltas.size <= maxDeltas) {
            SaveOperationLog(base, deltas)
        } else {
            SaveOperationLog(compactedBase(), emptyList())
        }

    companion object {
        const val MAX_DELTAS: Int = 32
    }
}
