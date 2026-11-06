package com.myapp.adventure.engine.ir

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
 * **Not included yet** (deferred to Ф3.2 / N7.base): `VarRef` as a
 * standalone node for arithmetic like `health + stamina > 50`. Add it
 * as a new subclass when needed — existing subclasses do not change.
 */
@Serializable
public sealed class Condition {

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