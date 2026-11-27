package com.myapp.adventure.ui.reader

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ф5.9: ON_PAUSE/ON_STOP persistence policy used by ReaderRoute. */
class ReaderLifecycleTest {

    private var saves = 0
    private var flushes = 0

    private fun handle(event: Lifecycle.Event, shouldFlush: Boolean) {
        ReaderLifecycle.handle(
            event = event,
            shouldFlush = shouldFlush,
            save = { saves++ },
            flush = { flushes++ },
        )
    }

    @Test
    fun pauseAlwaysRequestsSave() {
        handle(Lifecycle.Event.ON_PAUSE, shouldFlush = false)

        assertEquals(1, saves)
        assertEquals(0, flushes)
    }

    @Test
    fun stopFlushesNormalAdventure() {
        handle(Lifecycle.Event.ON_STOP, shouldFlush = true)

        assertEquals(0, saves)
        assertEquals(1, flushes)
    }

    @Test
    fun stopDoesNotFlushPreviewOrMissingUri() {
        handle(Lifecycle.Event.ON_STOP, shouldFlush = false)

        assertEquals(0, saves)
        assertEquals(0, flushes)
    }

    @Test
    fun otherEventsDoNothing() {
        handle(Lifecycle.Event.ON_START, shouldFlush = true)
        handle(Lifecycle.Event.ON_RESUME, shouldFlush = true)
        handle(Lifecycle.Event.ON_DESTROY, shouldFlush = true)

        assertEquals(0, saves)
        assertEquals(0, flushes)
    }
}
