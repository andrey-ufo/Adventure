package com.myapp.adventure.utils

import com.google.gson.Gson
import com.myapp.adventure.data.ProgressData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ф5.x: autosave store policy (cache, migration, errors, write/flush). */
class ProgressStoreTest {

    private val progressPath = listOf(ProgressStore.PROGRESS_FILE_NAME)

    private fun progress(
        fileName: String = "a.json",
        scene: String = "start",
        version: Int = ProgressMigration.CURRENT_VERSION,
    ) = ProgressData(
        version = version,
        adventureFileName = fileName,
        currentSceneId = scene,
        flags = listOf("started"),
        stats = mapOf("health" to 10),
    )

    private fun writeMap(storage: InMemorySaveStorage, map: Map<String, ProgressData>) {
        storage.writeDocument(progressPath, Gson().toJson(map))
        storage.writes.clear()
        storage.reads.clear()
    }

    @Test
    fun corruptRootJsonIsExplicitErrorAndDoesNotWrite() {
        val storage = InMemorySaveStorage()
        storage.writeDocument(progressPath, "{ definitely not json")
        storage.writes.clear()
        val store = ProgressStore(storage)

        val error = assertThrows(ProgressMigrationException::class.java) {
            store.load("a.json")
        }

        assertTrue(error.message.orEmpty().contains("поврежд"))
        assertTrue(storage.writes.isEmpty())
    }

    @Test
    fun nullRootJsonIsExplicitError() {
        val storage = InMemorySaveStorage()
        storage.writeDocument(progressPath, "null")
        val store = ProgressStore(storage)

        assertThrows(ProgressMigrationException::class.java) {
            store.load("a.json")
        }
    }

    @Test
    fun invalidEntryDoesNotHideOtherAdventures() {
        val storage = InMemorySaveStorage()
        writeMap(
            storage,
            mapOf(
                "good.json" to progress("good.json"),
                "future.json" to progress("future.json", version = 999),
            ),
        )
        val store = ProgressStore(storage)

        assertNotNull(store.load("good.json"))
        assertThrows(ProgressMigrationException::class.java) {
            store.load("future.json")
        }
    }

    @Test
    fun nullEntryDoesNotHideOtherAdventures() {
        val storage = InMemorySaveStorage()
        val json = """
            {
              "bad.json": null,
              "good.json": {"version": 1, "adventureFileName": "good.json", "currentSceneId": "s"}
            }
        """.trimIndent()
        storage.writeDocument(progressPath, json)
        val store = ProgressStore(storage)

        assertNotNull(store.load("good.json"))
        assertThrows(ProgressMigrationException::class.java) {
            store.load("bad.json")
        }
    }

    @Test
    fun savingSameValueIsNoOp() = runBlocking {
        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)
        val data = progress()

        assertTrue(store.save("a.json", data))
        store.flush()
        val writes = storage.writes.size

        assertFalse(store.save("a.json", data))
        store.flush()
        assertEquals(writes, storage.writes.size)
    }

    @Test
    fun flushWithoutChangesDoesNotWrite() = runBlocking {
        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)

        store.flush()

        assertTrue(storage.writes.isEmpty())
    }

    @Test
    fun writeFailureKeepsDataDirtyForRetry() = runBlocking {
        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)
        assertTrue(store.save("a.json", progress()))

        storage.failWrites = true
        store.flush()
        assertNull(storage.readDocument(progressPath))

        storage.failWrites = false
        store.flush()
        assertNotNull(storage.readDocument(progressPath))
    }

    @Test
    fun multipleAdventuresArePersistedTogether() = runBlocking {
        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)
        assertTrue(store.save("a.json", progress("a.json", "a")))
        assertTrue(store.save("b.json", progress("b.json", "b")))
        store.flush()

        val fresh = ProgressStore(storage)
        assertEquals("a", fresh.load("a.json")?.currentSceneId)
        assertEquals("b", fresh.load("b.json")?.currentSceneId)
    }

    @Test
    fun legacyV0EntryIsMigratedByStore() {
        val storage = InMemorySaveStorage()
        storage.writeDocument(
            progressPath,
            """
                {
                  "legacy.json": {
                    "adventureFileName": "legacy.json",
                    "currentSceneId": "old_scene",
                    "flags": ["x"],
                    "stats": {"health": 3}
                  }
                }
            """.trimIndent(),
        )
        val store = ProgressStore(storage)

        val loaded = store.load("legacy.json")

        assertNotNull(loaded)
        assertEquals(ProgressMigration.CURRENT_VERSION, loaded!!.version)
        assertEquals("old_scene", loaded.currentSceneId)
        assertEquals(listOf("x"), loaded.flags)
    }

    @Test
    fun deleteRemovesOnlyRequestedAdventure() = runBlocking {
        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)
        store.save("a.json", progress("a.json", "a"))
        store.save("b.json", progress("b.json", "b"))
        store.flush()

        assertTrue(store.delete("a.json"))
        store.flush()

        val fresh = ProgressStore(storage)
        assertNull(fresh.load("a.json"))
        assertEquals("b", fresh.load("b.json")?.currentSceneId)
    }

    @Test
    fun cacheReadsProgressFileOnce() {
        val storage = InMemorySaveStorage()
        writeMap(storage, mapOf("a.json" to progress("a.json")))
        val store = ProgressStore(storage)

        store.load("a.json")
        store.load("a.json")

        assertEquals(1, storage.reads.count { it == progressPath })
    }
}
