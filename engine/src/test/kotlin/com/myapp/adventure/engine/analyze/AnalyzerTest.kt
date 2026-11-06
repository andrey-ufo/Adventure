package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalyzerTest {

    // ---------- helpers ----------

    private fun scene(
        id: String,
        choices: List<Choice> = emptyList(),
    ): Scene = Scene(id = SceneId(id), text = "text of $id", choices = choices)

    private fun choice(id: String, next: String): Choice =
        Choice(id = ChoiceId(id), text = "to $next", nextScene = SceneId(next))

    private fun chapter(id: String, vararg scenes: Scene): Chapter =
        Chapter(id = id, title = id, scenes = scenes.toList())

    private fun adventure(start: String, vararg chapters: Chapter): Adventure =
        Adventure(
            title = "T",
            startScene = SceneId(start),
            chapters = chapters.toList(),
        )

    private fun compile(adventure: Adventure): CompiledAdventure =
        assertIs<CompileResult.Success>(Engine.compile(adventure)).compiled

    // ---------- reachable ----------

    @Test
    fun `linear chain - all scenes reachable`() {
        val a = scene("a", listOf(choice("c1", "b")))
        val b = scene("b", listOf(choice("c2", "c")))
        val c = scene("c")
        val compiled = compile(adventure("a", chapter("ch", a, b, c)))

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a"), SceneId("b"), SceneId("c")), report.reachable)
        assertEquals(emptyList(), report.unreachable)
        assertEquals(listOf(SceneId("c")), report.deadEnds)
    }

    @Test
    fun `orphan scene is unreachable`() {
        val a = scene("a", listOf(choice("c1", "b")))
        val b = scene("b")
        val orphan = scene("orphan")
        val compiled = compile(adventure("a", chapter("ch", a, b, orphan)))

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a"), SceneId("b")), report.reachable)
        assertEquals(listOf(SceneId("orphan")), report.unreachable)
        assertEquals(listOf(SceneId("b")), report.deadEnds)
    }

    @Test
    fun `cross-chapter transition is reachable`() {
        val a = scene("a", listOf(choice("c1", "b")))
        val b = scene("b")
        val compiled = compile(
            adventure("a", chapter("ch1", a), chapter("ch2", b)),
        )

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a"), SceneId("b")), report.reachable)
        assertEquals(emptyList(), report.unreachable)
    }

    @Test
    fun `self-loop is reachable and terminates`() {
        val a = scene("a", listOf(choice("c1", "a")))
        val compiled = compile(adventure("a", chapter("ch", a)))

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a")), report.reachable)
        assertEquals(emptyList(), report.unreachable)
        // a has choices (self-loop), so it is not a dead end.
        assertEquals(emptyList(), report.deadEnds)
    }

    @Test
    fun `two-way cycle is reachable`() {
        val a = scene("a", listOf(choice("c1", "b")))
        val b = scene("b", listOf(choice("c2", "a")))
        val compiled = compile(adventure("a", chapter("ch", a, b)))

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a"), SceneId("b")), report.reachable)
        assertEquals(emptyList(), report.unreachable)
        assertEquals(emptyList(), report.deadEnds)
    }

    @Test
    fun `missing start scene - everything unreachable`() {
        // Engine.compile rejects this (CompileError.MissingStartScene,
        // Ф1.6.2.1), so in production the reader never sees this state.
        // But CompiledAdventure is a public data class; anyone can
        // construct one by hand (tests, future tooling). This test
        // documents that Analyzer.reachable is **defensive**: with a
        // missing start scene, `reachable` is empty and every existing
        // scene is unreachable.
        val a = scene("a")
        val adv = adventure("ghost", chapter("ch", a))
        val compiled = CompiledAdventure(
            adventure = adv,
            scenesById = mapOf(SceneId("a") to a),
            chapterIndexBySceneId = mapOf(SceneId("a") to 0),
        )

        val report = Analyzer.reachable(compiled)
        assertEquals(emptySet(), report.reachable)
        assertEquals(listOf(SceneId("a")), report.unreachable)
        assertEquals(emptyList(), report.deadEnds)
    }

    @Test
    fun `nextScene to missing scene is not added to reachable`() {
        // Engine.compile rejects this (CompileError.MissingNextScene,
        // Ф1.6.2.1). Documenting Analyzer's defensive behaviour on a
        // manually-built CompiledAdventure: a missing target does not
        // enter `reachable` — it cannot be visited.
        val a = scene("a", listOf(choice("c1", "ghost")))
        val adv = adventure("a", chapter("ch", a))
        val compiled = CompiledAdventure(
            adventure = adv,
            scenesById = mapOf(SceneId("a") to a),
            chapterIndexBySceneId = mapOf(SceneId("a") to 0),
        )

        val report = Analyzer.reachable(compiled)
        assertEquals(setOf(SceneId("a")), report.reachable)
        assertEquals(emptyList(), report.unreachable)
        assertEquals(emptyList(), report.deadEnds)
    }

    @Test
    fun `missing nextScene does not produce a false unreachable lint`() {
        // Same setup as above; no lint issues. Missing references are
        // compile errors (Ф1.6.2.1), not lint issues. The analyzer sees a
        // valid CompiledAdventure with one dangling edge, but that edge
        // does not create a phantom scene, so nothing to lint.
        val a = scene("a", listOf(choice("c1", "ghost")))
        val adv = adventure("a", chapter("ch", a))
        val compiled = CompiledAdventure(
            adventure = adv,
            scenesById = mapOf(SceneId("a") to a),
            chapterIndexBySceneId = mapOf(SceneId("a") to 0),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    // ---------- lint ----------

    @Test
    fun `lint empty when everything reachable`() {
        val a = scene("a", listOf(choice("c1", "b")))
        val b = scene("b")
        val compiled = compile(adventure("a", chapter("ch", a, b)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint reports unreachable scene`() {
        val a = scene("a")
        val orphan = scene("orphan")
        val compiled = compile(adventure("a", chapter("ch", a, orphan)))

        val issues = Analyzer.lint(compiled)
        assertEquals(1, issues.size)
        val issue = issues.single()
        assertIs<LintKind.UnreachableScene>(issue.kind)
        assertEquals(SceneId("orphan"), issue.sceneId)
        assertEquals(Severity.WARNING, issue.severity)
        assertTrue(issue.message.contains("orphan"), issue.message)
        assertTrue(issue.message.contains("a"), issue.message)
    }

    @Test
    fun `lint issues are sorted by scene id`() {
        val a = scene("a")
        val orphanZ = scene("z_orphan")
        val orphanA = scene("a_orphan")
        val compiled = compile(
            adventure("a", chapter("ch", a, orphanZ, orphanA)),
        )

        val ids = Analyzer.lint(compiled).map { it.sceneId }
        assertEquals(listOf(SceneId("a_orphan"), SceneId("z_orphan")), ids)
    }
}