package com.myapp.adventure.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.ThemeManager

@Composable
fun SettingsScreen(
    onRecreateActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val addFontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.onFontPicked(it) } }

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val required = HealthPermission.getReadPermission(StepsRecord::class)
        viewModel.onHcPermissionResult(granted.contains(required))
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onNotificationPermissionResult(it) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onActivityPermissionResult(it) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAll() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is SettingsEvent.RequestHcPermission -> {
                    val required = HealthPermission.getReadPermission(StepsRecord::class)
                    hcLauncher.launch(setOf(required))
                }
                is SettingsEvent.RequestNotificationPermission ->
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                is SettingsEvent.RequestActivityPermission ->
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                is SettingsEvent.OpenSystemSettings -> {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                }
                is SettingsEvent.RecreateActivity -> onRecreateActivity()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ----- Permissions -----
        SectionHeader(stringResource(R.string.settings_permissions_title))

        OutlinedButton(
            onClick = { viewModel.onNotificationPermissionButton() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.notificationGranted) stringResource(R.string.settings_manage_notifications)
                else stringResource(R.string.settings_enable_notifications)
            )
        }
        OutlinedButton(
            onClick = { viewModel.onActivityPermissionButton() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.activityGranted) stringResource(R.string.settings_manage_activity)
                else stringResource(R.string.settings_enable_activity)
            )
        }

        // ----- Health Connect -----
        CheckboxRow(
            label = stringResource(R.string.settings_health_connect),
            checked = state.healthConnectEnabled,
            enabled = state.healthConnectAvailable,
            onCheckedChange = { viewModel.setHealthConnectEnabled(it) },
        )
        Text(
            text = stringResource(
                when {
                    !state.healthConnectAvailable -> R.string.settings_hc_unavailable_device
                    !state.healthConnectEnabled -> R.string.settings_hc_status_off
                    state.healthConnectActive -> R.string.settings_hc_status_active
                    else -> R.string.settings_hc_status_unavailable
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ----- Theme -----
        SectionHeader(stringResource(R.string.settings_theme_title))
        ThemeManager.Mode.entries.forEach { mode ->
            RadioRow(
                label = stringResource(
                    when (mode) {
                        ThemeManager.Mode.SYSTEM -> R.string.settings_theme_system
                        ThemeManager.Mode.LIGHT -> R.string.settings_theme_light
                        ThemeManager.Mode.DARK -> R.string.settings_theme_dark
                    }
                ),
                selected = state.themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ----- Font size -----
        SectionHeader(stringResource(R.string.settings_font_size_label))
        FontManager.FONT_SCALES.forEach { (label, scale) ->
            RadioRow(
                label = label,
                selected = state.fontScale == scale,
                onClick = { viewModel.setFontScale(scale) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ----- Font selection -----
        SectionHeader(stringResource(R.string.settings_font_label))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontPicker(
                selected = state.selectedFont,
                options = state.availableFonts,
                onSelected = { viewModel.requestFontChange(it) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                addFontLauncher.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/x-font-ttf",
                        "application/vnd.ms-opentype",
                    )
                )
            }) {
                Text(stringResource(R.string.settings_add_font))
            }
        }

        // Font sample
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_font_sample),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ----- Logging -----
        CheckboxRow(
            label = stringResource(R.string.settings_logging),
            checked = state.loggingEnabled,
            enabled = true,
            onCheckedChange = { viewModel.setLoggingEnabled(it) },
        )
    }

    // Font change confirmation
    state.pendingFontConfirmation?.let { newFont ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelFontChange() },
            title = { Text(stringResource(R.string.settings_change_font_title)) },
            text = {
                Text(stringResource(R.string.settings_change_font_message_fmt, newFont))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmFontChange() }) {
                    Text(stringResource(R.string.dialog_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFontChange() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FontPicker(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { font ->
                DropdownMenuItem(
                    text = { Text(font) },
                    onClick = {
                        expanded = false
                        onSelected(font)
                    },
                )
            }
        }
    }
}