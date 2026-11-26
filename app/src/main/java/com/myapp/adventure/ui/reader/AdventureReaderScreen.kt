package com.myapp.adventure.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.adventure.R
import com.myapp.adventure.engine.ChoiceState
import com.myapp.adventure.engine.Reason
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.step.StepSourceManager
import com.myapp.adventure.ui.theme.FontScopeProvider
import com.myapp.adventure.utils.FontScope

/**
 * Phase 3.4-b: main reader screen — HUD, scene text, gate banner, choices,
 * death/end buttons. Replaces the old View `render*` block.
 *
 * The screen is a pure renderer over `viewModel.state` + the derived
 * `currentScene()` / `getChoiceStates()` / HUD counts. All mutations go
 * through the callbacks; `ReaderRoute` routes them into the ViewModel.
 *
 * Full-screen overlays (HUD lists in 3.4-c, reader menu and warning list
 * in 3.4-d, debug panel in 3.4-e) are rendered inside this screen and
 * replace its content. Blocking loading/startup dialogs are rendered by
 * the owning route ([ReaderRoute]) as Compose dialogs.
 */
private enum class ReaderOverlay(val key: String) {
    HEALTH("health"),
    INVENTORY("inventory"),
    JOURNAL("journal"),
    MENU("menu"),
    SLOTS("slots"),
    WARNINGS("warnings"),
    DEBUG("debug");

    companion object {
        fun fromKey(key: String): ReaderOverlay? = entries.firstOrNull { it.key == key }
    }
}

