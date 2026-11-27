package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.debug.TestSafProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Ф5.9: real ContentResolver/DocumentsContract + debug provider on device. */
@RunWith(AndroidJUnit4::class)
class SafPersistenceInstrumentedTest {

    private lateinit var context: Context
    private lateinit var storage: SafSaveStorage
    private val treeUri = TestSafProvider.treeUri()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit()
            .putString("adventure_folder_uri", treeUri.toString())
            .commit()
        storage = requireNotNull(SafSaveStorage.forBaseFolder(context))
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        ProgressManager.resetForTests()
    }

    private fun prefs() =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)

    @Test
    fun nestedDirectoriesRoundTripOnDevice() {
        val dir = "instrumented-${System.nanoTime()}"
        val path = listOf(dir, "saves", "key", "slot-1.json")

        assertTrue(storage.writeDocument(path, "{\"a\":1}"))
        assertEquals("{\"a\":1}", storage.readDocument(path))
        assertEquals(listOf("slot-1.json"), storage.listDocuments(path.dropLast(1)))
        assertTrue(storage.deleteDocument(path))
        assertNull(storage.readDocument(path))
    }

    @Test
    fun slotManagerRoundTripOnDevice() = runBlocking {
        val adventureUri = createAdventureDocument("adventure-${System.nanoTime()}.json")
        val state = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = "",
            currentSceneId = "s1",
            lastAbsoluteAtSave = 55,
        )

        val slot = SlotManager.createSlot(context, adventureUri, "checkpoint", state)
        assertEquals(-1, slot.state.lastAbsoluteAtSave)
        assertEquals(1, SlotManager.listSlots(context, adventureUri).size)

        val loaded = SlotManager.loadSlot(context, adventureUri, slot.id)
        assertNotNull(loaded)
        assertEquals(slot.state.stats, loaded!!.state.stats)

        assertTrue(SlotManager.deleteSlot(context, adventureUri, slot.id))
        assertTrue(SlotManager.listSlots(context, adventureUri).isEmpty())
    }

    @Test
    fun progressManagerRoundTripOnDevice() = runBlocking {
        val adventureUri = createAdventureDocument("progress-${System.nanoTime()}.json")
        val progress = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = "",
            currentSceneId = "s1",
            stats = mapOf("gold" to 3),
        )

        ProgressManager.saveProgress(context, adventureUri, progress)
        ProgressManager.flush(context)
        ProgressManager.resetForTests()

        val loaded = ProgressManager.loadProgress(context, adventureUri)
        assertEquals("s1", loaded?.currentSceneId)
        assertEquals(3, loaded?.stats?.get("gold"))
    }

    private fun createAdventureDocument(name: String): Uri {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        return requireNotNull(
            DocumentsContract.createDocument(
                context.contentResolver,
                rootUri,
                "application/json",
                name,
            ),
        )
    }
}
