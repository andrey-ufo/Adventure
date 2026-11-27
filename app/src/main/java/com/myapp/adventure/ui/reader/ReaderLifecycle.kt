package com.myapp.adventure.ui.reader

import androidx.lifecycle.Lifecycle

/**
 * Ф5.9: lifecycle→persistence policy, extracted from `ReaderRoute` so the
 * ON_PAUSE/ON_STOP wiring can be tested without Compose.
 *
 * - ON_PAUSE: enqueue an autosave (the ViewModel itself ignores preview).
 * - ON_STOP: flush only when there is a normal persisted adventure;
 *   preview mode has no autosave file.
 */
internal object ReaderLifecycle {

    fun handle(
        event: Lifecycle.Event,
        shouldFlush: Boolean,
        save: () -> Unit,
        flush: () -> Unit,
    ) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> save()
            Lifecycle.Event.ON_STOP -> if (shouldFlush) flush()
            else -> Unit
        }
    }
}
