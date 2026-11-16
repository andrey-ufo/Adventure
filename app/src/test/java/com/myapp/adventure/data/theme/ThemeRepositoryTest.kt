package com.myapp.adventure.data.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeRepositoryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope

    /**
     * Creates a DataStore backed by [OkioStorage] explicitly. This bypasses
     * the platform-specific [androidx.datastore.preferences.core.PreferenceDataStoreFactory]
     * which on some JVM test configurations resolves to the Android artifact
     * and therefore to `FileStorage` (`java.io.File`). `FileStorage` relies
     * on `File.renameTo`, which fails on Windows when the target file already
     * exists (issue b/203087070).
     *
     * [OkioStorage] delegates file moves to Okio's `FileSystem.atomicMove`,
     * which on Windows uses `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`
     * and can replace an existing file.
     */
    private fun newRepository(scope: TestScope): ThemeRepository {
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file: File = File(tempFolder.root, "test_${System.nanoTime()}.preferences_pb")
        val ds: DataStore<Preferences> = DataStoreFactory.create(
            scope = dataStoreScope,
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.toOkioPath() },
            ),
        )
        return ThemeRepository(ds, scope.backgroundScope)
    }

    @After
    fun tearDown() {
        if (::dataStoreScope.isInitialized) {
            dataStoreScope.cancel()
        }
    }

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    @Test
    fun `empty DataStore yields defaults`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        assertEquals(ThemeSelection.DEFAULT_THEME_ID, r.getThemeIdSync())
        assertEquals(ThemeSelection.DEFAULT_NIGHT_MODE, r.getNightModeSync())
        assertTrue(r.getCustomThemesSync().isEmpty())
    }

    @Test
    fun `currentTheme resolves to DEFAULT on empty DataStore`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()
        assertEquals(BuiltInThemes.DEFAULT.id, r.currentTheme.value.id)
    }

    // ------------------------------------------------------------------
    // Night mode
    // ------------------------------------------------------------------

    @Test
    fun `setNightMode persists and updates cache synchronously`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        r.setNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, r.getNightModeSync())
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, r.selection.value.nightMode)
    }

    @Test
    fun `setNightMode survives repository recreation`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tempFolder.root, "persistence.preferences_pb")
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = DataStoreFactory.create(
            scope = dataStoreScope,
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.toOkioPath() },
            ),
        )

        val r1 = ThemeRepository(ds, backgroundScope)
        r1.awaitInitialized()
        r1.setNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val r2 = ThemeRepository(ds, backgroundScope)
        r2.awaitInitialized()

        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, r2.getNightModeSync())
    }

    // ------------------------------------------------------------------
    // Theme id
    // ------------------------------------------------------------------

    @Test
    fun `setThemeId with built-in id persists`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()
        r.setThemeId("default")
        assertEquals("default", r.getThemeIdSync())
    }

    @Test
    fun `setThemeId with unknown id falls back to default`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()
        r.setThemeId("nonexistent_theme_xyz")
        assertEquals("default", r.getThemeIdSync())
    }

    // ------------------------------------------------------------------
    // Custom themes
    // ------------------------------------------------------------------

    @Test
    fun `addOrReplaceCustomTheme stores a user theme`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        val custom = makeCustomTheme("custom_001", "My Theme")
        r.addOrReplaceCustomTheme(custom)

        assertEquals(1, r.getCustomThemesSync().size)
        assertEquals("custom_001", r.getCustomThemesSync()[0].id)
    }

    @Test
    fun `addOrReplaceCustomTheme with same id replaces`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        r.addOrReplaceCustomTheme(makeCustomTheme("custom_001", "First"))
        r.addOrReplaceCustomTheme(makeCustomTheme("custom_001", "Second"))

        assertEquals(1, r.getCustomThemesSync().size)
        assertEquals("Second", r.getCustomThemesSync()[0].displayName)
    }

    @Test
    fun `built-in theme cannot be added as custom`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        try {
            r.addOrReplaceCustomTheme(BuiltInThemes.DEFAULT)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `removeCustomTheme drops it from the list`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()
        r.addOrReplaceCustomTheme(makeCustomTheme("custom_001", "X"))
        r.removeCustomTheme("custom_001")
        assertTrue(r.getCustomThemesSync().isEmpty())
    }

    @Test
    fun `removeCustomTheme of active theme falls back to default`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()

        r.addOrReplaceCustomTheme(makeCustomTheme("custom_001", "X"))
        r.setThemeId("custom_001")
        assertEquals("custom_001", r.getThemeIdSync())

        r.removeCustomTheme("custom_001")
        assertEquals("default", r.getThemeIdSync())
    }

    @Test
    fun `custom themes survive repository recreation`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tempFolder.root, "custom_themes_persistence.preferences_pb")
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = DataStoreFactory.create(
            scope = dataStoreScope,
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.toOkioPath() },
            ),
        )

        val r1 = ThemeRepository(ds, backgroundScope)
        r1.awaitInitialized()
        r1.addOrReplaceCustomTheme(makeCustomTheme("custom_001", "Saved"))

        val r2 = ThemeRepository(ds, backgroundScope)
        r2.awaitInitialized()

        assertEquals(1, r2.getCustomThemesSync().size)
        assertEquals("Saved", r2.getCustomThemesSync()[0].displayName)
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    @Test
    fun `currentTheme resolves custom theme by id`() = runTest(UnconfinedTestDispatcher()) {
        val r = newRepository(this)
        r.awaitInitialized()
        val custom = makeCustomTheme("custom_abc", "Custom")
        r.addOrReplaceCustomTheme(custom)
        r.setThemeId("custom_abc")

        assertEquals("custom_abc", r.currentTheme.value.id)
        assertEquals("Custom", r.currentTheme.value.displayName)
    }

    // ------------------------------------------------------------------
    // Corruption
    // ------------------------------------------------------------------

    @Test
    fun `corrupted custom themes JSON is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tempFolder.root, "corrupt.preferences_pb")
        val ds = DataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { file.toOkioPath() },
            ),
        )
        ds.edit { it[ThemePreferences.KEY_CUSTOM_THEMES] = "{not valid json" }

        val r = ThemeRepository(ds, backgroundScope)
        r.awaitInitialized()

        assertTrue(r.getCustomThemesSync().isEmpty())
        assertEquals("default", r.getThemeIdSync())
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun makeCustomTheme(id: String, displayName: String): AppThemeConfig =
        BuiltInThemes.DEFAULT.copy(
            id = id,
            displayName = displayName,
            isBuiltIn = false,
        )
}