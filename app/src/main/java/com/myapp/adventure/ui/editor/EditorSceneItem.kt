package com.myapp.adventure.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTemplate
import com.myapp.adventure.ui.theme.AdventureTheme
import kotlinx.coroutines.delay

private const val TITLE_DEBOUNCE_MS = 300L
private const val TEXT_DEBOUNCE_MS = 500L

@Composable
fun EditorSceneItem(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    onRequestRebuildId: () -> Unit,
    onRequestPreview: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded = scene.uid in state.expandedScenes
    val borderModifier = if (highlighted) {
        Modifier.border(
            width = 2.dp,
            color = AdventureTheme.extendedColors.statusWarning,
            shape = MaterialTheme.shapes.small,
        )
    } else {
        Modifier
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(borderModifier),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SceneHeader(
                scene = scene,
                viewModel = viewModel,
                expanded = expanded,
                onToggleExpanded = { viewModel.toggleSceneExpanded(scene.uid) },
                onDelete = onRequestDelete,
                onDirty = onDirty,
            )
            SceneInfoRow(
                scene = scene,
                onRebuildId = onRequestRebuildId,
                onPreview = onRequestPreview,
            )
            if (expanded) {
                SceneBody(
                    scene = scene,
                    viewModel = viewModel,
                    onDirty = onDirty,
                )
            }
        }
    }
}

