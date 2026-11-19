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
 * **Why this exists.** `AdventureEditorViewModel` still exposes mutable
 * `MutableList`s (`scenes`, `variables`, `templates`, …) because the
 * existing View consumers (`EditorSceneAdapter`, `InitialStateAdapter`,
 * `VariablesAdapter`, `TemplateListAdapter`) mutate them in place and then
 * call `notifyDataSetChanged()`. Compose uses `equals()` to skip
 * recomposition; a mutation like `scene.title = "x"` keeps the same object
 * identity and never triggers an emit.
 *
 * [EditorUiState] fixes this for Compose consumers: each `commitState()`
 * takes a deep copy of every list, so each emit carries structurally
 * different objects and Compose recomposes. The View consumers are
 * unaffected — `uiState` runs in parallel with the mutable lists.
 *
 * **Migration path (Phase 3.1).** This is step 3.1b (after 3.1a). Later
 * steps move View consumers to Compose one by one (3.1c–3.1e). When the
 * last consumer is Compose-native (3.1f), the mutable lists can be
 * removed from the ViewModel; `commitState()` then becomes the only
 * entry point for state changes and the deep copies disappear.
 */
data class EditorUiState(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val variables: List<EditorVarDecl> = emptyList(),
    val templates: List<EditorTemplate> = emptyList(),
    val initialStateTags: List<EditorTag> = emptyList(),
    val scenes: List<EditorScene> = emptyList(),
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