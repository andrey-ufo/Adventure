package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Condition

/**
 * Result of [Evaluator.evaluateWithReason]: either the condition
 * holds ([Success]) or it does not, with a structured [Reason].
 *
 * **Why not just `Boolean` + `Reason?`.** A `Pair<Boolean, Reason?>`
 * allows the illegal state `(true, Some(reason))` — a "succeeded but
 * with a failure reason", which is meaningless. The sealed type makes
 * the invariant structural: `Success` carries no reason, `Failure`
 * carries exactly one.
 *
 * **Why `Reason` is a separate sealed hierarchy, not a string.** The
 * UI (N4 / Ф4) will format "requires `sword`", "need 300 more steps",
 * "does not have `sword`" in the user's locale. Formatting is a `:app`
 * concern (`strings.xml`, Ф7), not an `:engine` concern
 * (`documentation-rules.md §1`). [Reason] carries the structured data
 * (`name`, `actualValue`, `expectedValue`); the UI decides how to
 * phrase it.
 *
 * **Why `EvaluationResult` and not [StepResult].** `StepResult` (Ф1.5)
 * is the outcome of a **transition attempt** — moving to a different
 * scene. `EvaluationResult` is the outcome of **evaluating one
 * condition** — a pure function of `(Condition, PlayerState)`. A
 * `StepResult.Rejected(Blocked(...))` can contain several
 * `Blocker.ConditionNotMet(condition)`, one per failed condition;
 * `EvaluationResult` is what produces the [Reason] inside that
 * `Blocker`. Keeping the two types separate means `Engine.step`
 * composes reasons, and `Evaluator` produces them, without either
 * knowing the other's shape.
 */
public sealed class EvaluationResult {

    /**
     * The condition holds. No reason to carry — the condition is
     * satisfied, the UI shows the choice as enabled.
     */
    public data object Success : EvaluationResult()

    /**
     * The condition does not hold. See [reason] for the (structured,
     * minimal) cause. **"Minimal"** is per the short-circuit rules in
     * [Reason.AndFailed] / [Reason.OrFailed]: for `A && B` where `A`
     * is false, only `A`'s reason is reported (the semantics of `&&`
     * is that `B` is not evaluated); for `A || B` where both are
     * false, both reasons are reported (neither short-circuited).
     */
    public data class Failure(public val reason: Reason) : EvaluationResult()
}

/**
 * Why a [Condition] failed to hold against a [PlayerState].
 *
 * **One variant per [Condition] variant**, plus the boolean-operators
 * recursively. This makes the `when` in `:app` (N4 / Ф4 reason
 * formatting) exhaustive over the AST shape: adding a new [Condition]
 * variant in `:engine` breaks `:app` compilation until someone teaches
 * the UI to render its reason. That is the same "the compiler makes
 * you look at it" pattern used for [com.myapp.adventure.engine.ir.types.TypeError]
 * and [com.myapp.adventure.engine.analyze.LintKind].
 *
 * **No formatted strings.** `Reason` carries data (`name`, `op`,
 * `actualValue`, ...). Formatting is a UI concern (i18n, Ф7); `:engine`
 * never builds a user-facing sentence. This is the same separation as
 * [TypeError] (structured) vs. its `message` (English, shown in the
 * editor only).
 */
public sealed class Reason {

    /**
     * `has_X` where `X` is not in `state.flags`. The player does not
     * have flag `X`.
     *
     * UI: "requires `X`" or "you do not have `X`".
     */
    public data class HasFailed(public val name: String) : Reason()

    /**
     * A bare [Condition.VarRef] where the referenced Bool variable is
     * false. Semantically identical to [HasFailed] (both read
     * `state.flags`), but kept as a separate variant so the UI can
     * distinguish "requires `X`" (from `has_X`) from "requires `X`"
     * (from `X`) if the wording ever needs to change. Currently the
     * parser does not produce `VarRef` (see
     * [Condition.VarRef] KDoc); `VarRef` reasons appear only when
     * `Condition` is constructed by hand (tests, tooling).
     */
    public data class VarRefFailed(public val name: String) : Reason()

    /**
     * [Condition.Literal] with `value = false`. Always the same
     * (nothing to report beyond "the literal is false"), but a
     * distinct variant so `when` over [Reason] stays exhaustive when
     * the AST adds more literal-like variants.
     */
    public data object LiteralFailed : Reason()

    /**
     * `X <op> N` where `stats[X] <op> N` is false.
     *
     * `actualValue` is `state.stats[varName] ?: 0` — the value the
     * evaluator actually compared, not "unknown". If the variable is
     * not in `stats`, this is `0`; the type checker (Ф2.2) will have
     * rejected such an adventure in strict mode, but the reason is
     * still meaningful when the file was parsed in fallback mode.
     *
     * UI: "requires `health >= 50`, you have 30" — but the UI decides
     * the wording, not `:engine`.
     */
    public data class CompareFailed(
        public val varName: String,
        public val op: CompareOp,
        public val actualValue: Int,
        public val expectedValue: Int,
    ) : Reason()

    /**
     * `X in A..B` where `stats[X]` is outside `[A, B]`.
     *
     * `actualValue` same convention as [CompareFailed].
     */
    public data class InRangeFailed(
        public val varName: String,
        public val actualValue: Int,
        public val min: Int,
        public val max: Int,
    ) : Reason()

    /**
     * `!inner` where `inner` **succeeded**. `Not(X)` fails iff `X`
     * holds, so there is no failure of `inner` to report — the whole
     * reason is "the inner condition was true". [innerCondition] is
     * kept so the UI can format "!(...)" with the actual condition.
     */
    public data class NotFailed(
        public val innerCondition: Condition,
    ) : Reason()

    /**
     * `A && B` where one side is false. Only the **first failing side**
     * is reported (short-circuit: `B` is not evaluated when `A` is
     * false). The [failedReason] is the inner reason from whichever
     * side failed — UI can format it directly ("requires `sword`"),
     * without knowing whether it was the left or the right side.
     */
    public data class AndFailed(
        public val failedReason: Reason,
    ) : Reason()

    /**
     * `A || B` where **both** sides are false. Both reasons are kept
     * because neither short-circuited — the player cannot pick this
     * choice for two distinct reasons, and the UI may want to list
     * both ("requires `sword` or `magic`" when neither is present).
     */
    public data class OrFailed(
        public val leftReason: Reason,
        public val rightReason: Reason,
    ) : Reason()
}