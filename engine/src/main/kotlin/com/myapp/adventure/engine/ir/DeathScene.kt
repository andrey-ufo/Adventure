package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes how the player dies and what recovery options they get.
 *
 * Corresponds to `:app/data/Adventure.kt`'s `DeathScene`.
 *
 * **Not a real [Scene].** The death screen is not part of the chapter
 * sequence: it is shown when the player's `health` reaches zero (or another
 * death invariant fires — see Ф3.5). Modelling it as a separate class keeps
 * [Chapter.scenes] clean of pseudo-scenes that would confuse the analyzer
 * (reachability, chapter grouping).
 *
 * **Default id.** If the adventure file omits `death_scene.id`, the parser
 * (Ф1.3) uses `"death_scene"`, matching
 * `AdventureEditorViewModel.buildAdventure()`.
 *
 * **All three `allow*` flags default to `true`.** The current
 * `DeathScene` in `:app` has the same defaults; matching them keeps
 * `test_adventure.json` (which sets them explicitly) and adventures that
 * omit them behave identically.
 *
 * @property id scene-like identifier used by the reader for logging and
 *   for the "step back" mechanism.
 * @property text death message shown to the player.
 * @property allowStepBack whether the "step back" recovery button is shown.
 * @property allowChapterRestart whether the "restart chapter" button is shown.
 * @property allowAdventureRestart whether the "restart adventure" button is shown.
 */
@Serializable
public data class DeathScene(
    public val id: SceneId = SceneId("death_scene"),
    public val text: String = "💀 Вы погибли.",
    @SerialName("allow_step_back")
    public val allowStepBack: Boolean = true,
    @SerialName("allow_chapter_restart")
    public val allowChapterRestart: Boolean = true,
    @SerialName("allow_adventure_restart")
    public val allowAdventureRestart: Boolean = true,
)