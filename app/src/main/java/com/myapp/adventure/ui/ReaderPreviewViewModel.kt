package com.myapp.adventure.ui

import androidx.lifecycle.ViewModel
import com.myapp.adventure.ui.editor.EditorPreviewRequest
import java.util.UUID

/**
 * Phase 4-c: holds reader-preview payloads outside the navigation route.
 *
 * The preview JSON can be large, so it must not be encoded into a typed
 * navigation route. The editor stores the payload here and navigates with a
 * short id; the reader destination resolves it back.
 *
 * Activity-scoped (survives rotation, so preview keeps working after a
 * config change). Payloads are kept for the Activity lifetime: losing a
 * preview on process death is acceptable, and the map is bounded by how
 * many previews the user opens in one session.
 */
class ReaderPreviewViewModel : ViewModel() {

    private val payloads = mutableMapOf<String, EditorPreviewRequest>()

    fun put(request: EditorPreviewRequest): String {
        val id = UUID.randomUUID().toString()
        payloads[id] = request
        return id
    }

    fun get(id: String): EditorPreviewRequest? = payloads[id]
}
