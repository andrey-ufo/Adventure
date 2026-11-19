package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorUiState
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.VarKind

/**
 * Phase 3.1c-i: variables accordion. Rendered inside a `ComposeViewHolder`
 * that is one item of the editor's `RecyclerView`; the RecyclerView scrolls
 * the section (no local scroll here).
 *
 * Layout: one compact row per variable — name (weight 1) · kind · [min] ·
 * [max] · ✕. No text-field decorations: fields use `BasicTextField` with a
 * plain placeholder, matching the original `item_editor_variable.xml`
 * (transparent background, hint-only labels).
 */
@Composable
fun VariablesSection(
    state: EditorUiState,
    onToggleExpanded: () -> Unit,
    onAddVariable: () -> Unit,
    onRemoveVariable: (Int) -> Unit,
    onNameChanged: (Int, String) -> Unit,
    onKindChanged: (Int, VarKind) -> Unit,
    onMinChanged: (Int, Int?) -> Unit,
    onMaxChanged: (Int, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.variablesExpanded) "▼" else "▶",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.variables_header_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = if (state.variables.isEmpty()) {
                        stringResource(R.string.label_empty)
                    } else {
                        pluralWithNumber(
                            LocalContext.current,
                            R.plurals.word_variables,
                            state.variables.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.variablesExpanded) {
                Text(
                    text = stringResource(R.string.variables_header_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                state.variables.forEachIndexed { index, variable ->
                    VariableRow(
                        index = index,
                        variable = variable,
                        onNameChanged = onNameChanged,
                        onKindChanged = onKindChanged,
                        onMinChanged = onMinChanged,
                        onMaxChanged = onMaxChanged,
                        onRemove = { onRemoveVariable(index) },
                    )
                }
                TextButton(onClick = onAddVariable) {
                    Text(stringResource(R.string.variables_header_add))
                }
            }
        }
    }
}

@Composable
private fun VariableRow(
    index: Int,
    variable: EditorVarDecl,
    onNameChanged: (Int, String) -> Unit,
    onKindChanged: (Int, VarKind) -> Unit,
    onMinChanged: (Int, Int?) -> Unit,
    onMaxChanged: (Int, Int?) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BareTextField(
            value = variable.name,
            onValueChange = { onNameChanged(index, it) },
            placeholder = stringResource(R.string.variable_name_hint),
            modifier = Modifier.weight(1f),
        )
        KindDropdown(current = variable.kind, onChange = { onKindChanged(index, it) })
        if (variable.kind.needsMin) {
            BareTextField(
                value = variable.min?.toString() ?: "",
                onValueChange = { onMinChanged(index, it.toIntOrNull()) },
                placeholder = "min",
                modifier = Modifier.width(56.dp),
            )
        }
        if (variable.kind.needsMax) {
            BareTextField(
                value = variable.max?.toString() ?: "",
                onValueChange = { onMaxChanged(index, it.toIntOrNull()) },
                placeholder = "max",
                modifier = Modifier.width(56.dp),
            )
        }
        Text(
            text = "✕",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** A text field without any decoration (no outline, no underline). */
@Composable
private fun BareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun KindDropdown(current: VarKind, onChange: (VarKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = stringResource(current.displayNameRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VarKind.values().forEach { kind ->
                DropdownMenuItem(
                    text = { Text(stringResource(kind.displayNameRes)) },
                    onClick = {
                        expanded = false
                        if (kind != current) onChange(kind)
                    },
                )
            }
        }
    }
}