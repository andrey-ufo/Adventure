package com.myapp.adventure.engine.ir

import com.myapp.adventure.engine.ir.types.VarType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AST of a condition that gates a choice, a scene, or an invariant.
 *
 * In `:app/data/Adventure.kt` conditions are plain strings
 * (`"has_sword"`, `"health >= 50"`). In the IR they are a typed tree:
 * the string is parsed once in `JsonToIr` (Ф1.3), and every consumer
 * (`evaluator` in Ф3, `Analyzer` in Ф1.6, future JS runtime in E1)
 * works with this tree instead of re-parsing strings.
 *
 * **Added in Ф3.1:** [Literal] and [VarRef] — both for forward use by
 * Ф3.2 (N7.base, `&&` / `||` / `!`), not yet produced by the parser.
 * The parser (`ConditionParser`) intentionally rejects a bare
 * `"health"` today with `ConditionError` — "not a valid condition".
 * When Ф3.2 extends the grammar to accept `A && B` (where `B` is a
 * bare variable reference), the parser will produce
 * `VarRef("B")`. Until then, these nodes can only appear in hand-built
 * IR (tests, future tooling). The writer (`ConditionWriter`, Ф3.1.2)
 * already covers them; the reader's `Evaluator` does too.
 */
@Serializable
public sealed class Condition {

    /**
     * A boolean literal: `true` or `false`.
     *
     * **Not produced by the parser yet.** There is no JSON syntax for a
     * literal condition today — an author writes an empty
     * `"condition"` field to mean "always true", not `"true"`. This node
     * exists for two future needs:
     *
     * 1. **AST optimizations** in Ф3.2 (`true && X` → `X`,
     *    `false || X` → `X`) — the optimizer produces `Literal` as an
     *    intermediate or final form.
     * 2. **Round-trip completeness** in `ConditionWriter` (Ф3.1.2):
     *    the writer must handle every sealed variant; a missing variant
     *    would make the `when` non-exhaustive.
     *
     * Evaluated by `Evaluator.evaluate` as its [value].
     */
    @Serializable
    @SerialName("literal")
    public data class Literal(
        public val value: Boolean,
    ) : Condition()

    /**
     * A bare reference to a variable: `VarRef("has_sword")`.
     *
     * Semantically reads the same as `Has("has_sword")` for Bool
     * variables — "is this Bool true?". The reason for a separate node
     * is **syntax** in the future `&&` / `||` grammar (Ф3.2): when the
     * author writes `has_sword && health_check_passed`, the second
     * operand is a bare variable reference (`health_check_passed`),
     * not a `has_X` condition. Without `VarRef`, the parser would have
     * to invent a `Has` wrapper, which loses the syntactic distinction
     * and makes the writer emit `has_health_check_passed` — a different
     * string.
     *
     * **Not produced by the parser yet.** A bare `"health"` in JSON is
     * a parse error (`ConditionParser` returns `ConditionError` with
     * "not a valid condition"). This is **intentional** until Ф3.2:
     * accepting it today would silently treat a typo (`"helth"`) as
     * `VarRef("helth")` instead of rejecting the malformed condition.
     *
     * **Evaluator** handles this by reading `state.flags.contains(name)`
     * (same as `Has`). **Type checker** treats it as no-op in Ф3.1 —
     * `VarRef` is not produced by the parser, so there is nothing to
     * check in practice; Ф3.2 will add `VarRefOnNonBool` when the parser
     * starts producing the node.
     */
    @Serializable
    @SerialName("var_ref")
    public data class VarRef(
        public val name: String,
    ) : Condition()

    /**
     * `has_X` — true if boolean flag `X` is set.
     *
     * **Only for [VarType.Bool] variables.** For numeric variables
     * ([VarType.Integer], [VarType.IntRange], [VarType.ItemCount]) use
     * [Compare] instead: `sword > 0` rather than `has_sword`,
     * `health > 0` rather than `has_health`. The type checker (Ф2.2)
     * rejects `Has` on non-boolean variables with a hint to use [Compare].
     *
     * The parser (Ф1.3) does **not** enforce this — it is a pure
     * syntactic transformation `"has_X"` → `Has("X")` and knows nothing
     * about variable types. Type checking is the single responsibility of
     * Ф2.2, which keeps parser and type checker decoupled.
     */
    @Serializable
    @SerialName("has")
    public data class Has(
        public val name: String,
    ) : Condition()

    /** `!X` — logical negation of [inner]. */
    @Serializable
    @SerialName("not")
    public data class Not(
        public val inner: Condition,
    ) : Condition()

    /** `X && Y` — logical conjunction. Short-circuits left to right. */
    @Serializable
    @SerialName("and")
    public data class And(
        public val left: Condition,
        public val right: Condition,
    ) : Condition()

    /** `X || Y` — logical disjunction. Short-circuits left to right. */
    @Serializable
    @SerialName("or")
    public data class Or(
        public val left: Condition,
        public val right: Condition,
    ) : Condition()

    /**
     * `X <op> N` — comparison of variable [varName] against integer [value].
     *
     * `varName` must resolve to [VarType.Integer] or [VarType.IntRange];
     * anything else is a type error (Ф2). [CompareOp] determines the operator.
     */
    @Serializable
    @SerialName("compare")
    public data class Compare(
        @SerialName("var_name")
        public val varName: String,
        public val op: CompareOp,
        public val value: Int,
    ) : Condition()

    /**
     * `X in min..max` — inclusive range check for variable [varName].
     *
     * `varName` must resolve to [VarType.IntRange] (or [VarType.Integer]
     * when `min..max` is inside `IntRange` bounds); checked in Ф2.
     */
    @Serializable
    @SerialName("in_range")
    public data class InRange(
        @SerialName("var_name")
        public val varName: String,
        public val min: Int,
        public val max: Int,
    ) : Condition()
}