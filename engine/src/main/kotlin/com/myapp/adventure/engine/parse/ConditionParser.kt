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
 * **Grammar (Ф3.2 / N7.base):**
 *
 * ```
 * expr    = or
 * or      = and ("||" and)*
 * and     = not ("&&" not)*
 * not     = "!" not | primary
 * primary = "(" expr ")"
 *         | "true"
 *         | "false"
 *         | "has_" IDENT
 *         | IDENT "in" INT ".." INT
 *         | IDENT COMPARE_OP INT
 * ```
 *
 * **Precedence:** `!` (highest) > `&&` > `||` (lowest). `&&` and `||`
 * are **left-associative**: `a && b && c` parses as `And(And(a, b), c)`,
 * same for `||`.
 *
 * **Associativity and round-trip with [ConditionWriter].**
 * [ConditionWriter] produces a canonical minimal-bracket form; this
 * parser accepts it back. `parse(write(c))` is structurally equal to `c`
 * for the variants listed below.
 *
 * **Round-trip status (as of Ф3.2):**
 *
 * | Variant | Round-trips |
 * |---|---|
 * | `Has`, `Not(Has)`, `Compare` (all 6 ops) | ✅ |
 * | `And`, `Or`, `Not(And)`, `Not(Or)`, `Not(Compare)`, `!!`, `InRange`, `Literal` | ✅ |
 * | `VarRef` | ❌ — not produced by the parser, see below |
 *
 * **`VarRef` is deliberately NOT parsed in Ф3.2.** Bare identifiers
 * (e.g. `"health"`, `"sword && key"` where `sword` / `key` are Bool
 * flags) are parsed as `ConditionError`. Reason: accepting a bare name
 * would be ambiguous with `has_X` (an author with a variable named
 * `has_seen` would have no way to reference it — parser would see
 * `Has("seen")`). `VarRef` is planned for a future phase (see roadmap /
 * progress log), along with `TypeError.VarRefOnNonBool` and the
 * `has_`-prefix reservation. Until then: `has_X` is the only way to
 * reference a Bool variable, and that is the canonical syntax authors
 * have been using since Ф1.3.
 *
 * **Reserved characters.** Flag names (`has_X`) and variable names in
 * `X in A..B` / `X op N` must not contain `&`, `|`, `!`, `<`, `>`, `=`,
 * `(`, `)`. These are part of the condition syntax. A name containing
 * one → `ConditionError` with a fix hint. This catches `has_sword &&
 * health` if the author meant `has_sword && has_health`.
 *
 * **Not supported yet (post-Ф3.2):** `VarRef` (bare Bool reference);
 * `Set`/`Add`/`Sub` as effects (those are not conditions — `ConditionParser`
 * is condition-only); `roll(...)` (Ф12, RNG).
 */
public object ConditionParser {

    /**
     * Parses [input] as a single condition.
     *
     * @return [ParseResult.Success] with the AST node, or
     *   [ParseResult.Failure] with exactly one [ParseError.ConditionError].
     */
    public fun parse(input: String): ParseResult<Condition> {
        val s = input.trim()
        if (s.isEmpty()) {
            return fail("Empty condition string", input, 0)
        }
        return parseExpr(s, input)
    }

    // ---------- Grammar ----------

