package com.myapp.adventure.data

import com.google.gson.annotations.SerializedName

data class Adventure(
    val title: String,
    val author: String = "Неизвестен",
    val description: String = "",
    @SerializedName("initial_state")
    val initialState: PlayerState? = null,
    val tags: List<TagDefinition>? = null,
    @SerializedName("death_scene")
    val deathScene: DeathScene? = null,
    val chapters: List<Chapter>? = null
)

data class DeathScene(
    val id: String = "death_scene",
    val text: String = "💀 Вы погибли.",
    @SerializedName("allow_step_back")
    val allowStepBack: Boolean = true,
    @SerializedName("allow_chapter_restart")
    val allowChapterRestart: Boolean = true,
    @SerializedName("allow_adventure_restart")
    val allowAdventureRestart: Boolean = true
)

data class Chapter(
    val id: String,
    val title: String = "",
    @SerializedName("required_steps")
    val requiredSteps: Int = 0,
    val scenes: List<Scene>? = null
)

data class Scene(
    val id: String,
    val text: String,
    @SerializedName("set_flags")
    val setFlags: List<String>? = null,
    @SerializedName("remove_flags")
    val removeFlags: List<String>? = null,
    @SerializedName("modify_stats")
    val modifyStats: Map<String, Int>? = null,
    val choices: List<Choice>? = null
)

data class Choice(
    val text: String,
    @SerializedName("next_scene")
    val nextScene: String,
    val condition: String? = null,
    @SerializedName("set_flags")
    val setFlags: List<String>? = null,
    @SerializedName("remove_flags")
    val removeFlags: List<String>? = null,
    @SerializedName("modify_stats")
    val modifyStats: Map<String, Int>? = null
)

data class PlayerState(
    val flags: List<String>? = null,
    val stats: Map<String, Int>? = null
)