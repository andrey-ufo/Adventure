package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorUiState
import com.myapp.adventure.data.TagCategory

/**
 * Phase 3.1c-ii: initial-state accordion. Previously mirrored
 * `item_initial_state.xml`; now rendered as an item in the editor's
 * `LazyColumn` (`EditorScenesList`).
 */
@Composable
fun InitialStateSection(
    state: EditorUiState,
    onToggleExpanded: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (Int) -> Unit,
    onTagNameChanged: (Int, String) -> Unit,
    onTagValueChanged: (Int, Int) -> Unit,
    onTagDisplayChanged: (Int, String) -> Unit,
    onTagCategoryChanged: (Int, TagCategory) -> Unit,
    onTagVisibleChanged: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    var pendingRemoveIndex by remember { mutableStateOf<Int?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) Modifier.clickable(onClick = onToggleExpanded)
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (collapsible) {
                    Text(
                        text = if (state.initialStateExpanded) "▼" else "▶",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.item_initial_state_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = if (state.initialStateTags.isEmpty()) {
                        stringResource(R.string.label_empty)
                    } else {
                        pluralWithNumber(
                            LocalContext.current,
                            R.plurals.word_tags,
                            state.initialStateTags.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!collapsible || state.initialStateExpanded) {
                Text(
                    text = stringResource(R.string.item_initial_state_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                state.initialStateTags.forEachIndexed { index, tag ->
                    EditorTagRow(
                        tag = tag,
                        onNameChanged = { onTagNameChanged(index, it) },
                        onValueChanged = { onTagValueChanged(index, it) },
                        onDisplayChanged = { onTagDisplayChanged(index, it) },
                        onCategoryChanged = { onTagCategoryChanged(index, it) },
                        onVisibleChanged = { onTagVisibleChanged(index, it) },
                        onDelete = { pendingRemoveIndex = index },
                    )
                }
                TextButton(onClick = onAddTag) {
                    Text(stringResource(R.string.item_initial_state_add_tag))
                }
            }
        }
    }

    pendingRemoveIndex?.let { index ->
        val name = state.initialStateTags.getOrNull(index)?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingRemoveIndex = null },
            title = { Text(stringResource(R.string.initial_state_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.initial_state_delete_message_fmt,
                        name.ifBlank { stringResource(R.string.initial_state_untitled) },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveTag(index)
                    pendingRemoveIndex = null
                }) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveIndex = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}