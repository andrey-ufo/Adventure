package com.myapp.adventure.ui.editor

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.data.FileSaveStatus
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.projects.EditorProjectSession
import com.myapp.adventure.projects.ProjectDraftManager
import com.myapp.adventure.ui.AppSection
import com.myapp.adventure.utils.EditorDraftManager

private const val DEBOUNCE_MS = 400L

/**
 * Phase 4-b: the editor as a self-contained route.
 *
 * Owns everything that used to live in the old editor Fragment:
 *  - toolbar + scenes list (single Compose tree, no ViewBinding/XML layout);
 *  - SAF "open file" launcher and `loadAdventureFromUri`;
 *  - draft restore prompt, autosave debounce and save-on-exit;
 *  - undo/redo, "add scene", save-to-file;
 *  - preview payload construction.
 *
 * Navigation stays callback-based ([EditorDestination] and
 * [EditorPreviewRequest]): `AppNavHost` maps them to typed Navigation
 * Compose routes.
 *
 * The ViewModel is passed in and stays Activity-scoped (the child editor
 * screens share it), exactly as before.
 */
@Composable
fun EditorRoute(
    viewModel: AdventureEditorViewModel,
    onNavigate: (EditorDestination) -> Unit,
    onNavigateGlobal: (AppSection) -> Unit,
    onExit: () -> Unit,
    canOpenReader: Boolean,
    onPreviewRequest: (EditorPreviewRequest) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val debounceHandler = remember { Handler(Looper.getMainLooper()) }
    val saveRunnable = remember(context, viewModel) {
        Runnable { saveDraftImmediate(context, viewModel) }
    }

    var pendingDraft by remember { mutableStateOf<DraftRestore?>(null) }
    var showTypesMissing by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestExit(action: () -> Unit) {
        if (state.fileStatus == FileSaveStatus.DIRTY) {
            pendingExit = action
            showExitDialog = true
        } else {
            action()
        }
    }

    BackHandler { requestExit(onExit) }

    // One-shot setup: publish the initial snapshot, offer a draft restore,
    // then initialize history. Mirrors the old onViewCreated order.
    LaunchedEffect(viewModel) {
        val editorEmpty = viewModel.scenes.isEmpty() &&
            viewModel.title.isEmpty() &&
            viewModel.author.isEmpty() &&
            viewModel.description.isEmpty() &&
            viewModel.initialStateTags.isEmpty()
        val project = EditorProjectSession.current(context)
        if (editorEmpty && project != null) {
            val draft = ProjectDraftManager.load(context, project.id)
            if (draft != null) {
                applyDraft(context, viewModel, draft)
            } else {
                loadAdventureFromUri(
                    context,
                    viewModel,
                    project.fileUri.toUri(),
                    clearDraft = false,
                )
            }
        }
        viewModel.commitState()
        pendingDraft = readDraftForRestore(context, viewModel)
        if (!viewModel.isHistoryInitialized()) {
            viewModel.initHistory()
        }
    }

    // Persist unsaved work when leaving the route (old onDestroyView).
    DisposableEffect(viewModel) {
        onDispose {
            debounceHandler.removeCallbacks(saveRunnable)
            saveDraftSilently(context, viewModel)
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            debounceHandler.removeCallbacks(saveRunnable)
            EditorProjectSession.clear(context)
            showTypesMissing = loadAdventureFromUri(context, viewModel, uri)
        }
    }

    val markDirty: () -> Unit = {
        viewModel.setHasUnsavedChanges(true)
        viewModel.setSaveStatus(SaveStatus.EDITING)
        viewModel.commitState()
        debounceHandler.removeCallbacks(saveRunnable)
        debounceHandler.postDelayed(saveRunnable, DEBOUNCE_MS)
    }

    val onDirty: (Boolean) -> Unit = { structural ->
        if (structural) viewModel.commitSnapshot()
        markDirty()
    }

    val onUndo: () -> Unit = {
        debounceHandler.removeCallbacks(saveRunnable)
        if (viewModel.uiState.value.hasUnsavedChanges) viewModel.commitSnapshot()
        if (viewModel.undo()) {
            saveDraftImmediate(context, viewModel)
            viewModel.commitState()
        }
    }

    val onRedo: () -> Unit = {
        debounceHandler.removeCallbacks(saveRunnable)
        if (viewModel.redo()) {
            saveDraftImmediate(context, viewModel)
            viewModel.commitState()
        }
    }

    val onAddScene: () -> Unit = {
        viewModel.addScene()
        viewModel.commitSnapshot()
        markDirty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        EditorToolbar(
            state = state,
            onNavigateGlobal = onNavigateGlobal,
            canOpenReader = canOpenReader,
            onToggleActions = { viewModel.toggleMenuExpanded() },
            onUndo = onUndo,
            onRedo = onRedo,
            onAddScene = onAddScene,
            onVariables = { onNavigate(EditorDestination.Variables) },
            onInitialState = { onNavigate(EditorDestination.InitialState) },
            onSave = {
                debounceHandler.removeCallbacks(saveRunnable)
                saveAdventureToFile(context, viewModel)
            },
            onLoad = { openFileLauncher.launch(arrayOf("application/json")) },
            onEditMeta = { onNavigate(EditorDestination.Meta) },
            onValidate = { onNavigate(EditorDestination.Validation) },
            onStats = { onNavigate(EditorDestination.Stats) },
            onTemplates = { onNavigate(EditorDestination.Templates) },
            onSubroutines = { onNavigate(EditorDestination.Subroutines) },
            onProjects = { requestExit { onNavigate(EditorDestination.Projects) } },
            onHelp = { onNavigate(EditorDestination.Help) },
        )

        EditorScenesList(
            viewModel = viewModel,
            onDirty = onDirty,
            onPreviewScene = { uid ->
                buildPreviewRequest(context, viewModel, uid)?.let(onPreviewRequest)
            },
            onAddScene = onAddScene,
            modifier = Modifier.weight(1f),
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
                pendingExit = null
            },
            title = { Text(stringResource(R.string.editor_exit_dialog_title)) },
            text = { Text(stringResource(R.string.editor_exit_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveAdventureToFile(context, viewModel)) {
                            showExitDialog = false
                            val action = pendingExit
                            pendingExit = null
                            action?.invoke()
                        }
                    },
                ) {
                    Text(stringResource(R.string.editor_exit_dialog_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        val action = pendingExit
                        pendingExit = null
                        action?.invoke()
                    },
                ) {
                    Text(stringResource(R.string.editor_exit_dialog_exit))
                }
            },
        )
    }

    pendingDraft?.let { restore ->
        RestoreDraftDialog(
            dateText = restore.dateText,
            sceneCount = restore.draft.scenes.size,
            onRestore = {
                applyDraft(context, viewModel, restore.draft)
                pendingDraft = null
            },
            onDelete = {
                val project = EditorProjectSession.current(context)
                if (project != null) {
                    ProjectDraftManager.clear(context, project.id)
                } else {
                    EditorDraftManager.clearDraft(context)
                }
                pendingDraft = null
            },
            onDismiss = { pendingDraft = null },
        )
    }

    if (showTypesMissing) {
        TypesMissingDialog(
            onDeclare = {
                showTypesMissing = false
                onNavigate(EditorDestination.Variables)
            },
            onDismiss = { showTypesMissing = false },
        )
    }
}

// =====================================================================
// Dialogs
// =====================================================================

@Composable
private fun RestoreDraftDialog(
    dateText: String,
    sceneCount: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_dialog_restore_draft_title)) },
        text = {
            Text(
                stringResource(
                    R.string.editor_draft_restore_message_fmt,
                    dateText,
                    sceneCount,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.dialog_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.dialog_delete))
            }
        },
    )
}

@Composable
private fun TypesMissingDialog(
    onDeclare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_dialog_types_missing_title)) },
        text = { Text(stringResource(R.string.editor_dialog_types_missing_message)) },
        confirmButton = {
            TextButton(onClick = onDeclare) {
                Text(stringResource(R.string.dialog_declare))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_later))
            }
        },
    )
}
