package com.myapp.adventure.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.myapp.adventure.data.ProgressData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ф5.9: ProgressManager cache/debounce/error behaviour over real SAF calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressManagerTest {

    private lateinit var context: Context
    private lateinit var provider: FakeSafProvider
    private lateinit var storage: SafSaveStorage
    private val progressPath = listOf(ProgressStore.PROGRESS_FILE_NAME)

    @Before
    fun setUp() {
        ProgressManager.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(FakeSafProvider::class.java)
            .create(FakeSafProvider.AUTHORITY)
            .get()
        prefs().edit()
            .putString("adventure_folder_uri", provider.treeUri.toString())
            .commit()
        storage = requireNotNull(SafSaveStorage.forBaseFolder(context))
    }

    @After
    fun tearDown() {
        ProgressManager.resetForTests()
        prefs().edit().clear().commit()
    }

    private fun prefs() =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)

    private fun progress(scene: String = "s") = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = "",
        currentSceneId = scene,
        flags = listOf("f"),
        stats = mapOf("health" to 10),
    )

    @Test
    fun saveLoadFlushAndReload() = runBlocking {
        val uri = provider.createFile("a.json")
        val data = progress("one")

        ProgressManager.saveProgress(context, uri, data)
        assertEquals("one", ProgressManager.loadProgress(context, uri)?.currentSceneId)

        ProgressManager.flush(context)
        assertNotNull(storage.readDocument(progressPath))

        ProgressManager.resetForTests()
        assertEquals("one", ProgressManager.loadProgress(context, uri)?.currentSceneId)
    }

    @Test
    fun debouncedSaveWritesOnlyOnFlush() = runBlocking {
        val uri = provider.createFile("a.json")

        ProgressManager.saveProgress(context, uri, progress())
        assertNull(storage.readDocument(progressPath))

        ProgressManager.flush(context)
        assertNotNull(storage.readDocument(progressPath))
    }

    @Test
    fun futureVersionEntryIsExplicitAndOtherAdventureStillLoads() {
        runBlocking {
            val good = provider.createFile("good.json")
        val future = provider.createFile("future.json")
        storage.writeDocument(
            progressPath,
            Gson().toJson(
                mapOf(
                    "good.json" to progress("good"),
                    "future.json" to ProgressData(
                        version = 999,
                        adventureFileName = "",
                        currentSceneId = "future",
                    ),
                ),
            ),
        )

        assertEquals("good", ProgressManager.loadProgress(context, good)?.currentSceneId)
            assertThrows(ProgressMigrationException::class.java) {
                ProgressManager.loadProgress(context, future)
            }
        }
    }

    @Test
    fun corruptFileIsExplicitOnLoadButWarmUpDoesNotCrash() {
        runBlocking {
            val uri = provider.createFile("a.json")
            storage.writeDocument(progressPath, "{ corrupt")

            ProgressManager.warmUp(context)
            assertThrows(ProgressMigrationException::class.java) {
                ProgressManager.loadProgress(context, uri)
            }
        }
    }

    @Test
    fun deleteRemovesAutosave() = runBlocking {
        val uri = provider.createFile("a.json")
        ProgressManager.saveProgress(context, uri, progress())
        ProgressManager.flush(context)

        ProgressManager.deleteProgress(context, uri)
        ProgressManager.flush(context)

        assertNull(ProgressManager.loadProgress(context, uri))
        ProgressManager.resetForTests()
        assertNull(ProgressManager.loadProgress(context, uri))
    }

    @Test
    fun multipleAdventuresShareOneCacheFile() = runBlocking {
        val a = provider.createFile("a.json")
        val b = provider.createFile("b.json")
        ProgressManager.saveProgress(context, a, progress("a"))
        ProgressManager.saveProgress(context, b, progress("b"))
        ProgressManager.flush(context)

        ProgressManager.resetForTests()
        assertEquals("a", ProgressManager.loadProgress(context, a)?.currentSceneId)
        assertEquals("b", ProgressManager.loadProgress(context, b)?.currentSceneId)
        assertTrue(storage.listDocuments(emptyList()).contains("progress.json"))
    }
}
