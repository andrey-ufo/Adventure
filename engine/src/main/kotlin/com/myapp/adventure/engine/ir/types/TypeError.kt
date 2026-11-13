package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.SceneId

/**
 * One type error found by [TypeChecker].
 *
 * **What this is.** A `TypeError` means the author wrote something the
 * type checker can prove wrong against the declared variables:
 * - a reference to a name that is not declared ([UnknownVariable]);
 * - an operation whose operand type does not match ([HasOnNonBool],
 *   [ModifyStatOnNonNumeric], …);
 * - an initial value that contradicts the declared type
 *   ([InitialFlagOnNonBool], [InitialStatOnNonNumeric]).
 *
 * Unlike [com.myapp.adventure.engine.analyze.LintIssue] (`WARNING`), a
 * `TypeError` is meant to be **blocking** for the reader: it is the same
 * class of "the file would misbehave at runtime" as
 * [com.myapp.adventure.engine.CompileError]. The reader must not open a
 * file with type errors. Ф2.3 wires this in (`parse → typecheck →
 * typed IR`); Ф2.2 only defines the check itself.
 *
 * **Rendering.** [message] is a fully-formed English string ready to
 * show to the author. [sceneId] is the navigation hook: clicking the
 * error in the editor (Ф9.2) jumps to this scene. `null` for
 * `InitialState` errors, which do not belong to a scene.
 *
 * **Why sealed class, not enum.** Like [com.myapp.adventure.engine.analyze.LintKind],
 * future checks need per-variant data (`name`, `actualType`, …) that an
 * enum cannot carry. A sealed class grows without breaking exhaustive
 * `when` in callers that want to react per variant.
 *
 * **Ordering.** [TypeChecker.check] returns errors sorted by
 * `(sceneId == null first, sceneId, message)` — the same stable ordering
 * used by `Analyzer.lint`, so that snapshot tests of checker output stay
 * green.
 *
 * **Not covered here** (deferred):
 * - `IntRange(min > max)` — Ф3.6 (`Analyzer.deadConditions` territory).
 * - `Compare` with a literal outside the declared range
 *   (`skill > 20` on `IntRange(1, 12)`) — Ф3.6.
 * - Unknown variables in `Condition.Has` inside a `Not` / `And` / `Or`
 *   — handled by recursion, not by a separate variant.
 */
public sealed class TypeError {

    /** Human-readable, English, one sentence. */
    public abstract val message: String

    /**
     * Scene where the error was found. `null` for `InitialState` errors
     * ([InitialFlagOnNonBool], [InitialStatOnNonNumeric]).
     */
    public abstract val sceneId: SceneId?

    /**
     * A name is referenced from a condition or an effect, but it is not
     * declared in [com.myapp.adventure.engine.ir.Adventure.variables]
     * (in strict mode) or in `tags` + `initialState` (in fallback mode,
     * see [TypeChecker] KDoc).
     *
     * Almost always a typo (`has_swrod` for `has_sword`), or a variable
     * the author removed from one place and forgot to remove from
     * another.
     */
    public data class UnknownVariable(
        public val name: String,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Variable '$name' is used but not declared" +
                    (sceneId?.let { " (scene '${it.value}')" } ?: "") +
                    "."
    }

    /**
     * `has_X` is used on a variable whose declared type is not [VarType.Bool].
     *
     * Common mistake: `has_sword` when `sword` is `ItemCount`. The
     * semantic intent is "the player has at least one sword", which in the
     * IR is `sword > 0` ([com.myapp.adventure.engine.ir.Condition.Compare]),
     * not `has_sword`.
     */
    public data class HasOnNonBool(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Condition 'has_$name' requires '$name' to be Bool, " +
                    "but it is ${actualType.displayName()}. " +
                    "For numeric variables use '$name > 0' instead."
    }

    /**
     * `set_flag` is used on a variable whose declared type is not [VarType.Bool].
     *
     * `set_flag` is for boolean markers; a numeric stat is changed with
     * `modify_stat`.
     */
    public data class SetFlagOnNonBool(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Effect 'set_flag' on '$name' requires Bool, " +
                    "but '$name' is ${actualType.displayName()}. " +
                    "Use 'modify_stat' for numeric variables."
    }

