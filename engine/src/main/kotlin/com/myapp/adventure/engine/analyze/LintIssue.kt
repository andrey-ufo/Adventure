package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.ir.SceneId

/**
 * Severity of a [LintIssue].
 *
 * **Errors are not `LintIssue`s.** An error means "the file cannot be
 * opened or played" and lives in [com.myapp.adventure.engine.parse.ParseError]
 * or [com.myapp.adventure.engine.CompileError]. A [LintIssue] always means
 * "the file opens and plays, but something is wrong with it". This is the
 * parser/analyzer boundary fixed in Ф1.3.4.1.
 */
public enum class Severity {
    /** The author almost certainly made a mistake; fix before shipping. */
    WARNING,

    /** Informational — e.g. "this scene has no choices, so the adventure ends here". */
    INFO,
}

/**
 * Type of a [LintIssue]. One variant per check.
 *
 * **Why sealed and not an enum.** Future checks need per-variant data
 * ([LintKind.UnreachableScene] today; `TypeMismatch(variable)` in Ф2.2,
 * `DeadCondition(condition)` in Ф3.6, `MergeConflict(nodeId)` in Ф4.6).
 * An enum cannot carry that data; a sealed class can grow without
 * breaking exhaustive `when` in the analyzer or in the UI.
 *
 * **Why the per-variant data does not also live in [LintIssue].** Each
 * variant knows its own context; the issue carries only the fields the
 * **UI** needs for navigation (message, scene id) — so that the UI can
 * `when` on `kind` when it wants, and fall back to generic rendering
 * otherwise. Adding a new variant means adding it to `LintKind` and
 * teaching the UI to render it; no changes to `LintIssue` itself.
 */
public sealed class LintKind {

    /**
     * A scene is not reachable from [com.myapp.adventure.engine.ir.Adventure.startScene]
     * by following `choice.nextScene` links. The player can never see it.
     *
     * Almost always a mistake: a scene that was written but not wired up.
     * Occasionally intentional (a draft scene the author plans to link
     * later) — which is why this is a `WARNING`, not an error.
     */
    public data object UnreachableScene : LintKind()
}

/**
 * One semantic problem found by [Analyzer].
 *
 * **Rendering.** [message] is a fully-formed English string ready to show
 * to the author. It already mentions the scene by name/id, so the UI does
 * not need to reconstruct it. [sceneId] is the navigation hook: clicking
 * the issue in the editor (Ф9.2) jumps to this scene. `null` for issues
 * that do not belong to a specific scene (none today; future checks such
 * as "adventure has no chapters" might).
 *
 * **Ordering.** The analyzer returns issues sorted by scene id, so that
 * repeated runs produce identical output (important for golden tests of
 * the analyzer output later, and for stable UI).
 */
public data class LintIssue(
    public val kind: LintKind,
    public val message: String,
    public val sceneId: SceneId? = null,
    public val severity: Severity = Severity.WARNING,
)