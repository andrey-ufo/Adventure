package com.myapp.adventure.ui.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.step.HealthConnectStepSource
import com.myapp.adventure.step.StepSourceManager
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.FontScope
import com.myapp.adventure.utils.FontSettings
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ThemeManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Snapshot of the Settings screen. Font changes are applied immediately
 * via [FontManager]; no confirmation dialog or Activity recreation is
 * needed because the Compose theme observes the font state.
 */
data class SettingsUiState(
    val themeMode: ThemeManager.Mode = ThemeManager.Mode.SYSTEM,
    val fontSettings: FontSettings = FontSettings.Default,
    val availableFonts: List<String> = emptyList(),
    val loggingEnabled: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    val healthConnectEnabled: Boolean = false,
    val healthConnectActive: Boolean = false,
    val notificationGranted: Boolean = false,
    val activityGranted: Boolean = false,
)

sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
    data object RequestHcPermission : SettingsEvent
    data object RequestNotificationPermission : SettingsEvent
    data object RequestActivityPermission : SettingsEvent
    data object OpenSystemSettings : SettingsEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { refreshAll() }

    /** Reloads all visible state from managers/prefs. Call from ON_RESUME. */
    fun refreshAll() {
        val app = getApplication<Application>()
        val ctx = app.applicationContext

        val hcAvailable = HealthConnectClient.getSdkStatus(
            ctx, HealthConnectStepSource.PROVIDER_PACKAGE
        ) == HealthConnectClient.SDK_AVAILABLE

        _uiState.update {
            it.copy(
                themeMode = ThemeManager.getMode(ctx),
                fontSettings = FontManager.getSettings(ctx),
                availableFonts = FontManager.getAvailableFonts(ctx),
                loggingEnabled = app.getSharedPreferences("adventure_prefs", 0)
                    .getBoolean("logging_enabled", false),
                healthConnectAvailable = hcAvailable,
                healthConnectEnabled = StepSourceManager.isHealthConnectEnabled(ctx),
                healthConnectActive = StepSourceManager.isHealthConnectActive(ctx),
                notificationGranted = isNotificationGranted(app),
                activityGranted = isActivityGranted(app),
            )
        }
    }

    // ----- Theme -----

    fun setThemeMode(mode: ThemeManager.Mode) {
        val app = getApplication<Application>()
        if (mode != ThemeManager.getMode(app)) {
            ThemeManager.setMode(app, mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    // ----- Logging -----

    fun setLoggingEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        Logger.setEnabled(app, enabled)
        _uiState.update { it.copy(loggingEnabled = enabled) }
        viewModelScope.launch {
            _events.send(
                SettingsEvent.ShowToast(
                    app.getString(
                        if (enabled) R.string.settings_logging_enabled
                        else R.string.settings_logging_disabled
                    )
                )
            )
        }
    }

    // ----- Fonts -----

    fun setFontName(scope: FontScope, fontName: String) {
        val app = getApplication<Application>()
        if (fontName != FontManager.getSelectedFont(app, scope)) {
            FontManager.setSelectedFont(app, scope, fontName)
            _uiState.update { it.copy(fontSettings = FontManager.getSettings(app)) }
        }
    }

    fun setFontSize(scope: FontScope, sizePx: Int) {
        val app = getApplication<Application>()
        val clamped = FontSettings.clamp(sizePx)
        if (clamped != FontManager.getFontSizePx(app, scope)) {
            FontManager.setFontSizePx(app, scope, clamped)
            _uiState.update { it.copy(fontSettings = FontManager.getSettings(app)) }
        }
    }

    // ----- Font adding -----

    fun onFontPicked(uri: Uri) {
        val app = getApplication<Application>()
        val fileName = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else "font.ttf"
            } else "font.ttf"
        } ?: "font.ttf"

        FontManager.addFont(app, uri, fileName)
        refreshAll()
        viewModelScope.launch {
            _events.send(
                SettingsEvent.ShowToast(app.getString(R.string.settings_font_added_fmt, fileName))
            )
        }
    }

    // ----- Health Connect -----

    fun setHealthConnectEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        if (!enabled) {
            StepSourceManager.setHealthConnectEnabled(app, false)
            refreshAll()
            return
        }
        viewModelScope.launch {
            val granted = try {
                HealthConnectClient.getOrCreate(app)
                    .permissionController.getGrantedPermissions()
            } catch (_: Exception) {
                emptySet()
            }
            val required = HealthPermission.getReadPermission(StepsRecord::class)
            if (granted.contains(required)) {
                StepSourceManager.setHealthConnectEnabled(app, true)
                refreshAll()
            } else {
                _events.send(SettingsEvent.RequestHcPermission)
            }
        }
    }

    fun onHcPermissionResult(granted: Boolean) {
        val app = getApplication<Application>()
        if (granted) {
            StepSourceManager.setHealthConnectEnabled(app, true)
            viewModelScope.launch {
                _events.send(SettingsEvent.ShowToast(app.getString(R.string.settings_hc_enabled)))
            }
        } else {
            StepSourceManager.setHealthConnectEnabled(app, false)
            viewModelScope.launch {
                _events.send(
                    SettingsEvent.ShowToast(app.getString(R.string.settings_hc_permission_denied))
                )
            }
        }
        refreshAll()
    }

    // ----- Runtime permissions -----

    fun onNotificationPermissionButton() {
        if (_uiState.value.notificationGranted) {
            viewModelScope.launch { _events.send(SettingsEvent.OpenSystemSettings) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModelScope.launch { _events.send(SettingsEvent.RequestNotificationPermission) }
        }
    }

    fun onActivityPermissionButton() {
        if (_uiState.value.activityGranted) {
            viewModelScope.launch { _events.send(SettingsEvent.OpenSystemSettings) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModelScope.launch { _events.send(SettingsEvent.RequestActivityPermission) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!granted) {
            viewModelScope.launch {
                _events.send(
                    SettingsEvent.ShowToast(
                        getApplication<Application>().getString(
                            R.string.setup_toast_notifications_disabled
                        )
                    )
                )
            }
        }
        refreshAll()
    }

    fun onActivityPermissionResult(granted: Boolean) {
        val app = getApplication<Application>()
        if (!granted) {
            viewModelScope.launch {
                _events.send(
                    SettingsEvent.ShowToast(app.getString(R.string.setup_toast_activity_denied))
                )
            }
        } else {
            startStepCounterServiceIfNeeded(app)
        }
        refreshAll()
    }

    // ----- Internal -----

    private fun isNotificationGranted(app: Application): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    app, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    private fun isActivityGranted(app: Application): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED

    private fun startStepCounterServiceIfNeeded(app: Application) {
        if (!StepCounterService.isRunning) {
            val intent = Intent(app, StepCounterService::class.java).apply {
                action = StepCounterConstants.ACTION_START
            }
            app.startForegroundService(intent)
        }
    }
}