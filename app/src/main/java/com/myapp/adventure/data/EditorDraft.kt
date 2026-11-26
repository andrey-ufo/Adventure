package com.myapp.adventure.data

data class EditorDraft(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val variables: List<EditorVarDecl> = emptyList(),
    val templates: List<EditorTemplate> = emptyList(),
    val subroutines: List<EditorSubroutine> = emptyList(),
    val initialStateTags: List<EditorTag> = emptyList(),
    val scenes: List<EditorScene> = emptyList(),
    /** Ф4.13: the one-scene editor restores the last worked-on scene. */
    val activeSceneUid: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)