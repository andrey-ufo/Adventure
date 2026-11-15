package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One screen of an adventure: body text, optional effects, and (optionally)
 * a list of [Choice]s the player can pick.
 *
 * Corresponds to `:app/data/Adventure.kt`'s `Scene`, with two differences:
 *
 * 1. The three JSON fields `set_flags`, `remove_flags`, `modify_stats` are
 *    flattened into [effects] (see [Effect]). Order is preserved by the
 *    parser (Ф1.3): `set_flags` first, then `remove_flags`, then
 *    `modify_stats`, matching the current reader's `applyEffects`.
 * 2. Scenes are grouped into [Chapter]s. There is no `isChapterEnd` flag in
 *    the IR: chapter boundaries are the boundaries of [Chapter.scenes].
 *
 * **A scene with no [choices]** is a terminal scene: the story **must**
 * mark it with [isEnding] = `true`, or the analyzer reports
 * [com.myapp.adventure.engine.analyze.LintKind.DeadEnd] (severity
 * `ERROR`). The reader does **not** guess: it never auto-advances to the
 * next chapter. "End of chapter" must be expressed by an explicit
 * Continue-choice that leads to the first scene of the next chapter.
 *
 * @property id unique scene identifier within an adventure.
 * @property text body text shown to the player.
 * @property effects state changes applied the first time the scene is
 *   entered, in order. Re-entering a scene does not re-apply them.
 * @property choices actions shown below the text; may be empty.
 * @property gate optional step gate: the player must walk
 *   [Gate.requiredSteps] steps **after entering this scene** before any
 *   exit is allowed. `null` means "no gate". Gates are independent of
 *   chapters — a chapter may have zero, one, or many gated scenes, and a
 *   gated scene may sit anywhere in a chapter. See [Gate] for details.
 * @property isEnding whether this scene ends the adventure. Multiple
 *   scenes in the same adventure may be marked `true` — that is how
 *   authors model multiple endings ("win" / "die" / "retire rich" /
 *   etc.). A scene marked `true` **must** have no [choices] — the reader
 *   would otherwise render buttons that lead nowhere. Marking a scene
 *   with [isEnding] = `true` **and** non-empty [choices] is reported by
 *   [com.myapp.adventure.engine.analyze.LintKind.EndingSceneWithChoices]
 *   (severity `ERROR`). Defaults to `false`; this matches the
 *   pre-`isEnding` behaviour, where "no choices" silently meant "end".
 *
 * **Not to be confused with [DeathScene].** `DeathScene` is not part of
 * [Chapter.scenes] and does not carry [isEnding]. A scene marked
 * [isEnding] = `true` describes an *authored* ending ("you win", "you
 * retire"); the death screen is shown automatically when the player's
 * `health` reaches `0` and is **not** one of the scenes.
 */
@Serializable
public data class Scene(
    public val id: SceneId,
    public val text: String,
    public val effects: List<Effect> = emptyList(),
    public val choices: List<Choice> = emptyList(),
    public val gate: Gate? = null,
    @SerialName("is_ending")
    public val isEnding: Boolean = false,
    /**
     * Ф4.5 (N5): this scene is a **merge point** — a deliberate convergence
     * of two or more narrative branches. The field is metadata: it does
     * **not** change runtime behaviour (a merge point is an ordinary
     * scene; entering it via `Engine.step` applies the incoming choice's
     * effects as usual, and subsequent steps see the accumulated state).
     *
     * **Why the metadata then?** The analyzer (Ф4.6) will use it to
     * verify "all declared source branches are actually reachable and
     * lead here" — unreachable or inconsistent merges are author
     * mistakes. It also lets the editor's map view (Ф4.10) render a
     * visual join. Without the marker, a merge point is
     * indistinguishable from an ordinary scene that happens to have
     * multiple incoming edges.
     *
     * **Delta semantics (Ф4.5 decision C1).** "Merge transfers the
     * delta" is satisfied **implicitly**: each branch applies its own
     * `choice.effects` (and `scene.effects`, once per first entry)
     * before entering the merge scene; `PlayerState` arriving at the
     * merge is the sum of those deltas on top of the pre-branch state.
     * There is **no** runtime "merge delta" application — the current
     * model does not track per-branch deltas separately from the state.
     * The `merge: true` flag is a *declaration* that this scene is where
     * branches converge, not a runtime operation.
     */
    @SerialName("merge")
    public val isMerge: Boolean = false,
    /**
     * Ф4.5 (N5): declared source branches — scene ids that the author
     * says lead into this merge point. Optional even when [isMerge] is
     * `true`: if empty, the analyzer (Ф4.6) will collect incoming edges
     * from the `nextScene` graph. If non-empty, each id must exist —
     * [com.myapp.adventure.engine.CompileError.MissingMergeSource]
     * otherwise.
     *
     * The declaration is what makes the merge *explicit*: a plain scene
     * with two incoming choices is an *implicit* merge; a scene with
     * `merge: true` + `mergeSources` declares "these are the branches I
     * intended to join here", which the analyzer can then validate.
     */
    @SerialName("merge_sources")
    public val mergeSources: List<SceneId> = emptyList(),
)