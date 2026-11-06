package com.myapp.adventure.engine.ir

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
 * **A scene with no [choices]** is a terminal scene: the reader either
 * advances to the next chapter (if any) or ends the adventure. This matches
 * current behaviour and is validated in Ф2 (dead ends).
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
 */

@Serializable
public data class Scene(
    public val id: SceneId,
    public val text: String,
    public val effects: List<Effect> = emptyList(),
    public val choices: List<Choice> = emptyList(),
    public val gate: Gate? = null,
)