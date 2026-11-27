package com.myapp.adventure.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorUiState
import com.myapp.adventure.data.FileSaveStatus
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.ui.AppSection
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Editor toolbar: save status, the local action dropdown and the global
 * kebab menu. Actions are grouped by scope instead of a long horizontal
 * row; the current editor screen is marked in the global menu.
 *
 * Hosted by [EditorRoute], which applies `safeDrawingPadding` to the route
 * root; this composable adds no insets of its own.
 */
@Composable
fun EditorToolbar(
    state: EditorUiState,
    onNavigateGlobal: (AppSection) -> Unit,
    canOpenReader: Boolean,
    onToggleActions: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddScene: () -> Unit,
    onVariables: () -> Unit,
    onInitialState: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onEditMeta: () -> Unit,
    onValidate: () -> Unit,
    onStats: () -> Unit,
    onTemplates: () -> Unit,
    onSubroutines: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAction: (() -> Unit) -> (() -> Unit) = { action ->
        {
            onToggleActions()
            action()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleActions) {
                Text(stringResource(R.string.editor_actions) + " ▾")
            }
            Spacer(modifier = Modifier.weight(1f))
            SaveIndicators(state)
            GlobalMenuButton(
                current = AppSection.Editor,
                canOpenReader = canOpenReader,
                onNavigate = onNavigateGlobal,
            )
        }

        DropdownMenu(
            expanded = state.menuExpanded,
            onDismissRequest = onToggleActions,
        ) {
            ActionSection(stringResource(R.string.editor_actions_scene))
            ActionItem(
                label = stringResource(R.string.action_undo),
                enabled = state.undoEnabled,
                onClick = onAction(onUndo),
            )
            ActionItem(
                label = stringResource(R.string.action_redo),
                enabled = state.redoEnabled,
                onClick = onAction(onRedo),
            )
            ActionItem(
                stringResource(R.string.editor_add_scene),
                onClick = onAction(onAddScene),
            )

            ActionSection(stringResource(R.string.editor_actions_adventure))
            ActionItem(
                stringResource(R.string.variables_header_title),
                onAction(onVariables),
            )
            ActionItem(
                stringResource(R.string.item_initial_state_title),
                onAction(onInitialState),
            )
            ActionItem(stringResource(R.string.dialog_save), onAction(onSave))
            ActionItem(stringResource(R.string.editor_load), onAction(onLoad))
            ActionItem(stringResource(R.string.editor_edit_meta), onAction(onEditMeta))
            ActionItem(
                stringResource(R.string.editor_templates_button),
                onAction(onTemplates),
            )
            ActionItem(
                stringResource(R.string.editor_subroutines_button),
                onAction(onSubroutines),
            )

            ActionSection(stringResource(R.string.editor_actions_check))
            ActionItem(stringResource(R.string.editor_validate), onAction(onValidate))
            ActionItem(
                stringResource(R.string.editor_dialog_stats_title),
                onAction(onStats),
            )
            ActionItem(stringResource(R.string.editor_help_button), onAction(onHelp))
        }
    }
}

@Composable
private fun ActionSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun SaveIndicators(state: EditorUiState) {
    val draftSaving = state.saveStatus == SaveStatus.SAVING ||
        state.saveStatus == SaveStatus.EDITING
    Icon(
        painter = painterResource(
            if (draftSaving) {
                R.drawable.ic_editor_draft_syncing
            } else {
                R.drawable.ic_editor_draft_saved
            }
        ),
        contentDescription = stringResource(
            if (draftSaving) R.string.editor_draft_saving else R.string.editor_draft_saved
        ),
        tint = if (draftSaving) {
            AdventureTheme.extendedColors.statusWarning
        } else {
            AdventureTheme.extendedColors.statusSuccess
        },
        modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.size(6.dp))
    val fileDirty = state.fileStatus == FileSaveStatus.DIRTY
    Icon(
        painter = painterResource(
            if (fileDirty) {
                R.drawable.ic_editor_file_dirty
            } else {
                R.drawable.ic_editor_file_saved
            }
        ),
        contentDescription = stringResource(
            if (fileDirty) R.string.editor_file_dirty else R.string.editor_file_saved
        ),
        tint = if (fileDirty) {
            AdventureTheme.extendedColors.statusWarning
        } else {
            AdventureTheme.extendedColors.statusSuccess
        },
        modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.size(4.dp))
}
