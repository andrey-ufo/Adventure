package com.myapp.adventure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.ui.AppNavHost
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ThemeManager
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme bootstrap must happen BEFORE super.onCreate(): AppCompat reads
        // the night mode and theme during its own onCreate, and changing them
        // afterwards causes a flash or a spurious recreate().
        ThemeManager.initialize(this)
        FontManager.initialize(this)
        runBlocking { ThemeManager.awaitInitialized() }
        ThemeManager.apply(this)

        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("adventure_prefs", MODE_PRIVATE)
        val savedUri = prefs.getString("adventure_folder_uri", null)
        if (savedUri != null) {
            Logger.init(this, savedUri)
        }

        com.myapp.adventure.step.StepSourceManager.start(this)

        if (savedUri != null) {
            ensureStepCounterServiceRunning()
        }

        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    /**
     * Best-effort start of the foreground step-counter service on every
     * activity launch. The service handles re-entry gracefully; the
     * [StepCounterService.isRunning] guard avoids redundant work when it is
     * already running.
     */
    private fun ensureStepCounterServiceRunning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.log(
                this,
                "MainActivity: ACTIVITY_RECOGNITION denied; not starting service"
            )
            return
        }
        if (StepCounterService.isRunning) return
        try {
            val intent = Intent(this, StepCounterService::class.java).apply {
                action = StepCounterConstants.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
            Logger.log(this, "MainActivity: requested StepCounterService start")
        } catch (e: Exception) {
            Logger.log(this, "MainActivity: failed to start StepCounterService: ${e.message}")
        }
    }
}