package com.myapp.adventure.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric + fake DocumentsProvider: real SAF path/stream behaviour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafSaveStorageTest {

    private lateinit var context: Context
    private lateinit var provider: FakeSafProvider
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
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
    }

    private fun prefs() =
        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)

    @Test
    fun createsNestedDirectoriesAndRoundTripsText() {
        val path = listOf("saves", "key", "slot-1.json")

        assertTrue(storage.writeDocument(path, "{\"a\":1}"))
        assertEquals(provider.debugNodes(), "{\"a\":1}", storage.readDocument(path))
        assertEquals(provider.debugNodes(), listOf("slot-1.json"), storage.listDocuments(path.dropLast(1)))
    }

    @Test
    fun overwriteReplacesContent() {
        val path = listOf("saves", "key", "slot-1.json")

        assertTrue(storage.writeDocument(path, "old"))
        assertTrue(storage.writeDocument(path, "new"))

        assertEquals("new", storage.readDocument(path))
        assertEquals(listOf("slot-1.json"), storage.listDocuments(path.dropLast(1)))
    }

    @Test
    fun missingPathIsEmptyAndSafe() {
        assertNull(storage.readDocument(listOf("absent", "file.json")))
        assertTrue(storage.listDocuments(listOf("absent")).isEmpty())
        assertFalse(storage.deleteDocument(listOf("absent", "file.json")))
    }

    @Test
    fun deleteRemovesOnlyTheRequestedDocument() {
        val first = listOf("saves", "key", "slot-1.json")
        val second = listOf("saves", "key", "slot-2.json")
        storage.writeDocument(first, "1")
        storage.writeDocument(second, "2")

        assertTrue(storage.deleteDocument(first))

        assertNull(storage.readDocument(first))
        assertEquals("2", storage.readDocument(second))
        assertEquals(listOf("slot-2.json"), storage.listDocuments(first.dropLast(1)))
    }

    @Test
    fun listDocumentsExcludesDirectories() {
        storage.writeDocument(listOf("saves", "key", "slot-1.json"), "1")
        storage.writeDocument(listOf("saves", "key", "nested", "slot-2.json"), "2")

        assertEquals(listOf("slot-1.json"), storage.listDocuments(listOf("saves", "key")))
    }
}