@Composable
fun AdventureReaderScreen(
    viewModel: AdventureReaderViewModel,
    onPreviewExit: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onChoiceClick: (Choice) -> Unit,
    onStepBackClick: () -> Unit,
    onChapterRestartClick: () -> Unit,
    onAdventureRestartClick: () -> Unit,
    onMenuRestartClick: () -> Unit,
    onEndRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scene = viewModel.currentScene()
    val choiceStates = viewModel.getChoiceStates()

    var overlayKey by rememberSaveable { mutableStateOf("") }

    // One-shot: the menu navigated away and the reader is being shown again.
    // Suppress the restored overlay on the very first frame (no menu flash on
    // return), then clear the saved key so the next open starts clean.
    var closeOverlayPending by remember {
        mutableStateOf(viewModel.consumeOverlayCloseOnNextResume())
    }
    LaunchedEffect(Unit) {
        if (closeOverlayPending) {
            overlayKey = ""
            closeOverlayPending = false
        }
    }
    val effectiveOverlayKey = if (closeOverlayPending) "" else overlayKey
    val overlay = ReaderOverlay.fromKey(effectiveOverlayKey)

    // Ф5.5: loading a slot returns to the scene, not to the slots list.
    LaunchedEffect(state.slotLoaded) {
        if (state.slotLoaded) {
            viewModel.consumeSlotLoaded()
            overlayKey = ""
        }
    }

    if (overlay != null) {
        BackHandler { overlayKey = "" }
        when (overlay) {
            ReaderOverlay.HEALTH,
            ReaderOverlay.INVENTORY,
            ReaderOverlay.JOURNAL -> {
                val titleRes = when (overlay) {
                    ReaderOverlay.HEALTH -> R.string.reader_health_dialog_title
                    ReaderOverlay.INVENTORY -> R.string.reader_inventory_dialog_title
                    else -> R.string.reader_journal_dialog_title
                }
                val items = when (overlay) {
                    ReaderOverlay.HEALTH ->
                        viewModel.getHealthItems().map { HudListItem(it.first, it.second) }
                    ReaderOverlay.INVENTORY ->
                        viewModel.getInventoryItems().map { HudListItem(it.first, it.second) }
                    else -> viewModel.getJournalItems().map { HudListItem(it, null) }
                }
                HudListScreen(
                    titleRes = titleRes,
                    items = items,
                    onBack = { overlayKey = "" },
                )
            }
            ReaderOverlay.MENU -> {
                ReaderMenuScreen(
                    canStepBack = !state.previewMode && !state.isDead &&
                        state.previousStepSnapshot != null,
                    warningsCount = state.cachedPlayerWarnings.size,
                    onBack = { overlayKey = "" },
                    onMainMenu = {
                        // Keep the menu visible during the exit transition;
                        // it is closed when the reader is shown again.
                        viewModel.requestOverlayCloseOnNextResume()
                        onNavigateToMenu()
                    },
                    onSlots = {
                        overlayKey = ReaderOverlay.SLOTS.key
                        viewModel.refreshSlots()
                    },
                    onStepBack = {
                        overlayKey = ""
                        onStepBackClick()
                    },
                    onRestart = {
                        overlayKey = ""
                        onMenuRestartClick()
                    },
                    onWarnings = { overlayKey = ReaderOverlay.WARNINGS.key },
                    onSettings = {
                        viewModel.requestOverlayCloseOnNextResume()
                        onNavigateToSettings()
                    },
                )
            }
            ReaderOverlay.SLOTS -> {
                ReaderSlotsScreen(
                    slots = state.manualSlots,
                    loading = state.slotsLoading,
                    error = state.slotsError,
                    currentChapterNumber = viewModel.currentChapterNumber(),
                    onBack = { overlayKey = "" },
                    onCreate = viewModel::createSlot,
                    onLoad = viewModel::loadSlot,
                    onDelete = viewModel::deleteSlot,
                    onRetry = viewModel::refreshSlots,
                )
            }
            ReaderOverlay.WARNINGS -> {
                WarningInfoScreen(
                    warnings = viewModel.getCachedWarnings(),
                    onBack = { overlayKey = "" },
                )
            }
            ReaderOverlay.DEBUG -> {
                DebugPanelScreen(
                    sceneId = state.currentSceneId,
                    stats = state.stats,
                    flags = state.flags,
                    gateRequiredSteps = if (state.isGateActive) scene?.gate?.requiredSteps else null,
                    remainingSteps = state.remainingSteps,
                    onBack = { overlayKey = "" },
                    onSetStat = viewModel::debugSetStat,
                    onDeleteStat = viewModel::debugDeleteStat,
                    onAddFlag = viewModel::debugAddFlag,
                    onDeleteFlag = viewModel::debugDeleteFlag,
                    onResetState = viewModel::debugResetState,
                    onSetRemainingSteps = viewModel::setDebugRemainingSteps,
                    onPassGate = viewModel::debugPassGate,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        ReaderHud(
            previewMode = state.previewMode,
            health = viewModel.getHealthValue(),
            inventory = viewModel.getInventoryCount(),
            journal = viewModel.getJournalCount(),
            onMenuClick = {
                if (state.previewMode) {
                    onPreviewExit()
                } else {
                    overlayKey = ReaderOverlay.MENU.key
                }
            },
            onHealthClick = { overlayKey = ReaderOverlay.HEALTH.key },
            onInventoryClick = { overlayKey = ReaderOverlay.INVENTORY.key },
            onJournalClick = { overlayKey = ReaderOverlay.JOURNAL.key },
            onDebugClick = {
                if (state.previewMode) {
                    overlayKey = ReaderOverlay.DEBUG.key
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            val endMessage = state.storyEndedMessage
            val bodyText = when {
                state.isDead -> {
                    val adventure = state.adventure
                    adventure?.deathScene?.text?.takeIf { it.isNotEmpty() }
                        ?: stringResource(R.string.reader_default_death_text)
                }
                endMessage != null -> {
                    if (state.previewMode) {
                        stringResource(R.string.reader_end_text_preview, endMessage)
                    } else {
                        stringResource(R.string.reader_end_text_full, endMessage)
                    }
                }
                else -> scene?.text.orEmpty()
            }
            FontScopeProvider(FontScope.READER) {
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            }

            if (!state.isDead && endMessage == null && state.isGateActive) {
                GateBanner(
                    remainingSteps = state.remainingSteps,
                    totalSteps = scene?.gate?.requiredSteps ?: 0,
                    lastKnownAbsolute = state.lastKnownAbsolute,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.isDead -> DeathActions(
                        state = state,
                        onStepBackClick = onStepBackClick,
                        onChapterRestartClick = onChapterRestartClick,
                        onAdventureRestartClick = onAdventureRestartClick,
                    )
                    endMessage != null -> EndActions(
                        previewMode = state.previewMode,
                        onRestartClick = onEndRestartClick,
                    )
                    else -> {
                        val forceDisabled = state.isGateActive
                        choiceStates.forEach { (choice, choiceState) ->
                            ChoiceButton(
                                choice = choice,
                                state = choiceState,
                                forceDisabled = forceDisabled,
                                onClick = { onChoiceClick(choice) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// HUD
// =====================================================================

@Composable
private fun ReaderHud(
    previewMode: Boolean,
    health: Int,
    inventory: Int,
    journal: Int,
    onMenuClick: () -> Unit,
    onHealthClick: () -> Unit,
    onInventoryClick: () -> Unit,
    onJournalClick: () -> Unit,
    onDebugClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onMenuClick) {
            Text(
                text = if (previewMode) stringResource(R.string.reader_preview_back)
                else stringResource(R.string.action_menu),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onHealthClick) {
            Text(
                text = stringResource(R.string.reader_hud_health_fmt, health),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onInventoryClick) {
            Text(
                text = stringResource(R.string.reader_hud_inventory_fmt, inventory),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onJournalClick) {
            Text(
                text = stringResource(R.string.reader_hud_journal_fmt, journal),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        if (previewMode) {
            TextButton(onClick = onDebugClick) {
                Text(
                    text = stringResource(R.string.action_debug),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// =====================================================================
// Gate banner
// =====================================================================

@Composable
private fun GateBanner(
    remainingSteps: Int,
    totalSteps: Int,
    lastKnownAbsolute: Int,
) {
    val context = LocalContext.current
    val progressText = if (lastKnownAbsolute < 0) {
        stringResource(R.string.reader_gate_waiting)
    } else {
        val done = (totalSteps - remainingSteps).coerceIn(0, totalSteps)
        stringResource(R.string.reader_gate_progress_fmt, done, totalSteps, remainingSteps)
    }
    val hcHint = if (StepSourceManager.isHealthConnectActive(context)) {
        stringResource(R.string.reader_gate_health_connect_hint)
    } else ""
    Text(
        text = progressText + hcHint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

// =====================================================================
// Choices
// =====================================================================

/**
 * Ф4.4 / gate Ф4: the player sees why a choice is disabled. The engine
 * returns structured [Reason]s; this is the app-side, localized wording.
 */
@Composable
private fun formatChoiceDisabledReason(reason: Reason): String = when (reason) {
    is Reason.HasFailed -> stringResource(R.string.reader_choice_reason_need_fmt, reason.name)
    is Reason.VarRefFailed -> stringResource(R.string.reader_choice_reason_need_fmt, reason.name)
    is Reason.LiteralFailed -> stringResource(R.string.reader_choice_reason_literal)
    is Reason.CompareFailed -> stringResource(
        R.string.reader_choice_reason_compare_fmt,
        reason.varName,
        compareSymbol(reason.op),
        reason.expectedValue,
        reason.actualValue,
    )
    is Reason.InRangeFailed -> stringResource(
        R.string.reader_choice_reason_range_fmt,
        reason.varName,
        reason.min,
        reason.max,
        reason.actualValue,
    )
    is Reason.NotFailed -> stringResource(R.string.reader_choice_reason_not)
    is Reason.AndFailed -> formatChoiceDisabledReason(reason.failedReason)
    is Reason.OrFailed -> formatChoiceDisabledReason(reason.leftReason) +
            stringResource(R.string.reader_choice_reason_or) +
            formatChoiceDisabledReason(reason.rightReason)
}

private fun compareSymbol(op: CompareOp): String = when (op) {
    CompareOp.LT -> "<"
    CompareOp.LE -> "<="
    CompareOp.GT -> ">"
    CompareOp.GE -> ">="
    CompareOp.EQ -> "=="
    CompareOp.NE -> "!="
}

@Composable
private fun ChoiceButton(
    choice: Choice,
    state: ChoiceState,
    forceDisabled: Boolean,
    onClick: () -> Unit,
) {
    when {
        forceDisabled -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(choice.text)
                    Text(
                        text = stringResource(R.string.reader_choice_gate_reason),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        state is ChoiceState.Disabled -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(choice.text)
                    Text(
                        text = stringResource(
                            R.string.reader_choice_disabled_reason_fmt,
                            formatChoiceDisabledReason(state.reason),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        else -> {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(choice.text)
            }
        }
    }
}

// =====================================================================
// Death actions
// =====================================================================

@Composable
private fun DeathActions(
    state: ReaderUiState,
    onStepBackClick: () -> Unit,
    onChapterRestartClick: () -> Unit,
    onAdventureRestartClick: () -> Unit,
) {
    val adventure = state.adventure ?: return
    val compiled = state.compiled ?: return

    val allowStepBack = adventure.deathScene?.allowStepBack ?: true
    val allowChapter = adventure.deathScene?.allowChapterRestart ?: true
    val allowAdventure = adventure.deathScene?.allowAdventureRestart ?: true

    if (allowStepBack && state.previousStepSnapshot != null) {
        Button(
            onClick = onStepBackClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reader_death_button_step_back))
        }
    }

    val currentChapterIdx =
        compiled.chapterIndexBySceneId[SceneId(state.currentSceneId)] ?: 0
    val chapterSnapshot = state.chapterStartSnapshot
    if (allowChapter && chapterSnapshot != null && chapterSnapshot.chapterIndex == currentChapterIdx) {
        Button(
            onClick = onChapterRestartClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reader_death_button_chapter_restart))
        }
    }

    if (allowAdventure) {
        Button(
            onClick = onAdventureRestartClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.previewMode) R.string.reader_death_button_restart_preview
                    else R.string.reader_death_button_restart,
                )
            )
        }
    }
}

// =====================================================================
// End actions
// =====================================================================

@Composable
private fun EndActions(previewMode: Boolean, onRestartClick: () -> Unit) {
    Button(
        onClick = onRestartClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (previewMode) R.string.reader_end_button_restart_preview
                else R.string.reader_end_button_restart,
            )
        )
    }
}