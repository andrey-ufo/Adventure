package com.myapp.adventure.engine.parse

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition

/**
 * Writes a [Condition] AST back into a string in the format accepted by
 * [ConditionParser].
 *
 * **Symmetry with [ConditionParser].** The two objects form a round-trip:
 * for a condition that the parser can produce today, `parse(write(c)) ==
 * c` (structurally). This is the contract that makes the editor (Ф9.2)
 * able to represent a condition in JSON: the editor holds a
 * `Condition` in memory, and when the author saves, [write] serializes
 * it back into the `"condition"` field. If the two sides did not
 * round-trip, saving a file would silently change the meaning of a
 * condition — the worst possible failure mode.
 *
 * **What round-trips today.** Only the variants the parser produces:
 * - [Condition.Has]
 * - [Condition.Not]`(`[Condition.Has]`)`
 * - [Condition.Compare] (all 6 [CompareOp] values)
 *
 * **What does NOT round-trip today** (writer produces a string, parser
 * rejects it):
 * - [Condition.And] → `"a && b"` (parser: `&` is a reserved char).
 * - [Condition.Or] → `"a || b"` (`|` is reserved).
 * - [Condition.Not] where the inner is not `Has` — e.g.
 *   `Not(Compare(...))` → `"!(health >= 50)"` (parser: `!` only
 *   recognized as `!has_`).
 * - `Not(Not(...))` → `"!!has_x"` (parser: `!!` not recognized).
 * - [Condition.Literal] → `"true"` / `"false"` (no literal syntax).
 * - [Condition.VarRef] → bare name, e.g. `"sword"` (parser: a bare
 *   name without an operator is `ConditionError`).
 * - [Condition.InRange] → `"x in 0..10"` (parser: `in` / `..` not
 *   supported yet).
 *
 * These are all resolved by **Ф3.2 (N7.base)**, which extends
 * [ConditionParser] with `&&`, `||`, `!`, parentheses, and `X in a..b`.
 * The writer's output for these variants is already **canonical** — it
 * is the syntax that Ф3.2 will accept. Tests in
 * [ConditionWriterTest] pin both the string output (which is already
 * correct) and the current round-trip failure (which will flip to
 * success when Ф3.2 lands).
 *
 * **Canonical form.** The writer produces the **minimal-bracket** form
 * that respects precedence: `!` (highest) > `&&` > `||` (lowest).
 * Leaves ([Condition.Has], [Condition.Compare], [Condition.InRange],
 * [Condition.Literal], [Condition.VarRef]) are always at the top, no
 * brackets around them.
 *
 * For example:
 * - `And(Has("a"), Or(Has("b"), Has("c")))` → `"has_a && (has_b || has_c)"`.
 * - `Or(And(Has("a"), Has("b")), Has("c"))` → `"has_a && has_b || has_c"`.
 * - `Not(And(Has("a"), Has("b")))` → `"!(has_a && has_b)"`.
 * - `And(And(Has("a"), Has("b")), Has("c"))` → `"has_a && has_b && has_c"`
 *   (associative, no extra brackets).
 *
 * **What is NOT the writer's job.**
 * - **No simplification.** `And(Literal(true), Has("x"))` is written as
 *   `"true && has_x"`, not `"has_x"`. Simplification is a separate
 *   concern (`ConditionSimplifier`, future — Ф3.2 or later).
 * - **No effect writing.** `Effect` AST → string (the `"set_flags"` /
 *   `"modify_stats"` fields) is `IrToJson` (Ф8), not this writer.
 * - **No whitespace preferences.** `"a && b"` (single spaces) is what
 *   the writer produces; the parser accepts any whitespace. The writer
 *   is canonical, the parser is lenient.
 *
 * **Known corner case** (documented, not fixed): a variable whose name
 * starts with `"has_"` cannot be referenced from [Condition.VarRef] or
 * [Condition.Compare] unambiguously — the parser will interpret
 * `"has_x"` as `Has("x")`, not as `VarRef("has_x")` or as the start of a
 * comparison. This is a naming collision in the author's file, not a bug
 * in the writer. Ф3.2 will define the disambiguation rule (probably:
 * reserved prefix, rejected by [com.myapp.adventure.engine.ir.types.TypeChecker]).
 */
