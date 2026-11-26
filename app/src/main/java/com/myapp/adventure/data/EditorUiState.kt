package com.myapp.adventure.data

/** Visual state of the "💾 Save" indicator in the editor toolbar. */
enum class SaveStatus {
    /** Nothing pending to save — the adventure was just opened / loaded. */
    SAVED,
    /** Editing: debounce timer is running, changes not yet persisted. */
    EDITING,
    /** Snapshot taken, file write in progress. */
    SAVING,
    /** Loaded from a file just now. */
    LOADED,
    /** Saved to disk just now. */
    SAVED_TO_FILE,
}

/**
 * Immutable snapshot of the editor state, exposed as `StateFlow` from
 * [com.myapp.adventure.ui.editor.AdventureEditorViewModel].
 *
 * `AdventureEditorViewModel` still owns mutable lists (`scenes`,
 * `variables`, `templates`, …) as a transitional implementation detail;
 * every mutation ends with `commitState()`, which deep-copies them into
 * this snapshot. Compose reads only the snapshot and mutates state through
 * VM methods, so in-place mutations that keep object identity never reach
 * the UI.
 *
 * Removing the mutable lists is future work; `commitState()` is currently
 * the single publish point.
 */
data class EditorUiState(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val variables: List<EditorVarDecl> = emptyList(),
    val templates: List<EditorTemplate> = emptyList(),
    val subroutines: List<EditorSubroutine> = emptyList(),
    val initialStateTags: List<EditorTag> = emptyList(),
    val scenes: List<EditorScene> = emptyList(),
    /**
     * Ф4.13: the only scene shown by the main editor. Persisted in the
     * draft so reopening the editor returns to the last edited scene.
     * `null` only when there are no scenes.
     */
    val activeSceneUid: String? = null,
    val expandedScenes: Set<String> = emptySet(),
    val expandedSceneSettings: Set<String> = emptySet(),
    val expandedChoices: Set<String> = emptySet(),
    val expandedMapChapters: Set<Int> = emptySet(),
    val isHistoryInitialized: Boolean = false,
    val pendingJumpToSceneUid: String? = null,
    val variablesExpanded: Boolean = false,
    val initialStateExpanded: Boolean = false,

    // ----- Toolbar state (3.1b) -----
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val hasUnsavedChanges: Boolean = false,
    val menuExpanded: Boolean = false,
    val undoEnabled: Boolean = false,
    val redoEnabled: Boolean = false,
)