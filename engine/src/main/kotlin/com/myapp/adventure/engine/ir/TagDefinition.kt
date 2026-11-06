package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Registry entry for a tag used somewhere in an adventure.
 *
 * Each adventure declares its tags once in [Adventure.tags]. Every reference
 * to a tag (in [Condition], in [Effect], in scene/choice effects, in
 * [InitialState]) is resolved against this registry.
 *
 * @property name stable, machine-readable tag name (e.g. `"sword"`, `"health"`).
 * @property displayName human-readable name shown in the UI; falls back to
 *   [name] when blank.
 * @property category how the tag is interpreted by the reader's HUD.
 * @property visible whether the tag is shown in the HUD.
 */
@Serializable
public data class TagDefinition(
    public val name: String,
    @SerialName("display_name")
    public val displayName: String = "",
    public val category: TagCategory = TagCategory.JOURNAL,
    public val visible: Boolean = false,
)