package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.utils.ProgressMigration

/**
 * Explicit input for [ProgressDataFactory]. Keeping this separate from
 * `ReaderUiState` makes the save mapping testable without Android types
 * and makes a missing field a compile-time error rather than a silent
 * default.
 */
internal data class ProgressSaveInput(
    val adventureFileName: String,
    val baseSnapshot: ProgressSnapshot,
    val operationLog: List<StateDelta>,
    val currentChapterIndex: Int,
    val currentSceneId: String,
    val flags: List<String>,
    val stats: Map<String, Int>,
    val appliedSceneEffects: Set<String>,
    val itemOrder: List<String>,
    val previousStepSnapshot: PlayerStateSnapshot?,
    val chapterStartSnapshot: PlayerStateSnapshot?,
    val remainingSteps: Int,
    val isGateActive: Boolean,
    val lastAbsoluteAtSave: Int,
    val warningsFingerprint: String,
    val passedGates: List<String>,
)

/** Ф5.2b/Ф5.9: one place that maps reader fields to the persisted shape. */
internal object ProgressDataFactory {

    fun create(input: ProgressSaveInput): ProgressData = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = input.adventureFileName,
        baseSnapshot = input.baseSnapshot,
        delta = input.operationLog.map { it.toDto() },
        currentChapterIndex = input.currentChapterIndex,
        currentSceneId = input.currentSceneId,
        flags = input.flags,
        stats = input.stats,
        appliedSceneEffects = input.appliedSceneEffects,
        itemOrder = input.itemOrder,
        previousStepSnapshot = input.previousStepSnapshot,
        chapterStartSnapshot = input.chapterStartSnapshot,
        remainingSteps = input.remainingSteps,
        isGateActive = input.isGateActive,
        lastAbsoluteAtSave = input.lastAbsoluteAtSave,
        warningsFingerprint = input.warningsFingerprint,
        passedGates = input.passedGates,
    )
}
