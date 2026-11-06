package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.PlayerState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluatorTest {

    private fun state(
        flags: Set<String> = emptySet(),
        stats: Map<String, Int> = emptyMap(),
    ): PlayerState = PlayerState(flags = flags, stats = stats)

    @Test
    fun `has flag true when present`() {
        val s = state(flags = setOf("sword"))
        assertTrue(Evaluator.evaluate(Condition.Has("sword"), s))
    }

    @Test
    fun `has flag false when absent`() {
        assertFalse(Evaluator.evaluate(Condition.Has("sword"), state()))
    }

    @Test
    fun `has does not look at stats - sword in stats but not flags is false`() {
        // Regression for the Ф1.5 decision: `has_X` is only for Bool flags.
        // `sword` in `stats` (ItemCount) is NOT a flag; type checker in
        // Ф2.2 will reject `has_sword` on `sword: ItemCount`, but until
        // then the evaluator is strict.
        val s = state(stats = mapOf("sword" to 1))
        assertFalse(Evaluator.evaluate(Condition.Has("sword"), s))
    }

    @Test
    fun `not inverts`() {
        val s = state(flags = setOf("sword"))
        assertFalse(Evaluator.evaluate(Condition.Not(Condition.Has("sword")), s))
        assertTrue(Evaluator.evaluate(Condition.Not(Condition.Has("bow")), s))
    }

    @Test
    fun `and requires both`() {
        val s = state(flags = setOf("a"))
        assertTrue(
            Evaluator.evaluate(
                Condition.And(Condition.Has("a"), Condition.Not(Condition.Has("b"))),
                s,
            ),
        )
        assertFalse(
            Evaluator.evaluate(
                Condition.And(Condition.Has("a"), Condition.Has("b")),
                s,
            ),
        )
    }

    @Test
    fun `or requires one`() {
        val s = state(flags = setOf("a"))
        assertTrue(
            Evaluator.evaluate(
                Condition.Or(Condition.Has("a"), Condition.Has("b")),
                s,
            ),
        )
    }

    @Test
    fun `compare all operators`() {
        val s = state(stats = mapOf("health" to 50))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.GE, 50), s))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.LE, 50), s))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.EQ, 50), s))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.NE, 49), s))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.GT, 49), s))
        assertTrue(Evaluator.evaluate(Condition.Compare("health", CompareOp.LT, 51), s))
        assertFalse(Evaluator.evaluate(Condition.Compare("health", CompareOp.GT, 50), s))
        assertFalse(Evaluator.evaluate(Condition.Compare("health", CompareOp.LT, 50), s))
    }

    @Test
    fun `compare unknown stat defaults to zero`() {
        assertTrue(Evaluator.evaluate(Condition.Compare("mana", CompareOp.EQ, 0), state()))
        assertTrue(Evaluator.evaluate(Condition.Compare("mana", CompareOp.GE, 0), state()))
        assertFalse(Evaluator.evaluate(Condition.Compare("mana", CompareOp.GT, 0), state()))
    }

    @Test
    fun `in range inclusive`() {
        val s = state(stats = mapOf("health" to 50))
        assertTrue(Evaluator.evaluate(Condition.InRange("health", 50, 50), s))
        assertTrue(Evaluator.evaluate(Condition.InRange("health", 0, 100), s))
        assertFalse(Evaluator.evaluate(Condition.InRange("health", 51, 100), s))
    }

    @Test
    fun `is dead only when health at or below zero`() {
        assertFalse(Evaluator.isDead(state(stats = mapOf("health" to 1))))
        assertTrue(Evaluator.isDead(state(stats = mapOf("health" to 0))))
        assertTrue(Evaluator.isDead(state(stats = mapOf("health" to -5))))
    }

    @Test
    fun `is dead is false when health not declared`() {
        // "no health" means the adventure does not use death.
        assertFalse(Evaluator.isDead(state()))
    }
}