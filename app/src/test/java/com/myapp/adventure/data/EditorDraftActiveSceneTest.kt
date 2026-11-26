package com.myapp.adventure.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ф4.13: the one-scene editor restores the last worked-on scene. */
class EditorDraftActiveSceneTest {

    @Test
    fun activeSceneUidRoundTripsThroughDraft() {
        val scene = EditorScene(uid = "scene-uid", id = "start", title = "Старт")
        val draft = EditorDraft(
            title = "T",
            scenes = listOf(scene),
            activeSceneUid = scene.uid,
        )

        val json = Gson().toJson(draft)
        val back = Gson().fromJson(json, EditorDraft::class.java)

        assertEquals(scene.uid, back.activeSceneUid)
    }
}
