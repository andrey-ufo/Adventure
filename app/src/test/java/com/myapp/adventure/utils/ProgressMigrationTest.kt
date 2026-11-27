package com.myapp.adventure.utils

import com.google.gson.Gson
import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.ProgressSnapshot
import com.myapp.adventure.data.toDomainOrNull
import com.myapp.adventure.data.toDto
import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.engine.applyDeltas
import com.myapp.adventure.engine.ir.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Ф5.3/Ф5.4 (S6): versioning and legacy v0 → v1 migration. */
class ProgressMigrationTest {

    @Test
    fun legacyV0PreservesEverything() {
        val previous = PlayerStateSnapshot(
            sceneId = "previous",
            chapterIndex = 0,
            flags = listOf("a"),
            stats = mapOf("health" to 10),
        )
        val chapter = previous.copy(sceneId = "chapter")
        val legacy = ProgressData(
            version = 0,
            adventureFileName = "my_adventure.json",
            currentChapterIndex = 1,
            currentSceneId = "start",
            flags = listOf("started"),
            stats = mapOf("health" to 50, "potion" to 2),
            appliedSceneEffects = setOf("start"),
            itemOrder = listOf("potion"),
            previousStepSnapshot = previous,
            chapterStartSnapshot = chapter,
            remainingSteps = 3,
            isGateActive = true,
            lastAbsoluteAtSave = 12345,
            warningsFingerprint = "fingerprint",
            passedGates = listOf("chapter_1"),
        )

        val migrated = ProgressMigration.migrate(legacy)

        assertEquals(ProgressMigration.CURRENT_VERSION, migrated.version)
        assertEquals(legacy.copy(version = ProgressMigration.CURRENT_VERSION), migrated)
    }

    @Test
    fun currentVersionIsIdentity() {
        val current = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = "a.json",
            currentSceneId = "s",
        )
        assertEquals(current, ProgressMigration.migrate(current))
    }

    @Test
    fun unknownFutureVersionThrows() {
        val future = ProgressData(
            version = ProgressMigration.CURRENT_VERSION + 1,
            adventureFileName = "future.json",
        )
        assertThrows(ProgressMigrationException::class.java) {
            ProgressMigration.migrate(future)
        }
    }

    @Test
    fun baseSnapshotAndDeltaRoundTripThroughGson() {
        val base = ProgressSnapshot(
            currentChapterIndex = 0,
            currentSceneId = "start",
            flags = listOf("started"),
            stats = mapOf("health" to 10),
            itemOrder = emptyList(),
        )
        val progress = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = "a.json",
            baseSnapshot = base,
            delta = listOf(
                StateDelta.AddStat("health", 5).toDto(),
                StateDelta.SetFlag("sword").toDto(),
            ),
            currentSceneId = "start",
        )

        val back = Gson().fromJson(Gson().toJson(progress), ProgressData::class.java)
        assertEquals(base, back.baseSnapshot)

        val deltas = back.delta.orEmpty().mapNotNull { it.toDomainOrNull() }
        val state = applyDeltas(
            PlayerState(flags = base.flags.toSet(), stats = base.stats),
            deltas,
        )
        assertEquals(15, state.stats["health"])
        assertEquals(setOf("started", "sword"), state.flags)
    }

    @Test
    fun gsonNullsInNestedSnapshotsAndDeltaAreNormalized() {
        val json = """
            {
              "version": 1,
              "adventureFileName": "a.json",
              "currentSceneId": "s",
              "flags": null,
              "stats": null,
              "appliedSceneEffects": null,
              "itemOrder": null,
              "passedGates": null,
              "baseSnapshot": {
                "currentSceneId": "base",
                "flags": null,
                "stats": null,
                "itemOrder": null,
                "appliedSceneEffects": null,
                "passedGates": null
              },
              "previousStepSnapshot": {
                "sceneId": "prev",
                "flags": null,
                "stats": null,
                "itemOrder": null,
                "appliedSceneEffects": null
              },
              "delta": [null, {"type": "set_flag", "name": "x"}]
            }
        """.trimIndent()

        val parsed = Gson().fromJson(json, ProgressData::class.java)
        val migrated = ProgressMigration.migrate(parsed)

        assertEquals(emptyList<String>(), migrated.flags)
        assertEquals(emptyMap<String, Int>(), migrated.stats)
        assertEquals(emptySet<String>(), migrated.appliedSceneEffects)
        assertEquals(emptyList<String>(), migrated.passedGates)
        assertEquals(1, migrated.delta?.size)
        assertEquals(StateDelta.SetFlag("x"), migrated.delta?.single()?.toDomainOrNull())

        val base = migrated.baseSnapshot
        assertEquals("base", base?.currentSceneId)
        assertEquals(emptyList<String>(), base?.flags)
        assertEquals(emptyMap<String, Int>(), base?.stats)
        assertEquals(emptySet<String>(), base?.appliedSceneEffects)
        assertEquals(emptyList<String>(), base?.passedGates)

        val previous = migrated.previousStepSnapshot
        assertEquals("prev", previous?.sceneId)
        assertEquals(emptyList<String>(), previous?.flags)
        assertEquals(emptyMap<String, Int>(), previous?.stats)
    }

    @Test
    fun missingVersionInLegacyJsonReadsAsZero() {
        val json = """
            {"adventureFileName":"legacy.json","currentSceneId":"s",
             "remainingSteps":4,"isGateActive":true,"lastAbsoluteAtSave":99}
        """.trimIndent()
        val parsed = Gson().fromJson(json, ProgressData::class.java)

        assertEquals(0, parsed.version)
        val migrated = ProgressMigration.migrate(parsed)
        assertEquals(1, migrated.version)
        assertEquals(4, migrated.remainingSteps)
        assertEquals(true, migrated.isGateActive)
        assertEquals(99, migrated.lastAbsoluteAtSave)
    }
}
