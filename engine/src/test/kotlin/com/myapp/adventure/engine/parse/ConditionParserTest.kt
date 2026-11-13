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
        val s = assertIs<ParseResult.Success<Condition>>(result, "expected success for '$input', got $result")
        return s.value
    }

    private fun failure(input: String): List<ParseError> {
        val result = ConditionParser.parse(input)
        val f = assertIs<ParseResult.Failure>(result, "expected failure for '$input', got $result")
        assertTrue(f.errors.isNotEmpty(), "failure must contain at least one error")
        return f.errors
    }

    // ---------- Leaves ----------

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

    // ---------- Ф3.2: Literal ----------

    @Test
    fun `literal true`() {
        assertEquals(Condition.Literal(true), success("true"))
    }

    @Test
    fun `literal false`() {
        assertEquals(Condition.Literal(false), success("false"))
    }

    // ---------- Ф3.2: InRange ----------

    @Test
    fun `in range simple`() {
        assertEquals(
            Condition.InRange("health", 0, 100),
            success("health in 0..100"),
        )
    }

    @Test
    fun `in range with negative bounds`() {
        assertEquals(
            Condition.InRange("x", -10, 10),
            success("x in -10..10"),
        )
    }

    @Test
    fun `in range with spaces around dots`() {
        assertEquals(
            Condition.InRange("health", 0, 100),
            success("health in 0 .. 100"),
        )
    }

    // ---------- Ф3.2: And / Or / Not ----------

    @Test
    fun `simple and`() {
        assertEquals(
            Condition.And(Condition.Has("a"), Condition.Has("b")),
            success("has_a && has_b"),
        )
    }

    @Test
    fun `simple or`() {
        assertEquals(
            Condition.Or(Condition.Has("a"), Condition.Has("b")),
            success("has_a || has_b"),
        )
    }

    @Test
    fun `and binds tighter than or`() {
        assertEquals(
            Condition.Or(
                Condition.And(Condition.Has("a"), Condition.Has("b")),
                Condition.Has("c"),
            ),
            success("has_a && has_b || has_c"),
        )
    }

    @Test
    fun `or binds looser than and - alternative grouping`() {
        assertEquals(
            Condition.Or(
                Condition.Has("a"),
                Condition.And(Condition.Has("b"), Condition.Has("c")),
            ),
            success("has_a || has_b && has_c"),
        )
    }

    @Test
    fun `parentheses override precedence`() {
        assertEquals(
            Condition.And(
                Condition.Or(Condition.Has("a"), Condition.Has("b")),
                Condition.Has("c"),
            ),
            success("(has_a || has_b) && has_c"),
        )
    }

    @Test
    fun `double not`() {
        assertEquals(
            Condition.Not(Condition.Not(Condition.Has("a"))),
            success("!!has_a"),
        )
    }

    @Test
    fun `not applies to parenthesized expression`() {
        assertEquals(
            Condition.Not(
                Condition.And(Condition.Has("a"), Condition.Has("b")),
            ),
            success("!(has_a && has_b)"),
        )
    }

    @Test
    fun `not applies to comparison`() {
        assertEquals(
            Condition.Not(Condition.Compare("health", CompareOp.GE, 50)),
            success("!(health >= 50)"),
        )
    }

    @Test
    fun `whitespace is tolerated`() {
        assertEquals(
            Condition.And(Condition.Has("a"), Condition.Has("b")),
            success("  has_a   &&   has_b  "),
        )
    }

    @Test
    fun `associative and on three operands`() {
        assertEquals(
            Condition.And(
                Condition.And(Condition.Has("a"), Condition.Has("b")),
                Condition.Has("c"),
            ),
            success("has_a && has_b && has_c"),
        )
    }

    @Test
    fun `associative or on three operands`() {
        assertEquals(
            Condition.Or(
                Condition.Or(Condition.Has("a"), Condition.Has("b")),
                Condition.Has("c"),
            ),
            success("has_a || has_b || has_c"),
        )
    }

    @Test
    fun `and with compare inside`() {
        assertEquals(
            Condition.And(
                Condition.Compare("health", CompareOp.GT, 50),
                Condition.Compare("stamina", CompareOp.GT, 20),
            ),
            success("health > 50 && stamina > 20"),
        )
    }

    @Test
    fun `and with in range`() {
        assertEquals(
            Condition.And(
                Condition.InRange("health", 0, 100),
                Condition.Has("shield"),
            ),
            success("health in 0..100 && has_shield"),
        )
    }

    @Test
    fun `not before has in and`() {
        assertEquals(
            Condition.And(
                Condition.Not(Condition.Has("sword")),
                Condition.Not(Condition.Has("shield")),
            ),
            success("!has_sword && !has_shield"),
        )
    }

    @Test
    fun `literal true and has`() {
        assertEquals(
            Condition.And(Condition.Literal(true), Condition.Has("a")),
            success("true && has_a"),
        )
    }

    // ---------- Ф3.2: errors ----------

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
    fun `bare name without operator fails`() {
        // `health` — not `has_`, not literal, not paren, not in, not
        // compare. `VarRef` is not produced by the parser (Ф3.2:
        // deliberately deferred, see progress log).
        failure("health")
    }

    @Test
    fun `unclosed parenthesis fails`() {
        failure("(has_a")
    }

    @Test
    fun `empty parentheses fail`() {
        failure("()")
    }

    @Test
    fun `unbalanced parentheses fail`() {
        failure("(has_a) && (has_b")
    }

    @Test
    fun `trailing junk after literal fails`() {
        // `true` / `false` require exact match — no trailing tokens.
        failure("true extra")
        failure("false extra")
    }

    @Test
    fun `has_ allows spaces in flag name - documented behavior`() {
        // The parser intentionally allows `has_my flag` → `Has("my flag")`
        // (see `ConditionParser` KDoc — matches `:app` pre-Ф2 reader
        // behaviour where the author had flag names with spaces).
        // So `has_a extra` is `Has("a extra")`, not a parse error. This
        // test pins the design so a future refactor does not accidentally
        // break author files that rely on multi-word flag names.
        assertEquals(Condition.Has("a extra"), success("has_a extra"))
    }

    @Test
    fun `empty operand of or fails`() {
        failure("has_a || ")
        failure(" || has_a")
    }

    @Test
    fun `empty operand of and fails`() {
        failure("has_a && ")
        failure(" && has_a")
    }

    @Test
    fun `single ampersand is rejected`() {
        // "has_a & has_b" — `&` is not a valid operator (must be `&&`).
        failure("has_a & has_b")
    }

    @Test
    fun `single pipe is rejected`() {
        failure("has_a | has_b")
    }

    @Test
    fun `reserved char in flag name is rejected`() {
        // `has_sword&x` — `&` is reserved in a flag name.
        val errors = failure("has_sword&x")
        assertIs<ParseError.ConditionError>(errors.single())
        assertTrue(errors.single().message.contains("reserved character"))
    }

    @Test
    fun `has_sword and health is rejected - DoD Ф2_4 - now at primary level`() {
        // "has_sword && health" — `&&` splits, left parses as `Has("sword")`,
        // right side "health" is a bare name (VarRef — not supported in
        // Ф3.2) → ConditionError. DoD Ф2.4 "has_sword && health produces
        // an error" is preserved, but the error class has moved from
        // "reserved char in flag name" (old parser) to "unrecognized
        // syntax: health" (bare name, no VarRef support).
        val errors = failure("has_sword && health")
        assertIs<ParseError.ConditionError>(errors.single())
    }

    @Test
    fun `has_sword and has_health parses - DoD Ф2_4 via TypeChecker`() {
        // The corrected form: both sides are `has_`, both parse. Then
        // TypeChecker catches `HasOnNonBool` on `sword` (ItemCount) in
        // strict mode. The "has_sword && health is rejected" DoD case is
        // covered by the test above; this test pins the "well-formed
        // grammar" side.
        assertEquals(
            Condition.And(
                Condition.Has("sword"),
                Condition.Has("health"),
            ),
            success("has_sword && has_health"),
        )
    }

    @Test
    fun `not then compare parses as Not-Compare`() {
        // "!health > 5" — `!` is unary, applies to "health > 5" (Compare).
        // Result: Not(Compare(health, GT, 5)). With `VarRef` not supported,
        // the author cannot express "(bool) > 5" — that would be a type
        // error anyway. The intuitive reading `!(health > 5)` matches
        // this AST.
        assertEquals(
            Condition.Not(Condition.Compare("health", CompareOp.GT, 5)),
            success("!health > 5"),
        )
    }
}