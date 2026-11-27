package com.myapp.adventure.ui.editor

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-iii-b-3-ii-b-1: template list screen, Compose rewrite.
 *
 * State split:
 *  - The **list** reads from `viewModel.uiState.templates` (observable).
 *  - The **open edit dialog** is tracked in local `localEditing` state;
 *    `viewModel.editingTemplate` mirrors it so the dialog survives
 *    config changes (ViewModel keeps the working copy; the ComposeView
 *    re-reads it in `remember` on recreation).
 *
 * Save path:
 *  - `localEditing` → `viewModel.templates[idx]` (replace) or `.add`
 *    (new) → `viewModel.commitState()` → `uiState.templates` updates
 *    → Compose recomposes.
 */
@Composable
fun TemplateListScreen(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Initialised from the ViewModel so a config change while the dialog
    // is open restores it. All subsequent open/close updates go through
    // BOTH `localEditing` (Compose state) and `viewModel.editingTemplate`
    // (survives recreation).
    var localEditing by remember { mutableStateOf<EditorTemplate?>(viewModel.editingTemplate) }
    var pendingDelete by remember { mutableStateOf<EditorTemplate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
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
                    text = stringResource(R.string.template_list_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                TextButton(onClick = {
                    val fresh = EditorTemplate()
                    viewModel.editingTemplate = fresh
                    localEditing = fresh
                }) {
                    Text(stringResource(R.string.template_add))
                }
                GlobalMenuButton()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            if (state.templates.isEmpty()) {
                item(key = "__empty__") {
                    Text(
                        text = stringResource(R.string.item_empty_template_hint_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(state.templates, key = { it.uid }) { template ->
                    TemplateRow(
                        template = template,
                        onClick = {
                            val copy = template.deepCopy()
                            viewModel.editingTemplate = copy
                            localEditing = copy
                        },
                        onDelete = { pendingDelete = template },
                    )
                }
            }
        }
    }

    localEditing?.let { working ->
        val isNew = viewModel.templates.none { it.uid == working.uid }
        TemplateEditDialog(
            viewModel = viewModel,
            working = working,
            isNew = isNew,
            onSave = {
                val idx = viewModel.templates.indexOfFirst { it.uid == working.uid }
                if (idx >= 0) viewModel.templates[idx] = working
                else viewModel.templates.add(working)
                viewModel.commitState()
                viewModel.editingTemplate = null
                localEditing = null
                Toast.makeText(
                    context,
                    R.string.template_saved_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = {
                viewModel.editingTemplate = null
                localEditing = null
            },
        )
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.template_delete_message_fmt,
                        template.id.ifEmpty { stringResource(R.string.template_no_id) },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = viewModel.templates.indexOfFirst { it.uid == template.uid }
                    if (idx >= 0) {
                        viewModel.templates.removeAt(idx)
                        viewModel.commitState()
                    }
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplateRow(
    template: EditorTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (template.id.isNotBlank()) {
                    "ID: ${template.id}"
                } else {
                    stringResource(R.string.template_no_id)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = template.text.ifBlank {
                    stringResource(R.string.item_editor_choice_no_text)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = buildString {
                    append(pluralWithNumber(context, R.plurals.word_parameters, template.parameters.size))
                    append(" · ")
                    append(pluralWithNumber(context, R.plurals.word_effects, template.effects.size))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "✕",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
