package com.myapp.adventure.engine.parse

/**
 * A single error produced by the JSON parser ([JsonToIr]) or by the
 * condition parser ([ConditionParser]).
 *
 * The parser never throws. It accumulates errors and returns them all at
 * once in [ParseResult.Failure], so that the editor and the analyzer can
 * show the author every problem in one pass instead of one at a time.
 *
 * **Not** `@Serializable`: parse errors are an API for the caller, not part
 * of the on-disk format. If they ever need to be persisted (e.g. in a
 * validation report), serialize them separately.
 */
public sealed class ParseError {

    /** Human-readable, English, one sentence. */
    public abstract val message: String

    /**
     * JSON-level syntax error: malformed input, unknown field with
     * strict mode, wrong type for a well-known field.
     *
     * @property path JSON path to the offending value
     *   (e.g. `"chapters[0].scenes[2].choices[0].condition"`), when known.
     */
    public data class Syntax(
        override val message: String,
        public val path: String? = null,
    ) : ParseError()

    /**
     * Structural problem that is not a JSON syntax error: empty chapter
     * list, blank scene id, empty `next_scene`, etc.
     *
     * @property path same convention as [Syntax.path].
     */
    public data class Structure(
        override val message: String,
        public val path: String? = null,
    ) : ParseError()

    /**
     * Condition string could not be parsed into
     * [com.myapp.adventure.engine.ir.Condition].
     *
     * @property input the raw condition string from JSON.
     * @property position 0-based offset in [input] where the parser gave up,
     *   when determinable.
     * @property path JSON path to the offending condition
     *   (e.g. `"chapters[1:c2].scenes[0:start].choices[2].condition"`),
     *   filled in by [JsonToIr] when accumulating errors. `null` when the
     *   error came from a standalone [ConditionParser] call.
     */
    public data class ConditionError(
        override val message: String,
        public val input: String,
        public val position: Int? = null,
        public val path: String? = null,
    ) : ParseError()
}