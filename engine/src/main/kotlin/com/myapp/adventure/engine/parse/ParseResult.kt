package com.myapp.adventure.engine.parse

/**
 * Result of a parse operation: either a value of type [T] or a non-empty
 * list of [ParseError]s.
 *
 * Chosen over Kotlin's stdlib [Result] because:
 * - [Result.Failure] carries a single [Throwable], not a list of errors.
 * - Wrapping [ParseError] in an exception would lose the structured type
 *   (the caller wants to pattern-match on [ParseError.ConditionError] etc.).
 *
 * **Invariant:** [Failure.errors] is never empty. The parser returns
 * [Success] or [Failure] with at least one error, never both.
 */
public sealed class ParseResult<out T> {

    /** Successful parse. */
    public data class Success<T>(public val value: T) : ParseResult<T>()

    /** Failed parse with one or more errors. */
    public data class Failure(public val errors: List<ParseError>) : ParseResult<Nothing>()
}