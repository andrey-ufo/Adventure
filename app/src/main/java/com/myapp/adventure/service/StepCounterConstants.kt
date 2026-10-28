package com.myapp.adventure.service

object StepCounterConstants {
    const val ACTION_START = "com.myapp.adventure.START_STEP_COUNTER"
    const val ACTION_STOP = "com.myapp.adventure.STOP_STEP_COUNTER"
    const val ACTION_UPDATE = "com.myapp.adventure.UPDATE_STEPS"
    const val ACTION_SET_GOAL = "com.myapp.adventure.SET_GOAL"
    const val EXTRA_TARGET_STEPS = "target_steps"
    const val EXTRA_ADVENTURE_TITLE = "adventure_title"
    const val EXTRA_ADVENTURE_FILE_URI = "adventure_file_uri"
    const val EXTRA_CURRENT_STEPS = "current_steps"
    const val EXTRA_ABSOLUTE_STEPS = "absolute_steps"
    const val NOTIFICATION_CHANNEL_ID = "step_counter_channel"
    const val NOTIFICATION_ID = 1
}