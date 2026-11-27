package com.myapp.adventure.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.data.EditorSubroutine
import com.myapp.adventure.data.EditorSubroutineCall
import com.myapp.adventure.data.EditorSubroutineStep
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.VarKind
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.data.uniqueSubroutineId
import java.util.UUID

/**
 * Ф4.8: subroutines destination.
 *
 * One screen, two modes: a list of subroutines, or the editor for the
 * working copy in `viewModel.editingSubroutine`. The working copy lives in
 * the ViewModel so rotation keeps unsaved fields; the list reads the
 * observable `uiState.subroutines`.
 */
@Composable
fun SubroutinesRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SubroutinesScreen(
        viewModel = viewModel,
        onBack = onBack,
        onPersist = { saveDraftImmediate(context, viewModel) },
    )
}

@Composable
fun SubroutinesScreen(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
    onPersist: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var working by remember {
        mutableStateOf(viewModel.editingSubroutine?.deepCopy())
    }
    var baseline by remember {
        mutableStateOf(
            viewModel.editingSubroutine?.let { editing ->
                viewModel.subroutines.firstOrNull { it.uid == editing.uid }?.deepCopy()
                    ?: EditorSubroutine(uid = editing.uid)
            }
        )
    }
    var pendingDelete by remember { mutableStateOf<EditorSubroutine?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    fun open(subroutine: EditorSubroutine) {
        val copy = subroutine.deepCopy()
        viewModel.editingSubroutine = copy
        baseline = subroutine.deepCopy()
        working = copy
    }

    fun openNew() {
        val copy = EditorSubroutine()
        viewModel.editingSubroutine = copy
        baseline = EditorSubroutine(uid = copy.uid)
        working = copy
    }

    fun closeEditor() {
        showUnsavedDialog = false
        viewModel.editingSubroutine = null
        baseline = null
        working = null
    }

    fun hasUnsavedChanges(): Boolean =
        working != null && working != baseline

    fun requestClose() {
        if (hasUnsavedChanges()) {
            showUnsavedDialog = true
        } else {
            closeEditor()
        }
    }

    fun update(block: (EditorSubroutine) -> Unit) {
        val current = working?.deepCopy() ?: return
        block(current)
        working = current
        viewModel.editingSubroutine = current
    }

    fun persistAndRefresh() {
        viewModel.commitState()
        onPersist()
    }

    fun duplicate(source: EditorSubroutine) {
        val copy = source.deepCopy().copy(
            uid = UUID.randomUUID().toString(),
            id = uniqueSubroutineId(
                source.id,
                viewModel.subroutines.map { it.id }.toSet(),
            ),
        )
        // The duplicate is a real list item immediately (not a phantom
        // working copy): it is persisted before the editor opens, so
        // backing out leaves the copy intact.
        viewModel.subroutines.add(copy)
        persistAndRefresh()
        open(copy)
    }

    fun save() {
        val current = working ?: return
        if (current.id.isBlank()) return
        val index = viewModel.subroutines.indexOfFirst { it.uid == current.uid }
        if (index >= 0) {
            viewModel.subroutines[index] = current.deepCopy()
        } else {
            viewModel.subroutines.add(current.deepCopy())
        }
        closeEditor()
        persistAndRefresh()
    }

    BackHandler(enabled = working != null) { requestClose() }

    if (working == null) {
        SubroutineList(
            subroutines = state.subroutines,
            onBack = onBack,
            onAdd = ::openNew,
            onEdit = ::open,
            onDuplicate = ::duplicate,
            onDelete = { pendingDelete = it },
        )
    } else {
        SubroutineEditor(
            subroutine = working!!,
            allSubroutines = state.subroutines,
            onCancel = ::requestClose,
            onSave = ::save,
            onUpdate = ::update,
        )
    }

    if (showUnsavedDialog) {
        UnsavedSubroutineDialog(
            subroutineId = working?.id.orEmpty(),
            canSave = working?.id?.isNotBlank() == true,
            onSave = {
                showUnsavedDialog = false
                save()
            },
            onDiscard = ::closeEditor,
            onDismiss = { showUnsavedDialog = false },
        )
    }

    pendingDelete?.let { subroutine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.subroutine_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.subroutine_delete_message_fmt,
                        subroutine.id.ifBlank { stringResource(R.string.subroutine_untitled) },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.subroutines.removeAll { it.uid == subroutine.uid }
                    if (viewModel.editingSubroutine?.uid == subroutine.uid) {
                        closeEditor()
                    }
                    pendingDelete = null
                    persistAndRefresh()
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

// =====================================================================
// List
// =====================================================================

@Composable
private fun SubroutineList(
    subroutines: List<EditorSubroutine>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (EditorSubroutine) -> Unit,
    onDuplicate: (EditorSubroutine) -> Unit,
    onDelete: (EditorSubroutine) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        SectionBar(
            title = stringResource(R.string.subroutine_list_title),
            onBack = onBack,
            action = {
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.subroutine_add))
                }
            },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            if (subroutines.isEmpty()) {
                item(key = "__empty__") {
                    Text(
                        text = stringResource(R.string.subroutine_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(subroutines, key = { it.uid }) { subroutine ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEdit(subroutine) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subroutine.id.ifBlank {
                                        stringResource(R.string.subroutine_untitled)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.subroutine_summary_fmt,
                                        subroutine.inputs.size,
                                        subroutine.outputs.size,
                                        subroutine.body.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(R.string.subroutine_duplicate),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .clickable { onDuplicate(subroutine) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                text = "✕",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { onDelete(subroutine) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Editor
// =====================================================================

@Composable
private fun SubroutineEditor(
    subroutine: EditorSubroutine,
    allSubroutines: List<EditorSubroutine>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onUpdate: ((EditorSubroutine) -> Unit) -> Unit,
) {
    val localNames = (subroutine.inputs + subroutine.outputs)
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val outputNames = subroutine.outputs
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    var effectsStepIndex by remember { mutableStateOf<Int?>(null) }
    var callStepIndex by remember { mutableStateOf<Int?>(null) }
    var pendingParamRemoval by remember { mutableStateOf<ParamRemoval?>(null) }
    var pendingStepRemoval by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        SectionBar(
            title = stringResource(
                if (subroutine.id.isBlank()) {
                    R.string.subroutine_editor_title_new
                } else {
                    R.string.subroutine_editor_title
                }
            ),
            onBack = onCancel,
            backLabel = stringResource(R.string.dialog_cancel),
            action = {
                TextButton(
                    enabled = subroutine.id.isNotBlank(),
                    onClick = onSave,
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "id") {
                SectionTitle(stringResource(R.string.subroutine_id_label))
                BareTextField(
                    value = subroutine.id,
                    onValueChange = { value -> onUpdate { it.id = value } },
                    placeholder = stringResource(R.string.subroutine_id_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.subroutine_local_scope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            item(key = "inputs_header") {
                ParameterSection(
                    title = stringResource(R.string.subroutine_inputs_title),
                    parameters = subroutine.inputs,
                    onAdd = { onUpdate { it.inputs.add(EditorVarDecl()) } },
                    onRemove = { index ->
                        pendingParamRemoval = ParamRemoval(
                            isInput = true,
                            index = index,
                            name = subroutine.inputs[index].name,
                        )
                    },
                    onNameChanged = { index, value ->
                        onUpdate { it.inputs[index] = it.inputs[index].copy(name = value) }
                    },
                    onKindChanged = { index, kind ->
                        onUpdate { it.inputs[index] = it.inputs[index].copy(kind = kind) }
                    },
                    onMinChanged = { index, value ->
                        onUpdate { it.inputs[index] = it.inputs[index].copy(min = value) }
                    },
                    onMaxChanged = { index, value ->
                        onUpdate { it.inputs[index] = it.inputs[index].copy(max = value) }
                    },
                )
            }

            item(key = "outputs_header") {
                ParameterSection(
                    title = stringResource(R.string.subroutine_outputs_title),
                    parameters = subroutine.outputs,
                    onAdd = { onUpdate { it.outputs.add(EditorVarDecl()) } },
                    onRemove = { index ->
                        pendingParamRemoval = ParamRemoval(
                            isInput = false,
                            index = index,
                            name = subroutine.outputs[index].name,
                        )
                    },
                    onNameChanged = { index, value ->
                        onUpdate { it.outputs[index] = it.outputs[index].copy(name = value) }
                    },
                    onKindChanged = { index, kind ->
                        onUpdate { it.outputs[index] = it.outputs[index].copy(kind = kind) }
                    },
                    onMinChanged = { index, value ->
                        onUpdate { it.outputs[index] = it.outputs[index].copy(min = value) }
                    },
                    onMaxChanged = { index, value ->
                        onUpdate { it.outputs[index] = it.outputs[index].copy(max = value) }
                    },
                )
            }

            item(key = "steps_header") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(
                            text = stringResource(R.string.subroutine_steps_title),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onUpdate { it.body.add(EditorSubroutineStep()) } }) {
                            Text(stringResource(R.string.subroutine_add_step))
                        }
                    }
                    Text(
                        text = if (localNames.isEmpty()) {
                            stringResource(R.string.subroutine_body_scope_empty)
                        } else {
                            stringResource(
                                R.string.subroutine_body_scope_fmt,
                                localNames.joinToString(", "),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            itemsIndexed(subroutine.body) { index, step ->
                SubroutineStepCard(
                    index = index,
                    step = step,
                    onConditionChanged = { value ->
                        onUpdate { it.body[index].condition = value }
                    },
                    onEditEffects = { effectsStepIndex = index },
                    onPickCall = { callStepIndex = index },
                    onClearCall = { onUpdate { it.body[index].call = null } },
                    onDelete = { pendingStepRemoval = index },
                )
            }
        }
    }

    effectsStepIndex?.let { index ->
        val step = subroutine.body.getOrNull(index)
        if (step != null) {
            ChoiceEffectsDialog(
                choiceText = stringResource(R.string.subroutine_step_title_fmt, index + 1),
                initialTags = step.effects,
                titleRes = R.string.subroutine_step_effects_title,
                infoRes = R.string.subroutine_step_effects_info,
                onApply = { tags ->
                    onUpdate { current ->
                        current.body.getOrNull(index)?.let { target ->
                            target.effects.clear()
                            target.effects.addAll(tags.map { it.copy() })
                        }
                    }
                    effectsStepIndex = null
                },
                onDismiss = { effectsStepIndex = null },
            )
        } else {
            effectsStepIndex = null
        }
    }

    callStepIndex?.let { index ->
        val step = subroutine.body.getOrNull(index)
        if (step != null) {
            SubroutineCallDialog(
                subroutines = allSubroutines.filter { it.uid != subroutine.uid },
                inputSources = localNames,
                outputTargets = outputNames,
                initial = step.call,
                title = stringResource(R.string.subroutine_step_call_title),
                onApply = { call: EditorSubroutineCall ->
                    onUpdate { current ->
                        current.body.getOrNull(index)?.call = call
                    }
                    callStepIndex = null
                },
                onDismiss = { callStepIndex = null },
                onClear = if (step.call != null) {
                    {
                        onUpdate { current -> current.body.getOrNull(index)?.call = null }
                        callStepIndex = null
                    }
                } else {
                    null
                },
            )
        } else {
            callStepIndex = null
        }
    }

    pendingParamRemoval?.let { removal ->
        val kind = stringResource(
            if (removal.isInput) R.string.subroutine_param_kind_input
            else R.string.subroutine_param_kind_output,
        )
        AlertDialog(
            onDismissRequest = { pendingParamRemoval = null },
            title = { Text(stringResource(R.string.subroutine_param_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.subroutine_param_delete_message_fmt,
                        kind,
                        removal.name.ifBlank {
                            stringResource(R.string.subroutine_param_untitled)
                        },
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate { current ->
                        if (removal.isInput) {
                            current.inputs.getOrNull(removal.index)?.let {
                                current.inputs.removeAt(removal.index)
                            }
                        } else {
                            current.outputs.getOrNull(removal.index)?.let {
                                current.outputs.removeAt(removal.index)
                            }
                        }
                    }
                    pendingParamRemoval = null
                }) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingParamRemoval = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    pendingStepRemoval?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingStepRemoval = null },
            title = { Text(stringResource(R.string.subroutine_step_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.subroutine_step_delete_message_fmt,
                        index + 1,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate { current ->
                        if (index in current.body.indices) current.body.removeAt(index)
                    }
                    pendingStepRemoval = null
                }) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStepRemoval = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

private data class ParamRemoval(
    val isInput: Boolean,
    val index: Int,
    val name: String,
)

@Composable
private fun ParameterSection(
    title: String,
    parameters: List<EditorVarDecl>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onNameChanged: (Int, String) -> Unit,
    onKindChanged: (Int, VarKind) -> Unit,
    onMinChanged: (Int, Int?) -> Unit,
    onMaxChanged: (Int, Int?) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text = title, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.subroutine_add_param))
                }
            }
            if (parameters.isEmpty()) {
                Text(
                    text = stringResource(R.string.subroutine_no_params),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            parameters.forEachIndexed { index, parameter ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BareTextField(
                        value = parameter.name,
                        onValueChange = { onNameChanged(index, it) },
                        placeholder = stringResource(R.string.variable_name_hint),
                        modifier = Modifier.weight(1f),
                    )
                    KindDropdown(
                        current = parameter.kind,
                        onChange = { onKindChanged(index, it) },
                    )
                    if (parameter.kind.needsMin) {
                        BareTextField(
                            value = parameter.min?.toString() ?: "",
                            onValueChange = { onMinChanged(index, it.toIntOrNull()) },
                            placeholder = "min",
                            modifier = Modifier.width(56.dp),
                        )
                    }
                    if (parameter.kind.needsMax) {
                        BareTextField(
                            value = parameter.max?.toString() ?: "",
                            onValueChange = { onMaxChanged(index, it.toIntOrNull()) },
                            placeholder = "max",
                            modifier = Modifier.width(56.dp),
                        )
                    }
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { onRemove(index) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubroutineStepCard(
    index: Int,
    step: EditorSubroutineStep,
    onConditionChanged: (String) -> Unit,
    onEditEffects: () -> Unit,
    onPickCall: () -> Unit,
    onClearCall: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.subroutine_step_title_fmt, index + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            BareTextField(
                value = step.condition,
                onValueChange = onConditionChanged,
                placeholder = stringResource(R.string.subroutine_step_condition_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.subroutine_step_effects_fmt,
                        step.effects.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditEffects) {
                    Text(stringResource(R.string.subroutine_step_effects_button))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.call?.takeIf { it.subroutineId.isNotBlank() }?.let {
                        stringResource(R.string.subroutine_step_call_fmt, it.subroutineId)
                    } ?: stringResource(R.string.subroutine_step_no_call),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (step.call != null) {
                    TextButton(onClick = onClearCall) {
                        Text(stringResource(R.string.subroutine_call_remove))
                    }
                }
                TextButton(onClick = onPickCall) {
                    Text(stringResource(R.string.subroutine_step_call_button))
                }
            }
        }
    }
}

/**
 * Ф4.8a: leaving the subroutine editor with unsaved fields must be
 * explicit. "Сохранить" is disabled while the id is blank (the file cannot
 * reference it); the author can still discard or go back to editing.
 */
@Composable
private fun UnsavedSubroutineDialog(
    subroutineId: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subroutine_unsaved_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.subroutine_unsaved_message_fmt,
                        subroutineId.ifBlank { stringResource(R.string.subroutine_untitled) },
                    )
                )
                if (!canSave) {
                    Text(
                        text = stringResource(R.string.subroutine_unsaved_no_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = onSave,
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.subroutine_unsaved_discard))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        },
    )
}

// =====================================================================
// Small shared bits
// =====================================================================

@Composable
private fun SectionBar(
    title: String,
    onBack: () -> Unit,
    backLabel: String? = null,
    action: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(backLabel ?: stringResource(R.string.action_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            action()
            GlobalMenuButton()
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}
