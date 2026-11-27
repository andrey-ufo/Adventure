package com.myapp.adventure.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.SaveSlot
import com.myapp.adventure.data.SaveSlotPaths
import com.myapp.adventure.ui.AppSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ф5.5/Ф5.9: slots screen UI behaviour (JVM/Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderSlotsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun slot(id: String, name: String) = SaveSlot(
        id = id,
        name = name,
        createdAt = 0,
        updatedAt = 0,
        state = ProgressData(adventureFileName = "a.json"),
    )

    private fun setScreen(
        slots: List<SaveSlot> = emptyList(),
        loading: Boolean = false,
        error: String? = null,
        onCreate: (String) -> Unit = {},
        onLoad: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        rule.setContent {
            ReaderSlotsScreen(
                slots = slots,
                loading = loading,
                error = error,
                currentChapterNumber = 3,
                onBack = onBack,
                currentSection = AppSection.Reader,
                canOpenReader = true,
                onNavigateGlobal = {},
                onCreate = onCreate,
                onLoad = onLoad,
                onDelete = onDelete,
                onRetry = onRetry,
            )
        }
    }

    @Test
    fun emptyStateShowsCreateButton() {
        setScreen()

        rule.onNodeWithText("Ручных сохранений пока нет.").assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE).assertIsEnabled()
    }

    @Test
    fun createDialogUsesChapterDefaultAndReturnsTrimmedName() {
        var created: String? = null
        setScreen(onCreate = { created = it })

        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE).performClick()
        rule.onNodeWithText("Новое сохранение").assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.NAME_FIELD).assertTextContains("Глава 3")
        rule.onNodeWithTag(ReaderSlotsTestTags.NAME_FIELD)
            .performTextReplacement("  Мой слот  ")
        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE_CONFIRM).performClick()

        rule.runOnIdle { assertEquals("Мой слот", created) }
    }

    @Test
    fun limitDisablesCreateAndShowsMessage() {
        val slots = (1..SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE).map {
            slot("id-$it", "slot-$it")
        }
        setScreen(slots = slots)

        rule.onNodeWithTag(ReaderSlotsTestTags.CREATE).assertIsNotEnabled()
        rule.onNodeWithText(
            "Лимит сохранений: ${SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE}. Удалите одно из старых.",
        ).assertIsDisplayed()
    }

    @Test
    fun errorShowsMessageAndRetryCallback() {
        var retried = false
        setScreen(error = "Не удалось прочитать", onRetry = { retried = true })

        rule.onNodeWithText("Не удалось прочитать").assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.RETRY).performClick()

        rule.runOnIdle { assertEquals(true, retried) }
    }

    @Test
    fun loadRequiresConfirmationAndReturnsSlotId() {
        var loaded: String? = null
        setScreen(slots = listOf(slot("slot-1", "Перед боем")), onLoad = { loaded = it })

        rule.onNodeWithTag(ReaderSlotsTestTags.load("slot-1")).performClick()
        rule.onNodeWithText("Загрузить сохранение?").assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.LOAD_CONFIRM).performClick()

        rule.runOnIdle { assertEquals("slot-1", loaded) }
    }

    @Test
    fun deleteRequiresConfirmationAndReturnsSlotId() {
        var deleted: String? = null
        setScreen(slots = listOf(slot("slot-2", "Старый слот")), onDelete = { deleted = it })

        rule.onNodeWithTag(ReaderSlotsTestTags.delete("slot-2")).performClick()
        rule.onNodeWithText("«Старый слот» будет удалено без возможности отмены.")
            .assertIsDisplayed()
        rule.onNodeWithTag(ReaderSlotsTestTags.DELETE_CONFIRM).performClick()

        rule.runOnIdle { assertEquals("slot-2", deleted) }
    }

    @Test
    fun loadingDisablesRowActions() {
        setScreen(slots = listOf(slot("slot-1", "Слот")), loading = true)

        rule.onNodeWithTag(ReaderSlotsTestTags.load("slot-1")).assertIsNotEnabled()
        rule.onNodeWithTag(ReaderSlotsTestTags.delete("slot-1")).assertIsNotEnabled()
    }
}
