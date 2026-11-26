package com.myapp.adventure.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ф4.8c: duplicate ids are predictable and never collide. */
class EditorSubroutineIdTest {

    @Test
    fun unusedIdIsKept() {
        assertEquals(
            "consume",
            uniqueSubroutineId("consume", emptySet()),
        )
    }

    @Test
    fun usedIdGetsNumericSuffix() {
        assertEquals(
            "consume_2",
            uniqueSubroutineId("consume", setOf("consume")),
        )
    }

    @Test
    fun numericSuffixIsReplacedNotStacked() {
        assertEquals(
            "consume_3",
            uniqueSubroutineId("consume_2", setOf("consume", "consume_2")),
        )
    }

    @Test
    fun blankIdFallsBackToSubroutine() {
        assertEquals(
            "subroutine",
            uniqueSubroutineId("", emptySet()),
        )
        assertEquals(
            "subroutine_2",
            uniqueSubroutineId("", setOf("subroutine")),
        )
    }
}
