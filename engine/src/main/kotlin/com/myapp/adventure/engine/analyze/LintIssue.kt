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
    /**
     * The adventure runs, but something is almost certainly wrong
     * (unreachable scene, mistyped flag, dead code). The author should
     * fix it before shipping, but it will not break the player's
     * experience outright — the reader may simply reach content the
     * author did not intend.
     */
    WARNING,

    /**
     * The adventure is broken for the **player**, not just for the
     * author: a scene has no choices and no `is_ending` marker (the
     * player gets stuck on an empty screen with no way out), or a scene
     * is marked `is_ending` but also has choices (the reader shows a
     * "the end" screen with dead buttons).
     *
     * **[com.myapp.adventure.engine.analyze.Analyzer] returns `ERROR`
     * only for issues that the reader must refuse to load** (Ф1.7.1 will
     * enforce this). Anything the player can still play through, even
     * if suboptimally, is `WARNING`, not `ERROR`. The author sees both
     * in the editor; only `ERROR` blocks opening the file from the
     * reader's library.
     */
    ERROR,

    /** Informational — e.g. "this scene has no choices, so the adventure ends here". */
    INFO,
}

/**
 * Type of a [LintIssue]. One variant per check.
 *
 * **Why sealed and not an enum.** Future checks need per-variant data
 * ([LintKind.UnreachableScene] today; `TypeMismatch(variable)` in Ф2.2,
 * `DeadCondition(condition)` in Ф3.6, `MergeConflict(nodeId)` in Ф4.6).
 * Ф3.5 added [UnreachableInvariant] with a `reason` field, so
 * "reference to unset flag" and (future) "numeric condition never
 * holds" coexist in one variant.
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

    /**
     * A scene or choice reduces an `ITEM`/`HEALTH` variable that is never
     * established (not in `initialState.stats` with a positive value, and
     * not increased by any `ModifyStat` with `delta > 0`).
     *
     * The author probably forgot to grant the item before removing it.
     * The player will see a "−1 sword" effect and end up at 0 (clamped),
     * wondering where the sword came from.
     *
     * **WARNING, not ERROR.** It is possible for an author to write
     * `remove` without `grant` on purpose, e.g. "you drop the sword you
     * started with" where the sword was given in `initial_state.stats` —
     * but we already check `initial_state`, so the only way this fires is
     * when the item is genuinely never established. Still WARNING, because
     * the adventure still runs; it just looks wrong to the player.
     *
     * **Scope of "ITEM/HEALTH".** Today this is determined by
     * [com.myapp.adventure.engine.ir.TagDefinition.category] in
     * [com.myapp.adventure.engine.ir.Adventure.tags]. If a variable is
     * used in an effect but not declared in `tags`, it is treated as
     * `JOURNAL` (the default) and this check does not fire. The type
     * checker (Ф2.2) is where "unknown variable" is caught.
     */
    public data class RemovingUnestablishedItem(
        public val variableName: String,
    ) : LintKind()

    /**
     * The adventure declares a death scene, but `health` is never
     * initialized or modified. The death scene is dead code: the player
     * can never die.
     *
     * **WARNING, not ERROR.** The adventure runs; it just silently never
     * reaches the death scene. The author may have intended a no-death
     * adventure, in which case they should remove the death scene
     * entirely — but that is their call, not the analyzer's.
     */
    public data object DeathSceneWithoutHealth : LintKind()

    /**
     * A choice's [com.myapp.adventure.engine.ir.Condition.Has] (including
     * inside [com.myapp.adventure.engine.ir.Condition.Not] /
     * [com.myapp.adventure.engine.ir.Condition.And] /
     * [com.myapp.adventure.engine.ir.Condition.Or]) references a name
     * that **never appears in the adventure**:
     * - not in [com.myapp.adventure.engine.ir.InitialState.flags];
     * - not in [com.myapp.adventure.engine.ir.InitialState.stats]'s keys;
     * - not the target of any [com.myapp.adventure.engine.ir.Effect.SetFlag]
     *   in any scene or choice.
     *
     * The author almost certainly mistyped the name (`has_swrod` for
     * `has_sword`), or removed the flag from wherever it used to be set
     * and forgot to update the condition.
     *
     * **Shim note (Ф1.6).** For now this check treats *stats keys* as
     * "established" as well, because the current `test_adventure.json`
     * uses `has_sword` while `sword` is declared as `ItemCount`. A
     * strict reading ("`has_X` is only valid when `X` is a boolean
     * flag") belongs to the type checker, which lands in Ф2.2 as
     * `HasOnNonBoolVariable`. The intention of this check is narrower:
     * catch names that **do not exist anywhere**. Type mismatch
     * (`has_sword` on `sword: ItemCount`) is a different, orthogonal
     * problem and stays deferred.
     *
     * **WARNING, not ERROR.** The adventure opens and plays. The
     * condition on this choice, though, will never behave as intended:
     * `has_X` will evaluate to `false` at runtime, so the choice will
     * be silently disabled. Once Ф2.2 exists, this will be raised
     * earlier as a compile-time error (unknown variable), but the
     * transition period still benefits from the warning.
     *
     * @property flagName the name as it appears in the AST.
     */
    public data class ConditionOnUnestablishedFlag(
        public val flagName: String,
    ) : LintKind()

    /**
     * The adventure uses `health` (declared in
     * [com.myapp.adventure.engine.ir.InitialState.stats] or modified by a
     * [com.myapp.adventure.engine.ir.Effect.ModifyStat] with
     * `name == "health"` in any scene or choice), but does **not**
     * define [com.myapp.adventure.engine.ir.Adventure.deathScene].
     *
     * The player's health can reach 0 and the reader will fall back to a
     * built-in default death screen — which the author probably did not
     * intend. Inverse of [DeathSceneWithoutHealth]: that one fires when
     * the death screen exists but can never be reached; this one fires
     * when the death screen is reachable but does not exist.
     *
     * **WARNING, not ERROR.** The adventure runs and ends; the default
     * death screen is a reasonable fallback. The author just gets a
     * generic message instead of their authored one.
     */
    public data object DeathWithoutDeathScene : LintKind()

    /**
     * A reachable scene has no [com.myapp.adventure.engine.ir.Scene.choices]
     * and is **not** marked
     * [com.myapp.adventure.engine.ir.Scene.isEnding] = `true`. The player
     * will see "the end" here and will not reach the remaining scenes.
     *
     * Since Ф1.7.0, "no choices" alone is no longer an implicit ending —
     * an ending **must** be explicit via `is_ending`. This is a project
     * decision (2026-11-07): pre-alpha drops backward compatibility, and
     * the reader no longer guesses "no choices = advance to the next
     * chapter". The rule is: a scene is either (a) non-terminal (has
     * choices), or (b) explicitly marked as an ending
     * (`is_ending = true`, no choices).
     *
     * **WARNING, not ERROR (revised 2026-11-07).** The player gets
     * warned, but **is not blocked** from opening the file: the reader
     * shows a "N warnings — continue?" dialog and lets the player
     * decide. An unfinished story (author forgot to mark the ending,
     * or intentionally left it open) is a *quality* problem, not a
     * *usability* blocker — the player can still read the whole story
     * up to that point. The original decision (2026-11-07, morning)
     * made this an `ERROR` on the reasoning "player gets stuck on a
     * blank screen"; the user pointed out that a blank screen the
     * player chose to reach is not the same as a screen with dead
     * buttons, and the reader should not override the player's
     * decision to keep reading. Only genuinely broken constructs
     * ([EndingSceneWithChoices] — the player would see buttons that
     * do nothing when clicked) remain `ERROR`.
     *
     * **Why not silently pass.** The warning dialog names the scene,
     * so a player who reaches the dead end and decides to report it
     * has a concrete pointer to send the author.
     *
     * **Not the same as [UnreachableScene].** An unreachable terminal
     * scene (no choices, no `is_ending`, and cannot be reached from
     * `start_scene`) is reported as [UnreachableScene], not as this:
     * the player never sees it, so there is nothing to unstick.
     */
    public data object DeadEnd : LintKind()

    /**
     * Ф3.5 (N2): an [com.myapp.adventure.engine.ir.Invariant] references
     * a Bool flag that **never** appears anywhere — not in
     * `initialState.flags`, not in `initialState.stats.keys`, not as a
     * `SetFlag` target in any scene or choice. The invariant cannot
     * fire, so its outcome (typically `Death`) is dead code.
     *
     * The author almost certainly mistyped the flag name, or removed
     * the flag from wherever it used to be set. This is a **subset**
     * of the broader `Analyzer.deadConditions` analysis planned for
     * Ф3.6 (which will also handle numeric conditions — e.g.
     * `health < -100` where `health` is `IntMin(0)` — and
     * contradictory flag sets). Ф3.5 checks only the simplest case
     * (`Has` / `VarRef` on a name that does not exist), which catches
     * the most common typo class without the false-positive risk of
     * numeric reachability analysis.
     *
     * **WARNING, not ERROR.** The adventure runs and plays; the
     * invariant is simply dead. An author might have added it as a
     * placeholder for a future scene.
     *
     * @property reason human-readable cause. Today the only kind is
     *   "references flag(s) that are never set"; the field exists so
     *   that a future numeric-reachability cause can be distinguished
     *   by the UI without a new variant.
     */
    public data class UnreachableInvariant(
        public val reason: String,
    ) : LintKind()

    /**
     * A scene is marked
     * [com.myapp.adventure.engine.ir.Scene.isEnding] = `true` **and** has
     * one or more [com.myapp.adventure.engine.ir.Scene.choices].
     *
     * The reader (Ф1.7.1) will show the "the end" screen and ignore the
     * choices — the player sees dead buttons that do nothing. The author
     * almost certainly either (a) forgot to remove the choices, or (b)
     * accidentally ticked "end of game" on a non-terminal scene.
     *
     * **ERROR, not WARNING.** Unlike `WARNING`-level issues ("the
     * adventure runs, author should fix"), this is a contradiction the
     * author must resolve before the file makes sense. The reader refuses
     * to load it (Ф1.7.1).
     *
     * @property sceneId the contradictory scene.
     */
    public data class EndingSceneWithChoices(
        public val sceneId: SceneId,
    ) : LintKind()
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