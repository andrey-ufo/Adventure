package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.utils.ProgressMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Ф5.2b/Ф5.9: pure base+delta replay and legacy/unknown fallbacks. */
class SavedProgressApplierTest {

    private val base = ProgressSnapshot(
        flags = listOf("started"),
        stats = mapOf("health" to 10, "gold" to 3),
    )

    private fun saved(
        baseSnapshot: ProgressSnapshot? = base,
        delta: List<StateDeltaDto>? = listOf(
            StateDelta.AddStat("health", 5).toDto(),
            StateDelta.SetFlag("sword").toDto(),
        ),
    ) = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = "a.json",
        baseSnapshot = baseSnapshot,
        delta = delta,
        flags = listOf("legacy"),
        stats = mapOf("health" to 1),
    )

    @Test
    fun replaysBaseAndDelta() {
        val replayed = SavedProgressApplier.replay(saved())

        assertNotNull(replayed)
        assertEquals(15, replayed!!.state.stats["health"])
        assertEquals(3, replayed.state.stats["gold"])
        assertEquals(setOf("started", "sword"), replayed.state.flags)
        assertEquals(2, replayed.deltas.size)
    }

    @Test
    fun emptyDeltaStillReplaysBase() {
        val replayed = SavedProgressApplier.replay(saved(delta = emptyList()))

        assertNotNull(replayed)
        assertEquals(10, replayed!!.state.stats["health"])
        assertEquals(setOf("started"), replayed.state.flags)
        assertEquals(emptyList<StateDelta>(), replayed.deltas)
    }

    @Test
    fun missingBaseFallsBackToLegacy() {
        assertNull(SavedProgressApplier.replay(saved(baseSnapshot = null)))
    }

    @Test
    fun missingDeltaFallsBackToLegacy() {
        assertNull(SavedProgressApplier.replay(saved(delta = null)))
    }

    @Test
    fun unknownDeltaVariantFallsBackInsteadOfSkipping() {
        assertNull(
            SavedProgressApplier.replay(
                saved(
                    delta = listOf(
                        StateDelta.SetFlag("ok").toDto(),
                        StateDeltaDto("future_operation", name = "x"),
                    ),
                ),
            ),
        )
    }
}
