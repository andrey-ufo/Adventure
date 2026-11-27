package com.myapp.adventure.utils

import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlotException
import com.myapp.adventure.data.SaveSlotPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ф5.5/Ф5.9: slot storage policies independent of SAF/UI. */
class SlotStoreTest {

    private val key = SaveSlotPaths.adventureKey("adventure.json")

    private fun state(scene: String = "s1") = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = "adventure.json",
        currentSceneId = scene,
    )

    @Test
    fun createLoadDeleteRoundTrip() {
        val storage = InMemorySaveStorage()
        val store = SlotStore(storage)

        val created = store.create(key, "checkpoint", state(), now = 100)
        val loaded = store.load(key, created.id)

        assertNotNull(loaded)
        assertEquals(created, loaded)
        assertTrue(store.delete(key, created.id))
        assertNull(store.load(key, created.id))
        assertTrue(store.list(key).isEmpty())
    }

    @Test
    fun missingSlotLoadAndDeleteAreSafe() {
        val store = SlotStore(InMemorySaveStorage())

        assertNull(store.load(key, "missing"))
        assertFalse(store.delete(key, "missing"))
    }

    @Test
    fun capAllowsTwentyAndRejectsTwentyFirst() {
        val storage = InMemorySaveStorage()
        val store = SlotStore(storage)

        repeat(SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE) { i ->
            store.create(key, "slot-$i", state(), now = 1_000L + i)
        }

        val error = assertThrows(SaveSlotException::class.java) {
            store.create(key, "overflow", state(), now = 2_000)
        }
        assertTrue(error.message.orEmpty().contains("лимит"))
        assertEquals(SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE, store.list(key).size)
    }

    @Test
    fun listIsNewestFirstAndIgnoresOtherFiles() {
        val storage = InMemorySaveStorage()
        val store = SlotStore(storage)
        store.create(key, "middle", state(), now = 200)
        store.create(key, "newest", state(), now = 300)
        store.create(key, "oldest", state(), now = 100)
        storage.writeDocument(
            listOf(SaveSlotPaths.SAVES_DIRECTORY, key, "notes.txt"),
            "not a slot",
        )

        val names = store.list(key).map { it.name }

        assertEquals(listOf("newest", "middle", "oldest"), names)
    }

    @Test
    fun corruptSlotIsExplicitErrorWithFileName() {
        val storage = InMemorySaveStorage()
        val store = SlotStore(storage)
        val fileName = SaveSlotPaths.slotFileName("bad")
        storage.writeDocument(
            listOf(SaveSlotPaths.SAVES_DIRECTORY, key, fileName),
            "{ broken json",
        )

        val error = assertThrows(SaveSlotException::class.java) {
            store.list(key)
        }

        assertTrue(error.message.orEmpty().contains(fileName))
    }

    @Test
    fun blankNameFallsBackToDefault() {
        val slot = SlotStore(InMemorySaveStorage()).create(key, "   ", state())

        assertEquals("Сохранение", slot.name)
    }

    @Test
    fun writeFailureDoesNotCreateSlot() {
        val storage = InMemorySaveStorage()
        storage.failWrites = true
        val store = SlotStore(storage)

        assertThrows(SaveSlotException::class.java) {
            store.create(key, "checkpoint", state())
        }
        assertTrue(store.list(key).isEmpty())
    }
}
