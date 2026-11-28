package com.myapp.adventure.projects

/**
 * Stable handle to one NewAdventures project.
 *
 * [id] is the identity persisted by the draft/session layers. It is the
 * document URI string today; C (workspace/versions) may replace it with a
 * stable project id without changing the editor UI.
 */
data class ProjectRef(
    val id: String,
    val fileUri: String,
    val fileName: String,
    val title: String,
    val author: String,
    val modifiedAt: Long,
)
