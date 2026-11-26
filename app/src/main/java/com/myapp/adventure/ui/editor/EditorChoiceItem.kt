package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.ui.theme.AdventureTheme
import kotlinx.coroutines.delay

private const val CHOICE_TEXT_DEBOUNCE_MS = 300L
private const val CHOICE_COND_DEBOUNCE_MS = 300L

@Composable
fun EditorChoiceItem(
    sceneUid: String,
    choice: EditorChoice,
    viewModel: AdventureEditorViewModel,
    onDirty: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded = choice.uid in state.expandedChoices
    val canOpenTarget = choice.nextSceneId.isNotBlank() &&
            state.scenes.any { it.id == choice.nextSceneId }
    var pendingDelete by remember { mutableStateOf(false) }
    var showEffectsDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            ChoiceHeader(
                choice = choice,
                expanded = expanded,
                canOpenTarget = canOpenTarget,
                onOpenTarget = {
                    if (canOpenTarget) viewModel.openSceneById(choice.nextSceneId)
                },
                onToggle = { viewModel.toggleChoiceExpanded(choice.uid) },
                onEffectsClick = { showEffectsDialog = true },
                onDeleteClick = { pendingDelete = true },
            )
            if (expanded) {
                ChoiceBody(
                    sceneUid = sceneUid,
                    choice = choice,
                    viewModel = viewModel,
                    onDirty = onDirty,
                )
            }
        }
    }

    if (pendingDelete) {
        DeleteChoiceDialog(
            choiceText = choice.text,
            onConfirm = {
                viewModel.removeChoiceByUid(sceneUid, choice.uid)
                onDirty(true)
                pendingDelete = false
            },
            onDismiss = { pendingDelete = false },
        )
    }
    if (showEffectsDialog) {
        ChoiceEffectsDialog(
            choiceText = choice.text,
            initialTags = choice.tags,
            onApply = { newTags ->
                viewModel.applyChoiceTags(sceneUid, choice.uid, newTags)
                onDirty(false)
                showEffectsDialog = false
            },
            onDismiss = { showEffectsDialog = false },
        )
    }
}

