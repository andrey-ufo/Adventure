package com.myapp.adventure.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Ф5.5/Ф5.9: slots UI on a real device. */
@RunWith(AndroidJUnit4::class)
class ReaderSlotsScreenInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setScreen(
        slots: List<SaveSlot> = emptyList(),
        onCreate: (String) -> Unit = {},
        onLoad: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
    ) {
        rule.setContent {
            ReaderSlotsScreen(
                slots = slots,
                loading = false,
                error = null,
                currentChapterNumber = 4,
                onBack = {},
                onCreate = onCreate,
                onLoad = onLoad,
                onDelete = onDelete,
                onRetry = {},
            )
        }
    }

    private fun slot(id: String, name: String) = SaveSlot(
        id = id,
        name = name,
        createdAt = 0,
        updatedAt = 0,
        state = ProgressData(adventureFileName = "a.json"),
    )

    @Test
    fun emptyStateAndCreateFlow() {
        var created: String? = null
        setScreen(onCreate = { created = it })

        rule.onNodeWithText("Ручных сохранений пока нет.").assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE).assertIsEnabled().performClick()
        rule.onNodeWithTag(ReaderSlotsTestTags.NAME_FIELD)
            .performTextReplacement("Смартфон")
        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE_CONFIRM).performClick()

        rule.runOnIdle { assertEquals("Смартфон", created) }
    }

    @Test
    fun loadAndDeleteReturnSelectedSlot() {
        var loaded: String? = null
        var deleted: String? = null
        setScreen(
            slots = listOf(slot("slot-1", "Перед боем")),
            onLoad = { loaded = it },
            onDelete = { deleted = it },
        )

        rule.onNodeWithTag(ReaderSlotsTestTags.load("slot-1")).performClick()
        rule.onNodeWithTag(ReaderSlotsTestTags.LOAD_CONFIRM).performClick()
        rule.runOnIdle { assertEquals("slot-1", loaded) }

        rule.onNodeWithTag(ReaderSlotsTestTags.delete("slot-1")).performClick()
        rule.onNodeWithTag(ReaderSlotsTestTags.DELETE_CONFIRM).performClick()
        rule.runOnIdle { assertEquals("slot-1", deleted) }
    }
}
