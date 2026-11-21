package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.data.EditorVarDecl
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.VarKind
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-iii-b-3-ii-b-1: template edit dialog, base fields only.
 *
 * Covers: id (+ hint), text (+ desc), visibility condition, availability
 * condition, next-scene (via ScenePickerDialog + NewSceneDialog).
 *
 * Parameters and effects are **preserved as-is** (never touched); the
 * dialog shows a read-only summary line when present. Full editing of
 * params/effects lands in 3.1c-iii-b-3-ii-b-2.
 *
 * Validation matches the old View implementation: id `[a-z0-9_]+`,
 * text non-blank, next-scene non-empty.
 */
@Composable
fun TemplateEditDialog(
    viewModel: AdventureEditorViewModel,
    working: EditorTemplate,
    isNew: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var localId by remember(working.uid) { mutableStateOf(working.id) }
    var localText by remember(working.uid) { mutableStateOf(working.text) }
    var localVisibility by remember(working.uid) { mutableStateOf(working.visibilityCondition) }
    var localAvailability by remember(working.uid) { mutableStateOf(working.availabilityCondition) }
    var localNextSceneId by remember(working.uid) { mutableStateOf(working.nextSceneId) }
    var showPicker by remember { mutableStateOf(false) }
    var showNewScene by remember { mutableStateOf(false) }
    val localParams = remember(working.uid) {
        mutableStateListOf<EditorVarDecl>().apply {
            addAll(working.parameters.map { it.copy() })
        }
    }
    val localEffects = remember(working.uid) {
        mutableStateListOf<EditorTag>().apply {
            addAll(working.effects.map { it.copy() })
        }
    }

    val idRegex = remember { Regex("[a-z0-9_]+") }
    val idOk = localId.isNotEmpty() && idRegex.matches(localId)
    val textOk = localText.trim().isNotEmpty()
    val nextOk = localNextSceneId.isNotEmpty()
    val canSave = idOk && textOk && nextOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.template_new_title
                    else R.string.template_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel(stringResource(R.string.template_edit_id_label))
                BareTextField(
                    value = localId,
                    onValueChange = { localId = it },
                    placeholder = "use_item",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = when {
                        localId.isEmpty() -> stringResource(R.string.template_id_hint_default)
                        idOk -> "✓"
                        else -> stringResource(R.string.template_id_hint_invalid)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        localId.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                        idOk -> AdventureTheme.extendedColors.statusSuccess
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                SectionLabel(stringResource(R.string.template_edit_text_label))
                BareTextField(
                    value = localText,
                    onValueChange = { localText = it },
                    placeholder = stringResource(R.string.template_edit_text_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.template_edit_text_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                SectionLabel(stringResource(R.string.template_edit_visibility_label))
                BareTextField(
                    value = localVisibility,
                    onValueChange = { localVisibility = it },
                    placeholder = "itemId > 0",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                SectionLabel(stringResource(R.string.template_edit_availability_label))
                BareTextField(
                    value = localAvailability,
                    onValueChange = { localAvailability = it },
                    placeholder = "itemId > 0",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.template_edit_next_scene_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = nextSceneDisplay(localNextSceneId, viewModel.scenes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = nextSceneColor(localNextSceneId, viewModel.scenes),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPicker = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showNewScene = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                SectionLabel(stringResource(R.string.template_edit_params_label))
                localParams.forEachIndexed { idx, decl ->
                    TemplateParamRow(
                        decl = decl,
                        onNameChanged = { localParams[idx] = decl.copy(name = it) },
                        onKindChanged = { localParams[idx] = decl.copy(kind = it) },
                        onMinChanged = { localParams[idx] = decl.copy(min = it) },
                        onMaxChanged = { localParams[idx] = decl.copy(max = it) },
                        onDelete = { localParams.removeAt(idx) },
                    )
                }
                TextButton(onClick = { localParams.add(EditorVarDecl()) }) {
                    Text(stringResource(R.string.template_edit_add_param))
                }

                SectionLabel(stringResource(R.string.template_edit_effects_label))
                localEffects.forEachIndexed { idx, effect ->
                    EditorTagRow(
                        tag = effect,
                        onNameChanged = { localEffects[idx] = effect.copy(name = it) },
                        onValueChanged = { localEffects[idx] = effect.copy(value = it) },
                        onDisplayChanged = { localEffects[idx] = effect.copy(displayName = it) },
                        onCategoryChanged = { newCategory ->
                            val newTag = if (newCategory == TagCategory.ITEM && effect.value == 0) {
                                effect.copy(category = newCategory, value = 1)
                            } else {
                                effect.copy(category = newCategory)
                            }
                            localEffects[idx] = newTag
                        },
                        onVisibleChanged = { localEffects[idx] = effect.copy(visible = it) },
                        onDelete = { localEffects.removeAt(idx) },
                    )
                }
                TextButton(onClick = {
                    localEffects.add(EditorTag(category = TagCategory.ITEM, value = 1))
                }) {
                    Text(stringResource(R.string.dialog_edit_choice_tags_add_effect))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    working.id = localId.trim()
                    working.text = localText
                    working.visibilityCondition = localVisibility.trim()
                    working.availabilityCondition = localAvailability.trim()
                    working.nextSceneId = localNextSceneId
                    working.parameters.clear()
                    working.parameters.addAll(localParams)
                    working.effects.clear()
                    working.effects.addAll(localEffects)
                    onSave()
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

    if (showPicker) {
        ScenePickerDialog(
            scenes = viewModel.scenes,
            initialSceneId = localNextSceneId,
            allowSelf = true,
            onPicked = { picked ->
                localNextSceneId = picked
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showNewScene) {
        NewSceneDialog(
            onCreate = { title ->
                val finalTitle = title.ifBlank {
                    context.getString(
                        R.string.editor_new_scene_default_title_fmt,
                        viewModel.scenes.size + 1,
                    )
                }
                val newScene = viewModel.addSceneWithTitle(finalTitle)
                localNextSceneId = newScene.id
                showNewScene = false
            },
            onDismiss = { showNewScene = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun nextSceneDisplay(id: String, scenes: List<EditorScene>): String = when {
    id.isEmpty() -> stringResource(R.string.template_next_scene_unset)
    id == "@self" -> stringResource(R.string.template_next_scene_self)
    else -> {
        val scene = scenes.firstOrNull { it.id == id }
        if (scene != null) {
            stringResource(
                R.string.template_next_scene_resolved_fmt,
                scene.title.ifBlank { stringResource(R.string.label_without_title) },
                id,
            )
        } else {
            stringResource(R.string.template_next_scene_missing_fmt, id)
        }
    }
}

@Composable
private fun nextSceneColor(id: String, scenes: List<EditorScene>): Color = when {
    id.isEmpty() -> AdventureTheme.extendedColors.statusWarning
    id == "@self" -> MaterialTheme.colorScheme.primary
    scenes.any { it.id == id } -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun TemplateParamRow(
    decl: EditorVarDecl,
    onNameChanged: (String) -> Unit,
    onKindChanged: (VarKind) -> Unit,
    onMinChanged: (Int?) -> Unit,
    onMaxChanged: (Int?) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BareTextField(
            value = decl.name,
            onValueChange = onNameChanged,
            placeholder = stringResource(R.string.variable_name_hint),
            modifier = Modifier.weight(1f),
        )
        BareDropdown(
            current = decl.kind,
            items = VarKind.values().toList(),
            label = { stringResource(it.displayNameRes) },
            onSelected = onKindChanged,
        )
        if (decl.kind.needsMin) {
            BareTextField(
                value = decl.min?.toString() ?: "",
                onValueChange = { onMinChanged(it.toIntOrNull()) },
                placeholder = "min",
                modifier = Modifier.width(56.dp),
            )
        }
        if (decl.kind.needsMax) {
            BareTextField(
                value = decl.max?.toString() ?: "",
                onValueChange = { onMaxChanged(it.toIntOrNull()) },
                placeholder = "max",
                modifier = Modifier.width(56.dp),
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