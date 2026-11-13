package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EvaluatorTest {

    private fun state(
        flags: Set<String> = emptySet(),
        stats: Map<String, Int> = emptyMap(),
    ): PlayerState = PlayerState(flags = flags, stats = stats)

    // ---------- evaluate(): Boolean wrapper (Ф1.5) ----------

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

    // ---------- Ф3.1: Literal and VarRef ----------

    @Test
    fun `literal true evaluates to true`() {
        assertTrue(Evaluator.evaluate(Condition.Literal(true), state()))
    }

    @Test
    fun `literal false evaluates to false`() {
        assertFalse(Evaluator.evaluate(Condition.Literal(false), state()))
    }

    @Test
    fun `var ref reads flags same as has`() {
        val s = state(flags = setOf("sword"))
        assertTrue(Evaluator.evaluate(Condition.VarRef("sword"), s))
        assertFalse(Evaluator.evaluate(Condition.VarRef("bow"), s))
    }

    // ---------- Ф3.3: evaluateWithReason ----------

    @Test
    fun `success result for true condition`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Has("sword"),
            state(flags = setOf("sword")),
        )
        assertEquals(EvaluationResult.Success, result)
    }

    @Test
    fun `has failed reason`() {
        val result = Evaluator.evaluateWithReason(Condition.Has("sword"), state())
        assertEquals(
            EvaluationResult.Failure(Reason.HasFailed("sword")),
            result,
        )
    }

    @Test
    fun `var ref failed reason`() {
        val result = Evaluator.evaluateWithReason(Condition.VarRef("sword"), state())
        assertEquals(
            EvaluationResult.Failure(Reason.VarRefFailed("sword")),
            result,
        )
    }

    @Test
    fun `literal false reason`() {
        val result = Evaluator.evaluateWithReason(Condition.Literal(false), state())
        assertEquals(
            EvaluationResult.Failure(Reason.LiteralFailed),
            result,
        )
    }

    @Test
    fun `compare failed reason carries actual and expected`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Compare("health", CompareOp.GE, 50),
            state(stats = mapOf("health" to 30)),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.CompareFailed(
                    varName = "health",
                    op = CompareOp.GE,
                    actualValue = 30,
                    expectedValue = 50,
                )
            ),
            result,
        )
    }

    @Test
    fun `compare failed on missing stat reports actual zero`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Compare("mana", CompareOp.GT, 0),
            state(),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.CompareFailed(
                    varName = "mana",
                    op = CompareOp.GT,
                    actualValue = 0,
                    expectedValue = 0,
                )
            ),
            result,
        )
    }

    @Test
    fun `in range failed reason`() {
        val result = Evaluator.evaluateWithReason(
            Condition.InRange("health", 50, 100),
            state(stats = mapOf("health" to 30)),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.InRangeFailed(
                    varName = "health",
                    actualValue = 30,
                    min = 50,
                    max = 100,
                )
            ),
            result,
        )
    }

    @Test
    fun `not failed reason when inner succeeds`() {
        val inner = Condition.Has("sword")
        val result = Evaluator.evaluateWithReason(
            Condition.Not(inner),
            state(flags = setOf("sword")),
        )
        assertEquals(
            EvaluationResult.Failure(Reason.NotFailed(inner)),
            result,
        )
    }

    @Test
    fun `not succeeds when inner fails`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Not(Condition.Has("sword")),
            state(),
        )
        assertEquals(EvaluationResult.Success, result)
    }

    @Test
    fun `and failed on left reports left reason`() {
        val result = Evaluator.evaluateWithReason(
            Condition.And(Condition.Has("a"), Condition.Has("b")),
            state(),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.AndFailed(Reason.HasFailed("a")),
            ),
            result,
        )
    }

    @Test
    fun `and failed on right reports right reason`() {
        val result = Evaluator.evaluateWithReason(
            Condition.And(Condition.Has("a"), Condition.Has("b")),
            state(flags = setOf("a")),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.AndFailed(Reason.HasFailed("b")),
            ),
            result,
        )
    }

    @Test
    fun `and short circuits on false left`() {
        // Left fails, right not evaluated. Reason is left's, not
        // OrFailed or anything from right.
        val result = Evaluator.evaluateWithReason(
            Condition.And(
                Condition.Has("a"),
                Condition.Compare("health", CompareOp.GE, 50),
            ),
            state(stats = mapOf("health" to 0)),
        )
        // Only "a" missing is reported; health=0 comparison not attempted.
        assertEquals(
            EvaluationResult.Failure(
                Reason.AndFailed(Reason.HasFailed("a")),
            ),
            result,
        )
    }

    @Test
    fun `or failed both sides reports both reasons`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Or(Condition.Has("a"), Condition.Has("b")),
            state(),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.OrFailed(
                    leftReason = Reason.HasFailed("a"),
                    rightReason = Reason.HasFailed("b"),
                )
            ),
            result,
        )
    }

    @Test
    fun `or short circuits on true left`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Or(Condition.Has("a"), Condition.Has("b")),
            state(flags = setOf("a")),
        )
        assertEquals(EvaluationResult.Success, result)
    }

    @Test
    fun `or short circuits on true right`() {
        val result = Evaluator.evaluateWithReason(
            Condition.Or(Condition.Has("a"), Condition.Has("b")),
            state(flags = setOf("b")),
        )
        assertEquals(EvaluationResult.Success, result)
    }

    @Test
    fun `nested and inside or reason is or of left-and and right`() {
        // `(a && b) || c` with `a=false, b=false, c=false`:
        // Left (And) fails with AndFailed(HasFailed("a"));
        // Right (c) fails with HasFailed("c");
        // Whole thing: OrFailed(AndFailed(HasFailed("a")), HasFailed("c")).
        val result = Evaluator.evaluateWithReason(
            Condition.Or(
                Condition.And(Condition.Has("a"), Condition.Has("b")),
                Condition.Has("c"),
            ),
            state(),
        )
        assertEquals(
            EvaluationResult.Failure(
                Reason.OrFailed(
                    leftReason = Reason.AndFailed(Reason.HasFailed("a")),
                    rightReason = Reason.HasFailed("c"),
                )
            ),
            result,
        )
    }

    @Test
    fun `double not on true inner succeeds`() {
        // !!true = true
        val result = Evaluator.evaluateWithReason(
            Condition.Not(Condition.Not(Condition.Has("a"))),
            state(flags = setOf("a")),
        )
        assertEquals(EvaluationResult.Success, result)
    }

    @Test
    fun `double not on false inner fails with nested not reason`() {
        // !!false = false; reason structure:
        // Not(Not(Has("a"))) where a=false:
        //   inner=Has("a") → Failure(HasFailed("a"))
        //   Not(inner) → Success
        //   Not(Not(inner)) → Failure(NotFailed(Not(Has("a"))))
        val innerNot = Condition.Not(Condition.Has("a"))
        val result = Evaluator.evaluateWithReason(
            Condition.Not(innerNot),
            state(),
        )
        assertEquals(
            EvaluationResult.Failure(Reason.NotFailed(innerNot)),
            result,
        )
    }

    @Test
    fun `failure reason is well typed for when - impossible state is rejected by construction`() {
        // Sanity check: `EvaluationResult.Success` cannot carry a reason.
        // The assertion is that the compiler accepts this line — if
        // someone changes `Success` to `data class Success(reason:
        // Reason?)`, this test would still compile but the semantic
        // invariant would be lost. Keeping it as a **compile-time** test
        // by usage: constructing `Success` with an argument is a syntax
        // error, so the only valid form is `EvaluationResult.Success`.
        val success: EvaluationResult = EvaluationResult.Success
        assertIs<EvaluationResult.Success>(success)
    }
}