package com.myapp.adventure.data

data class ProgressData(
    val adventureFileName: String,
    val currentChapterIndex: Int = 0,
    val currentSceneId: String = "",
    val flags: List<String> = emptyList(),
    val stats: Map<String, Int> = emptyMap(),
    val appliedSceneEffects: Set<String> = emptySet(),
    val itemOrder: List<String> = emptyList(),
    val previousStepSnapshot: PlayerStateSnapshot? = null,
    val chapterStartSnapshot: PlayerStateSnapshot? = null,

    // ----- Steps gate (delta-based, survives reboot) -----

    /** Steps remaining until the next chapter opens. */
    val remainingSteps: Int = 0,

    /** Whether a steps gate is currently active. */
    val isGateActive: Boolean = false,

    /**
     * Raw TYPE_STEP_COUNTER reading at the moment this ProgressData was saved.
     * On load, the delta (currentAbsolute - lastAbsoluteAtSave) is subtracted from
     * [remainingSteps] to catch up with steps taken while the app was closed.
     * -1 means "unknown" (sensor has not produced any event yet).
     */
    val lastAbsoluteAtSave: Int = -1,

    /**
     * Fingerprint of the player-visible analyzer warnings at the moment
     * the player acknowledged them ("Продолжить игру" in the warning
     * dialog). Empty string = no acknowledgment yet, or the file has
     * changed since the last acknowledgment.
     *
     * **Why a fingerprint, not a Boolean.** If the author fixes one
     * warning and introduces another, a Boolean would stay `true` and
     * the player would never see the new warning. The fingerprint
     * changes whenever the set of warnings changes (a warning removed
     * or added, or a message changed), so the dialog re-appears — which
     * is correct: the player has not acknowledged *this* set yet.
     *
     * The fingerprint format is opaque; only the reader computes and
     * compares it. Old `progress.json` files without this field default
     * to `""`, which means "show the warning dialog on next open" —
     * the safe default.
     */
    val warningsFingerprint: String = "",

    /**
     * Scene ids whose **non-repeatable** gates (`gate_repeatable =
     * false`, the default) have been passed in this playthrough.
     *
     * Persisting this is essential: without it, a player who passed a
     * one-shot gate, closed the app, and reopened it would have to walk
     * the same steps again — the entire chapter-unlocking mechanic
     * would be broken across sessions. `repeatable = true` gates are
     * never listed here: they re-engage on every visit, so "passed" is
     * not a persistent state for them.
     *
     * Cleared by "restart adventure" (a new playthrough starts from
     * scratch). Old `progress.json` files without this field default to
     * `emptyList()`, which means "no gates passed" — the safe default.
     *
     * **In-progress gate state** (a gate the player is currently
     * walking through) is stored in [isGateActive] + [remainingSteps],
     * which already exist. This field is only about *completed*
     * non-repeatable gates.
     */
    val passedGates: List<String> = emptyList()
)