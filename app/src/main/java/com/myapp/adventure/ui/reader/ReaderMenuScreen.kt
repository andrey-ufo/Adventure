package com.myapp.adventure.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.ui.AppSection

/**
 * Phase 3.4-d: full-screen reader menu, a Compose overlay inside
 * [AdventureReaderScreen]. Replaces the View `AlertDialog.setItems` that
 * used to live in the old reader Fragment's menu dialog.
 *
 * Visibility is controlled by the caller; this composable is stateless
 * except for the "[restart]" confirmation dialog, which survives rotation
 * via [rememberSaveable].
 *
 * Items mirror the old dialog:
 *  1. main menu (navigation),
 *  2. step back (normal mode, not dead, snapshot available),
 *  3. restart with confirmation,
 *  4. warnings (only when the cached list is non-empty).
 *
 * Settings lives in the global kebab, not in this local menu.
 */
@Composable
fun ReaderMenuScreen(
    canStepBack: Boolean,
    warningsCount: Int,
    onBack: () -> Unit,
    onMainMenu: () -> Unit,
    onSlots: () -> Unit,
    onStepBack: () -> Unit,
    onRestart: () -> Unit,
    onWarnings: () -> Unit,
    currentSection: AppSection,
    canOpenReader: Boolean,
    onNavigateGlobal: (AppSection) -> Unit,
) {
    var showRestartConfirm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ReaderOverlayHeader(
            title = stringResource(R.string.reader_menu_title),
            onBack = onBack,
            currentSection = currentSection,
            canOpenReader = canOpenReader,
            onNavigateGlobal = onNavigateGlobal,
        )

        ReaderMenuItem(
            text = stringResource(R.string.reader_menu_main_menu),
            onClick = onMainMenu,
        )
        ReaderMenuItem(
            text = stringResource(R.string.reader_menu_slots),
            onClick = onSlots,
        )
        if (canStepBack) {
            ReaderMenuItem(
                text = stringResource(R.string.reader_menu_step_back),
                onClick = onStepBack,
            )
        }
        ReaderMenuItem(
            text = stringResource(R.string.reader_menu_restart),
            onClick = { showRestartConfirm = true },
        )
        if (warningsCount > 0) {
            ReaderMenuItem(
                text = stringResource(R.string.reader_menu_warnings_fmt, warningsCount),
                onClick = onWarnings,
            )
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text(stringResource(R.string.reader_restart_dialog_title)) },
            text = { Text(stringResource(R.string.reader_restart_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    onRestart()
                }) {
                    Text(stringResource(R.string.reader_restart_dialog_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReaderMenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
