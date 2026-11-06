package com.myapp.adventure.engine.parse

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConditionParserTest {

    private fun success(input: String): Condition {
        val result = ConditionParser.parse(input)
        val s = assertIs<ParseResult.Success<Condition>>(result, "expected success for '$input'")
        return s.value
    }

    private fun failure(input: String): List<ParseError> {
        val result = ConditionParser.parse(input)
        val f = assertIs<ParseResult.Failure>(result, "expected failure for '$input'")
        assertTrue(f.errors.isNotEmpty(), "failure must contain at least one error")
        return f.errors
    }

    @Test
    fun `has flag`() {
        assertEquals(Condition.Has("sword"), success("has_sword"))
    }

    @Test
    fun `has flag with spaces`() {
        assertEquals(Condition.Has("sword"), success("  has_sword  "))
        assertEquals(Condition.Has("my flag"), success("has_my flag"))
    }

    @Test
    fun `negated has flag`() {
        assertEquals(
            Condition.Not(Condition.Has("sword")),
            success("!has_sword"),
        )
    }

    @Test
    fun `all comparison operators`() {
        assertEquals(Condition.Compare("health", CompareOp.GE, 50), success("health >= 50"))
        assertEquals(Condition.Compare("health", CompareOp.GT, 50), success("health > 50"))
        assertEquals(Condition.Compare("health", CompareOp.LE, 50), success("health <= 50"))
        assertEquals(Condition.Compare("health", CompareOp.LT, 50), success("health < 50"))
        assertEquals(Condition.Compare("health", CompareOp.EQ, 50), success("health == 50"))
        assertEquals(Condition.Compare("health", CompareOp.NE, 50), success("health != 50"))
    }

    @Test
    fun `negative integer`() {
        assertEquals(Condition.Compare("health", CompareOp.GE, -10), success("health >= -10"))
    }

    @Test
    fun `comparison with variable name containing spaces`() {
        assertEquals(
            Condition.Compare("my stat", CompareOp.GT, 0),
            success("my stat > 0"),
        )
    }

    @Test
    fun `empty string fails`() {
        failure("")
        failure("   ")
    }

    @Test
    fun `non-integer value fails`() {
        val errors = failure("health >= abc")
        assertIs<ParseError.ConditionError>(errors.single())
    }

    @Test
    fun `unknown syntax fails`() {
        failure("health")   // no operator
        failure("&&")       // no operand
        failure("has_")     // missing name
        failure(">= 50")    // missing var
        failure("health >=") // missing value
    }

    @Test
    fun `and-or not supported in F1_3`() {
        // Planned for Ф3.2 / N7.base. Today this must fail cleanly, not silently.
        val errors = failure("health >= 50 && stamina > 20")
        assertIs<ParseError.ConditionError>(errors.single())
    }

    @Test
    fun `reserved char in flag name is rejected`() {
        // Regression: before this fix, `has_sword && health` silently parsed
        // as Condition.Has("sword && health") — a flag that never exists.
        // Silent pass hides author errors; must fail loudly instead.
        val errors1 = failure("has_sword && health")
        assertIs<ParseError.ConditionError>(errors1.single())
        assertTrue(errors1.single().message.contains("reserved character"))

        val errors2 = failure("!has_sword || health")
        assertIs<ParseError.ConditionError>(errors2.single())
        assertTrue(errors2.single().message.contains("reserved character"))
    }

    @Test
    fun `reserved char in comparison var name is rejected`() {
        // `!health > 5` would otherwise parse as Compare("!health", GT, 5) —
        // silent acceptance of unsupported general negation.
        val errors = failure("!health > 5")
        assertIs<ParseError.ConditionError>(errors.single())
        assertTrue(errors.single().message.contains("reserved character"))
    }
}