package com.myapp.adventure.service

object StepCounterConstants {
    const val ACTION_START = "com.myapp.adventure.START_STEP_COUNTER"
    const val ACTION_STOP = "com.myapp.adventure.STOP_STEP_COUNTER"
    const val ACTION_UPDATE = "com.myapp.adventure.UPDATE_STEPS"

    /** Absolute (raw) reading from the step counter sensor since boot. */
    const val EXTRA_ABSOLUTE_STEPS = "absolute_steps"

    /** Delta since the previous sensor event (diff of absolute readings). */
    const val EXTRA_DELTA = "delta_steps"

    /**
     * Boolean: whether a step counter sensor is present on this device.
     * Broadcast together with [ACTION_UPDATE] so consumers can react to it.
     */
    const val EXTRA_SENSOR_AVAILABLE = "sensor_available"

    const val NOTIFICATION_CHANNEL_ID = "step_counter_channel"
    const val NOTIFICATION_ID = 1
}