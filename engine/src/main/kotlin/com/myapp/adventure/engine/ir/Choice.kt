package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single selectable action in a [Scene].
 *
 * Corresponds to `:app/data/Adventure.kt`'s `Choice`:
 * `text`, `next_scene`, `condition`, and the three effect fields
 * (`set_flags`, `remove_flags`, `modify_stats`) flattened into [effects].
 *
 * **Order matters.** When the player picks a choice, [effects] are applied
 * in list order (Ф1.5), matching the current reader behaviour where
 * `set_flags` run before `modify_stats`.
 *
 * **Availability.** A `null` [condition] means "always available". A
 * non-null [condition] is evaluated by the engine (Ф3); today's reader
 * (`AdventureReaderFragment.updateChoices`) hides unavailable choices.
 * The planned N4 / Ф4 behaviour is to show them as disabled with a
 * reason — that is a UI decision and does not affect this data model.
 *

 * @property nextScene scene the player transitions to when picking this
 *   choice. Required — there is no "do nothing" choice; use a self-loop
 *   (`nextScene = currentScene.id`) if you want the player to stay.
 * @property condition optional gate; `null` means always available.
 * @property effects state changes applied on pick, in order.
 */
@Serializable
public data class Choice(
    public val id: ChoiceId,
    public val text: String,
    @SerialName("next_scene")
    public val nextScene: SceneId,
    public val condition: Condition? = null,
    public val effects: List<Effect> = emptyList(),
)