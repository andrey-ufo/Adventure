package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.VarKind

/**
 * Phase 3.1c-iii-b-3-ii-a: argument entry (Compose rewrite of the old
 * View-based `TemplateArgsDialog.show`).
 *
 * One field per parameter; label is "`name` (`kind display`)". Values are
 * raw strings — `applyTemplateToChoice` interprets them per `VarKind`
 * (`"true"`/`"false"` for Bool, `"3"` for numeric, `"potion"` for
 * ItemCount/ItemList). Validation is non-blank only, matching the old
 * implementation: the positive button stays disabled until every field
 * has a non-blank value; `onApply` never sees a partial map.
 */
@Composable
fun TemplateArgsDialog(
    template: EditorTemplate,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(template.uid) {
        mutableStateMapOf<String, String>().apply {
            template.parameters.forEach { p -> this[p.name] = "" }
        }
    }
    val allFilled = template.parameters.all { p ->
        (values[p.name] ?: "").trim().isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.editor_template_args_title_fmt,
                    template.text.ifBlank {
                        stringResource(R.string.editor_template_picker_no_text)
                    },
                )
            )
        },
        text = {
            Column {
                template.parameters.forEach { param ->
                    Text(
                        text = stringResource(
                            R.string.editor_template_args_label_fmt,
                            param.name,
                            stringResource(param.kind.displayNameRes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    BareTextField(
                        value = values[param.name] ?: "",
                        onValueChange = { values[param.name] = it },
                        placeholder = hintFor(param.kind),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allFilled,
                onClick = {
                    val args = template.parameters.associate { p ->
                        p.name to (values[p.name] ?: "").trim()
                    }
                    onApply(args)
                },
            ) {
                Text(stringResource(R.string.dialog_save))
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
private fun hintFor(kind: VarKind): String = stringResource(
    when (kind) {
        VarKind.ITEM_COUNT -> R.string.editor_template_args_hint_item_count
        VarKind.BOOL -> R.string.editor_template_args_hint_bool
        VarKind.INTEGER, VarKind.INT_MIN, VarKind.INT_RANGE ->
            R.string.editor_template_args_hint_integer
        VarKind.ITEM_LIST -> R.string.editor_template_args_hint_default
    }
)