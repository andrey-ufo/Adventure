package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Category of a tag. Determines how the tag is displayed in the reader's HUD
 * (health / inventory / journal) and how its value is interpreted.
 *
 * - [HEALTH] — numeric health-like value; shown in the HUD heart icon.
 * - [ITEM] — inventory item; shown in the HUD backpack icon.
 * - [JOURNAL] — boolean-like flag or visible journal entry.
 *
 * `@SerialName` values match the on-disk JSON (legacy from the Android app
 * model) so that existing adventure files continue to parse.
 */
@Serializable
public enum class TagCategory {
    @SerialName("HEALTH")
    HEALTH,

    @SerialName("ITEM")
    ITEM,

    @SerialName("JOURNAL")
    JOURNAL,
}