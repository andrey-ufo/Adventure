package com.myapp.adventure.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R

@Composable
fun DeleteSceneDialog(
    sceneTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fallback = stringResource(R.string.label_without_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_scene_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.editor_scene_delete_message_fmt,
                    sceneTitle.ifBlank { fallback },
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
fun NewSceneDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_new_scene_title)) },
        text = {
            Column {
                BareTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.item_editor_scene_title_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.dialog_new_scene_id_note),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }) {
                Text(stringResource(R.string.dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
fun RebuildIdDialog(
    sceneTitle: String,
    oldId: String,
    newId: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_rebuild_id_title)) },
        text = {
            Text(
                stringResource(
                    R.string.editor_rebuild_id_message_fmt,
                    sceneTitle,
                    oldId.ifBlank { stringResource(R.string.label_empty) },
                    newId,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.editor_rebuild_id_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/**
 * Phase 3.1c-iii-b: step-gate picker. Returns `(steps, repeatable)`.
 * Presets apply immediately; custom input validates on "Применить".
 */
@Composable
fun StepsGateDialog(
    initialSteps: Int,
    initialRepeatable: Boolean,
    onApply: (steps: Int, repeatable: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var customSteps by remember {
        mutableStateOf(if (initialSteps > 0) initialSteps.toString() else "")
    }
    var repeatable by remember { mutableStateOf(initialRepeatable) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.editor_steps_gate_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_chapter_steps_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.dialog_chapter_steps_average_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                listOf(
                    500 to R.string.dialog_chapter_steps_preset_500,
                    1000 to R.string.dialog_chapter_steps_preset_1000,
                    4000 to R.string.dialog_chapter_steps_preset_4000,
                    8000 to R.string.dialog_chapter_steps_preset_8000,
                ).forEach { (preset, labelRes) ->
                    TextButton(onClick = { onApply(preset, repeatable) }) {
                        Text(stringResource(labelRes))
                    }
                }
                Text(
                    text = stringResource(R.string.dialog_chapter_steps_custom_label),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                BareTextField(
                    value = customSteps,
                    onValueChange = { new -> customSteps = new.filter { it.isDigit() } },
                    placeholder = stringResource(R.string.dialog_chapter_steps_custom_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repeatable, onCheckedChange = { repeatable = it })
                    Text(
                        text = stringResource(R.string.dialog_chapter_steps_repeatable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.dialog_chapter_steps_repeatable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = customSteps.toIntOrNull()
                if (v != null && v > 0) {
                    onApply(v, repeatable)
                } else {
                    Toast.makeText(
                        context,
                        R.string.editor_toast_steps_integer_positive,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }) {
                Text(stringResource(R.string.dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/** Phase 3.1c-iii-b: "replace the other death scene?" confirmation. */
@Composable
fun ReplaceDeathSceneDialog(
    previousTitle: String,
    newTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_scene_replace_death_title)) },
        text = {
            Text(
                stringResource(
                    R.string.editor_scene_replace_death_message_fmt,
                    previousTitle,
                    newTitle,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}