    private fun parseExpr(text: String, original: String): ParseResult<Condition> {
        val orParts = splitTopLevel(text, "||")
        if (orParts.size > 1) {
            val conditions = mutableListOf<Condition>()
            for (part in orParts) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) {
                    return fail("Empty operand of '||' in '$original'", original, 0)
                }
                when (val r = parseAnd(trimmed, original)) {
                    is ParseResult.Success -> conditions.add(r.value)
                    is ParseResult.Failure -> return r
                }
            }
            var result = conditions[0]
            for (i in 1 until conditions.size) {
                result = Condition.Or(result, conditions[i])
            }
            return ParseResult.Success(result)
        }
        return parseAnd(text, original)
    }

    private fun parseAnd(text: String, original: String): ParseResult<Condition> {
        val andParts = splitTopLevel(text, "&&")
        if (andParts.size > 1) {
            val conditions = mutableListOf<Condition>()
            for (part in andParts) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) {
                    return fail("Empty operand of '&&' in '$original'", original, 0)
                }
                when (val r = parseNot(trimmed, original)) {
                    is ParseResult.Success -> conditions.add(r.value)
                    is ParseResult.Failure -> return r
                }
            }
            var result = conditions[0]
            for (i in 1 until conditions.size) {
                result = Condition.And(result, conditions[i])
            }
            return ParseResult.Success(result)
        }
        return parseNot(text, original)
    }

    private fun parseNot(text: String, original: String): ParseResult<Condition> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return fail("Empty condition in '$original'", original, 0)
        }
        if (trimmed.startsWith("!") && !trimmed.startsWith("!=")) {
            val inner = trimmed.substring(1).trim()
            if (inner.isEmpty()) {
                return fail("Missing operand after '!' in '$original'", original, 0)
            }
            return when (val r = parseNot(inner, original)) {
                is ParseResult.Success -> ParseResult.Success(Condition.Not(r.value))
                is ParseResult.Failure -> r
            }
        }
        return parsePrimary(trimmed, original)
    }

    private fun parsePrimary(text: String, original: String): ParseResult<Condition> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return fail("Empty condition in '$original'", original, 0)
        }

        // Parentheses
        if (trimmed.startsWith("(")) {
            if (!trimmed.endsWith(")")) {
                return fail("Unclosed parenthesis in '$original'", original, 0)
            }
            if (!isMatchingParens(trimmed)) {
                return fail(
                    "Parentheses in '$trimmed' do not form a single group. " +
                            "If you meant to join multiple conditions, use '&&' or '||'.",
                    original,
                    0,
                )
            }
            return parseExpr(trimmed.substring(1, trimmed.length - 1), original)
        }

        // Literal
        if (trimmed == "true") return ParseResult.Success(Condition.Literal(true))
        if (trimmed == "false") return ParseResult.Success(Condition.Literal(false))

        // has_X
        if (trimmed.startsWith("has_")) {
            val name = trimmed.removePrefix("has_").trim()
            if (name.isEmpty()) {
                return fail("Missing variable name after 'has_' in '$original'", original, 0)
            }
            checkNoReservedChars(name, original, "Flag name")?.let { return it }
            return ParseResult.Success(Condition.Has(name))
        }

        // InRange: X in A..B
        val inRangeMatch = IN_RANGE_REGEX.find(trimmed)
        if (inRangeMatch != null) {
            val varName = inRangeMatch.groupValues[1].trim()
            if (varName.isEmpty()) {
                return fail("Missing variable name before 'in' in '$original'", original, 0)
            }
            checkNoReservedChars(varName, original, "Variable name")?.let { return it }
            val minStr = inRangeMatch.groupValues[2]
            val maxStr = inRangeMatch.groupValues[3]
            val min = minStr.toIntOrNull()
                ?: return fail("Expected integer after 'in', got '$minStr' in '$original'", original, 0)
            val max = maxStr.toIntOrNull()
                ?: return fail("Expected integer after '..', got '$maxStr' in '$original'", original, 0)
            return ParseResult.Success(Condition.InRange(varName, min, max))
        }

        // Compare: X <op> N. Two-char ops first so `>=` is not consumed as `>` + `=`.
        val compareOps = listOf(
            ">=" to CompareOp.GE,
            "<=" to CompareOp.LE,
            "==" to CompareOp.EQ,
            "!=" to CompareOp.NE,
            ">" to CompareOp.GT,
            "<" to CompareOp.LT,
        )
        for ((opStr, op) in compareOps) {
            val idx = trimmed.indexOf(opStr)
            if (idx <= 0) continue
            val varName = trimmed.substring(0, idx).trim()
            val valueStr = trimmed.substring(idx + opStr.length).trim()
            val value = valueStr.toIntOrNull() ?: continue
            if (varName.isEmpty()) continue
            checkNoReservedChars(varName, original, "Variable name")?.let { return it }
            return ParseResult.Success(Condition.Compare(varName, op, value))
        }

        return fail(
            "Unrecognized condition syntax: '$trimmed'. " +
                    "Expected one of: 'has_X', '!has_X', 'X <op> N', " +
                    "'X in A..B', 'true', 'false', a parenthesized expression, " +
                    "or '&&' / '||' joining any two of the above.",
            original,
            0,
        )
    }

    // ---------- Helpers ----------

    private val IN_RANGE_REGEX = Regex("""^(.+?)\s+in\s+(-?\d+)\s*\.\.\s*(-?\d+)$""")

    /**
     * Splits [input] by [op] at the top parenthesis level. `||` / `&&`
     * inside `(...)` do not split the outer expression:
     * `(a || b) && c` splits only by `&&`.
     */
    private fun splitTopLevel(input: String, op: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < input.length) {
            when (input[i]) {
                '(' -> { depth++; i++ }
                ')' -> { depth--; i++ }
                else -> {
                    if (depth == 0 && input.startsWith(op, i)) {
                        parts.add(input.substring(start, i))
                        i += op.length
                        start = i
                    } else {
                        i++
                    }
                }
            }
        }
        parts.add(input.substring(start))
        return parts
    }

    /**
     * True iff [s] starts with `(`, ends with `)`, and the opening `(`
     * matches the final `)`. `"(a) && (b)"` returns false — the first
     * `(` matches the `)` at index 2, not the last `)`.
     */
    private fun isMatchingParens(s: String): Boolean {
        if (!s.startsWith("(") || !s.endsWith(")")) return false
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && i < s.length - 1) return false
                }
            }
        }
        return depth == 0
    }

    private val RESERVED_CHARS: Set<Char> = setOf('&', '|', '!', '<', '>', '=', '(', ')')

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
                        "are part of the condition syntax, not the variable name.",
                input,
                0,
            )
        }
        return null
    }

    private fun fail(message: String, input: String, position: Int): ParseResult.Failure =
        ParseResult.Failure(listOf(ParseError.ConditionError(message, input, position)))
}