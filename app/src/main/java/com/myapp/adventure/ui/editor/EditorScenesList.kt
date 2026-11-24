package com.myapp.adventure.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.TagCategory

@Composable
fun EditorScenesList(
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
    onPreviewScene: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var pendingDeleteScene by remember { mutableStateOf<EditorScene?>(null) }
    var pendingRebuildScene by remember { mutableStateOf<EditorScene?>(null) }

    // Phase 3.1c-iii-c: scroll + highlight on jump request from child
    // editor screens (search / validation click-through).
    var highlightedUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.pendingJumpToSceneUid) {
        val uid = state.pendingJumpToSceneUid ?: return@LaunchedEffect
        val sceneIndex = state.scenes.indexOfFirst { it.uid == uid }
        if (sceneIndex >= 0) {
            // +2: item 0 = variables section, item 1 = initial-state section
            listState.animateScrollToItem(sceneIndex + 2)
            highlightedUid = uid
        }
        viewModel.clearPendingJumpToScene()
    }

    LaunchedEffect(highlightedUid) {
        if (highlightedUid != null) {
            delay(2500L)
            highlightedUid = null
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "variables") {
            VariablesSection(
                state = state,
                onToggleExpanded = { viewModel.toggleVariablesExpanded() },
                onAddVariable = {
                    viewModel.addVariable()
                    onDirty(true)
                },
                onRemoveVariable = { idx ->
                    viewModel.removeVariable(idx)
                    onDirty(true)
                },
                onNameChanged = { idx, v -> viewModel.updateVariableName(idx, v); onDirty(false) },
                onKindChanged = { idx, v -> viewModel.updateVariableKind(idx, v); onDirty(false) },
                onMinChanged = { idx, v -> viewModel.updateVariableMin(idx, v); onDirty(false) },
                onMaxChanged = { idx, v -> viewModel.updateVariableMax(idx, v); onDirty(false) },
            )
        }
        item(key = "initial_state") {
            InitialStateSection(
                state = state,
                onToggleExpanded = { viewModel.updateInitialStateExpanded(!state.initialStateExpanded) },
                onAddTag = {
                    viewModel.addInitialStateTag()
                    onDirty(true)
                },
                onRemoveTag = { idx ->
                    viewModel.removeInitialStateTag(idx)
                    onDirty(true)
                },
                onTagNameChanged = { idx, v -> viewModel.updateInitialStateTagName(idx, v); onDirty(false) },
                onTagValueChanged = { idx, v -> viewModel.updateInitialStateTagValue(idx, v); onDirty(false) },
                onTagDisplayChanged = { idx, v -> viewModel.updateInitialStateTagDisplay(idx, v); onDirty(false) },
                onTagCategoryChanged = { idx, v -> viewModel.updateInitialStateTagCategory(idx, v); onDirty(false) },
                onTagVisibleChanged = { idx, v -> viewModel.updateInitialStateTagVisible(idx, v); onDirty(false) },
            )
        }
        items(state.scenes, key = { it.uid }) { scene ->
            EditorSceneItem(
                scene = scene,
                viewModel = viewModel,
                onDirty = onDirty,
                onRequestDelete = { pendingDeleteScene = scene },
                onRequestRebuildId = { pendingRebuildScene = scene },
                onRequestPreview = { onPreviewScene(scene.uid) },
                highlighted = scene.uid == highlightedUid,
            )
        }
    }

    pendingDeleteScene?.let { scene ->
        DeleteSceneDialog(
            sceneTitle = scene.title,
            onConfirm = {
                viewModel.removeSceneByUid(scene.uid)
                onDirty(true)
                pendingDeleteScene = null
            },
            onDismiss = { pendingDeleteScene = null },
        )
    }

    pendingRebuildScene?.let { scene ->
        val context = LocalContext.current
        val previewId = remember(scene.uid) { viewModel.previewRebuiltId(scene.uid) }
        if (previewId == null) {
            LaunchedEffect(scene.uid) {
                Toast.makeText(
                    context,
                    R.string.editor_toast_title_first,
                    Toast.LENGTH_SHORT,
                ).show()
                pendingRebuildScene = null
            }
        } else if (previewId == scene.id) {
            LaunchedEffect(scene.uid) {
                Toast.makeText(
                    context,
                    R.string.editor_toast_id_matches,
                    Toast.LENGTH_SHORT,
                ).show()
                pendingRebuildScene = null
            }
        } else {
            RebuildIdDialog(
                sceneTitle = scene.title,
                oldId = scene.id,
                newId = previewId,
                onConfirm = {
                    viewModel.rebuildSceneId(scene.uid)
                    onDirty(true)
                    pendingRebuildScene = null
                },
                onDismiss = { pendingRebuildScene = null },
            )
        }
    }
}