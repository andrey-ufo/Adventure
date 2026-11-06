package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineCompileTest {

    private fun adventure(vararg chapters: Chapter): Adventure = Adventure(
        title = "T",
        chapters = chapters.toList(),
    )

    private fun chapter(id: String, vararg scenes: Scene): Chapter = Chapter(
        id = id,
        title = id,
        scenes = scenes.toList(),
    )

    private fun scene(id: String): Scene = Scene(id = SceneId(id), text = "x")

    @Test
    fun `single scene compiles`() {
        val adv = adventure(chapter("c1", scene("s1")))
        val result = Engine.compile(adv)
        val compiled = assertIs<CompileResult.Success>(result).compiled
        assertEquals(1, compiled.scenesById.size)
        assertEquals("s1", compiled.scenesById[SceneId("s1")]?.id?.value)
        assertEquals(0, compiled.chapterIndexBySceneId[SceneId("s1")])
    }

    @Test
    fun `multiple chapters compile`() {
        val adv = adventure(
            chapter("c1", scene("s1"), scene("s2")),
            chapter("c2", scene("s3")),
        )
        val compiled = assertIs<CompileResult.Success>(Engine.compile(adv)).compiled
        assertEquals(3, compiled.scenesById.size)
        assertEquals(0, compiled.chapterIndexBySceneId[SceneId("s1")])
        assertEquals(0, compiled.chapterIndexBySceneId[SceneId("s2")])
        assertEquals(1, compiled.chapterIndexBySceneId[SceneId("s3")])
    }

    @Test
    fun `duplicate scene id fails with chapter indices`() {
        val adv = adventure(
            chapter("c1", scene("s1")),
            chapter("c2", scene("s1")),
        )
        val result = Engine.compile(adv)
        val errors = assertIs<CompileResult.Failure>(result).errors
        val duplicate = errors.filterIsInstance<CompileError.DuplicateSceneId>().single()
        assertEquals(SceneId("s1"), duplicate.sceneId)
        assertEquals(listOf(0, 1), duplicate.chapterIndices)
        assertTrue(duplicate.message.contains("chapters 1, 2"), duplicate.message)
    }
}