public object ConditionWriter {

    /**
     * Serializes [condition] into the condition-string format accepted
     * by [ConditionParser.parse].
     *
     * The output is **canonical**: single spaces around binary operators,
     * no trailing whitespace, minimal brackets (only where precedence
     * requires them).
     */
    public fun write(condition: Condition): String =
        writeWithPrecedence(condition, minPrecedence = PREC_OR)

    // ---------- Precedence ----------

    /** Leaves: no internal boolean operators. */
    private const val PREC_LEAF = 3

    /** Unary prefix `!`. */
    private const val PREC_NOT = 2

    /** `&&`. */
    private const val PREC_AND = 1

    /** `||`. */
    private const val PREC_OR = 0

    private fun precedence(c: Condition): Int = when (c) {
        is Condition.Literal,
        is Condition.VarRef,
        is Condition.Has,
        is Condition.Compare,
        is Condition.InRange,
            -> PREC_LEAF
        is Condition.Not -> PREC_NOT
        is Condition.And -> PREC_AND
        is Condition.Or -> PREC_OR
    }

    // ---------- Core writer ----------

    /**
     * Writes [c] and, if its precedence is **strictly lower** than
     * [minPrecedence], wraps the result in `(…)`.
     *
     * Precedence is a partial order: `!` > `&&` > `||`. A child whose
     * precedence equals the parent's does **not** get brackets — `&&`
     * and `||` are associative, so `a && b && c` is unambiguous
     * regardless of the tree shape.
     */
    private fun writeWithPrecedence(c: Condition, minPrecedence: Int): String {
        val body = when (c) {
            is Condition.Literal -> if (c.value) "true" else "false"
            is Condition.VarRef -> c.name
            is Condition.Has -> "has_${c.name}"
            is Condition.Compare -> "${c.varName} ${opSymbol(c.op)} ${c.value}"
            is Condition.InRange -> "${c.varName} in ${c.min}..${c.max}"
            is Condition.Not -> "!" + writeNotChild(c.inner)
            is Condition.And ->
                writeWithPrecedence(c.left, PREC_AND) + " && " +
                        writeWithPrecedence(c.right, PREC_AND)
            is Condition.Or ->
                writeWithPrecedence(c.left, PREC_OR) + " || " +
                        writeWithPrecedence(c.right, PREC_OR)
        }
        return if (precedence(c) < minPrecedence) "($body)" else body
    }

    /**
     * Writes the child of a `!` prefix.
     *
     * Precedence alone is **not** enough for this position. `!` is a
     * prefix operator, and `!x > 5` reads as `(!x) > 5` in most
     * languages, not `!(x > 5)`. Bracket decisions are therefore made
     * per-variant, not by precedence:
     *
     * - `Has`, `VarRef`, `Literal`, `Not` → **no brackets**
     *   (`!has_x`, `!x`, `!true`, `!!y` — all unambiguous).
     * - `Compare`, `InRange`, `And`, `Or` → **brackets**
     *   (`!(x >= 5)`, `!(x in 0..10)`, `!(a && b)`, `!(a || b)` — a
     *   bare `!` would bind to the first token of the child, not the
     *   whole expression).
     *
     * `Not` recurses here rather than via `writeWithPrecedence` so that
     * `Not(Not(Has("a")))` produces `"!!has_a"` (no parenthesised
     * `"!(!has_a)"`), which is the canonical form the parser will
     * accept once Ф3.2 supports `!!`.
     */
    private fun writeNotChild(c: Condition): String = when (c) {
        is Condition.Has -> "has_${c.name}"
        is Condition.VarRef -> c.name
        is Condition.Literal -> if (c.value) "true" else "false"
        is Condition.Not -> "!" + writeNotChild(c.inner)
        is Condition.Compare, is Condition.InRange, is Condition.And, is Condition.Or ->
            "(" + writeWithPrecedence(c, PREC_OR) + ")"
    }

    // ---------- Operator symbols ----------

    private fun opSymbol(op: CompareOp): String = when (op) {
        CompareOp.LT -> "<"
        CompareOp.LE -> "<="
        CompareOp.GT -> ">"
        CompareOp.GE -> ">="
        CompareOp.EQ -> "=="
        CompareOp.NE -> "!="
    }
}