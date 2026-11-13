package com.myapp.adventure.engine.parse

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Unit tests for [ConditionWriter].
 *
 * Three groups:
 * 1. **True round-trip** — for the condition variants the parser
 *    produces today (`Has`, `Not(Has)`, `Compare`), `parse(write(c))`
 *    is structurally equal to `c`. These are the tests that must stay
 *    green at all times — they are the writer/parser contract.
 * 2. **Canonical string output** — for variants that the parser cannot
 *    produce yet (`And`, `Or`, `Literal`, `VarRef`, `InRange`,
 *    `Not(non-Has)`), the writer produces the string that **Ф3.2** will
 *    accept. Only the string is asserted here; round-trip will be
 *    tested when Ф3.2 lands.
 * 3. **Documented non-round-trip** — explicit negative assertions that
 *    the current parser cannot read back the string the writer produces
 *    for the future variants. When Ф3.2 extends the parser, these tests
 *    fail loudly and the author flips them to `parse(write(c)) == c`.
 */
class ConditionWriterTest {

    // ---------- helpers ----------

    private fun roundTrip(condition: Condition): Condition {
        val written = ConditionWriter.write(condition)
        val parsed = ConditionParser.parse(written)
        val success = assertIs<ParseResult.Success<Condition>>(
            parsed,
            "expected '$written' to parse, got $parsed",
        )
        return success.value
    }

    private fun written(condition: Condition): String =
        ConditionWriter.write(condition)

    private fun parseFails(condition: Condition) {
        val written = ConditionWriter.write(condition)
        val parsed = ConditionParser.parse(written)
        assertIs<ParseResult.Failure>(
            parsed,
            "expected '$written' to fail to parse (documented limitation), " +
                    "but it produced ${parsed}",
        )
    }

    // ---------- 1. True round-trip (parser supports today) ----------

    @Test
    fun `has round-trips`() {
        assertEquals(
            Condition.Has("sword"),
            roundTrip(Condition.Has("sword")),
        )
    }

    @Test
    fun `has with spaces round-trips`() {
        assertEquals(
            Condition.Has("my flag"),
            roundTrip(Condition.Has("my flag")),
        )
    }

    @Test
    fun `not has round-trips`() {
        assertEquals(
            Condition.Not(Condition.Has("sword")),
            roundTrip(Condition.Not(Condition.Has("sword"))),
        )
    }

    @Test
    fun `compare round-trips for all six operators`() {
        val ops = listOf(
            CompareOp.LT, CompareOp.LE, CompareOp.GT,
            CompareOp.GE, CompareOp.EQ, CompareOp.NE,
        )
        for (op in ops) {
            val condition = Condition.Compare("health", op, 50)
            assertEquals(
                condition,
                roundTrip(condition),
                "round-trip failed for CompareOp.$op",
            )
        }
    }

    @Test
    fun `compare with negative value round-trips`() {
        assertEquals(
            Condition.Compare("health", CompareOp.GE, -10),
            roundTrip(Condition.Compare("health", CompareOp.GE, -10)),
        )
    }

    @Test
    fun `compare with spaces in variable name round-trips`() {
        assertEquals(
            Condition.Compare("my stat", CompareOp.GT, 0),
            roundTrip(Condition.Compare("my stat", CompareOp.GT, 0)),
        )
    }

    // ---------- 2. Canonical string output (Ф3.2 will make these parse) ----------

    @Test
    fun `literal writes as true or false`() {
        assertEquals("true", written(Condition.Literal(true)))
        assertEquals("false", written(Condition.Literal(false)))
    }

    @Test
    fun `varRef writes as bare name`() {
        assertEquals("sword", written(Condition.VarRef("sword")))
    }

    @Test
    fun `in range writes with range syntax`() {
        assertEquals(
            "health in 0..100",
            written(Condition.InRange("health", 0, 100)),
        )
    }

    @Test
    fun `simple and writes without brackets`() {
        assertEquals(
            "has_a && has_b",
            written(Condition.And(Condition.Has("a"), Condition.Has("b"))),
        )
    }

    @Test
    fun `simple or writes without brackets`() {
        assertEquals(
            "has_a || has_b",
            written(Condition.Or(Condition.Has("a"), Condition.Has("b"))),
        )
    }

    @Test
    fun `and inside or does not need brackets - and binds tighter`() {
        // Or(And(a, b), c) → "a && b || c" (no brackets: && has higher precedence)
        assertEquals(
            "has_a && has_b || has_c",
            written(
                Condition.Or(
                    Condition.And(Condition.Has("a"), Condition.Has("b")),
                    Condition.Has("c"),
                ),
            ),
        )
    }

    @Test
    fun `or inside and needs brackets`() {
        // And(a, Or(b, c)) → "a && (b || c)" (brackets required)
        assertEquals(
            "has_a && (has_b || has_c)",
            written(
                Condition.And(
                    Condition.Has("a"),
                    Condition.Or(Condition.Has("b"), Condition.Has("c")),
                ),
            ),
        )
    }

