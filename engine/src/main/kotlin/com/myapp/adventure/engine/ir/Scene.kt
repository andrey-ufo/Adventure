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
)