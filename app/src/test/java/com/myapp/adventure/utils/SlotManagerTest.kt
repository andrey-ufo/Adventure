package com.myapp.adventure.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlotException
import com.myapp.adventure.data.SaveSlotPaths
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ф5.5/Ф5.9: SlotManager over the Robolectric DocumentsProvider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SlotManagerTest {

    private lateinit var context: Context
    private lateinit var provider: FakeSafProvider
    private lateinit var adventureUri: Uri
    private lateinit var storage: SafSaveStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(FakeSafProvider::class.java)
            .create(FakeSafProvider.AUTHORITY)
            .get()
        prefs().edit()
            .putString("adventure_folder_uri", provider.treeUri.toString())
            .commit()
        storage = requireNotNull(SafSaveStorage.forBaseFolder(context))
        adventureUri = provider.createFile("adventure.json")
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    private fun prefs() =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)

    private fun state(scene: String = "s1") = ProgressData(
        version = ProgressMigration.CURRENT_VERSION,
        adventureFileName = "",
        currentSceneId = scene,
        lastAbsoluteAtSave = 777,
    )

    @Test
    fun createListLoadDeleteRoundTrip() = runBlocking {
        val slot = SlotManager.createSlot(context, adventureUri, "checkpoint", state())

        assertEquals("adventure.json", slot.state.adventureFileName)
        assertEquals(-1, slot.state.lastAbsoluteAtSave)
        assertEquals(1, SlotManager.listSlots(context, adventureUri).size)

        val loaded = SlotManager.loadSlot(context, adventureUri, slot.id)
        assertNotNull(loaded)
        assertEquals(slot, loaded)

        assertTrue(SlotManager.deleteSlot(context, adventureUri, slot.id))
        assertTrue(SlotManager.listSlots(context, adventureUri).isEmpty())
        assertNull(SlotManager.loadSlot(context, adventureUri, slot.id))
    }

    @Test
    fun missingSlotIsNullAndDeleteIsFalse() = runBlocking {
        assertNull(SlotManager.loadSlot(context, adventureUri, "missing"))
        assertTrue(!SlotManager.deleteSlot(context, adventureUri, "missing"))
    }

    @Test
    fun capIsEnforcedThroughManager() = runBlocking {
        repeat(SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE) { i ->
            SlotManager.createSlot(context, adventureUri, "slot-$i", state())
        }

        val error = runCatching {
            SlotManager.createSlot(context, adventureUri, "overflow", state())
        }.exceptionOrNull()
        assertTrue(error is SaveSlotException)
        assertEquals(
            SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE,
            SlotManager.listSlots(context, adventureUri).size,
        )
    }

    @Test
    fun blankNameGetsDefaultThroughManager() = runBlocking {
        val slot = SlotManager.createSlot(context, adventureUri, "  ", state())

        assertEquals("Сохранение", slot.name)
    }

    @Test
    fun corruptSlotIsExplicitErrorThroughManager() = runBlocking {
        val key = SaveSlotPaths.adventureKey("adventure.json")
        storage.writeDocument(
            listOf(SaveSlotPaths.SAVES_DIRECTORY, key, SaveSlotPaths.slotFileName("bad")),
            "{ broken",
        )

        val error = runCatching {
            SlotManager.listSlots(context, adventureUri)
        }.exceptionOrNull() as? SaveSlotException
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("slot-bad.json"))
    }
}
