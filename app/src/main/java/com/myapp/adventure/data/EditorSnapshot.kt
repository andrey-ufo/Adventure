package com.myapp.adventure.data

data class EditorSnapshot(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val variables: List<EditorVarDecl> = emptyList(),
    val templates: List<EditorTemplate> = emptyList(),
    val subroutines: List<EditorSubroutine> = emptyList(),
    val initialStateTags: List<EditorTag> = emptyList(),
    val scenes: List<EditorScene> = emptyList(),
)