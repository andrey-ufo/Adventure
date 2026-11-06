package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


class EngineCompileTest {

    private fun adventure(vararg chapters: Chapter): Adventure = Adventure(
        title = "T",
        startScene = chapters.firstOrNull()?.scenes?.firstOrNull()?.id ?: SceneId(""),
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

    @Test
    fun `missing start scene fails compile`() {
        // startScene points to "ghost"; the only scene is "s1".
        val adv = Adventure(
            title = "T",
            startScene = SceneId("ghost"),
            chapters = listOf(chapter("c1", scene("s1"))),
        )
        val errors = assertIs<CompileResult.Failure>(Engine.compile(adv)).errors
        val missing = errors.filterIsInstance<CompileError.MissingStartScene>().single()
        assertEquals(SceneId("ghost"), missing.startSceneId)
        assertTrue(missing.message.contains("ghost"), missing.message)
    }

    @Test
    fun `missing next_scene fails compile with choice details`() {
        val a = Scene(
            id = SceneId("a"),
            text = "text of a",
            choices = listOf(
                Choice(
                    id = ChoiceId("c1"),
                    text = "Go",
                    nextScene = SceneId("ghost"),
                ),
            ),
        )
        val adv = Adventure(
            title = "T",
            startScene = SceneId("a"),
            chapters = listOf(chapter("ch", a)),
        )
        val errors = assertIs<CompileResult.Failure>(Engine.compile(adv)).errors
        val missing = errors.filterIsInstance<CompileError.MissingNextScene>().single()
        assertEquals(SceneId("a"), missing.sceneId)
        assertEquals(ChoiceId("c1"), missing.choiceId)
        assertEquals(SceneId("ghost"), missing.targetSceneId)
        assertTrue(missing.message.contains("c1"), missing.message)
        assertTrue(missing.message.contains("ghost"), missing.message)
    }

    @Test
    fun `multiple missing next_scene errors are reported in stable order`() {
        val a = Scene(
            id = SceneId("a"),
            text = "text of a",
            choices = listOf(
                Choice(id = ChoiceId("c1"), text = "Go", nextScene = SceneId("ghost1")),
                Choice(id = ChoiceId("c2"), text = "Go", nextScene = SceneId("ghost2")),
            ),
        )
        val b = Scene(
            id = SceneId("b"),
            text = "text of b",
            choices = listOf(
                Choice(id = ChoiceId("c1"), text = "Go", nextScene = SceneId("ghost3")),
            ),
        )
        val adv = Adventure(
            title = "T",
            startScene = SceneId("a"),
            chapters = listOf(chapter("ch", a, b)),
        )
        val errors = assertIs<CompileResult.Failure>(Engine.compile(adv)).errors
        val missing = errors.filterIsInstance<CompileError.MissingNextScene>()
        assertEquals(3, missing.size)
        // Sorted by (sceneId, choiceId): a/c1, a/c2, b/c1.
        assertEquals(SceneId("a"), missing[0].sceneId)
        assertEquals(ChoiceId("c1"), missing[0].choiceId)
        assertEquals(SceneId("a"), missing[1].sceneId)
        assertEquals(ChoiceId("c2"), missing[1].choiceId)
        assertEquals(SceneId("b"), missing[2].sceneId)
        assertEquals(ChoiceId("c1"), missing[2].choiceId)
    }
}