package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene

private const val SCENE_PICKER_MAX_RESULTS = 50

/**
 * Phase 3.1c-iii-b-3-i: reusable scene picker dialog (Compose rewrite of
 * the old View-based `ScenePickerDialog`).
 *
 * Search by `id` or `title`, case-insensitive. Empty query → hint;
 * non-empty with no matches → "nothing found". Results capped at
 * [SCENE_PICKER_MAX_RESULTS] to keep the dialog fast with 500+ scenes.
 *
 * `allowSelf = true` adds a `@self` special row at the top (used by the
 * template editor; the plain-choice picker passes `false`). Does not
 * create scenes — the caller wires a "new scene" flow separately via
 * `NewSceneDialog`.
 */
@Composable
fun ScenePickerDialog(
    scenes: List<EditorScene>,
    initialSceneId: String,
    allowSelf: Boolean,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember {
        mutableStateOf(
            if (initialSceneId.isNotEmpty() && initialSceneId != "@self") {
                scenes.firstOrNull { it.id == initialSceneId }?.id ?: ""
            } else ""
        )
    }

    val trimmed = query.trim().lowercase()
    val filtered = remember(scenes, trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else scenes
            .filter { it.id.lowercase().contains(trimmed) || it.title.lowercase().contains(trimmed) }
            .take(SCENE_PICKER_MAX_RESULTS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scene_picker_title)) },
        text = {
            Column {
                BareTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.scene_picker_query_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowSelf) {
                    Text(
                        text = stringResource(R.string.scene_picker_self_row_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked("@self") }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
                when {
                    trimmed.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.scene_picker_start_typing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    filtered.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.scene_picker_nothing_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(top = 8.dp),
                        ) {
                            items(filtered, key = { it.uid }) { scene ->
                                ScenePickerRow(
                                    scene = scene,
                                    onClick = { onPicked(scene.id) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun ScenePickerRow(scene: EditorScene, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Text(
            text = scene.title.ifBlank { stringResource(R.string.label_without_title) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = scene.id.ifEmpty { stringResource(R.string.scene_no_id) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}