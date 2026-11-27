package com.myapp.adventure.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.SaveSlot
import com.myapp.adventure.data.SaveSlotPaths
import com.myapp.adventure.ui.AppSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stable semantics tags shared by Robolectric and instrumented UI tests. */
internal object ReaderSlotsTestTags {
    const val CREATE = "slots_create"
    const val NAME_FIELD = "slots_name"
    const val CREATE_CONFIRM = "slots_create_confirm"
    const val LOAD_CONFIRM = "slots_load_confirm"
    const val DELETE_CONFIRM = "slots_delete_confirm"
    const val RETRY = "slots_retry"

    fun load(slotId: String): String = "slots_load_$slotId"
    fun delete(slotId: String): String = "slots_delete_$slotId"
}

/**
 * Ф5.5: manual save slots for the current adventure.
 *
 * Minimal screen, matching the existing reader overlays: list + create,
 * with explicit load/delete confirmations. All disk work lives in
 * [AdventureReaderViewModel]/`SlotManager`; this composable is stateless
 * apart from dialog visibility.
 */
@Composable
fun ReaderSlotsScreen(
    slots: List<SaveSlot>,
    loading: Boolean,
    error: String?,
    currentChapterNumber: Int,
    onBack: () -> Unit,
    currentSection: AppSection,
    canOpenReader: Boolean,
    onNavigateGlobal: (AppSection) -> Unit,
    onCreate: (String) -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var slotToLoadId by rememberSaveable { mutableStateOf<String?>(null) }
    var slotToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val atCapacity = slots.size >= SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE
    val canCreate = !loading && !atCapacity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ReaderOverlayHeader(
            title = stringResource(R.string.reader_slots_title),
            onBack = onBack,
            currentSection = currentSection,
            canOpenReader = canOpenReader,
            onNavigateGlobal = onNavigateGlobal,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Button(
                onClick = { showCreateDialog = true },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReaderSlotsTestTags.CREATE),
            ) {
                Text(stringResource(R.string.reader_slots_create))
            }

            if (atCapacity) {
                Text(
                    text = stringResource(
                        R.string.reader_slots_limit_fmt,
                        SaveSlotPaths.MAX_SLOTS_PER_ADVENTURE,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = onRetry,
                    enabled = !loading,
                    modifier = Modifier.testTag(ReaderSlotsTestTags.RETRY),
                ) {
                    Text(stringResource(R.string.reader_slots_retry))
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                loading && slots.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                slots.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.reader_slots_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                else -> {
                    slots.forEach { slot ->
                        SlotRow(
                            slot = slot,
                            enabled = !loading,
                            onLoadClick = { slotToLoadId = slot.id },
                            onDeleteClick = { slotToDeleteId = slot.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val defaultName =
            stringResource(R.string.reader_slots_default_name_fmt, currentChapterNumber)
        var name by rememberSaveable { mutableStateOf(defaultName) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.reader_slots_create_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.reader_slots_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReaderSlotsTestTags.NAME_FIELD),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        onCreate(name.trim())
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag(ReaderSlotsTestTags.CREATE_CONFIRM),
                ) {
                    Text(stringResource(R.string.dialog_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    if (slotToLoadId != null) {
        AlertDialog(
            onDismissRequest = { slotToLoadId = null },
            title = { Text(stringResource(R.string.reader_slots_load_title)) },
            text = { Text(stringResource(R.string.reader_slots_load_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = slotToLoadId
                        slotToLoadId = null
                        if (id != null) onLoad(id)
                    },
                    modifier = Modifier.testTag(ReaderSlotsTestTags.LOAD_CONFIRM),
                ) {
                    Text(stringResource(R.string.reader_slots_load))
                }
            },
            dismissButton = {
                TextButton(onClick = { slotToLoadId = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    if (slotToDeleteId != null) {
        val slotName = slots.firstOrNull { it.id == slotToDeleteId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { slotToDeleteId = null },
            title = { Text(stringResource(R.string.reader_slots_delete_title)) },
            text = {
                Text(stringResource(R.string.reader_slots_delete_message_fmt, slotName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = slotToDeleteId
                        slotToDeleteId = null
                        if (id != null) onDelete(id)
                    },
                    modifier = Modifier.testTag(ReaderSlotsTestTags.DELETE_CONFIRM),
                ) {
                    Text(stringResource(R.string.dialog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { slotToDeleteId = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun SlotRow(
    slot: SaveSlot,
    enabled: Boolean,
    onLoadClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = slot.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.reader_slots_updated_fmt,
                formatSlotTime(slot.updatedAt),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onLoadClick,
                enabled = enabled,
                modifier = Modifier.testTag(ReaderSlotsTestTags.load(slot.id)),
            ) {
                Text(stringResource(R.string.reader_slots_load))
            }
            TextButton(
                onClick = onDeleteClick,
                enabled = enabled,
                modifier = Modifier.testTag(ReaderSlotsTestTags.delete(slot.id)),
            ) {
                Text(stringResource(R.string.reader_slots_delete))
            }
        }
    }
}

private fun formatSlotTime(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
