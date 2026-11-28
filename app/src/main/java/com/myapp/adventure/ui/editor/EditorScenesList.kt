package com.myapp.adventure.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import kotlinx.coroutines.delay

/**
 * Ф4.13: the main editor shows **one** scene — the last one the author
 * worked on — always expanded. Navigation to neighbours is provided by
 * the incoming-scenes popup and the per-choice jump button; global search
 * lives inline above this pane.
 */
@Composable
fun EditorScenesList(
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
    onPreviewScene: (String) -> Unit,
    onAddScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var pendingDeleteScene by remember { mutableStateOf<EditorScene?>(null) }
    var pendingRebuildScene by remember { mutableStateOf<EditorScene?>(null) }
    var highlightedUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.pendingJumpToSceneUid) {
        val uid = state.pendingJumpToSceneUid ?: return@LaunchedEffect
        highlightedUid = uid
        viewModel.clearPendingJumpToScene()
    }

    LaunchedEffect(highlightedUid) {
        if (highlightedUid != null) {
            delay(2500L)
            highlightedUid = null
        }
    }

    val active = state.scenes.firstOrNull { it.uid == state.activeSceneUid }
        ?: state.scenes.firstOrNull()
    var lastActiveUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.uid) {
        // Reset the scroll when the author navigates to another scene, but
        // leave the initial position at the top so the search is visible.
        if (lastActiveUid != null && lastActiveUid != active?.uid && active != null) {
            listState.scrollToItem(1)
        }
        lastActiveUid = active?.uid
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
    ) {
        // Ф4.13a: search scrolls with the scene instead of being pinned.
        item(key = "scene_search") {
            SceneSearchBlock(
                scenes = state.scenes,
                onOpenScene = { uid -> viewModel.setActiveScene(uid) },
            )
        }
        if (active == null) {
            item(key = "__no_scenes__") {
                Column(modifier = Modifier.padding(24.dp)) {
                    TextButton(onClick = onAddScene) {
                        Text(stringResource(R.string.editor_no_scenes_hint))
                    }
                    Text(
                        text = stringResource(R.string.editor_no_scenes_save_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            item(key = active.uid) {
                EditorSceneItem(
                    scene = active,
                    viewModel = viewModel,
                    onDirty = onDirty,
                    onRequestDelete = { pendingDeleteScene = active },
                    onRequestRebuildId = { pendingRebuildScene = active },
                    onRequestPreview = { onPreviewScene(active.uid) },
                    highlighted = active.uid == highlightedUid,
                )
            }
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
