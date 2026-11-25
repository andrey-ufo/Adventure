package com.myapp.adventure.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ф4.8: subroutines and `Choice.call` must survive the Gson round-trip
 * used by `saveAdventureToFile` → `loadAdventureFromUri`. The engine
 * parser has its own tests; this pins the editor's wire names
 * (`subroutines`, `subroutine_id`, `set_flags`, …).
 */
class AdventureSubroutineSerializationTest {

    private val gson = Gson()

    @Test
    fun choiceCallRoundTrips() {
        val choice = Choice(
            text = "Выпить зелье",
            nextScene = "start",
            call = SubroutineCallDto(
                subroutineId = "consume",
                args = mapOf("item" to "potion", "health" to "health"),
                outputs = mapOf("item" to "potion", "health" to "health"),
            ),
        )

        val json = gson.toJson(choice)
        assertTrue(json.contains("\"subroutine_id\":\"consume\""))
        assertTrue(json.contains("\"args\""))

        val back = gson.fromJson(json, Choice::class.java)
        assertNotNull(back.call)
        val call = back.call!!
        assertEquals("consume", call.subroutineId)
        assertEquals(mapOf("item" to "potion", "health" to "health"), call.args)
        assertEquals(mapOf("item" to "potion", "health" to "health"), call.outputs)
    }

    @Test
    fun subroutineRoundTripsWithFlatBodyAndNestedCall() {
        val subroutine = SubroutineDto(
            id = "consume",
            inputs = listOf(VarDeclDto("item", "item_count")),
            outputs = listOf(VarDeclDto("item", "item_count")),
            body = listOf(
                SubroutineStepDto(
                    condition = "item > 0",
                    modifyStats = mapOf("item" to -1),
                    call = SubroutineCallDto(
                        subroutineId = "inner",
                        args = mapOf("x" to "item"),
                        outputs = mapOf("y" to "item"),
                    ),
                ),
            ),
        )
        val adventure = Adventure(
            version = 5,
            title = "T",
            subroutines = listOf(subroutine),
            chapters = emptyList(),
        )

        val json = gson.toJson(adventure)
        assertTrue(json.contains("\"subroutines\""))
        assertTrue(json.contains("\"subroutine_id\":\"inner\""))

        val back = gson.fromJson(json, Adventure::class.java)
        assertNotNull(back.subroutines)
        val sub = back.subroutines!!.single()
        assertEquals("consume", sub.id)
        assertEquals("item", sub.inputs?.single()?.name)
        assertNotNull(sub.body)
        val step = sub.body!!.single()
        assertEquals("item > 0", step.condition)
        assertEquals(mapOf("item" to -1), step.modifyStats)
        assertEquals("inner", step.call?.subroutineId)
        assertEquals(mapOf("x" to "item"), step.call?.args)
        assertEquals(mapOf("y" to "item"), step.call?.outputs)
    }

    @Test
    fun absentSubroutinesStayAbsent() {
        val adventure = Adventure(
            version = 5,
            title = "T",
            chapters = emptyList(),
        )

        val json = gson.toJson(adventure)
        assertTrue(!json.contains("\"subroutines\""))
        assertEquals(null, gson.fromJson(json, Adventure::class.java).subroutines)
    }
}