    @Test
    fun `nested and is associative - no extra brackets`() {
        // And(And(a, b), c) → "a && b && c"
        assertEquals(
            "has_a && has_b && has_c",
            written(
                Condition.And(
                    Condition.And(Condition.Has("a"), Condition.Has("b")),
                    Condition.Has("c"),
                ),
            ),
        )
    }

    @Test
    fun `nested or is associative - no extra brackets`() {
        // Or(Or(a, b), c) → "a || b || c"
        assertEquals(
            "has_a || has_b || has_c",
            written(
                Condition.Or(
                    Condition.Or(Condition.Has("a"), Condition.Has("b")),
                    Condition.Has("c"),
                ),
            ),
        )
    }

    @Test
    fun `not has writes without brackets`() {
        assertEquals(
            "!has_sword",
            written(Condition.Not(Condition.Has("sword"))),
        )
    }

    @Test
    fun `not compare needs brackets`() {
        assertEquals(
            "!(health >= 50)",
            written(Condition.Not(Condition.Compare("health", CompareOp.GE, 50))),
        )
    }

    @Test
    fun `not and needs brackets`() {
        assertEquals(
            "!(has_a && has_b)",
            written(Condition.Not(Condition.And(Condition.Has("a"), Condition.Has("b")))),
        )
    }

    @Test
    fun `not or needs brackets`() {
        assertEquals(
            "!(has_a || has_b)",
            written(Condition.Not(Condition.Or(Condition.Has("a"), Condition.Has("b")))),
        )
    }

    @Test
    fun `complex precedence - mixed and or`() {
        // Or(And(a, b), And(c, d)) → "a && b || c && d"
        assertEquals(
            "has_a && has_b || has_c && has_d",
            written(
                Condition.Or(
                    Condition.And(Condition.Has("a"), Condition.Has("b")),
                    Condition.And(Condition.Has("c"), Condition.Has("d")),
                ),
            ),
        )
    }

    @Test
    fun `complex precedence - not on and inside or`() {
        // Or(Not(And(a, b)), c) → "!(a && b) || c"
        assertEquals(
            "!(has_a && has_b) || has_c",
            written(
                Condition.Or(
                    Condition.Not(
                        Condition.And(Condition.Has("a"), Condition.Has("b")),
                    ),
                    Condition.Has("c"),
                ),
            ),
        )
    }

    // ---------- 3. Documented non-round-trip (Ф3.2 will flip these) ----------

    @Test
    fun `and round trips`() {
        assertEquals(
            Condition.And(Condition.Has("a"), Condition.Has("b")),
            roundTrip(Condition.And(Condition.Has("a"), Condition.Has("b"))),
        )
    }

    @Test
    fun `or round trips`() {
        assertEquals(
            Condition.Or(Condition.Has("a"), Condition.Has("b")),
            roundTrip(Condition.Or(Condition.Has("a"), Condition.Has("b"))),
        )
    }

    @Test
    fun `literal round trips`() {
        assertEquals(Condition.Literal(true), roundTrip(Condition.Literal(true)))
        assertEquals(Condition.Literal(false), roundTrip(Condition.Literal(false)))
    }

    @Test
    fun `varRef does not round trip - parser rejects - until F3_2`() {
        parseFails(Condition.VarRef("sword"))
    }

    @Test
    fun `in range round trips`() {
        assertEquals(
            Condition.InRange("health", 0, 100),
            roundTrip(Condition.InRange("health", 0, 100)),
        )
    }

    @Test
    fun `not compare round trips`() {
        assertEquals(
            Condition.Not(Condition.Compare("health", CompareOp.GE, 50)),
            roundTrip(Condition.Not(Condition.Compare("health", CompareOp.GE, 50))),
        )
    }

    @Test
    fun `double not round trips`() {
        assertEquals(
            Condition.Not(Condition.Not(Condition.Has("a"))),
            roundTrip(Condition.Not(Condition.Not(Condition.Has("a")))),
        )
    }

    // ---------- 4. Known corner case: names starting with `has_` ----------

    @Test
    fun `varRef name starting with has_ is ambiguous - parses as Has`() {
        // Documented in ConditionWriter KDoc: a name starting with
        // "has_" cannot be referenced from VarRef unambiguously. The
        // parser interprets "has_x" as Has("x"), not VarRef("has_x").
        // Not a bug in the writer — a naming collision in the author's
        // file. Ф3.2 will define the disambiguation rule (probably:
        // reserved prefix, rejected by TypeChecker).
        val condition = Condition.VarRef("has_x")
        val text = ConditionWriter.write(condition)
        assertEquals("has_x", text)
        val parsed = ConditionParser.parse(text)
        val success = assertIs<ParseResult.Success<Condition>>(parsed)
        // Different AST:
        assertEquals(Condition.Has("x"), success.value)
        assertNotEquals(condition, success.value)
    }
}