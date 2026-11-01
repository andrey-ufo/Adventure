package com.myapp.adventure.data

data class EditorDraft(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val scenes: List<EditorScene> = emptyList(),
    val savedAt: Long = System.currentTimeMillis()
)