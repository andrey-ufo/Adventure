package com.myapp.adventure.ui.editor

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.data.SaveStatus

private const val CHILD_SAVE_DEBOUNCE_MS = 400L

/**
 * Ф4.13: shared dirty/autosave behavior for full-screen child settings
 * screens (Variables, Initial State). The main [EditorRoute] is disposed
 * while a child screen is on top, so its debounce cannot run; this helper
 * resumes the same draft autosave and marks the editor dirty.
 */
@Composable
internal fun rememberChildDirty(
    viewModel: AdventureEditorViewModel,
): (structural: Boolean) -> Unit {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val saveRunnable = remember(context, viewModel) {
        Runnable { saveDraftImmediate(context, viewModel) }
    }
    DisposableEffect(viewModel) {
        onDispose {
            handler.removeCallbacks(saveRunnable)
            saveDraftSilently(context, viewModel)
        }
    }
    return remember(viewModel, context) {
        { structural ->
            if (structural) viewModel.commitSnapshot()
            viewModel.setHasUnsavedChanges(true)
            viewModel.setSaveStatus(SaveStatus.EDITING)
            viewModel.commitState()
            handler.removeCallbacks(saveRunnable)
            handler.postDelayed(saveRunnable, CHILD_SAVE_DEBOUNCE_MS)
        }
    }
}

@Composable
fun VariablesRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val onDirty = rememberChildDirty(viewModel)
    val finish = {
        saveDraftImmediate(context, viewModel)
        onBack()
    }
    BackHandler { finish() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ChildTopBar(
            title = stringResource(R.string.variables_header_title),
            onBack = finish,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            VariablesSection(
                state = state,
                onToggleExpanded = {},
                onAddVariable = {
                    viewModel.addVariable()
                    onDirty(true)
                },
                onRemoveVariable = { idx ->
                    viewModel.removeVariable(idx)
                    onDirty(true)
                },
                onNameChanged = { idx, v ->
                    viewModel.updateVariableName(idx, v)
                    onDirty(false)
                },
                onKindChanged = { idx, v ->
                    viewModel.updateVariableKind(idx, v)
                    onDirty(false)
                },
                onMinChanged = { idx, v ->
                    viewModel.updateVariableMin(idx, v)
                    onDirty(false)
                },
                onMaxChanged = { idx, v ->
                    viewModel.updateVariableMax(idx, v)
                    onDirty(false)
                },
                collapsible = false,
            )
        }
    }
}

@Composable
fun InitialStateRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val onDirty = rememberChildDirty(viewModel)
    val finish = {
        saveDraftImmediate(context, viewModel)
        onBack()
    }
    BackHandler { finish() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ChildTopBar(
            title = stringResource(R.string.item_initial_state_title),
            onBack = finish,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            InitialStateSection(
                state = state,
                onToggleExpanded = {},
                onAddTag = {
                    viewModel.addInitialStateTag()
                    onDirty(true)
                },
                onRemoveTag = { idx ->
                    viewModel.removeInitialStateTag(idx)
                    onDirty(true)
                },
                onTagNameChanged = { idx, v ->
                    viewModel.updateInitialStateTagName(idx, v)
                    onDirty(false)
                },
                onTagValueChanged = { idx, v ->
                    viewModel.updateInitialStateTagValue(idx, v)
                    onDirty(false)
                },
                onTagDisplayChanged = { idx, v ->
                    viewModel.updateInitialStateTagDisplay(idx, v)
                    onDirty(false)
                },
                onTagCategoryChanged = { idx, v ->
                    viewModel.updateInitialStateTagCategory(idx, v)
                    onDirty(false)
                },
                onTagVisibleChanged = { idx, v ->
                    viewModel.updateInitialStateTagVisible(idx, v)
                    onDirty(false)
                },
                collapsible = false,
            )
        }
    }
}

@Composable
private fun ChildTopBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            GlobalMenuButton()
        }
    }
}
