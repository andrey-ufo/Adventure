package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ф5.2 (S5): operation-log apply semantics. */
class StateDeltaTest {

    @Test
    fun `base plus three deltas equals expected state`() {
        val base = PlayerState(
            flags = setOf("started"),
            stats = mapOf("health" to 10, "gold" to 3),
        )
        val deltas = listOf(
            StateDelta.AddStat("health", 5),
            StateDelta.SubStat("gold", 1),
            StateDelta.SetFlag("has_sword"),
        )

        val state = applyDeltas(base, deltas)

        assertEquals(15, state.stats["health"])
        assertEquals(2, state.stats["gold"])
        assertEquals(setOf("started", "has_sword"), state.flags)
    }

    @Test
    fun `numeric deltas clamp at zero`() {
        val base = PlayerState(stats = mapOf("health" to 3))

        assertEquals(
            0,
            applyDelta(base, StateDelta.SubStat("health", 10)).stats["health"],
        )
        assertEquals(
            0,
            applyDelta(base, StateDelta.AddStat("health", -10)).stats["health"],
        )
        assertEquals(
            0,
            applyDelta(base, StateDelta.SetStat("health", -1)).stats["health"],
        )
    }

    @Test
    fun `remove flag and set stat are idempotent`() {
        val base = PlayerState(flags = setOf("x"), stats = mapOf("gold" to 1))
        val once = applyDelta(base, StateDelta.RemoveFlag("x"))
        val twice = applyDelta(once, StateDelta.RemoveFlag("x"))
        assertEquals(emptySet(), twice.flags)

        val setOnce = applyDelta(base, StateDelta.SetStat("gold", 10))
        val setTwice = applyDelta(setOnce, StateDelta.SetStat("gold", 10))
        assertEquals(10, setTwice.stats["gold"])
    }
}
