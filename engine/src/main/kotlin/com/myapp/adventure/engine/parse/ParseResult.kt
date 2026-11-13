package com.myapp.adventure.engine.parse

import com.myapp.adventure.engine.ir.types.TypeError

/**
 * Result of a parse operation: either a value of type [T] or an error
 * describing why parsing failed.
 *
 * Chosen over Kotlin's stdlib [Result] because:
 * - [Result.Failure] carries a single [Throwable], not a list of errors.
 * - Wrapping [ParseError] in an exception would lose the structured type
 *   (the caller wants to pattern-match on [ParseError.ConditionError] etc.).
 *
 * **Two-stage pipeline (Ф2.3).** Starting with Ф2.3,
 * [com.myapp.adventure.engine.format.JsonToIr.parse] performs **two**
 * checks in sequence:
 * 1. **Parse** (JSON syntax, structural problems, condition syntax) —
 *    produces [ParseError]s.
 * 2. **Type check** ([com.myapp.adventure.engine.ir.types.TypeChecker.check],
 *    Ф2.2) — unknown variables, wrong types — produces
 *    [TypeError]s.
 *
 * The two stages are sequential and mutually exclusive in [Failure]:
 * - if parsing fails, type checking is **not attempted** — a partial IR
 *   has missing conditions and effects (a choice whose condition string
 *   failed to parse has `condition = null`), so type checking it would
 *   produce false negatives at best, and false positives at worst. Result:
 *   `Failure(errors = [...], typeErrors = emptyList())`.
 * - if parsing succeeds, type checking runs on the full IR. Result:
 *   `Success(ir)` when there are no type errors, or
 *   `Failure(errors = emptyList(), typeErrors = [...])` otherwise.
 *
 * **Invariant (enforced in `init`):** [Failure] contains **exactly one**
 * non-empty list — either [Failure.errors] or [Failure.typeErrors].
 * Both empty would be a `Success`; both non-empty would mean the two
 * stages overlapped, which contradicts the sequential design.
 *
 * **Why not a single `List<ParseError>` that includes type errors as a
 * variant.** The two error kinds come from different layers of the
 * pipeline (parser vs type checker) and carry different structured data
 * ([ParseError] has JSON paths and condition fragments; [TypeError] has
 * variable names and types). Merging them into one list would blur the
 * I3 boundary between "the file cannot be parsed" and "the file parses,
 * but its variable references are wrong". Two fields keep the boundary
 * visible in the type system.
 */
public sealed class ParseResult<out T> {

    /** Successful parse + type check. */
    public data class Success<T>(public val value: T) : ParseResult<T>()

    /**
     * Failed parse or failed type check.
     *
     * @property errors JSON syntax / structural / condition-syntax errors,
     *   produced by the parser. Non-empty iff parsing failed.
     * @property typeErrors type errors from
     *   [com.myapp.adventure.engine.ir.types.TypeChecker.check], produced
     *   after a fully successful parse. Non-empty iff parsing succeeded
     *   but the IR has semantic problems. See class KDoc for the pipeline.
     *
     * **Exactly one** of [errors] / [typeErrors] is non-empty; enforced by
     * the `init` block below.
     */
    public data class Failure(
        public val errors: List<ParseError>,
        public val typeErrors: List<TypeError> = emptyList(),
    ) : ParseResult<Nothing>() {
        init {
            require(errors.isNotEmpty() || typeErrors.isNotEmpty()) {
                "ParseResult.Failure must contain at least one error in " +
                        "`errors` (parse) or `typeErrors` (type check)"
            }
            require(!(errors.isNotEmpty() && typeErrors.isNotEmpty())) {
                "ParseResult.Failure must not contain both parse errors and type errors: " +
                        "type checking runs only on a fully parsed IR (Ф2.3 / B1 decision). " +
                        "Got ${errors.size} parse errors and ${typeErrors.size} type errors."
            }
        }
    }
}