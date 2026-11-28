package com.myapp.adventure.ui.list

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import com.myapp.adventure.ui.AppSection
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.utils.ProgressManager
import com.myapp.adventure.utils.QuestStorage
import com.myapp.adventure.utils.SlotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DetailsConfirm {
    RESTART,
    DELETE,
}

@Composable
fun AdventureDetailsScreen(
    fileUri: String,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onRestart: () -> Unit,
    onLoadSlot: () -> Unit,
    onDeleted: () -> Unit,
    onNavigateGlobal: (AppSection) -> Unit,
    canOpenReader: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AdventureDetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = remember(fileUri) { fileUri.toUri() }
    var confirm by remember { mutableStateOf<DetailsConfirm?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.details_header),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            GlobalMenuButton(
                current = null,
                canOpenReader = canOpenReader,
                onNavigate = onNavigateGlobal,
            )
        }

        when {
            state.isLoading -> Text(
                text = stringResource(R.string.details_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            state.error || state.adventure == null -> {
                Text(
                    text = stringResource(R.string.details_error),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                TextButton(onClick = viewModel::load) {
                    Text(stringResource(R.string.details_retry))
                }
            }

            else -> {
                val adventure = requireNotNull(state.adventure)
                Text(
                    text = adventure.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (adventure.author.isNotBlank()) {
                    Text(
                        text = adventure.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (adventure.description.isNotBlank()) {
                    Text(
                        text = adventure.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.details_meta_fmt,
                        adventure.chapters.size,
                        adventure.chapters.sumOf { it.scenes.size },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Button(
                    onClick = onRead,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.details_read))
                }
                OutlinedButton(
                    onClick = { confirm = DetailsConfirm.RESTART },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.details_restart))
                }
                OutlinedButton(
                    onClick = onLoadSlot,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.details_load_slot))
                }
                TextButton(
                    onClick = { confirm = DetailsConfirm.DELETE },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    when (confirm) {
        DetailsConfirm.RESTART -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.details_restart_title)) },
            text = { Text(stringResource(R.string.details_restart_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        busy = true
                        scope.launch {
                            ProgressManager.deleteProgress(context, uri)
                            ProgressManager.flush(context)
                            busy = false
                            onRestart()
                        }
                    },
                ) {
                    Text(stringResource(R.string.details_restart_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )

        DetailsConfirm.DELETE -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.details_delete_title)) },
            text = { Text(stringResource(R.string.details_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        busy = true
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                QuestStorage.deleteQuestFolder(context, uri)
                            }
                            if (!deleted) {
                                busy = false
                                Toast.makeText(
                                    context,
                                    R.string.details_delete_error,
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            ProgressManager.deleteProgress(context, uri)
                            ProgressManager.flush(context)
                            runCatching { SlotManager.deleteAllSlots(context, uri) }
                            busy = false
                            onDeleted()
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.details_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )

        null -> Unit
    }
}