@Composable
private fun ChoiceHeader(
    choice: EditorChoice,
    expanded: Boolean,
    canOpenTarget: Boolean,
    onOpenTarget: () -> Unit,
    onToggle: () -> Unit,
    onEffectsClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▼" else "▶",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = if (expanded) {
                stringResource(R.string.item_editor_choice_text_hint)
            } else {
                choice.text.ifBlank { stringResource(R.string.item_editor_choice_no_text) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (expanded) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        val badges = buildList {
            if (choice.tags.isNotEmpty()) add("⚙ ${choice.tags.size}")
            if (choice.nextSceneId.isNotEmpty()) add("🔗")
            if (choice.conditionTag.isNotEmpty()) add("?")
            if (choice.visibilityConditionTag.isNotEmpty()) add("👁")
            if (choice.subroutineCall != null) add("⚡")
            if (choice.templateUid != null) add("📦")
        }.joinToString(" ")
        if (badges.isNotEmpty()) {
            Text(
                text = badges,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Text(
            text = "→",
            color = if (canOpenTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clickable(enabled = canOpenTarget, onClick = onOpenTarget)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
            text = "⚙",
            color = AdventureTheme.extendedColors.statusWarning,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clickable(onClick = onEffectsClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
            text = "✕",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clickable(onClick = onDeleteClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChoiceBody(
    sceneUid: String,
    choice: EditorChoice,
    viewModel: AdventureEditorViewModel,
    onDirty: (Boolean) -> Unit,
) {
    var localText by remember(choice.uid) { mutableStateOf(choice.text) }
    LaunchedEffect(choice.text) {
        if (choice.text != localText) localText = choice.text
    }
    LaunchedEffect(localText) {
        if (localText != choice.text) {
            delay(CHOICE_TEXT_DEBOUNCE_MS)
            viewModel.updateChoiceText(sceneUid, choice.uid, localText)
            onDirty(false)
        }
    }
    BareTextField(
        value = localText,
        onValueChange = { localText = it },
        placeholder = stringResource(R.string.item_editor_choice_text_hint),
        modifier = Modifier.fillMaxWidth(),
    )

    NextScenePicker(sceneUid = sceneUid, choice = choice, viewModel = viewModel, onDirty = onDirty)

    ChoiceSubroutineRow(
        sceneUid = sceneUid,
        choice = choice,
        viewModel = viewModel,
        onDirty = onDirty,
    )

    // Ф4.10: make the template origin explicit. The 📦 badge is easy to
    // miss; this row answers "why does this choice exist without a
    // subroutine call?".
    choice.templateUid?.let { templateId ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.item_editor_choice_template_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = templateId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    var localCondition by remember(choice.uid) { mutableStateOf(choice.conditionTag) }
    LaunchedEffect(choice.conditionTag) {
        if (choice.conditionTag != localCondition) localCondition = choice.conditionTag
    }
    LaunchedEffect(localCondition) {
        if (localCondition != choice.conditionTag) {
            delay(CHOICE_COND_DEBOUNCE_MS)
            viewModel.updateChoiceCondition(sceneUid, choice.uid, localCondition)
            onDirty(false)
        }
    }
    BareTextField(
        value = localCondition,
        onValueChange = { localCondition = it },
        placeholder = stringResource(R.string.item_editor_choice_condition_hint),
        modifier = Modifier.fillMaxWidth(),
    )

    var localVisibility by remember(choice.uid) {
        mutableStateOf(choice.visibilityConditionTag)
    }
    LaunchedEffect(choice.visibilityConditionTag) {
        if (choice.visibilityConditionTag != localVisibility) {
            localVisibility = choice.visibilityConditionTag
        }
    }
    LaunchedEffect(localVisibility) {
        if (localVisibility != choice.visibilityConditionTag) {
            delay(CHOICE_COND_DEBOUNCE_MS)
            viewModel.updateChoiceVisibilityCondition(sceneUid, choice.uid, localVisibility)
            onDirty(false)
        }
    }
    BareTextField(
        value = localVisibility,
        onValueChange = { localVisibility = it },
        placeholder = stringResource(R.string.item_editor_choice_visibility_hint),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NextScenePicker(
    sceneUid: String,
    choice: EditorChoice,
    viewModel: AdventureEditorViewModel,
    onDirty: (Boolean) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showNewSceneDialog by remember { mutableStateOf(false) }

    val scenes = viewModel.scenes
    val hasOrphan = choice.nextSceneId.isNotEmpty() &&
            scenes.none { it.id == choice.nextSceneId }
    val currentScene = scenes.firstOrNull { it.id == choice.nextSceneId }
    val display = when {
        choice.nextSceneId.isEmpty() -> "—"
        choice.nextSceneId == "@self" -> "@self"
        currentScene != null -> currentScene.title.ifBlank { choice.nextSceneId }
        else -> choice.nextSceneId
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.item_editor_choice_next_scene_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .clickable { showPicker = true }
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        )
        if (hasOrphan) {
            Text(
                text = "⚠",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { showNewSceneDialog = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }

    if (showPicker) {
        ScenePickerDialog(
            scenes = scenes,
            initialSceneId = choice.nextSceneId,
            allowSelf = false,
            onPicked = { picked ->
                viewModel.setChoiceNextSceneId(sceneUid, choice.uid, picked)
                onDirty(true)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showNewSceneDialog) {
        NewSceneDialog(
            onCreate = { title ->
                val newScene = viewModel.addSceneAndReturn(title)
                viewModel.setChoiceNextSceneId(sceneUid, choice.uid, newScene.id)
                onDirty(true)
                showNewSceneDialog = false
            },
            onDismiss = { showNewSceneDialog = false },
        )
    }
}

/**
 * Ф4.8: "Подпрограмма: …" row on a choice. Opens the shared
 * [SubroutineCallDialog] with global variable names; the engine validates
 * the call. A blank call is "none"; clearing writes `null`.
 */
@Composable
private fun ChoiceSubroutineRow(
    sceneUid: String,
    choice: EditorChoice,
    viewModel: AdventureEditorViewModel,
    onDirty: (Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val globalNames = state.variables
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val call = choice.subroutineCall?.takeIf { it.subroutineId.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.subroutine_choice_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = call?.subroutineId ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .clickable { showDialog = true }
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        )
        if (call != null) {
            Text(
                text = "✕",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clickable {
                        viewModel.setChoiceSubroutineCall(sceneUid, choice.uid, null)
                        onDirty(true)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (showDialog) {
        SubroutineCallDialog(
            subroutines = state.subroutines,
            inputSources = globalNames,
            outputTargets = globalNames,
            initial = choice.subroutineCall,
            title = stringResource(R.string.subroutine_call_dialog_choice_title),
            showChoiceAvailabilityHint = true,
            onApply = { newCall ->
                viewModel.setChoiceSubroutineCall(sceneUid, choice.uid, newCall)
                onDirty(true)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            onClear = if (call != null) {
                {
                    viewModel.setChoiceSubroutineCall(sceneUid, choice.uid, null)
                    onDirty(true)
                    showDialog = false
                }
            } else {
                null
            },
        )
    }
}