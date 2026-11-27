package com.myapp.adventure.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapp.adventure.ui.AppSection

/**
 * Phase 3.4-e: developer debug panel, a Compose overlay inside
 * [AdventureReaderScreen] (preview mode only). Replaces the large View
 * `AlertDialog` that the old reader Fragment used to build.
 *
 * Debug labels are deliberately kept as inline Russian literals, per
 * `docs/i18n-policy.md` ("Debug panel labels ... not user-facing").
 *
 * The panel is a pure renderer: it reads `ReaderUiState` fields and routes
 * every mutation through callbacks. Because the parent collects state,
 * stats/flags/gate progress update live; the old `refresh()` calls are gone.
 */
private enum class DebugDialog(val key: String) {
    EDIT_STAT("edit_stat"),
    ADD_STAT("add_stat"),
    ADD_FLAG("add_flag"),
    RESET("reset");

    companion object {
        fun fromKey(key: String): DebugDialog? = entries.firstOrNull { it.key == key }
    }
}

@Composable
fun DebugPanelScreen(
    sceneId: String,
    stats: Map<String, Int>,
    flags: List<String>,
    gateRequiredSteps: Int?,
    remainingSteps: Int,
    onBack: () -> Unit,
    currentSection: AppSection,
    canOpenReader: Boolean,
    onNavigateGlobal: (AppSection) -> Unit,
    onSetStat: (String, Int) -> Unit,
    onDeleteStat: (String) -> Unit,
    onAddFlag: (String) -> Unit,
    onDeleteFlag: (String) -> Unit,
    onResetState: () -> Unit,
    onSetRemainingSteps: (Int) -> Unit,
    onPassGate: () -> Unit,
) {
    var dialogKey by rememberSaveable { mutableStateOf("") }
    var editStatName by rememberSaveable { mutableStateOf("") }
    var editStatValue by rememberSaveable { mutableStateOf(0) }
    val dialog = DebugDialog.fromKey(dialogKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ReaderOverlayHeader(
            title = "Отладка",
            onBack = onBack,
            currentSection = currentSection,
            canOpenReader = canOpenReader,
            onNavigateGlobal = onNavigateGlobal,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = "Текущая сцена: $sceneId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Статы")
            if (stats.isEmpty()) {
                EmptyHint("(нет статов)")
            } else {
                stats.toSortedMap().forEach { (name, value) ->
                    StatRow(
                        name = name,
                        value = value,
                        onEdit = {
                            editStatName = name
                            editStatValue = value
                            dialogKey = DebugDialog.EDIT_STAT.key
                        },
                        onDelete = { onDeleteStat(name) },
                    )
                }
            }
            TextButton(onClick = { dialogKey = DebugDialog.ADD_STAT.key }) {
                Text("+ Стат")
            }

            SectionTitle("Флаги")
            if (flags.isEmpty()) {
                EmptyHint("(нет флагов)")
            } else {
                flags.sorted().forEach { flag ->
                    FlagRow(name = flag, onDelete = { onDeleteFlag(flag) })
                }
            }
            TextButton(onClick = { dialogKey = DebugDialog.ADD_FLAG.key }) {
                Text("+ Флаг")
            }

            if (gateRequiredSteps != null) {
                GateSection(
                    sceneId = sceneId,
                    requiredSteps = gateRequiredSteps,
                    remainingSteps = remainingSteps,
                    onSetRemainingSteps = onSetRemainingSteps,
                    onPassGate = onPassGate,
                )
            }

            TextButton(
                onClick = { dialogKey = DebugDialog.RESET.key },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Сбросить к начальному состоянию",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    when (dialog) {
        DebugDialog.EDIT_STAT -> EditStatDialog(
            statName = editStatName,
            initialValue = editStatValue,
            onConfirm = { value ->
                onSetStat(editStatName, value)
                dialogKey = ""
            },
            onDismiss = { dialogKey = "" },
        )
        DebugDialog.ADD_STAT -> AddStatDialog(
            onConfirm = { name, value ->
                onSetStat(name, value)
                dialogKey = ""
            },
            onDismiss = { dialogKey = "" },
        )
        DebugDialog.ADD_FLAG -> AddFlagDialog(
            onConfirm = { name ->
                onAddFlag(name)
                dialogKey = ""
            },
            onDismiss = { dialogKey = "" },
        )
        DebugDialog.RESET -> ResetStateDialog(
            onConfirm = {
                onResetState()
                dialogKey = ""
            },
            onDismiss = { dialogKey = "" },
        )
        null -> Unit
    }
}

// =====================================================================
// Sections
// =====================================================================

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatRow(
    name: String,
    value: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(onClick = onEdit) {
            Text("изм.")
        }
        TextButton(onClick = onDelete) {
            Text("×")
        }
    }
}

@Composable
private fun FlagRow(name: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete) {
            Text("×")
        }
    }
}

@Composable
private fun GateSection(
    sceneId: String,
    requiredSteps: Int,
    remainingSteps: Int,
    onSetRemainingSteps: (Int) -> Unit,
    onPassGate: () -> Unit,
) {
    var stepsInput by rememberSaveable(sceneId, requiredSteps) {
        mutableStateOf(remainingSteps.toString())
    }
    val parsed = stepsInput.toIntOrNull()

    SectionTitle("Шаги до следующей главы")
    Text(
        text = "Симуляция прогресса шагомера. При достижении порога глава откроется.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    val done = (requiredSteps - remainingSteps).coerceIn(0, requiredSteps)
    Text(
        text = "Сцена «$sceneId»: пройдено $done из $requiredSteps (осталось $remainingSteps)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = stepsInput,
            onValueChange = { stepsInput = it },
            label = { Text("Осталось шагов") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { parsed?.let(onSetRemainingSteps) },
            enabled = parsed != null && parsed >= 0,
        ) {
            Text("Применить")
        }
    }

    TextButton(onClick = onPassGate) {
        Text(
            text = "Открыть главу сейчас",
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// =====================================================================
// Sub-dialogs (Compose; replace the View AlertDialogs)
// =====================================================================

@Composable
private fun EditStatDialog(
    statName: String,
    initialValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue.toString()) }
    val parsed = value.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить значение") },
        text = {
            Column {
                Text(
                    text = statName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Значение") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
            ) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun AddStatDialog(
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("0") }
    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить стат") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Значение (число)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName, value.toIntOrNull() ?: 0) },
                enabled = trimmedName.isNotEmpty(),
            ) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun AddFlagDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить флаг") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = trimmedName.isNotEmpty(),
            ) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun ResetStateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сбросить?") },
        text = { Text("Сбросить флаги и статы к начальному состоянию?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Сбросить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
