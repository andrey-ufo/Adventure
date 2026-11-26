package com.myapp.adventure.utils

import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.ProgressSnapshot
import com.myapp.adventure.data.SaveSlotPaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ф5.6 (S2): autosave must never overwrite a manual slot.
 *
 * The test drives the same persistence calls `ReaderRoute` makes:
 * `ON_PAUSE` → `ProgressStore.save`, `ON_STOP` → `ProgressStore.flush`.
 * Both stores use one in-memory [SaveStorage], so the real path logic is
 * exercised without Android/SAF.
 */
class AutosaveSlotIsolationTest {

    private class InMemorySaveStorage : SaveStorage {
        val files = linkedMapOf<List<String>, String>()
        val writes = mutableListOf<List<String>>()

        override fun readDocument(path: List<String>): String? = files[path]

        override fun writeDocument(path: List<String>, content: String): Boolean {
            val stablePath = path.toList()
            writes += stablePath
            files[stablePath] = content
            return true
        }

        override fun listDocuments(directory: List<String>): List<String> =
            files.keys
                .filter { it.size == directory.size + 1 && it.dropLast(1) == directory }
                .map { it.last() }

        override fun deleteDocument(path: List<String>): Boolean =
            files.remove(path) != null
    }

    private val adventureFileName = "test_adventure.json"
    private val adventureKey = SaveSlotPaths.adventureKey(adventureFileName)

    private fun state(scene: String, health: Int): ProgressData = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = adventureFileName,
        baseSnapshot = ProgressSnapshot(
            currentSceneId = scene,
            stats = mapOf("health" to health),
        ),
        currentSceneId = scene,
        stats = mapOf("health" to health),
    )

    @Test
    fun onPauseAndOnStopAutosaveDoesNotTouchManualSlot() = runBlocking {
        val storage = InMemorySaveStorage()
        val slotStore = SlotStore(storage)
        val slot = slotStore.create(adventureKey, "Перед подземельем", state("before", 10))
        val slotPath = listOf(
            SaveSlotPaths.SAVES_DIRECTORY,
            adventureKey,
            SaveSlotPaths.slotFileName(slot.id),
        )
        val slotBefore = storage.readDocument(slotPath)
        assertNotNull(slotBefore)
        storage.writes.clear()

        // ReaderRoute ON_PAUSE → saveProgress, ON_STOP → flushProgress.
        val autosave = ProgressStore(storage)
        assertTrue(autosave.save(adventureFileName, state("later", 42)))
        autosave.flush()

        assertEquals(slotBefore, storage.readDocument(slotPath))
        assertEquals(listOf(listOf(ProgressStore.PROGRESS_FILE_NAME)), storage.writes)
        val progressJson = storage.readDocument(listOf(ProgressStore.PROGRESS_FILE_NAME))
        assertNotNull(progressJson)
        assertTrue(progressJson!!.contains("\"later\""))
    }

    @Test
    fun creatingManualSlotDoesNotWriteAutosave() {
        val storage = InMemorySaveStorage()

        SlotStore(storage).create(adventureKey, "safe point", state("s1", 5))

        assertNull(storage.readDocument(listOf(ProgressStore.PROGRESS_FILE_NAME)))
        assertTrue(
            storage.writes.all {
                it.firstOrNull() == SaveSlotPaths.SAVES_DIRECTORY
            },
        )
    }

    @Test
    fun autosaveKeepsAllManualSlotsAndTheirContent() = runBlocking {
        val storage = InMemorySaveStorage()
        val slotStore = SlotStore(storage)
        val first = slotStore.create(adventureKey, "first", state("s1", 5))
        val second = slotStore.create(adventureKey, "second", state("s2", 7))
        val firstPath = listOf(
            SaveSlotPaths.SAVES_DIRECTORY,
            adventureKey,
            SaveSlotPaths.slotFileName(first.id),
        )
        val secondPath = listOf(
            SaveSlotPaths.SAVES_DIRECTORY,
            adventureKey,
            SaveSlotPaths.slotFileName(second.id),
        )
        val firstBefore = storage.readDocument(firstPath)
        val secondBefore = storage.readDocument(secondPath)

        val autosave = ProgressStore(storage)
        assertTrue(autosave.save(adventureFileName, state("s3", 1)))
        autosave.flush()

        assertEquals(firstBefore, storage.readDocument(firstPath))
        assertEquals(secondBefore, storage.readDocument(secondPath))
        assertEquals(2, slotStore.list(adventureKey).size)
    }

    @Test
    fun slotAndAutosavePathsAreDisjoint() {
        val rootProgress = listOf(ProgressStore.PROGRESS_FILE_NAME)
        val slotDirectory = listOf(SaveSlotPaths.SAVES_DIRECTORY, adventureKey)
        val slotFile = slotDirectory + SaveSlotPaths.slotFileName("any-id")

        assertTrue(rootProgress.first() != slotDirectory.first())
        assertTrue(slotFile.take(2) == slotDirectory)
        assertTrue(slotFile.size > slotDirectory.size)
    }
}
