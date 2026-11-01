package com.myapp.adventure.data

enum class SearchFieldType(val icon: String) {
    SCENE_TITLE("📄"),
    SCENE_TEXT("📝"),
    CHOICE_TEXT("💬"),
    TAG_NAME("🏷")
}

data class SearchResult(
    val sceneUid: String,
    val sceneTitle: String,
    val fieldType: SearchFieldType,
    val detail: String,
    val contextBefore: String,
    val match: String,
    val contextAfter: String,
    val totalMatchesInField: Int,
    val charOffset: Int
)