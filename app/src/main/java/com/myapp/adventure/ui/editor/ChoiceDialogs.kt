package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.TagCategory

/** Phase 3.1c-iii-b-2: delete-choice confirmation. */
@Composable
fun DeleteChoiceDialog(
    choiceText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fallback = stringResource(R.string.item_editor_choice_no_text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_choice_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.editor_choice_delete_message_fmt,
                    choiceText.ifBlank { fallback },
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

/**
 * Phase 3.1c-iii-b-2: choice effects dialog.
 *
 * Uses a local `SnapshotStateList` copy of `initialTags` and applies it back
 * via `onApply` on "Готово". This makes "Отмена" (and system back) discard
 * edits — unlike the old View implementation, which mutated `choice.tags`
 * in place and had no undo inside the dialog.
 */
@Composable
fun ChoiceEffectsDialog(
    choiceText: String,
    initialTags: List<EditorTag>,
    onApply: (List<EditorTag>) -> Unit,
    onDismiss: () -> Unit,
) {
    val localTags = remember {
        mutableStateListOf<EditorTag>().apply {
            addAll(initialTags.map { it.copy() })
        }
    }
    val fallback = stringResource(R.string.item_editor_choice_no_text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_choice_effects_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.editor_choice_effects_info,
                        choiceText.ifBlank { fallback },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                localTags.forEachIndexed { idx, tag ->
                    EditorTagRow(
                        tag = tag,
                        onNameChanged = { v -> localTags[idx] = tag.copy(name = v) },
                        onValueChanged = { v -> localTags[idx] = tag.copy(value = v) },
                        onDisplayChanged = { v -> localTags[idx] = tag.copy(displayName = v) },
                        onCategoryChanged = { cat ->
                            val newTag = tag.copy(
                                category = cat,
                                value = if (cat == TagCategory.ITEM && tag.value == 0) 1 else tag.value,
                            )
                            localTags[idx] = newTag
                        },
                        onVisibleChanged = { v -> localTags[idx] = tag.copy(visible = v) },
                        onDelete = { localTags.removeAt(idx) },
                    )
                }
                if (localTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_choice_effects_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    localTags.add(EditorTag(category = TagCategory.ITEM, value = 1))
                }) {
                    Text(stringResource(R.string.dialog_edit_choice_tags_add_effect))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(localTags.toList()) }) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}