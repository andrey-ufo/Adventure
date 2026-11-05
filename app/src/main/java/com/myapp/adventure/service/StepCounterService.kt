package com.myapp.adventure.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myapp.adventure.MainActivity
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.StepHistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StepCounterService : Service(), SensorEventListener {

    companion object {
        /** Hint for UI only. Never gate startForeground() on this. */
        var isRunning = false

        /** Last raw absolute reading from the sensor. */
        @Volatile
        var absoluteSteps: Int = 0
            private set

        /** True once at least one sensor event has been received in this process. */
        @Volatile
        var hasFirstEvent: Boolean = false
            private set

        /** True if this device has a TYPE_STEP_COUNTER sensor. */
        @Volatile
        var isSensorAvailable: Boolean = false
            private set

        private const val PREFS_NAME = "step_counter_service_prefs"
        private const val KEY_LAST_RAW = "last_raw_value"
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var isListening = false

    // ----- Baseline / delta tracking (accessed from onSensorChanged) -----

    /**
     * Last raw value we've seen. Loaded from prefs on start so a process restart
     * (without reboot) doesn't lose the baseline.
     * -1 means "unknown".
     */
    @Volatile
    private var lastRawSteps: Int = -1

    /** True once we have a valid baseline. */
    @Volatile
    private var hasBaseline: Boolean = false

    /** Accumulated delta waiting to be flushed by the background worker. */
    private val pendingDelta = AtomicInteger(0)

    /** Set by onSensorChanged when a sensor reboot is detected; consumed in the worker. */
    private val rebootDetected = AtomicBoolean(false)

    // ----- Background worker -----

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    // ==================== Lifecycle ====================

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("adventure_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString("adventure_folder_uri", null)
        if (folderUriString != null) {
            Logger.init(applicationContext, folderUriString)
        }

        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        isSensorAvailable = stepSensor != null

        val savedRaw = loadLastRaw()
        if (savedRaw >= 0) {
            lastRawSteps = savedRaw
            hasBaseline = true
        }

        Logger.log(
            applicationContext,
            "StepCounterService created, sensor: ${if (isSensorAvailable) "available" else "MISSING"}, " +
                    "savedRaw=$savedRaw"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log(
            applicationContext,
            "onStartCommand: action=${intent?.action}, isRunning=$isRunning, isListening=$isListening"
        )

        when (intent?.action) {
            StepCounterConstants.ACTION_STOP -> {
                Logger.log(applicationContext, "ACTION_STOP received, stopping service")
                shutdown()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START, null (START_STICKY restart), or unknown action.
                // startForeground is idempotent; Android 12+ requires it within 5s.
                startForeground(StepCounterConstants.NOTIFICATION_ID, buildNotificationStatic())
                isRunning = true

                if (!isListening && stepSensor != null) {
                    sensorManager.registerListener(
                        this,
                        stepSensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                    isListening = true
                    Logger.log(applicationContext, "Sensor listener registered")
                }

                startWorker()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWorker()
        stopListening()
        isRunning = false
        Logger.log(applicationContext, "StepCounterService destroyed")
        super.onDestroy()
    }

    // ==================== Sensor ====================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val raw = event.values[0].toInt()
        absoluteSteps = raw
        hasFirstEvent = true

        // Sensor reboot detection: a fresh boot resets the counter to a small value.
        if (hasBaseline && raw < lastRawSteps) {
            lastRawSteps = raw
            saveLastRaw(raw)
            pendingDelta.set(0)
            rebootDetected.set(true)
            broadcastUpdate(raw, 0)
            return
        }

        val delta = if (hasBaseline) (raw - lastRawSteps).coerceAtLeast(0) else 0
        if (!hasBaseline) {
            hasBaseline = true
        }
        lastRawSteps = raw
        saveLastRaw(raw)

        if (delta > 0) {
            pendingDelta.addAndGet(delta)
        }

        broadcastUpdate(raw, delta)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun broadcastUpdate(raw: Int, delta: Int) {
        sendBroadcast(
            Intent(StepCounterConstants.ACTION_UPDATE).apply {
                setPackage(packageName)
                putExtra(StepCounterConstants.EXTRA_ABSOLUTE_STEPS, raw)
                putExtra(StepCounterConstants.EXTRA_DELTA, delta)
                putExtra(StepCounterConstants.EXTRA_SENSOR_AVAILABLE, isSensorAvailable)
            }
        )
    }

    private fun stopListening() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    // ==================== Background worker ====================

    /**
     * Runs while the service is in foreground.
     * Every 1.5s drains pendingDelta into StepHistoryManager, every ~15s refreshes the notification.
     * Does NOT touch the sensor.
     */
    private fun startWorker() {
        stopWorker()
        workerJob = scope.launch {
            var ticksSinceNotifUpdate = 0
            while (isActive) {
                delay(1_500L)

                val delta = pendingDelta.getAndSet(0)
                if (delta > 0) {
                    try {
                        StepHistoryManager.recordDelta(applicationContext, delta)
                    } catch (e: Exception) {
                        Logger.log(applicationContext, "recordDelta failed: ${e.message}")
                    }
                }

                if (rebootDetected.compareAndSet(true, false)) {
                    Logger.log(
                        applicationContext,
                        "Sensor reboot detected, baseline reset to $lastRawSteps"
                    )
                }

                ticksSinceNotifUpdate++
                if (ticksSinceNotifUpdate >= 10) {
                    ticksSinceNotifUpdate = 0
                    refreshNotificationContent()
                }
            }
        }
    }

    private fun stopWorker() {
        workerJob?.cancel()
        workerJob = null
    }

    private fun shutdown() {
        stopWorker()
        stopListening()
        // Try to flush the last pending delta before stopping.
        val tail = pendingDelta.getAndSet(0)
        if (tail > 0) {
            scope.launch {
                try {
                    StepHistoryManager.recordDelta(applicationContext, tail)
                } catch (_: Exception) {
                }
            }
        }
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ==================== Notification ====================

    /** Static notification used to satisfy startForeground() within 5s. */
    private fun buildNotificationStatic(): Notification {
        val text = if (!isSensorAvailable) {
            "Датчик шагов недоступен на этом устройстве"
        } else if (!hasFirstEvent) {
            "Ожидание данных датчика…"
        } else {
            "Отслеживание шагов активно"
        }
        return newNotificationBuilder()
            .setContentText(text)
            .build()
    }

    private fun refreshNotificationContent() {
        if (!isRunning) return

        val todaySteps = try {
            StepHistoryManager.getTodaySteps(applicationContext)
        } catch (_: Exception) {
            0
        }

        val text = when {
            !isSensorAvailable -> "Датчик шагов недоступен на этом устройстве"
            !hasFirstEvent -> "Ожидание данных датчика…"
            else -> "Сегодня: $todaySteps шагов"
        }

        val notification = newNotificationBuilder()
            .setContentText(text)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(StepCounterConstants.NOTIFICATION_ID, notification)
    }

    private fun newNotificationBuilder(): NotificationCompat.Builder {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, StepCounterConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Шагомер Adventure")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            StepCounterConstants.NOTIFICATION_CHANNEL_ID,
            "Шагомер",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // ==================== Baseline persistence ====================

    private fun saveLastRaw(value: Int) {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_RAW, value)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadLastRaw(): Int {
        return try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_RAW, -1)
        } catch (_: Exception) {
            -1
        }
    }
}