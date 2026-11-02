package com.myapp.adventure.data

import com.google.gson.annotations.SerializedName

enum class TagCategory {
    @SerializedName("HEALTH") HEALTH,
    @SerializedName("ITEM") ITEM,
    @SerializedName("JOURNAL") JOURNAL
}

data class TagDefinition(
    val name: String,
    @SerializedName("display_name")
    val displayName: String = "",
    val category: TagCategory = TagCategory.JOURNAL,
    val visible: Boolean = false
)