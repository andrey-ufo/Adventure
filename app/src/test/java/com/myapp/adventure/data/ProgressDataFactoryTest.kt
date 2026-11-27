package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.utils.ProgressMigration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ф5.2b/Ф5.9: explicit mapping of every persisted ProgressData field. */
class ProgressDataFactoryTest {

    @Test
    fun mapsEveryFieldWithoutDroppingAnything() {
        val base = ProgressSnapshot(
            currentChapterIndex = 1,
            currentSceneId = "base",
            flags = listOf("base_flag"),
            stats = mapOf("base" to 1),
            itemOrder = listOf("base_item"),
            appliedSceneEffects = setOf("base_scene"),
            remainingSteps = 11,
            isGateActive = true,
            warningsFingerprint = "base_fp",
            passedGates = listOf("base_gate"),
        )
        val previous = PlayerStateSnapshot(
            sceneId = "previous",
            chapterIndex = 1,
            flags = listOf("p"),
            stats = mapOf("p" to 2),
            itemOrder = listOf("p_item"),
            appliedSceneEffects = setOf("p_scene"),
        )
        val chapter = previous.copy(sceneId = "chapter")
        val input = ProgressSaveInput(
            adventureFileName = "file.json",
            baseSnapshot = base,
            operationLog = listOf(
                StateDelta.AddStat("gold", 2),
                StateDelta.SetFlag("sword"),
            ),
            currentChapterIndex = 3,
            currentSceneId = "current",
            flags = listOf("base_flag", "sword"),
            stats = mapOf("base" to 1, "gold" to 2),
            appliedSceneEffects = setOf("base_scene", "current"),
            itemOrder = listOf("base_item", "sword"),
            previousStepSnapshot = previous,
            chapterStartSnapshot = chapter,
            remainingSteps = 7,
            isGateActive = true,
            lastAbsoluteAtSave = 12345,
            warningsFingerprint = "warnings_fp",
            passedGates = listOf("base_gate", "gate_2"),
        )

        val progress = ProgressDataFactory.create(input)

        assertEquals(ProgressMigration.CURRENT_VERSION, progress.version)
        assertEquals("file.json", progress.adventureFileName)
        assertEquals(base, progress.baseSnapshot)
        assertEquals(input.operationLog.map { it.toDto() }, progress.delta)
        assertEquals(3, progress.currentChapterIndex)
        assertEquals("current", progress.currentSceneId)
        assertEquals(input.flags, progress.flags)
        assertEquals(input.stats, progress.stats)
        assertEquals(input.appliedSceneEffects, progress.appliedSceneEffects)
        assertEquals(input.itemOrder, progress.itemOrder)
        assertEquals(previous, progress.previousStepSnapshot)
        assertEquals(chapter, progress.chapterStartSnapshot)
        assertEquals(7, progress.remainingSteps)
        assertEquals(true, progress.isGateActive)
        assertEquals(12345, progress.lastAbsoluteAtSave)
        assertEquals("warnings_fp", progress.warningsFingerprint)
        assertEquals(listOf("base_gate", "gate_2"), progress.passedGates)
    }
}
