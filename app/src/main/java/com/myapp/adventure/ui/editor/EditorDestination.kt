package com.myapp.adventure.ui.editor

/**
 * Phase 4-b: editor screens reachable from the toolbar. Declared as a
 * sealed hierarchy so [EditorRoute] stays navigation-agnostic; `AppNavHost`
 * maps the destinations to typed Navigation Compose routes.
 */
sealed interface EditorDestination {
    data object Menu : EditorDestination
    data object Templates : EditorDestination
    data object Subroutines : EditorDestination
    data object Meta : EditorDestination
    data object Validation : EditorDestination
    data object Search : EditorDestination
    data object Stats : EditorDestination
}

/**
 * Payload for opening the reader in preview mode. The editor builds it from
 * the current state; `AppNavHost` stores it in `ReaderPreviewViewModel` and
 * navigates with a short id (the JSON is too large for a route argument).
 */
data class EditorPreviewRequest(
    val adventureJson: String,
    val sceneId: String,
    val chapterIndex: Int,
)
