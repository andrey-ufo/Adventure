package com.myapp.adventure.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorUiState
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Editor toolbar: top row (menu / title / save-status / toggle) plus the
 * expandable actions row (undo/redo/add/save/load/meta/validate/search/
 * stats/templates). Reacts to [state] — no View bindings involved.
 *
 * Hosted by [EditorRoute], which applies `safeDrawingPadding` to the route
 * root; this composable adds no insets of its own.
 */
@Composable
fun EditorToolbar(
    state: EditorUiState,
    onMenuClick: () -> Unit,
    onToggleActions: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddScene: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onEditMeta: () -> Unit,
    onValidate: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopRow(
            title = state.title,
            saveStatus = state.saveStatus,
            menuExpanded = state.menuExpanded,
            onMenuClick = onMenuClick,
            onToggleActions = onToggleActions,
        )
        if (state.menuExpanded) {
            ActionsRow(
                undoEnabled = state.undoEnabled,
                redoEnabled = state.redoEnabled,
                onUndo = onUndo,
                onRedo = onRedo,
                onAddScene = onAddScene,
                onSave = onSave,
                onLoad = onLoad,
                onEditMeta = onEditMeta,
                onValidate = onValidate,
                onSearch = onSearch,
                onStats = onStats,
                onTemplates = onTemplates,
            )
        }
    }
}

@Composable
private fun TopRow(
    title: String,
    saveStatus: SaveStatus,
    menuExpanded: Boolean,
    onMenuClick: () -> Unit,
    onToggleActions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onMenuClick) {
            Text(stringResource(R.string.action_menu))
        }
        Text(
            text = title.ifBlank { stringResource(R.string.editor_title_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = stringResource(saveStatusLabel(saveStatus)),
            color = saveStatusColor(saveStatus),
            fontSize = 11.sp,
            maxLines = 1,
        )
        TextButton(onClick = onToggleActions) {
            Text(if (menuExpanded) "▴" else "▾")
        }
    }
}

@Composable
private fun ActionsRow(
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddScene: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onEditMeta: () -> Unit,
    onValidate: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onTemplates: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ToolbarTextButton(
            label = stringResource(R.string.action_undo),
            onClick = onUndo,
            enabled = undoEnabled,
            fontSize = 20.sp,
        )
        ToolbarTextButton(
            label = stringResource(R.string.action_redo),
            onClick = onRedo,
            enabled = redoEnabled,
            fontSize = 20.sp,
        )
        SmallDivider()
        ToolbarTextButton(stringResource(R.string.editor_add_scene), onAddScene)
        ToolbarTextButton(stringResource(R.string.dialog_save), onSave)
        ToolbarTextButton(stringResource(R.string.editor_load), onLoad)
        ToolbarTextButton(stringResource(R.string.editor_edit_meta), onEditMeta)
        ToolbarTextButton(stringResource(R.string.editor_validate), onValidate)
        ToolbarTextButton(stringResource(R.string.editor_search), onSearch)
        ToolbarTextButton(stringResource(R.string.menu_statistics), onStats)
        ToolbarTextButton(stringResource(R.string.editor_templates_button), onTemplates)
    }
}

@Composable
private fun ToolbarTextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SmallDivider() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun saveStatusLabel(status: SaveStatus): Int = when (status) {
    SaveStatus.SAVED -> R.string.editor_save_status_saved
    SaveStatus.EDITING -> R.string.editor_save_status_editing
    SaveStatus.SAVING -> R.string.editor_save_status_saving
    SaveStatus.LOADED -> R.string.editor_save_status_loaded
    SaveStatus.SAVED_TO_FILE -> R.string.editor_save_status_saved_to_file
}

@Composable
private fun saveStatusColor(status: SaveStatus): Color = when (status) {
    SaveStatus.EDITING -> AdventureTheme.extendedColors.statusWarning
    SaveStatus.SAVING -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveStatus.SAVED,
    SaveStatus.LOADED,
    SaveStatus.SAVED_TO_FILE -> AdventureTheme.extendedColors.statusSuccess
}