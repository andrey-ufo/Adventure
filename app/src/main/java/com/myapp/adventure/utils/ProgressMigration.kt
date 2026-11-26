package com.myapp.adventure.utils

import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.ProgressSnapshot

/**
 * Ф5.3/Ф5.4 (S6): versioned progress migration.
 *
 * The only current on-disk shape is v0 (the legacy `ProgressData` without
 * a `version` field). v1 is the versioned envelope with the same fields;
 * Ф5.2b will add optional `baseSnapshot`/`delta` fields without a
 * breaking bump (old readers ignore unknown fields, new readers default
 * them to empty).
 */
class ProgressMigrationException(message: String) : Exception(message)

object ProgressMigration {

    const val CURRENT_VERSION: Int = 1

    /**
     * Migrates one entry to [CURRENT_VERSION].
     *
     * v0 → v1 is an identity migration of the current fields:
     * `lastAbsoluteAtSave`, gates, warnings fingerprint, passed gates,
     * snapshots and all player state are preserved. It must not reset
     * anything — a legacy player must continue exactly where they were.
     *
     * Unknown versions (negative, or newer than this app) throw an
     * explicit [ProgressMigrationException]; callers must show an error,
     * not silently start a new game or overwrite the file.
     */
    fun migrate(data: ProgressData): ProgressData {
        if (data.version < 0) {
            throw ProgressMigrationException(
                "Progress '${data.adventureFileName}' has invalid version ${data.version}",
            )
        }
        if (data.version > CURRENT_VERSION) {
            throw ProgressMigrationException(
                "Progress '${data.adventureFileName}' uses version ${data.version}, " +
                        "but this app supports up to $CURRENT_VERSION. " +
                        "Update the app or restore an older backup.",
            )
        }
        return normalize(data)
    }

    /**
     * Gson uses `Unsafe` for Kotlin data classes without a no-arg
     * constructor, so fields absent from an old JSON can be `null` at
     * runtime despite their non-null Kotlin types. Normalise every
     * collection/string before the app compares or copies the object.
     *
     * Public for Ф5.5: slot files embed the same [ProgressData] state and
     * need the exact same Gson null-safety before it reaches the reader.
     */
    fun normalize(data: ProgressData): ProgressData = ProgressData(
        version = CURRENT_VERSION,
        adventureFileName = (data.adventureFileName as String?) ?: "",
        baseSnapshot = data.baseSnapshot?.let(::normalizeProgressSnapshot),
        delta = data.delta,
        currentChapterIndex = data.currentChapterIndex,
        currentSceneId = (data.currentSceneId as String?) ?: "",
        flags = (data.flags as List<String>?) ?: emptyList(),
        stats = (data.stats as Map<String, Int>?) ?: emptyMap(),
        appliedSceneEffects = (data.appliedSceneEffects as Set<String>?) ?: emptySet(),
        itemOrder = (data.itemOrder as List<String>?) ?: emptyList(),
        previousStepSnapshot = data.previousStepSnapshot?.let(::normalizeSnapshot),
        chapterStartSnapshot = data.chapterStartSnapshot?.let(::normalizeSnapshot),
        remainingSteps = data.remainingSteps,
        isGateActive = data.isGateActive,
        lastAbsoluteAtSave = data.lastAbsoluteAtSave,
        warningsFingerprint = (data.warningsFingerprint as String?) ?: "",
        passedGates = (data.passedGates as List<String>?) ?: emptyList(),
    )

    private fun normalizeProgressSnapshot(data: ProgressSnapshot): ProgressSnapshot =
        ProgressSnapshot(
            currentChapterIndex = data.currentChapterIndex,
            currentSceneId = (data.currentSceneId as String?) ?: "",
            flags = (data.flags as List<String>?) ?: emptyList(),
            stats = (data.stats as Map<String, Int>?) ?: emptyMap(),
            itemOrder = (data.itemOrder as List<String>?) ?: emptyList(),
            appliedSceneEffects = (data.appliedSceneEffects as Set<String>?) ?: emptySet(),
            remainingSteps = data.remainingSteps,
            isGateActive = data.isGateActive,
            warningsFingerprint = (data.warningsFingerprint as String?) ?: "",
            passedGates = (data.passedGates as List<String>?) ?: emptyList(),
        )

    private fun normalizeSnapshot(data: PlayerStateSnapshot): PlayerStateSnapshot =
        PlayerStateSnapshot(
            sceneId = (data.sceneId as String?) ?: "",
            chapterIndex = data.chapterIndex,
            flags = (data.flags as List<String>?) ?: emptyList(),
            stats = (data.stats as Map<String, Int>?) ?: emptyMap(),
            itemOrder = (data.itemOrder as List<String>?) ?: emptyList(),
            appliedSceneEffects = (data.appliedSceneEffects as Set<String>?) ?: emptySet(),
        )
}
