package com.myapp.adventure.data

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ф4.10/Ф4.11: the reference sample must be explainable in the editor.
 *
 * The engine golden covers the IR; this test pins the **app data** layer
 * used by `EditorRouteSupport` — template refs, subroutine calls,
 * visibility and merge must all survive Gson, and a plain choice must not
 * look like a subroutine call.
 */
class TestAdventureEditorDataTest {

    private val gson = Gson()

    private fun sample(): Adventure {
        val candidates = listOf(
            File("src/main/res/raw/test_adventure.json"),
            File("app/src/main/res/raw/test_adventure.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("test_adventure.json not found from ${File(".").absolutePath}")
        return gson.fromJson(file.readText(), Adventure::class.java)
    }

    @Test
    fun sampleShowsBothSubroutines() {
        val ids = sample().subroutines.orEmpty().map { it.id }.toSet()
        assertEquals(setOf("drink_potion", "drink_potion_strong"), ids)
    }

    @Test
    fun templateChoiceKeepsTemplateRefAndVisibility() {
        val adventure = sample()
        val choice = adventure.chapters.orEmpty()
            .flatMap { it.scenes.orEmpty() }
            .flatMap { it.choices.orEmpty() }
            .first { it.text.contains("через шаблон rest") }

        assertEquals("rest", choice.templateRef?.templateId)
        assertNotNull(choice.visibilityCondition)
        assertNull(choice.call)
    }

    @Test
    fun templateIsUsedWithDifferentArguments() {
        val args = sample().chapters.orEmpty()
            .flatMap { it.scenes.orEmpty() }
            .flatMap { it.choices.orEmpty() }
            .mapNotNull { it.templateRef?.takeIf { ref -> ref.templateId == "rest" } }
            .mapNotNull { it.args["stat"] }
            .toSet()

        assertEquals(setOf("health", "stamina"), args)
    }

    @Test
    fun plainChoiceHasNoSubroutineCall() {
        val adventure = sample()
        val choice = adventure.chapters.orEmpty()
            .flatMap { it.scenes.orEmpty() }
            .flatMap { it.choices.orEmpty() }
            .first { it.text.startsWith("Обычный выбор: открыть набор зелий") }

        assertNull(choice.call)
        assertNotNull(choice.condition)
    }

    @Test
    fun subroutineChoicesPointAtTheRightIds() {
        val adventure = sample()
        val choices = adventure.chapters.orEmpty()
            .flatMap { it.scenes.orEmpty() }
            .flatMap { it.choices.orEmpty() }
            .filter { it.call != null }

        val ids = choices.mapNotNull { it.call?.subroutineId }.toSet()
        assertTrue(ids.contains("drink_potion"))
        assertTrue(ids.contains("drink_potion_strong"))
    }

    @Test
    fun mergeSceneKeepsDeclaration() {
        val adventure = sample()
        val merge = adventure.chapters.orEmpty()
            .flatMap { it.scenes.orEmpty() }
            .first { it.id == "merge_test" }

        assertEquals(true, merge.merge)
        assertEquals(setOf("health_test", "items_test"), merge.mergeSources.orEmpty().toSet())
    }
}
