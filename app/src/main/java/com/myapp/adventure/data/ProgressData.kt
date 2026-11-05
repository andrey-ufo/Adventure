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
    val lastAbsoluteAtSave: Int = -1
)