@Composable
private fun SceneHeader(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
    onDirty: (structural: Boolean) -> Unit,
) {
    var localTitle by remember(scene.uid) { mutableStateOf(scene.title) }
    LaunchedEffect(scene.title) {
        if (scene.title != localTitle) localTitle = scene.title
    }
    LaunchedEffect(localTitle) {
        if (localTitle != scene.title) {
            delay(TITLE_DEBOUNCE_MS)
            // Route through the ViewModel — `scene` is a deep copy from
            // EditorUiState.scenes, not the live model. Mutating it directly
            // is lost on the next `commitState()`.
            viewModel.updateSceneTitle(scene.uid, localTitle)
            onDirty(false)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (expanded) "▼" else "▶",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clickable(onClick = onToggleExpanded)
                .padding(8.dp),
        )
        BasicTextField(
            value = localTitle,
            onValueChange = { localTitle = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                ) {
                    if (localTitle.isEmpty()) {
                        Text(
                            text = stringResource(R.string.item_editor_scene_title_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun SceneInfoRow(
    scene: EditorScene,
    onRebuildId: () -> Unit,
    onPreview: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val idText = when {
            scene.id.isNotEmpty() ->
                context.getString(R.string.editor_scene_id_label_fmt, scene.id)
            scene.title.isNotBlank() ->
                context.getString(R.string.editor_scene_id_label_will_generate)
            else ->
                context.getString(R.string.editor_scene_id_label_enter_title)
        }
        Text(
            text = idText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (scene.title.isNotBlank() && scene.id.isNotBlank()) {
            TextButton(onClick = onRebuildId) {
                Text("↻", style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onClick = onPreview) {
            Text("▶", style = MaterialTheme.typography.bodyMedium)
        }
        if (scene.isChapterEnd) {
            Text(
                text = stringResource(R.string.item_editor_scene_chapter_end_badge),
                style = MaterialTheme.typography.bodySmall,
                color = AdventureTheme.extendedColors.statusWarning,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (scene.isDeathScene) {
            Text(
                text = stringResource(R.string.item_editor_scene_death_badge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SceneBody(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
) {
    var localText by remember(scene.uid) { mutableStateOf(scene.text) }
    LaunchedEffect(scene.text) {
        if (scene.text != localText) localText = scene.text
    }
    LaunchedEffect(localText) {
        if (localText != scene.text) {
            delay(TEXT_DEBOUNCE_MS)
            // See SceneHeader: `scene` is a deep copy, write to live model.
            viewModel.updateSceneText(scene.uid, localText)
            onDirty(false)
        }
    }

    BasicTextField(
        value = localText,
        onValueChange = { localText = it },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) {
                if (localText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.item_editor_scene_text_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        scene.tags.forEachIndexed { idx, tag ->
            EditorTagRow(
                tag = tag,
                onNameChanged = { v ->
                    viewModel.updateSceneTag(scene.uid, idx, v, tag.value, tag.displayName, tag.category, tag.visible)
                    onDirty(false)
                },
                onValueChanged = { v ->
                    viewModel.updateSceneTag(scene.uid, idx, tag.name, v, tag.displayName, tag.category, tag.visible)
                    onDirty(false)
                },
                onDisplayChanged = { v ->
                    viewModel.updateSceneTag(scene.uid, idx, tag.name, tag.value, v, tag.category, tag.visible)
                    onDirty(false)
                },
                onCategoryChanged = { v ->
                    viewModel.updateSceneTag(scene.uid, idx, tag.name, tag.value, tag.displayName, v, tag.visible)
                    onDirty(false)
                },
                onVisibleChanged = { v ->
                    viewModel.updateSceneTag(scene.uid, idx, tag.name, tag.value, tag.displayName, tag.category, v)
                    onDirty(false)
                },
                onDelete = {
                    viewModel.removeSceneTag(scene.uid, idx)
                    onDirty(true)
                },
            )
        }
        TextButton(onClick = {
            viewModel.addSceneTag(scene.uid)
            onDirty(true)
        }) {
            Text(stringResource(R.string.item_editor_scene_add_tag))
        }
    }

    SceneSettingsBlock(
        scene = scene,
        viewModel = viewModel,
        onDirty = onDirty,
    )

    SceneChoicesBlock(
        scene = scene,
        viewModel = viewModel,
        onDirty = onDirty,
    )
}

// ---------------------------------------------------------------------------
// Phase 3.1c-iii-b: scene settings block (chapter end / gate / ending / death)
// ---------------------------------------------------------------------------

@Composable
private fun SceneSettingsBlock(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
) {
    val settingsExpanded = viewModel.isSceneSettingsExpanded(scene.uid)
    Text(
        text = stringResource(R.string.item_editor_scene_settings_toggle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.toggleSceneSettings(scene.uid) }
            .padding(8.dp),
    )
    if (settingsExpanded) {
        Column(modifier = Modifier.padding(start = 16.dp)) {
            CheckRow(
                label = stringResource(R.string.item_editor_scene_chapter_end),
                checked = scene.isChapterEnd,
                onCheckedChange = {
                    viewModel.setSceneChapterEnd(scene.uid, it)
                    onDirty(true)
                },
            )
            SceneGateRow(scene = scene, viewModel = viewModel, onDirty = onDirty)
            CheckRow(
                label = stringResource(R.string.item_editor_scene_ending),
                checked = scene.isEnding,
                onCheckedChange = {
                    viewModel.setSceneEnding(scene.uid, it)
                    onDirty(false)
                },
            )
            SceneDeathRow(scene = scene, viewModel = viewModel, onDirty = onDirty)
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SceneGateRow(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
) {
    var showStepsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gateOn = scene.gateRequiredSteps > 0

    CheckRow(
        label = stringResource(R.string.item_editor_scene_scene_gate),
        checked = gateOn,
        onCheckedChange = { checked ->
            when {
                checked && scene.gateRequiredSteps == 0 -> showStepsDialog = true
                !checked -> {
                    viewModel.setSceneGate(scene.uid, 0, false)
                    onDirty(true)
                }
            }
        },
    )
    if (gateOn) {
        Text(
            text = formatStepsDisplay(context, scene.gateRequiredSteps, scene.gateRepeatable),
            style = MaterialTheme.typography.bodyMedium,
            color = AdventureTheme.extendedColors.statusWarning,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showStepsDialog = true }
                .padding(6.dp),
        )
    }
    if (showStepsDialog) {
        StepsGateDialog(
            initialSteps = scene.gateRequiredSteps,
            initialRepeatable = scene.gateRepeatable,
            onApply = { steps, repeatable ->
                viewModel.setSceneGate(scene.uid, steps, repeatable)
                onDirty(true)
                showStepsDialog = false
            },
            onCancel = { showStepsDialog = false },
        )
    }
}

@Composable
private fun SceneDeathRow(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var pendingReplacePreviousTitle by remember { mutableStateOf<String?>(null) }

    CheckRow(
        label = stringResource(R.string.item_editor_scene_death_scene),
        checked = scene.isDeathScene,
        onCheckedChange = { checked ->
            if (checked) {
                val previous = viewModel.scenes.firstOrNull {
                    it.isDeathScene && it.uid != scene.uid
                }
                if (previous != null) {
                    pendingReplacePreviousTitle = previous.title.ifBlank {
                        context.getString(R.string.label_without_title)
                    }
                } else {
                    viewModel.setDeathScene(scene.uid, true)
                    onDirty(true)
                }
            } else {
                viewModel.setDeathScene(scene.uid, false)
                onDirty(true)
            }
        },
    )

    if (scene.isDeathScene) {
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.item_editor_scene_death_options_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckRow(
                label = stringResource(R.string.item_editor_scene_death_allow_step_back),
                checked = scene.deathAllowStepBack,
                onCheckedChange = {
                    viewModel.setSceneDeathAllowStepBack(scene.uid, it)
                    onDirty(false)
                },
            )
            CheckRow(
                label = stringResource(R.string.item_editor_scene_death_allow_chapter_restart),
                checked = scene.deathAllowChapterRestart,
                onCheckedChange = {
                    viewModel.setSceneDeathAllowChapterRestart(scene.uid, it)
                    onDirty(false)
                },
            )
            CheckRow(
                label = stringResource(R.string.item_editor_scene_death_allow_adventure_restart),
                checked = scene.deathAllowAdventureRestart,
                onCheckedChange = {
                    viewModel.setSceneDeathAllowAdventureRestart(scene.uid, it)
                    onDirty(false)
                },
            )
        }
    }

    pendingReplacePreviousTitle?.let { prevTitle ->
        ReplaceDeathSceneDialog(
            previousTitle = prevTitle,
            newTitle = scene.title.ifBlank {
                context.getString(R.string.label_without_title)
            },
            onConfirm = {
                viewModel.setDeathScene(scene.uid, true)
                onDirty(true)
                pendingReplacePreviousTitle = null
            },
            onDismiss = { pendingReplacePreviousTitle = null },
        )
    }
}

@Composable
private fun SceneChoicesBlock(
    scene: EditorScene,
    viewModel: AdventureEditorViewModel,
    onDirty: (structural: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showTemplatePicker by remember { mutableStateOf(false) }
    var pendingArgsTemplate by remember { mutableStateOf<EditorTemplate?>(null) }

    if (scene.choices.isEmpty()) {
        Text(
            text = stringResource(R.string.editor_choices_count_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
    }
    scene.choices.forEach { choice ->
        EditorChoiceItem(
            sceneUid = scene.uid,
            choice = choice,
            viewModel = viewModel,
            onDirty = onDirty,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            viewModel.addChoiceToScene(scene.uid)
            onDirty(true)
        }) {
            Text(stringResource(R.string.item_editor_scene_add_choice))
        }
        TextButton(onClick = {
            if (viewModel.templates.isEmpty()) {
                Toast.makeText(
                    context,
                    R.string.editor_template_no_templates_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                showTemplatePicker = true
            }
        }) {
            Text(stringResource(R.string.editor_add_from_template))
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = viewModel.templates.toList(),
            onPicked = { template ->
                showTemplatePicker = false
                if (template.parameters.isEmpty()) {
                    val ok = viewModel.addChoiceFromTemplate(
                        sceneUid = scene.uid,
                        templateUid = template.id,
                        args = emptyMap(),
                    )
                    if (ok) {
                        onDirty(true)
                        Toast.makeText(
                            context,
                            R.string.editor_toast_template_applied,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            R.string.editor_template_apply_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    pendingArgsTemplate = template
                }
            },
            onDismiss = { showTemplatePicker = false },
        )
    }

    pendingArgsTemplate?.let { template ->
        TemplateArgsDialog(
            template = template,
            onApply = { args ->
                val ok = viewModel.addChoiceFromTemplate(
                    sceneUid = scene.uid,
                    templateUid = template.id,
                    args = args,
                )
                if (ok) {
                    onDirty(true)
                    Toast.makeText(
                        context,
                        R.string.editor_toast_template_applied,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.editor_template_apply_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                pendingArgsTemplate = null
            },
            onDismiss = { pendingArgsTemplate = null },
        )
    }
}