    /**
     * `remove_flag` is used on a variable whose declared type is not
     * [VarType.Bool]. Symmetric to [SetFlagOnNonBool].
     */
    public data class RemoveFlagOnNonBool(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Effect 'remove_flag' on '$name' requires Bool, " +
                    "but '$name' is ${actualType.displayName()}. " +
                    "Use 'modify_stat' with a negative delta for numeric variables."
    }

    /**
     * A numeric effect (`modify_stat` or `set`) is applied to a variable
     * whose declared type is [VarType.Bool] or [VarType.ItemList] — both
     * non-numeric.
     *
     * Both `ModifyStat` and `Set` (Ф3.1) share this variant: from the
     * type checker's point of view, the distinction between "add delta"
     * and "set absolute value" is irrelevant — the operation requires a
     * number, and the variable is not a number. Merging them keeps the
     * `TypeError` surface small; the [message] mentions `modify_stat`
     * because that is the only form the parser produces today.
     */
    public data class ModifyStatOnNonNumeric(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Effect 'modify_stat' on '$name' requires a numeric type, " +
                    "but '$name' is ${actualType.displayName()}. " +
                    "Use 'set_flag' / 'remove_flag' for Bool variables."
    }

    /**
     * A [com.myapp.adventure.engine.ir.Condition.Compare] uses a non-numeric
     * variable. `has_X` is the correct form for [VarType.Bool].
     */
    public data class CompareOnNonNumeric(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "Comparison on '$name' requires a numeric type, " +
                    "but '$name' is ${actualType.displayName()}. " +
                    "For Bool variables use 'has_$name'."
    }

    /**
     * A [com.myapp.adventure.engine.ir.Condition.InRange] uses a non-numeric
     * variable. Ranges only make sense for numbers.
     */
    public data class InRangeOnNonNumeric(
        public val name: String,
        public val actualType: VarType,
        override val sceneId: SceneId?,
    ) : TypeError() {
        override val message: String =
            "'$name in a..b' requires a numeric type, " +
                    "but '$name' is ${actualType.displayName()}."
    }

    /**
     * A variable in [com.myapp.adventure.engine.ir.InitialState.flags] has a
     * declared type other than [VarType.Bool].
     *
     * `initial_state.flags` is for boolean markers; numeric starting
     * values belong in `initial_state.stats`.
     */
    public data class InitialFlagOnNonBool(
        public val name: String,
        public val actualType: VarType,
    ) : TypeError() {
        override val sceneId: SceneId? = null
        override val message: String =
            "'$name' is listed in initial_state.flags, which requires Bool, " +
                    "but it is ${actualType.displayName()}. " +
                    "Move it to initial_state.stats."
    }

    /**
     * A variable in [com.myapp.adventure.engine.ir.InitialState.stats] has a
     * declared type that is not numeric ([VarType.Bool] or
     * [VarType.ItemList]).
     */
    public data class InitialStatOnNonNumeric(
        public val name: String,
        public val actualType: VarType,
    ) : TypeError() {
        override val sceneId: SceneId? = null
        override val message: String =
            "'$name' is listed in initial_state.stats, which requires a numeric type, " +
                    "but it is ${actualType.displayName()}. " +
                    "Move it to initial_state.flags."
    }
}

/**
 * Human-readable representation of a [VarType] for error messages.
 *
 * `data class` / `data object` already provide a `toString()`, but its
 * output is Kotlin-syntax (`IntRange(min=0, max=100)`), which reads poorly
 * in a message shown to an author. `IntRange(0..100)` is closer to the
 * authoring-level notation.
 */
internal fun VarType.displayName(): String = when (this) {
    is VarType.Bool -> "Bool"
    is VarType.Integer -> "Integer"
    is VarType.IntMin -> "IntMin($min)"
    is VarType.IntRange -> "IntRange($min..$max)"
    is VarType.ItemCount -> "ItemCount"
    is VarType.ItemList -> "ItemList"
}