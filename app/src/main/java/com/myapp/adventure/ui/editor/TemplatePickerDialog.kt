package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTemplate

/**
 * Phase 3.1c-iii-b-3-ii-a: template picker (Compose rewrite of the old
 * View-based `TemplatePickerDialog.show`).
 *
 * Shows every template in creation order. Empty list → "no templates"
 * message (the caller is expected to check first and show a Toast;
 * this branch is the race fallback).
 */
@Composable
fun TemplatePickerDialog(
    templates: List<EditorTemplate>,
    onPicked: (EditorTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_template_pick_title)) },
        text = {
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_template_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(templates, key = { it.uid }) { template ->
                        TemplatePickerRow(
                            template = template,
                            onClick = { onPicked(template) },
                        )
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
private fun TemplatePickerRow(template: EditorTemplate, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
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
                stringResource(R.string.editor_template_picker_no_text)
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
}