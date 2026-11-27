package com.myapp.adventure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.myapp.adventure.utils.SafSaveStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Proves the Robolectric + Android resources setup is wired. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {

    @Test
    fun applicationContextAndAdventurePrefsAreAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.myapp.adventure", context.packageName)
        assertNull(SafSaveStorage.forBaseFolder(context))

        context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("adventure_folder_uri", "content://test/tree")
            .commit()

        val storage = SafSaveStorage.forBaseFolder(context)
        assertNotNull(storage)
        assertEquals("content://test/tree", storage!!.treeUriString)
    }
}
