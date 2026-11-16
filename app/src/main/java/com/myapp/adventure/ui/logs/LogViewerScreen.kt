package com.myapp.adventure.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: LogViewerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    // One-shot events → Toast. collectLatest cancels a previous collector
    // if the screen leaves composition and comes back, which is what we
    // want for one-shot toasts.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LogViewerEvent.LogCleared ->
                    Toast.makeText(context, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LogViewerContent(
        state = state,
        showClearDialog = showClearDialog,
        onCopyClick = { copyLogToClipboard(context, state.content) },
        onClearClick = { showClearDialog = true },
        onClearConfirmed = {
            showClearDialog = false
            viewModel.clearLog()
        },
        onClearDismissed = { showClearDialog = false },
        modifier = modifier,
    )
}

@Composable
private fun LogViewerContent(
    state: LogViewerUiState,
    showClearDialog: Boolean,
    onCopyClick: () -> Unit,
    onClearClick: () -> Unit,
    onClearConfirmed: () -> Unit,
    onClearDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logs_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCopyClick) {
                Text(stringResource(R.string.logs_copy))
            }
            TextButton(onClick = onClearClick) {
                Text(
                    text = stringResource(R.string.dialog_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Text(
            text = if (state.isLoading) stringResource(R.string.logs_loading) else state.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = onClearDismissed,
            title = { Text(stringResource(R.string.logs_clear_title)) },
            text = { Text(stringResource(R.string.logs_clear_message)) },
            confirmButton = {
                TextButton(onClick = onClearConfirmed) {
                    Text(stringResource(R.string.dialog_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = onClearDismissed) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

private fun copyLogToClipboard(context: Context, text: String) {
    if (text.isEmpty()) {
        Toast.makeText(context, R.string.logs_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    @Suppress("UsePropertyAccessSyntax")
    clipboard.setPrimaryClip(ClipData.newPlainText("log", text))
    Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
}