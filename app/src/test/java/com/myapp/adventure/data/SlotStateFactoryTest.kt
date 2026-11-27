package com.myapp.adventure.data

import com.myapp.adventure.utils.ProgressMigration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ф5.5/Ф5.9: manual-slot state semantics without Android/SAF. */
class SlotStateFactoryTest {

    @Test
    fun embedsFileNameAndResetsStepCatchUp() {
        val state = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = "old.json",
            currentSceneId = "s",
            lastAbsoluteAtSave = 12345,
        )

        val prepared = SlotStateFactory.prepare(state, "new.json")

        assertEquals("new.json", prepared.adventureFileName)
        assertEquals(-1, prepared.lastAbsoluteAtSave)
        assertEquals("s", prepared.currentSceneId)
    }

    @Test
    fun migratesLegacyStateBeforeSavingSlot() {
        val legacy = ProgressData(
            version = 0,
            adventureFileName = "old.json",
            currentSceneId = "s",
        )

        val prepared = SlotStateFactory.prepare(legacy, "old.json")

        assertEquals(ProgressMigration.CURRENT_VERSION, prepared.version)
        assertEquals(-1, prepared.lastAbsoluteAtSave)
    }
}
