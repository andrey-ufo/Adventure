package com.myapp.adventure.engine.parse

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition

/**
 * Parses a condition string into a [Condition] AST node.
 *
 * **Syntactic only.** This parser knows nothing about variable types or
 * declared variables — that is the type checker's job (Ф2.2). It accepts
 * `has_sword` and produces [Condition.Has] even if `sword` is declared as
 * `ItemCount`; Ф2.2 rejects that with a hint to use `sword > 0` instead.
 *
 * **Grammar supported in Ф1.3:**
 *
 * | Input              | Result                                |
 * |--------------------|---------------------------------------|
 * | `has_X`            | `Condition.Has("X")`                  |
 * | `!has_X`           | `Condition.Not(Has("X"))`             |
 * | `X >= N`           | `Condition.Compare("X", GE, N)`       |
 * | `X > N`            | `Condition.Compare("X", GT, N)`       |
 * | `X <= N`           | `Condition.Compare("X", LE, N)`       |
 * | `X < N`            | `Condition.Compare("X", LT, N)`       |
 * | `X == N`           | `Condition.Compare("X", EQ, N)`       |
 * | `X != N`           | `Condition.Compare("X", NE, N)`       |
 *
 * Whitespace around `X`, the operator, and `N` is ignored. `N` may be
 * negative (`-10`). `X` may contain spaces (`"my stat >= 0"` is valid) —
 * this matches the current reader behaviour where
 * `condition.removePrefix("has_").trim()` keeps interior spaces.
 *
 * **Not supported yet (planned for Ф3.2 / N7.base):** `&&`, `||`, `!X`
 * (general negation), parentheses, `X in a..b`. Passing `"a && b"` today
 * returns a [ParseError.ConditionError]; it will parse in Ф3.2.
 */
public object ConditionParser {

    /**
     * Parses [input] as a single condition.
     *
     * @return [ParseResult.Success] with the AST node, or
     *   [ParseResult.Failure] with exactly one [ParseError.ConditionError].
     */
    /**
     * Characters reserved for future operators (`&&`, `||`, `!`, comparisons,
     * parentheses — planned for Ф3.2 / N7.base). A flag name must not contain
     * them: `has_sword && health` is not "a flag named 'sword && health'",
     * it is unsupported syntax. Accepting it silently would hide the error.
     */
    private val RESERVED_CHARS: Set<Char> = setOf('&', '|', '!', '<', '>', '=', '(', ')')

    public fun parse(input: String): ParseResult<Condition> {
        val s = input.trim()
        if (s.isEmpty()) {
            return fail("Empty condition string", input, 0)
        }

        // 1. `!has_X` — negation of a boolean flag
        if (s.startsWith("!has_")) {
            val name = s.removePrefix("!has_").trim()
            if (name.isEmpty()) {
                return fail("Missing variable name after '!has_'", input, 0)
            }
            checkNoReservedChars(name, input, "Flag name")?.let { return it }
            return ParseResult.Success(Condition.Not(Condition.Has(name)))
        }

        // 2. `has_X` — boolean flag
        if (s.startsWith("has_")) {
            val name = s.removePrefix("has_").trim()
            if (name.isEmpty()) {
                return fail("Missing variable name after 'has_'", input, 0)
            }
            checkNoReservedChars(name, input, "Flag name")?.let { return it }
            return ParseResult.Success(Condition.Has(name))
        }

        // 3. Comparison. Order matters: check two-character operators first,
        // so that `>=` is not consumed as `>` with `= ...`.
        val twoCharOps = listOf(
            ">=" to CompareOp.GE,
            "<=" to CompareOp.LE,
            "==" to CompareOp.EQ,
            "!=" to CompareOp.NE,
        )
        for ((token, op) in twoCharOps) {
            val idx = s.indexOf(token)
            if (idx >= 0) return buildCompare(s, token, op, input, idx)
        }

        val oneCharOps = listOf(
            ">" to CompareOp.GT,
            "<" to CompareOp.LT,
        )
        for ((token, op) in oneCharOps) {
            val idx = s.indexOf(token)
            if (idx >= 0) return buildCompare(s, token, op, input, idx)
        }

        return fail(
            "Unrecognized condition syntax: '$s'. " +
                    "Expected 'has_X', '!has_X', or 'X <op> N' where <op> is one of " +
                    ">=, >, <=, <, ==, !=.",
            input,
            0,
        )
    }

    private fun buildCompare(
        s: String,
        token: String,
        op: CompareOp,
        input: String,
        idx: Int,
    ): ParseResult<Condition> {
        val varName = s.substring(0, idx).trim()
        val valuePart = s.substring(idx + token.length).trim()

        if (varName.isEmpty()) {
            return fail("Missing variable name before '$token'", input, idx)
        }
        checkNoReservedChars(varName, input, "Variable name")?.let { return it }
        if (valuePart.isEmpty()) {
            return fail("Missing integer value after '$token'", input, idx + token.length)
        }
        val value = valuePart.toIntOrNull()
            ?: return fail(
                "Expected integer value after '$token', got '$valuePart'",
                input,
                idx + token.length,
            )
        return ParseResult.Success(Condition.Compare(varName, op, value))
    }

    private fun checkNoReservedChars(
        value: String,
        input: String,
        forWhat: String,
    ): ParseResult.Failure? {
        val bad = value.firstOrNull { it in RESERVED_CHARS }
        if (bad != null) {
            return fail(
                "$forWhat '$value' contains reserved character '$bad'. " +
                        "Logical operators (&&, ||, !), comparisons, and parentheses " +
                        "are not supported yet (planned for Ф3.2 / N7.base).",
                input,
                0,
            )
        }
        return null
    }

    private fun fail(message: String, input: String, position: Int): ParseResult.Failure =
        ParseResult.Failure(listOf(ParseError.ConditionError(message, input, position)))
}