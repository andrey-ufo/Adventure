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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myapp.adventure.MainActivity
import com.myapp.adventure.R
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.StepHistoryManager

class StepCounterService : Service(), SensorEventListener {

    companion object {
        var isRunning = false
        var absoluteSteps: Int = 0
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var isListening = false

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
        Logger.log(applicationContext, "StepCounterService создан, датчик: ${if (stepSensor != null) "доступен" else "ОТСУТСТВУЕТ"}")

        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
            Logger.log(applicationContext, "Слушатель зарегистрирован в onCreate")
        } else {
            Logger.log(applicationContext, "Не удалось зарегистрировать слушатель: датчик отсутствует")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log(applicationContext, "onStartCommand: action=${intent?.action}, isRunning=$isRunning, isListening=$isListening")
        when (intent?.action) {
            StepCounterConstants.ACTION_START -> {
                if (!isListening && stepSensor != null) {
                    sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
                    isListening = true
                    Logger.log(applicationContext, "Слушатель зарегистрирован в onStartCommand")
                }
                if (!isRunning) {
                    startForeground(StepCounterConstants.NOTIFICATION_ID, buildNotification())
                    isRunning = true
                    Logger.log(applicationContext, "Сервис переведён в foreground")
                    // Записываем текущее значение при старте
                    StepHistoryManager.recordCurrentSteps(applicationContext, absoluteSteps)
                }
            }
            StepCounterConstants.ACTION_STOP -> {
                Logger.log(applicationContext, "Получен STOP, игнорируем")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            absoluteSteps = event.values[0].toInt()
            if (absoluteSteps % 100 == 0) {
                Logger.log(applicationContext, "Абсолютные шаги: $absoluteSteps")
            }
            sendBroadcast(Intent(StepCounterConstants.ACTION_UPDATE).apply {
                putExtra(StepCounterConstants.EXTRA_ABSOLUTE_STEPS, absoluteSteps)
            })
            // Запись истории при каждом изменении (оптимизировано внутри менеджера)
            StepHistoryManager.recordCurrentSteps(applicationContext, absoluteSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, StepCounterConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Шагомер Adventure")
            .setContentText("Отслеживание шагов активно")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = NotificationCompat.Builder(this, StepCounterConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Шагомер Adventure")
            .setContentText("Отслеживание шагов активно")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(StepCounterConstants.NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                StepCounterConstants.NOTIFICATION_CHANNEL_ID,
                "Шагомер",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
        isRunning = false
        Logger.log(applicationContext, "StepCounterService уничтожен")
        super.onDestroy()
    }
}