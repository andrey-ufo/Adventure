package com.myapp.adventure.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.myapp.adventure.step.StepSourceManager
import com.myapp.adventure.utils.Logger

/**
 * Restarts the step pipeline after a device reboot or an app update.
 *
 *  - BOOT_COMPLETED: the system killed our process at shutdown; nothing else
 *    will restart the foreground service, so we do it ourselves.
 *  - MY_PACKAGE_REPLACED: user updated the app; the process was killed during
 *    install and would otherwise stay dead until the user opens the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
        val folderUri = prefs.getString("adventure_folder_uri", null) ?: return

        Logger.init(context, folderUri)
        Logger.log(context, "BootReceiver: received $action")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.log(context, "BootReceiver: ACTIVITY_RECOGNITION denied; not starting service")
        } else {
            try {
                val svc = Intent(context, StepCounterService::class.java)
                    .setAction(StepCounterConstants.ACTION_START)
                ContextCompat.startForegroundService(context, svc)
                Logger.log(context, "BootReceiver: StepCounterService started")
            } catch (e: Exception) {
                Logger.log(context, "BootReceiver: failed to start service: ${e.message}")
            }
        }

        StepSourceManager.start(context)
    }
}