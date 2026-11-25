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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.myapp.adventure.ui.theme.FontScopeProvider
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.FontScope
import com.myapp.adventure.utils.FontScopeSettings
import com.myapp.adventure.utils.FontSettings
import com.myapp.adventure.utils.ThemeManager
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
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

        // ----- Fonts -----
        SectionHeader(stringResource(R.string.settings_fonts_title))
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

        FontScopeSection(
            scope = FontScope.READER,
            title = stringResource(R.string.settings_font_reader_label),
            settings = state.fontSettings.reader,
            options = state.availableFonts,
            sample = stringResource(R.string.settings_font_sample_reader),
            onFontSelected = { viewModel.setFontName(FontScope.READER, it) },
            onSizeChange = { viewModel.setFontSize(FontScope.READER, it) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        FontScopeSection(
            scope = FontScope.UI,
            title = stringResource(R.string.settings_font_interface_label),
            settings = state.fontSettings.ui,
            options = state.availableFonts,
            sample = stringResource(R.string.settings_font_sample_interface),
            onFontSelected = { viewModel.setFontName(FontScope.UI, it) },
            onSizeChange = { viewModel.setFontSize(FontScope.UI, it) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        FontScopeSection(
            scope = FontScope.EDITOR,
            title = stringResource(R.string.settings_font_editor_label),
            settings = state.fontSettings.editor,
            options = state.availableFonts,
            sample = stringResource(R.string.settings_font_sample_editor),
            onFontSelected = { viewModel.setFontName(FontScope.EDITOR, it) },
            onSizeChange = { viewModel.setFontSize(FontScope.EDITOR, it) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ----- Logging -----
        CheckboxRow(
            label = stringResource(R.string.settings_logging),
            checked = state.loggingEnabled,
            enabled = true,
            onCheckedChange = { viewModel.setLoggingEnabled(it) },
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
private fun FontScopeSection(
    scope: FontScope,
    title: String,
    settings: FontScopeSettings,
    options: List<String>,
    sample: String,
    onFontSelected: (String) -> Unit,
    onSizeChange: (Int) -> Unit,
) {
    SectionHeader(title)
    FontPicker(
        selected = settings.fontName,
        options = options,
        onSelected = onFontSelected,
        modifier = Modifier.fillMaxWidth(),
    )
    FontSizeRow(
        sizePx = settings.sizePx,
        defaultSizePx = FontSettings.Default[scope].sizePx,
        onSizeChange = onSizeChange,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
    ) {
        FontScopeProvider(scope) {
            Text(
                text = sample,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSizeRow(
    sizePx: Int,
    defaultSizePx: Int,
    onSizeChange: (Int) -> Unit,
) {
    val resetDescription = stringResource(R.string.settings_font_size_reset)
    // Пока пользователь ведёт палец, меняем только число: если масштабировать
    // текст сразу, вся разметка пересчитывается и ползунок «убегает» из-под пальца.
    var pendingSizePx by remember(sizePx) { mutableFloatStateOf(sizePx.toFloat()) }
    val pendingInt = pendingSizePx.roundToInt()
    val sizeDescription = stringResource(
        R.string.settings_font_size_content_description_fmt,
        pendingInt,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { onSizeChange(defaultSizePx) }) {
            Text(
                text = "↺",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = resetDescription },
            )
        }
        Text(
            text = pendingInt.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .semantics { contentDescription = sizeDescription },
        )
        Slider(
            value = pendingSizePx,
            onValueChange = { pendingSizePx = it },
            onValueChangeFinished = { onSizeChange(pendingSizePx.roundToInt()) },
            valueRange = FontSettings.MIN_SIZE_PX.toFloat()..FontSettings.MAX_SIZE_PX.toFloat(),
            steps = 0,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.dp),
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
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
    val context = LocalContext.current
    val selectedFamily = remember(selected) {
        FontManager.getFontFamily(context, selected)
    }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected,
                fontFamily = selectedFamily,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { font ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = font,
                            fontFamily = FontManager.getFontFamily(context, font),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(font)
                    },
                )
            }
        }
    }
}