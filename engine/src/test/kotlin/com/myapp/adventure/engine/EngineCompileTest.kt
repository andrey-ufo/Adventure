package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.Subroutine
import com.myapp.adventure.engine.ir.SubroutineCall
import com.myapp.adventure.engine.ir.SubroutineStep
import com.myapp.adventure.engine.ir.types.VarDecl
import com.myapp.adventure.engine.ir.types.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


class EngineCompileTest {

    private fun adventure(
        vararg chapters: Chapter,
        subroutines: List<Subroutine> = emptyList(),
    ): Adventure = Adventure(
        title = "T",
        startScene = chapters.firstOrNull()?.scenes?.firstOrNull()?.id ?: SceneId(""),
        subroutines = subroutines,
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

    // ---------- Ф4.5: merge sources ----------

    @Test
    fun `merge source pointing to missing scene fails compile`() {
        val m = Scene(
            id = SceneId("m"),
            text = "M",
            isMerge = true,
            mergeSources = listOf(SceneId("ghost")),
        )
        val adv = Adventure(
            title = "T",
            startScene = SceneId("m"),
            chapters = listOf(chapter("ch", m)),
        )
        val errors = assertIs<CompileResult.Failure>(Engine.compile(adv)).errors
        val missing = errors.filterIsInstance<CompileError.MissingMergeSource>().single()
        assertEquals(SceneId("m"), missing.sceneId)
        assertEquals(SceneId("ghost"), missing.mergeSourceId)
        assertTrue(missing.message.contains("ghost"), missing.message)
    }

    @Test
    fun `valid merge sources compile`() {
        val a = Scene(
            id = SceneId("a"),
            text = "A",
            choices = listOf(Choice(ChoiceId("ca"), "Go", SceneId("m"))),
        )
        val b = Scene(
            id = SceneId("b"),
            text = "B",
            choices = listOf(Choice(ChoiceId("cb"), "Go", SceneId("m"))),
        )
        val m = Scene(
            id = SceneId("m"),
            text = "M",
            isMerge = true,
            mergeSources = listOf(SceneId("a"), SceneId("b")),
        )
        val adv = Adventure(
            title = "T",
            startScene = SceneId("a"),
            chapters = listOf(chapter("ch", a, b, m)),
        )
        assertIs<CompileResult.Success>(Engine.compile(adv))
    }

    // ---------- Ф4.7: subroutine calls ----------

    private fun consumeSubroutine(): Subroutine = Subroutine(
        id = "consume",
        inputs = listOf(VarDecl("item", VarType.ItemCount)),
        outputs = listOf(VarDecl("item", VarType.ItemCount)),
        body = listOf(
            SubroutineStep(effects = listOf(Effect.ModifyStat("item", -1))),
        ),
    )

    @Test
    fun `valid subroutine call compiles`() {
        val use = Choice(
            id = ChoiceId("use"),
            text = "Use",
            nextScene = SceneId("s1"),
            call = SubroutineCall(
                subroutineId = "consume",
                args = mapOf("item" to "potion"),
                outputs = mapOf("item" to "potion"),
            ),
        )
        val s1 = Scene(SceneId("s1"), "S", choices = listOf(use))
        val adv = adventure(
            chapter("c", s1),
            subroutines = listOf(consumeSubroutine()),
        )
        assertIs<CompileResult.Success>(Engine.compile(adv))
    }

    @Test
    fun `missing subroutine fails compile`() {
        val choice = Choice(
            id = ChoiceId("use"),
            text = "Use",
            nextScene = SceneId("s1"),
            call = SubroutineCall("ghost"),
        )
        val s1 = Scene(SceneId("s1"), "S", choices = listOf(choice))
        val adv = adventure(chapter("c", s1))

        val error = assertIs<CompileResult.Failure>(Engine.compile(adv))
            .errors
            .filterIsInstance<CompileError.MissingSubroutine>()
            .single()
        assertEquals("ghost", error.subroutineId)
        assertEquals(SceneId("s1"), error.sceneId)
        assertEquals(ChoiceId("use"), error.choiceId)
        assertTrue(error.message.contains("ghost"), error.message)
        assertTrue(error.message.contains("scene 's1'"), error.message)
    }

    @Test
    fun `subroutine missing input argument fails compile`() {
        val choice = Choice(
            id = ChoiceId("use"),
            text = "Use",
            nextScene = SceneId("s1"),
            call = SubroutineCall("consume", args = emptyMap(), outputs = mapOf("item" to "potion")),
        )
        val s1 = Scene(SceneId("s1"), "S", choices = listOf(choice))
        val adv = adventure(
            chapter("c", s1),
            subroutines = listOf(consumeSubroutine()),
        )

        val error = assertIs<CompileResult.Failure>(Engine.compile(adv))
            .errors
            .filterIsInstance<CompileError.InvalidSubroutineCall>()
            .single()
        assertEquals("consume", error.subroutineId)
        assertTrue(error.detail.contains("missing input"), error.detail)
        assertTrue(error.detail.contains("item"), error.detail)
    }

    @Test
    fun `subroutine unknown output name fails compile`() {
        val choice = Choice(
            id = ChoiceId("use"),
            text = "Use",
            nextScene = SceneId("s1"),
            call = SubroutineCall(
                subroutineId = "consume",
                args = mapOf("item" to "potion"),
                outputs = mapOf("ghost" to "potion"),
            ),
        )
        val s1 = Scene(SceneId("s1"), "S", choices = listOf(choice))
        val adv = adventure(
            chapter("c", s1),
            subroutines = listOf(consumeSubroutine()),
        )

        val error = assertIs<CompileResult.Failure>(Engine.compile(adv))
            .errors
            .filterIsInstance<CompileError.InvalidSubroutineCall>()
            .single()
        assertTrue(error.detail.contains("unknown output"), error.detail)
        assertTrue(error.detail.contains("ghost"), error.detail)
    }

    @Test
    fun `subroutine cycle fails compile`() {
        val first = Subroutine(
            id = "a",
            body = listOf(SubroutineStep(call = SubroutineCall("b"))),
        )
        val second = Subroutine(
            id = "b",
            body = listOf(SubroutineStep(call = SubroutineCall("a"))),
        )
        val s1 = scene("s1")
        val adv = adventure(
            chapter("c", s1),
            subroutines = listOf(first, second),
        )

        val cycle = assertIs<CompileResult.Failure>(Engine.compile(adv))
            .errors
            .filterIsInstance<CompileError.SubroutineCycle>()
            .single()
        assertEquals("a", cycle.path.first())
        assertEquals(cycle.path.first(), cycle.path.last())
        assertTrue(cycle.message.contains("Recursion is not allowed"), cycle.message)
    }

    @Test
    fun `duplicate subroutine id fails compile`() {
        val s1 = scene("s1")
        val adv = adventure(
            chapter("c", s1),
            subroutines = listOf(
                Subroutine(id = "dup"),
                Subroutine(id = "dup"),
            ),
        )

        val error = assertIs<CompileResult.Failure>(Engine.compile(adv))
            .errors
            .filterIsInstance<CompileError.DuplicateSubroutineId>()
            .single()
        assertEquals("dup", error.subroutineId)
    }
}