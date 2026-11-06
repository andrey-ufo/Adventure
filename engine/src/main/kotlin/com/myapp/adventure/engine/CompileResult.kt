package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.SceneId

/**
 * Result of [Engine.compile]: either a [CompiledAdventure] or a non-empty
 * list of [CompileError]s.
 *
 * **Why a dedicated type, not `Result<CompiledAdventure>`.** Same reason
 * as [com.myapp.adventure.engine.parse.ParseResult]: the callers (reader,
 * editor, analyzer) want to **pattern-match** on error types and show
 * per-error UI. `Result.failure(Throwable)` would force every caller to
 * catch and downcast, and the editor would lose the structured info it
 * needs to point at the offending scene.
 *
 * **Invariant:** [Failure.errors] is never empty. [Engine.compile]
 * returns [Success] or [Failure] with at least one error, never both.
 *
 * **Which errors are covered here, and which are not.**
 * `Engine.compile` only reports errors that **make the indexes impossible
 * to build** — currently just [CompileError.DuplicateSceneId]. Everything
 * else (`nextScene` to a missing scene, unreachable scenes, `has_X` on a
 * numeric variable) is **not** a compile error: an adventure with such
 * issues still compiles and still runs; the analyzer (Ф1.6) flags those
 * issues in the editor, but the reader must not refuse to open the file
 * on the user's first launch because a non-fatal warning exists. This is
 * the same **parser vs analyzer** boundary we fixed in Ф1.3.4.1: compile
 * = "can I build the indexes?", analyzer = "is the IR coherent?".
 */
public sealed class CompileResult {

    /** Successful compile: indexes are ready for [Engine.step]. */
    public data class Success(public val compiled: CompiledAdventure) : CompileResult()

    /** Failed compile: at least one [CompileError]. */
    public data class Failure(public val errors: List<CompileError>) : CompileResult()
}

/**
 * A single compile error: a problem that prevents building the runtime
 * indexes.
 *
 * **What is here.** Only [DuplicateSceneId] today. If a future feature
 * makes another index impossible to build (for example, two
 * `ChoiceId`s within the same scene — not the case today: choices are
 * looked up by id from the scene's own list, no map is built), add a new
 * variant. Keep this type small: it is **not** a general-purpose
 * validation error list. That is `Analyzer`'s job (Ф1.6).
 */
public sealed class CompileError {

    /** Human-readable, English, one sentence. */
    public abstract val message: String

    /**
     * Two or more scenes share the same [SceneId]. The runtime cannot
     * tell them apart; the author almost certainly renamed one and
     * forgot to update the other.
     *
     * @property sceneId the duplicated id.
     * @property chapterIndices indexes (0-based) in
     *   [com.myapp.adventure.engine.ir.Adventure.chapters] where the
     *   scenes live. Two or more entries.
     */
    public data class DuplicateSceneId(
        public val sceneId: SceneId,
        public val chapterIndices: List<Int>,
    ) : CompileError() {
        override val message: String =
            "Duplicate scene id '${sceneId.value}' in chapters " +
                    chapterIndices.joinToString(", ") { (it + 1).toString() } +
                    ". Rename one of them or merge them into a single scene."
    }
}