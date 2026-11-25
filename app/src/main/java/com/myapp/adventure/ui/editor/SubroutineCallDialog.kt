package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorSubroutine
import com.myapp.adventure.data.EditorSubroutineCall

/**
 * Ф4.8: pick a subroutine and bind its typed inputs/outputs.
 *
 * **Two call sites, one dialog.**
 * - From a choice: [inputSources] / [outputTargets] are the adventure's
 *   global variable names.
 * - From a subroutine step (nested call): they are the enclosing frame's
 *   local names — inputs and outputs for sources, outputs only for
 *   targets. The engine type checker validates the types.
 *
 * The dialog only prevents blank input mappings; type mismatches and
 * missing/extra references are reported by `Engine.compile` /
 * `TypeChecker` in the validation dialog, not hidden here.
 */
@Composable
fun SubroutineCallDialog(
    subroutines: List<EditorSubroutine>,
    inputSources: List<String>,
    outputTargets: List<String>,
    initial: EditorSubroutineCall?,
    title: String,
    onApply: (EditorSubroutineCall) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val usable = remember(subroutines) {
        subroutines.filter { it.id.isNotBlank() }
    }
    var selectedId by remember {
        mutableStateOf(
            initial?.subroutineId?.takeIf { id -> usable.any { it.id == id } }
                ?: usable.firstOrNull()?.id
                ?: "",
        )
    }
    val selected = usable.firstOrNull { it.id == selectedId }

    val args = remember { mutableStateMapOf<String, String>() }
    val outputs = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(selectedId) {
        args.clear()
        outputs.clear()
        val sub = usable.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        val prior = initial?.takeIf { it.subroutineId == selectedId }
        sub.inputs.forEach { input ->
            val candidate = prior?.args?.get(input.name)
            args[input.name] = candidate?.takeIf { it in inputSources }.orEmpty()
        }
        sub.outputs.forEach { output ->
            val candidate = prior?.outputs?.get(output.name)
            outputs[output.name] = candidate?.takeIf { it in outputTargets }.orEmpty()
        }
    }

    val allInputsBound = selected?.inputs?.all { input ->
        (args[input.name] ?: "").trim().isNotEmpty()
    } ?: false
    val canConfirm = selected != null && allInputsBound

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (usable.isEmpty()) {
                    Text(
                        text = stringResource(R.string.subroutine_call_no_subroutines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subroutine_call_pick),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    BareDropdown(
                        current = selectedId,
                        items = usable.map { it.id },
                        label = { id -> id.ifBlank { stringResource(R.string.subroutine_untitled) } },
                        onSelected = { selectedId = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    selected?.let { sub ->
                        if (sub.inputs.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.subroutine_call_inputs))
                            sub.inputs.forEach { input ->
                                MappingRow(
                                    label = "${input.name} (${stringResource(input.kind.displayNameRes)})",
                                    current = args[input.name].orEmpty(),
                                    options = inputSources,
                                    emptyLabel = stringResource(R.string.subroutine_call_not_selected),
                                    onSelected = { args[input.name] = it },
                                )
                            }
                            if (inputSources.isEmpty()) {
                                Hint(stringResource(R.string.subroutine_call_no_scope))
                            }
                        }

                        if (sub.outputs.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.subroutine_call_outputs))
                            sub.outputs.forEach { output ->
                                MappingRow(
                                    label = "${output.name} (${stringResource(output.kind.displayNameRes)})",
                                    current = outputs[output.name].orEmpty(),
                                    options = outputTargets,
                                    emptyLabel = stringResource(R.string.subroutine_call_discard),
                                    onSelected = { outputs[output.name] = it },
                                )
                            }
                            if (outputTargets.isEmpty()) {
                                Hint(stringResource(R.string.subroutine_call_no_targets))
                            }
                        }

                        if (sub.inputs.isEmpty() && sub.outputs.isEmpty()) {
                            Hint(stringResource(R.string.subroutine_call_no_parameters))
                        }
                    }

                    if (onClear != null && initial != null) {
                        TextButton(onClick = onClear) {
                            Text(stringResource(R.string.subroutine_call_remove))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val sub = selected ?: return@TextButton
                    onApply(
                        EditorSubroutineCall(
                            subroutineId = sub.id,
                            args = sub.inputs.associate { input ->
                                input.name to args[input.name].orEmpty().trim()
                            },
                            outputs = sub.outputs.mapNotNull { output ->
                                val target = outputs[output.name].orEmpty().trim()
                                if (target.isEmpty()) null else output.name to target
                            }.toMap(),
                        )
                    )
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun MappingRow(
    label: String,
    current: String,
    options: List<String>,
    emptyLabel: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        BareDropdown(
            current = current,
            items = listOf("") + options,
            label = { value -> value.ifBlank { emptyLabel } },
            onSelected = onSelected,
        )
    }
}
