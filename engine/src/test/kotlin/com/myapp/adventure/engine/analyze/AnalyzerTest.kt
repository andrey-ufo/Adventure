package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.DeathScene
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory
import com.myapp.adventure.engine.ir.TagDefinition
import com.myapp.adventure.engine.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalyzerTest {

    // ---------- helpers ----------

    private fun scene(
        id: String,
        choices: List<Choice> = emptyList(),
        effects: List<Effect> = emptyList(),
        isEnding: Boolean = false,
    ): Scene = Scene(
        id = SceneId(id),
        text = "text of $id",
        choices = choices,
        effects = effects,
        isEnding = isEnding,
    )

    private fun choice(
        id: String,
        next: String,
        condition: Condition? = null,
    ): Choice = Choice(
        id = ChoiceId(id),
        text = "to $next",
        nextScene = SceneId(next),
        condition = condition,
    )

    private fun chapter(id: String, vararg scenes: Scene): Chapter =
        Chapter(id = id, title = id, scenes = scenes.toList())

    private fun adventure(start: String, vararg chapters: Chapter): Adventure =
        Adventure(
            title = "T",
            startScene = SceneId(start),
            chapters = chapters.toList(),
        )

    private fun adventureWithLintSetup(
        start: String,
        tags: List<TagDefinition> = emptyList(),
        initialState: InitialState = InitialState(),
        deathScene: DeathScene? = null,
        vararg chapters: Chapter,
    ): Adventure = Adventure(
        title = "T",
        startScene = SceneId(start),
        tags = tags,
        initialState = initialState,
        deathScene = deathScene,
        chapters = chapters.toList(),
    )

    private fun itemTag(name: String): TagDefinition =
        TagDefinition(name = name, category = TagCategory.ITEM)

    private fun healthTag(name: String = "health"): TagDefinition =
        TagDefinition(name = name, category = TagCategory.HEALTH)

    private fun journalTag(name: String): TagDefinition =
        TagDefinition(name = name, category = TagCategory.JOURNAL)

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
        val b = scene("b", isEnding = true)
        val compiled = compile(adventure("a", chapter("ch", a, b)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint reports unreachable scene`() {
        // Ф1.6.2.3 fix: `a` used to be terminal, which now also triggers
        // DeadEnd (reachable terminal not at the end of the last chapter).
        // Adding a self-loop keeps `a` reachable and non-terminal, so the
        // test focuses on its intent: orphan is reported as
        // UnreachableScene and nothing else fires.
        val a = scene("a", choices = listOf(choice("loop", "a")))
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
        // Ф1.6.2.3 fix: `a` used to be terminal → also triggers DeadEnd,
        // which is sorted to the front and broke the expected list. A
        // self-loop keeps `a` reachable and non-terminal, so the only
        // issues are the two orphan scenes, which is what the test is
        // actually about (stable sorting).
        val a = scene("a", choices = listOf(choice("loop", "a")))
        val orphanZ = scene("z_orphan")
        val orphanA = scene("a_orphan")
        val compiled = compile(
            adventure("a", chapter("ch", a, orphanZ, orphanA)),
        )

        val ids = Analyzer.lint(compiled).map { it.sceneId }
        assertEquals(listOf(SceneId("a_orphan"), SceneId("z_orphan")), ids)
    }

    // ---------- lint: removing unestablished item ----------

    @Test
    fun `lint reports removing unestablished item`() {
        val a = scene(
            "a",
            effects = listOf(Effect.ModifyStat("sword", -1)),
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                tags = listOf(itemTag("sword")),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        val issue = Analyzer.lint(compiled)
            .single { it.kind is LintKind.RemovingUnestablishedItem }
        assertEquals(
            LintKind.RemovingUnestablishedItem("sword"),
            issue.kind,
        )
        assertEquals(SceneId("a"), issue.sceneId)
        assertEquals(Severity.WARNING, issue.severity)
        assertTrue(issue.message.contains("sword"), issue.message)
    }

    @Test
    fun `lint does not report removing item established in initial state`() {
        val a = scene(
            "a",
            effects = listOf(Effect.ModifyStat("sword", -1)),
            isEnding = true,
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                tags = listOf(itemTag("sword")),
                initialState = InitialState(stats = mapOf("sword" to 1)),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint does not report removing item established by positive effect`() {
        // 'sword' is given in scene 'give' and reduced in scene 'use'.
        val give = scene(
            "give",
            choices = listOf(choice("c", "use")),
            effects = listOf(Effect.ModifyStat("sword", 1)),
        )
        val use = scene(
            "use",
            effects = listOf(Effect.ModifyStat("sword", -1)),
            isEnding = true,
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "give",
                tags = listOf(itemTag("sword")),
                chapters = arrayOf(chapter("ch", give, use)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint ignores negative change on journal variable`() {
        // 'mana' is JOURNAL, not ITEM/HEALTH; reducing it is not this check's job.
        val a = scene(
            "a",
            effects = listOf(Effect.ModifyStat("mana", -5)),
            isEnding = true,
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                tags = listOf(journalTag("mana")),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    // ---------- lint: death scene without health ----------

    @Test
    fun `lint reports death scene without health`() {
        val a = scene("a")
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                deathScene = DeathScene(),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        val issue = Analyzer.lint(compiled)
            .single { it.kind is LintKind.DeathSceneWithoutHealth }
        assertEquals(LintKind.DeathSceneWithoutHealth, issue.kind)
        assertEquals(null, issue.sceneId)
        assertEquals(Severity.WARNING, issue.severity)
        assertTrue(issue.message.contains("health"), issue.message)
    }

    @Test
    fun `lint does not report death scene when health in initial state`() {
        val a = scene("a", isEnding = true)
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                initialState = InitialState(stats = mapOf("health" to 100)),
                deathScene = DeathScene(),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint does not report death scene when health modified in scene`() {
        // Ф1.6.2.2 fix: the previous version had only a negative
        // ModifyStat("health", -10), which legitimately triggers
        // RemovingUnestablishedItem — a *different* check that lives in
        // the same lint() call. The test's intent is narrow: verify that
        // DeathSceneWithoutHealth stays silent when health is modified
        // anywhere (the check does not care about the sign). Adding a
        // positive ModifyStat makes health "established", so
        // RemovingUnestablishedItem does not fire either, and
        // assertEquals(emptyList(), …) again means "this sample is
        // semantically clean" — the convention used by every other
        // clean-sample test in this file.
        val a = scene(
            "a",
            effects = listOf(
                Effect.ModifyStat("health", 10),
                Effect.ModifyStat("health", -10),
            ),
            isEnding = true,
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                tags = listOf(healthTag()),
                deathScene = DeathScene(),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    // ---------- lint: condition on unestablished flag ----------

    @Test
    fun `lint reports condition on unestablished flag`() {
        val a = scene(
            "a",
            choices = listOf(
                choice("c1", "a", condition = Condition.Has("never_set")),
            ),
        )
        val compiled = compile(adventure("a", chapter("ch", a)))

        val issue = Analyzer.lint(compiled).single()
        assertEquals(LintKind.ConditionOnUnestablishedFlag("never_set"), issue.kind)
        assertEquals(SceneId("a"), issue.sceneId)
        assertEquals(Severity.WARNING, issue.severity)
        assertTrue(issue.message.contains("never_set"), issue.message)
    }

    @Test
    fun `lint reports negated condition on unestablished flag`() {
        val a = scene(
            "a",
            choices = listOf(
                choice(
                    "c1",
                    "a",
                    condition = Condition.Not(Condition.Has("never_set")),
                ),
            ),
        )
        val compiled = compile(adventure("a", chapter("ch", a)))

        val issue = Analyzer.lint(compiled).single()
        assertEquals(LintKind.ConditionOnUnestablishedFlag("never_set"), issue.kind)
    }

    @Test
    fun `lint does not report condition on initial flag`() {
        val a = scene(
            "a",
            choices = listOf(
                choice("c1", "a", condition = Condition.Has("started")),
            ),
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                initialState = InitialState(flags = listOf("started")),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint does not report condition on flag set elsewhere`() {
        val a = scene(
            "a",
            effects = listOf(Effect.SetFlag("found")),
            choices = listOf(choice("c1", "b")),
        )
        val b = scene(
            "b",
            choices = listOf(
                choice("c2", "a", condition = Condition.Has("found")),
            ),
        )
        val compiled = compile(adventure("a", chapter("ch", a, b)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint does not report condition on name that appears as stat`() {
        // Shim behavior (see LintKind.ConditionOnUnestablishedFlag KDoc):
        // has_sword is not a flag, but sword appears in initial_state.stats,
        // so we treat it as "known name". Type mismatch is Ф2.2's job.
        // NOTE: when Ф2.2 adds HasOnNonBoolVariable, this test will need
        // updating — the intent shifts to "sword exists, but using has_*
        // on it is a type error, not an unknown-name error".
        val a = scene(
            "a",
            choices = listOf(
                choice("c1", "a", condition = Condition.Has("sword")),
            ),
        )
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                initialState = InitialState(stats = mapOf("sword" to 1)),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    // ---------- lint: death without death scene ----------

    @Test
    fun `lint reports health in initial state without death scene`() {
        val a = scene("a")
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                initialState = InitialState(stats = mapOf("health" to 100)),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        val issue = Analyzer.lint(compiled)
            .single { it.kind is LintKind.DeathWithoutDeathScene }
        assertEquals(LintKind.DeathWithoutDeathScene, issue.kind)
        assertEquals(null, issue.sceneId)
        assertTrue(issue.message.contains("health"), issue.message)
    }

    @Test
    fun `lint reports health modified in scene without death scene`() {
        val a = scene("a", effects = listOf(Effect.ModifyStat("health", -10)))
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                tags = listOf(healthTag()),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertTrue(
            Analyzer.lint(compiled).any { it.kind == LintKind.DeathWithoutDeathScene },
            "expected DeathWithoutDeathScene, got ${Analyzer.lint(compiled)}",
        )
    }

    @Test
    fun `lint does not report no health no death scene`() {
        val a = scene("a", isEnding = true)
        val compiled = compile(adventure("a", chapter("ch", a)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint does not report health with death scene`() {
        val a = scene("a", isEnding = true)
        val compiled = compile(
            adventureWithLintSetup(
                start = "a",
                initialState = InitialState(stats = mapOf("health" to 100)),
                deathScene = DeathScene(),
                chapters = arrayOf(chapter("ch", a)),
            ),
        )

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    // ---------- lint: dead end ----------

    @Test
    fun `lint does not report dead end on last scene of last chapter`() {
        // Ф1.7.0: "last scene of the last chapter" is no longer an
        // implicit ending — the scene must be explicitly marked.
        val a = scene("a", isEnding = true) // terminal, last scene, explicitly an ending
        val compiled = compile(adventure("a", chapter("ch", a)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint reports reachable dead end mid-adventure`() {
        val a = scene("a", choices = listOf(choice("c1", "b")))
        val b = scene("b") // terminal, reachable, not last scene
        val c = scene("c") // last scene, but unreachable from a
        val compiled = compile(
            adventure("a", chapter("ch1", a, b), chapter("ch2", c)),
        )

        val issues = Analyzer.lint(compiled)
        val kinds = issues.map { it.kind }
        assertTrue(LintKind.DeadEnd in kinds, "expected DeadEnd, got $kinds")
        assertTrue(
            LintKind.UnreachableScene in kinds,
            "expected UnreachableScene(c), got $kinds",
        )
        assertEquals(
            listOf(SceneId("b")),
            issues.filter { it.kind == LintKind.DeadEnd }.mapNotNull { it.sceneId },
        )
    }

    @Test
    fun `lint does not report dead end on unreachable terminal scene`() {
        // Ф1.7.0: `b` must be explicitly marked as an ending, otherwise
        // the "last scene of the last chapter" convenience no longer
        // applies and DeadEnd(b) fires.
        val a = scene("a", choices = listOf(choice("c1", "b")))
        val b = scene("b", isEnding = true) // reachable, terminal, explicitly an ending
        val orphan = scene("orphan")         // unreachable, terminal, mid-chapter
        val compiled = compile(
            adventure("a", chapter("ch", a, orphan, b)),
        )

        val kinds = Analyzer.lint(compiled).map { it.kind }
        assertTrue(LintKind.UnreachableScene in kinds)
        assertTrue(
            LintKind.DeadEnd !in kinds,
            "unreachable terminal scene must not be double-flagged as DeadEnd: $kinds",
        )
    }

    // ---------- integration: test_adventure.json is lint-clean ----------

    @Test
    fun `lint reports dead end as WARNING`() {
        // Revised 2026-11-07: DeadEnd is a WARNING (the reader warns,
        // does not block). Only EndingSceneWithChoices remains ERROR.
        val a = scene("a", choices = listOf(choice("c1", "b")))
        val b = scene("b") // terminal, no isEnding → DeadEnd
        val compiled = compile(adventure("a", chapter("ch", a, b)))

        val deadEnd = Analyzer.lint(compiled).single { it.kind == LintKind.DeadEnd }
        assertEquals(Severity.WARNING, deadEnd.severity)
        assertEquals(SceneId("b"), deadEnd.sceneId)
        assertTrue(deadEnd.message.contains("is_ending"), deadEnd.message)
    }

    @Test
    fun `lint reports ending scene with choices`() {
        val a = scene(
            "a",
            choices = listOf(choice("c1", "a")),
            isEnding = true, // contradictory: ending + choices
        )
        val compiled = compile(adventure("a", chapter("ch", a)))

        val issue = Analyzer.lint(compiled).single {
            it.kind is LintKind.EndingSceneWithChoices
        }
        assertEquals(Severity.ERROR, issue.severity)
        assertEquals(SceneId("a"), issue.sceneId)
        assertEquals(LintKind.EndingSceneWithChoices(SceneId("a")), issue.kind)
        assertTrue(issue.message.contains("1 choice"), issue.message)
    }

    @Test
    fun `lint does not report ending scene without choices`() {
        val a = scene("a", isEnding = true)
        val compiled = compile(adventure("a", chapter("ch", a)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `multiple endings are allowed`() {
        // mid branches to two endings; both are isEnding = true, no choices.
        val mid = scene(
            "mid",
            choices = listOf(
                choice("c1", "win"),
                choice("c2", "retire"),
            ),
        )
        val win = scene("win", isEnding = true)
        val retire = scene("retire", isEnding = true)
        val compiled = compile(adventure("mid", chapter("ch", mid, win, retire)))

        assertEquals(emptyList(), Analyzer.lint(compiled))
    }

    @Test
    fun `lint is clean on test_adventure_json`() {
        // test_adventure.json is the reference sample: every new user gets
        // it copied into their library on first launch. If the analyzer
        // flags something in it, either the sample is broken (fix the file)
        // or the new check is too aggressive (fix the check). This test is
        // the tripwire.
        val stream = javaClass.getResourceAsStream("/test_adventure.json")
            ?: error("test_adventure.json not on classpath")
        val raw = stream.bufferedReader().use { it.readText() }
        val adventure = assertIs<ParseResult.Success<Adventure>>(
            JsonToIr.parse(raw),
            "test_adventure.json must parse successfully",
        ).value
        val compiled = assertIs<CompileResult.Success>(
            Engine.compile(adventure),
            "test_adventure.json must compile successfully",
        ).compiled

        assertEquals(
            emptyList(),
            Analyzer.lint(compiled),
            "test_adventure.json must be lint-clean under current rules",
        )
    }
}