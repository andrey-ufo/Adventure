package com.myapp.adventure.ui.reader

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.ProgressSnapshot
import com.myapp.adventure.data.toDto
import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.utils.FakeSafProvider
import com.myapp.adventure.utils.ProgressManager
import com.myapp.adventure.utils.ProgressMigration
import com.myapp.adventure.utils.ProgressStore
import com.myapp.adventure.utils.SafSaveStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ф5.9: reader init/step/save/reload through the real ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdventureReaderViewModelTest {

    private lateinit var context: Context
    private lateinit var provider: FakeSafProvider
    private lateinit var storage: SafSaveStorage
    private lateinit var adventureUri: String
    private val progressPath = listOf(ProgressStore.PROGRESS_FILE_NAME)

    private val adventureJson = """
        {
          "version": 5,
          "title": "VM test",
          "start_scene": "s1",
          "chapters": [
            {
              "id": "c1",
              "title": "Chapter",
              "scenes": [
                {
                  "id": "s1",
                  "text": "Start",
                  "choices": [
                    {
                      "text": "Go",
                      "next_scene": "s2",
                      "modify_stats": {"gold": 1},
                      "set_flags": ["moved"]
                    }
                  ]
                },
                {"id": "s2", "text": "End", "is_ending": true}
              ]
            }
          ]
        }
    """.trimIndent()

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
        adventureUri = provider.createFile("adventure.json").toString()
        storage.writeDocument(listOf("adventure.json"), adventureJson)
    }

    @After
    fun tearDown() {
        ProgressManager.resetForTests()
        prefs().edit().clear().commit()
    }

    private fun prefs() =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)

    private fun newViewModel(): AdventureReaderViewModel =
        AdventureReaderViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun arguments() = Bundle().apply {
        putString("adventure_json", adventureJson)
        putBoolean("preview_mode", false)
        putString("adventure_file_uri", adventureUri)
    }

    @Test
    fun stepSavesProgressAndNewViewModelLoadsIt() = runBlocking {
        val vm = newViewModel()
        assertEquals(ReaderInitResult.Success, vm.init(arguments()))
        assertEquals("s1", vm.state.value.currentSceneId)

        val choice = requireNotNull(vm.state.value.compiled)
            .scenesById.getValue(SceneId("s1"))
            .choices
            .first()
        vm.onChoicePicked(choice)
        assertEquals("s2", vm.state.value.currentSceneId)
        assertEquals(1, vm.state.value.stats["gold"])
        assertTrue(vm.state.value.flags.contains("moved"))
        vm.flushProgress()

        val saved = readSavedProgress()
        assertEquals("s2", saved.currentSceneId)
        assertEquals(1, saved.stats["gold"])
        assertTrue(saved.flags.contains("moved"))
        assertNotNull(saved.baseSnapshot)
        assertTrue(saved.delta.orEmpty().size <= 32)

        ProgressManager.resetForTests()
        val reloaded = newViewModel()
        assertEquals(ReaderInitResult.Success, reloaded.init(arguments()))
        assertEquals("s2", reloaded.state.value.currentSceneId)
        assertEquals(1, reloaded.state.value.stats["gold"])
        assertTrue(reloaded.state.value.flags.contains("moved"))
    }

    @Test
    fun stepBackRestoresPreviousStateAndSavesIt() = runBlocking {
        val vm = newViewModel()
        assertEquals(ReaderInitResult.Success, vm.init(arguments()))
        val choice = requireNotNull(vm.state.value.compiled)
            .scenesById.getValue(SceneId("s1"))
            .choices
            .first()

        vm.onChoicePicked(choice)
        vm.onStepBack()
        vm.flushProgress()

        assertEquals("s1", vm.state.value.currentSceneId)
        assertEquals(0, vm.state.value.stats["gold"] ?: 0)
        assertTrue(!vm.state.value.flags.contains("moved"))
        assertEquals("s1", readSavedProgress().currentSceneId)
    }

    @Test
    fun adventureRestartResetsStateAndAutosave() = runBlocking {
        val vm = newViewModel()
        assertEquals(ReaderInitResult.Success, vm.init(arguments()))
        val choice = requireNotNull(vm.state.value.compiled)
            .scenesById.getValue(SceneId("s1"))
            .choices
            .first()

        vm.onChoicePicked(choice)
        vm.onRestartAdventure()
        vm.flushProgress()

        assertEquals("s1", vm.state.value.currentSceneId)
        assertEquals(0, vm.state.value.stats["gold"] ?: 0)
        assertTrue(!vm.state.value.flags.contains("moved"))
        assertEquals("s1", readSavedProgress().currentSceneId)
    }

    @Test
    fun legacyProgressFallsBackToCurrentFields() = runBlocking {
        storage.writeDocument(
            progressPath,
            Gson().toJson(
                mapOf(
                    "adventure.json" to ProgressData(
                        version = 0,
                        adventureFileName = "",
                        currentSceneId = "s1",
                        flags = listOf("legacy"),
                        stats = mapOf("gold" to 7),
                    ),
                ),
            ),
        )

        val vm = newViewModel()
        assertEquals(ReaderInitResult.Success, vm.init(arguments()))
        assertEquals("s1", vm.state.value.currentSceneId)
        assertEquals(7, vm.state.value.stats["gold"])
        assertTrue(vm.state.value.flags.contains("legacy"))
    }

    @Test
    fun futureProgressVersionShowsBlockingError() {
        storage.writeDocument(
            progressPath,
            Gson().toJson(
                mapOf(
                    "adventure.json" to ProgressData(
                        version = 999,
                        adventureFileName = "",
                        currentSceneId = "s1",
                    ),
                ),
            ),
        )

        val result = newViewModel().init(arguments())

        assertTrue(result is ReaderInitResult.ProgressError)
    }

    @Test
    fun corruptProgressShowsBlockingError() {
        storage.writeDocument(progressPath, "{ broken")

        val result = newViewModel().init(arguments())

        assertTrue(result is ReaderInitResult.ProgressError)
    }

    @Test
    fun oversizedLoadedLogIsCompacted() = runBlocking {
        val base = ProgressSnapshot(
            currentSceneId = "s1",
            stats = mapOf("gold" to 0),
        )
        val deltas = (1..40).map { StateDelta.AddStat("gold", 1) }
        storage.writeDocument(
            progressPath,
            Gson().toJson(
                mapOf(
                    "adventure.json" to ProgressData(
                        version = ProgressMigration.CURRENT_VERSION,
                        adventureFileName = "",
                        baseSnapshot = base,
                        delta = deltas.map { it.toDto() },
                        currentSceneId = "s1",
                        flags = emptyList(),
                        stats = mapOf("gold" to 40),
                    ),
                ),
            ),
        )

        val vm = newViewModel()
        assertEquals(ReaderInitResult.Success, vm.init(arguments()))
        assertEquals(40, vm.state.value.stats["gold"])
        assertTrue(vm.state.value.operationLog.size <= 32)
    }

    private fun readSavedProgress(): ProgressData {
        val json = requireNotNull(storage.readDocument(progressPath))
        val type = object : TypeToken<Map<String, ProgressData>>() {}.type
        return requireNotNull(
            Gson().fromJson<Map<String, ProgressData>>(json, type)["adventure.json"],
        )
    }
}
