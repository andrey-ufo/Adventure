package com.myapp.adventure.engine

import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.Gate
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.toPlayerState
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.engine.CompiledAdventure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineStepTest {

    // ---------- helpers ----------

    private fun scene(
        id: String,
        choices: List<Choice> = emptyList(),
        gate: Gate? = null,
        effects: List<Effect> = emptyList(),
    ): Scene = Scene(
        id = SceneId(id),
        text = "text of $id",
        effects = effects,
        choices = choices,
        gate = gate,
    )

    private fun choice(
        id: String,
        next: String,
        condition: Condition? = null,
        effects: List<Effect> = emptyList(),
    ): Choice = Choice(
        id = ChoiceId(id),
        text = "choice $id",
        nextScene = SceneId(next),
        condition = condition,
        effects = effects,
    )

    private fun chapter(id: String, vararg scenes: Scene): Chapter =
        Chapter(id = id, title = id, scenes = scenes.toList())

    private fun adventure(vararg chapters: Chapter): Adventure = Adventure(
        title = "T",
        startScene = chapters.firstOrNull()?.scenes?.firstOrNull()?.id ?: SceneId(""),
        chapters = chapters.toList(),
    )

    private fun compile(adventure: Adventure): CompiledAdventure =
        assertIs<CompileResult.Success>(Engine.compile(adventure)).compiled

    private fun state(
        flags: Set<String> = emptySet(),
        stats: Map<String, Int> = emptyMap(),
    ): PlayerState = PlayerState(flags = flags, stats = stats)

    private fun rejected(result: StepResult): RejectReason =
        assertIs<StepResult.Rejected>(result, "expected Rejected, got $result").reason

    // ---------- happy path ----------

    @Test
    fun `simple transition succeeds and returns target scene`() {
        val a = scene("a", listOf(choice("c", next = "b")))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))
        val initial = state()

        val result = Engine.step(compiled, initial, SceneId("a"), ChoiceId("c"))
        val success = assertIs<StepResult.Success>(result)
        assertEquals(SceneId("b"), success.currentScene.id)
        assertEquals(false, success.isDead)
        assertEquals(initial, success.state)
    }

    @Test
    fun `choice effects applied to state`() {
        val a = scene(
            "a",
            listOf(
                choice(
                    "c",
                    next = "b",
                    effects = listOf(Effect.ModifyStat("health", -10)),
                ),
            ),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100)),
            SceneId("a"),
            ChoiceId("c"),
        )
        val success = assertIs<StepResult.Success>(result)
        assertEquals(90, success.state.stats["health"])
    }

    @Test
    fun `state is not mutated`() {
        val a = scene(
            "a",
            listOf(choice("c", next = "b", effects = listOf(Effect.ModifyStat("health", -10)))),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))
        val initial = state(stats = mapOf("health" to 100))

        Engine.step(compiled, initial, SceneId("a"), ChoiceId("c"))

        assertEquals(100, initial.stats["health"], "original state must not be mutated")
    }

    @Test
    fun `effects applied in list order`() {
        // Set then remove: flag absent. If order were reversed, flag present.
        val a = scene(
            "a",
            listOf(
                choice(
                    "c",
                    next = "b",
                    effects = listOf(Effect.SetFlag("x"), Effect.RemoveFlag("x")),
                ),
            ),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"))
        val success = assertIs<StepResult.Success>(result)
        assertTrue("x" !in success.state.flags)
    }

    // ---------- structural errors ----------

    @Test
    fun `scene not found`() {
        val a = scene("a", listOf(choice("c", next = "a")))
        val compiled = compile(adventure(chapter("ch", a)))

        val reason = rejected(
            Engine.step(compiled, state(), SceneId("missing"), ChoiceId("c")),
        )
        assertIs<RejectReason.SceneNotFound>(reason)
        assertEquals(SceneId("missing"), reason.sceneId)
    }

    @Test
    fun `choice not found`() {
        val a = scene("a", listOf(choice("c", next = "a")))
        val compiled = compile(adventure(chapter("ch", a)))

        val reason = rejected(
            Engine.step(compiled, state(), SceneId("a"), ChoiceId("other")),
        )
        assertIs<RejectReason.ChoiceNotFound>(reason)
        assertEquals(ChoiceId("other"), reason.choiceId)
    }

    @Test
    fun `next scene not found - bug`() {
        // Engine.compile rejects this (Ф1.6.2.1:
        // CompileError.MissingNextScene), so in production the reader
        // never sees this state. But CompiledAdventure is a public data
        // class; anyone can construct one by hand (tests, future tooling).
        // This test documents that Engine.step is **defensive**: with a
        // manually-built CompiledAdventure that has a dangling nextScene,
        // step returns Rejected(SceneNotFound) and does NOT apply choice
        // effects (otherwise the player's health would drain into the void
        // before the reader notices the missing scene).
        val a = scene(
            "a",
            listOf(
                choice("c", next = "ghost", effects = listOf(Effect.ModifyStat("health", -50))),
            ),
        )
        val adv = adventure(chapter("ch", a))
        val compiled = CompiledAdventure(
            adventure = adv,
            scenesById = mapOf(SceneId("a") to a),
            chapterIndexBySceneId = mapOf(SceneId("a") to 0),
        )

        val reason = rejected(
            Engine.step(
                compiled,
                state(stats = mapOf("health" to 100)),
                SceneId("a"),
                ChoiceId("c"),
            ),
        )
        assertIs<RejectReason.SceneNotFound>(reason)
        assertEquals(SceneId("ghost"), reason.sceneId)
    }

    // ---------- blockers: condition ----------

    @Test
    fun `condition passes - success`() {
        val a = scene(
            "a",
            listOf(
                choice(
                    "c",
                    next = "b",
                    condition = Condition.Compare("health", com.myapp.adventure.engine.ir.CompareOp.GE, 50),
                ),
            ),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100)),
            SceneId("a"),
            ChoiceId("c"),
        )
        assertIs<StepResult.Success>(result)
    }

    @Test
    fun `condition fails - blocked with ConditionNotMet`() {
        val condition = Condition.Compare("health", com.myapp.adventure.engine.ir.CompareOp.GE, 50)
        val a = scene("a", listOf(choice("c", next = "b", condition = condition)))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val reason = rejected(
            Engine.step(compiled, state(stats = mapOf("health" to 10)), SceneId("a"), ChoiceId("c")),
        )
        val blocked = assertIs<RejectReason.Blocked>(reason)
        assertEquals(1, blocked.blockers.size)
        val blocker = assertIs<Blocker.ConditionNotMet>(blocked.blockers[0])
        assertEquals(condition, blocker.condition)
    }

    // ---------- blockers: gate ----------

    @Test
    fun `gate passed - success`() {
        val a = scene("a", listOf(choice("c", next = "b")), gate = Gate(100))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(
            compiled,
            state(),
            SceneId("a"),
            ChoiceId("c"),
            stepsInScene = 150,
        )
        assertIs<StepResult.Success>(result)
    }

    @Test
    fun `gate not passed - blocked with remaining`() {
        val a = scene("a", listOf(choice("c", next = "b")), gate = Gate(100))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val reason = rejected(
            Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"), stepsInScene = 40),
        )
        val blocked = assertIs<RejectReason.Blocked>(reason)
        val gateBlocker = assertIs<Blocker.GateNotPassed>(blocked.blockers.single())
        assertEquals(60, gateBlocker.remainingSteps)
    }

    @Test
    fun `gate present and stepsInScene null - safe default blocked`() {
        // null = "unknown". Treated as 0 walked, so gate blocks with full remaining.
        // Safety: a caller that forgets to pass steps cannot bypass the gate.
        val a = scene("a", listOf(choice("c", next = "b")), gate = Gate(100))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val reason = rejected(
            Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"), stepsInScene = null),
        )
        val blocked = assertIs<RejectReason.Blocked>(reason)
        assertEquals(100, (blocked.blockers.single() as Blocker.GateNotPassed).remainingSteps)
    }

    @Test
    fun `no gate and stepsInScene null - ignored`() {
        val a = scene("a", listOf(choice("c", next = "b")))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"), stepsInScene = null)
        assertIs<StepResult.Success>(result)
    }

    // ---------- multiple blockers ----------

    @Test
    fun `condition and gate both fail - all blockers reported in order`() {
        val condition = Condition.Compare("health", com.myapp.adventure.engine.ir.CompareOp.GE, 50)
        val a = scene("a", listOf(choice("c", next = "b", condition = condition)), gate = Gate(100))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val reason = rejected(
            Engine.step(compiled, state(stats = mapOf("health" to 10)), SceneId("a"), ChoiceId("c"), stepsInScene = 30),
        )
        val blocked = assertIs<RejectReason.Blocked>(reason)
        assertEquals(2, blocked.blockers.size)
        assertIs<Blocker.ConditionNotMet>(blocked.blockers[0])
        assertIs<Blocker.GateNotPassed>(blocked.blockers[1])
        assertEquals(70, (blocked.blockers[1] as Blocker.GateNotPassed).remainingSteps)
    }

    // ---------- death by choice effects ----------

    @Test
    fun `choice effects kill player - isDead true`() {
        val a = scene(
            "a",
            listOf(
                choice("c", next = "b", effects = listOf(Effect.ModifyStat("health", -999))),
            ),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100)),
            SceneId("a"),
            ChoiceId("c"),
        )
        val success = assertIs<StepResult.Success>(result)
        assertTrue(success.isDead)
        assertEquals(0, success.state.stats["health"]) // clamped
    }

    @Test
    fun `choice effects do not kill - isDead false`() {
        val a = scene(
            "a",
            listOf(choice("c", next = "b", effects = listOf(Effect.ModifyStat("health", -10)))),
        )
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100)),
            SceneId("a"),
            ChoiceId("c"),
        )
        val success = assertIs<StepResult.Success>(result)
        assertEquals(false, success.isDead)
        assertEquals(90, success.state.stats["health"])
    }

    @Test
    fun `no health declared - not dead`() {
        val a = scene("a", listOf(choice("c", next = "b")))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch", a, b)))

        val result = Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"))
        val success = assertIs<StepResult.Success>(result)
        assertEquals(false, success.isDead)
    }

    // ---------- cross-chapter ----------

    @Test
    fun `cross-chapter transition - success`() {
        val a = scene("a", listOf(choice("c", next = "b")))
        val b = scene("b")
        val compiled = compile(adventure(chapter("ch1", a), chapter("ch2", b)))

        val result = Engine.step(compiled, state(), SceneId("a"), ChoiceId("c"))
        val success = assertIs<StepResult.Success>(result)
        assertEquals(SceneId("b"), success.currentScene.id)
    }

    // ---------- real test_adventure.json: documented pre-Continue state ----------

    @Test
    fun `chapter_end_1 has explicit Continue choice - player can proceed after gate`() {
        // Ф1.5.5.0: chapter_end_1 and chapter_end_2 now have explicit
        // "Continue" choices. This is the "test first, then fix" pattern:
        // the previous test asserted "no choices"; after the fix, the
        // scene must have exactly one Continue choice, and step() with it
        // must succeed once the gate (5 steps) is passed.
        val json = loadResource("/test_adventure.json")
        val parseResult = JsonToIr.parse(json)
        val adventure = assertIs<ParseResult.Success<Adventure>>(parseResult).value
        val compiled = assertIs<CompileResult.Success>(Engine.compile(adventure)).compiled

        val chapterEnd1 = assertNotNull(compiled.scenesById[SceneId("chapter_end_1")])
        assertEquals(1, chapterEnd1.choices.size, "chapter_end_1 should have one Continue choice")
        val continueChoice = chapterEnd1.choices.single()
        assertEquals("Продолжить", continueChoice.text)
        assertEquals(SceneId("conditions_test"), continueChoice.nextScene)

        // Gate 5, stepsInScene = 4 → blocked.
        val blocked = rejected(
            Engine.step(
                compiled,
                state = adventure.initialState.toPlayerState(),
                sceneId = SceneId("chapter_end_1"),
                choiceId = continueChoice.id,
                stepsInScene = 4,
            ),
        )
        val blockedReason = assertIs<RejectReason.Blocked>(blocked)
        assertEquals(1, (blockedReason.blockers.single() as Blocker.GateNotPassed).remainingSteps)

        // Gate 5, stepsInScene = 5 → success, moves to conditions_test.
        val success = assertIs<StepResult.Success>(
            Engine.step(
                compiled,
                state = adventure.initialState.toPlayerState(),
                sceneId = SceneId("chapter_end_1"),
                choiceId = continueChoice.id,
                stepsInScene = 5,
            ),
        )
        assertEquals(SceneId("conditions_test"), success.currentScene.id)
    }

    @Test
    fun `chapter_end_2 has explicit Continue choice going to final`() {
        val json = loadResource("/test_adventure.json")
        val parseResult = JsonToIr.parse(json)
        val adventure = assertIs<ParseResult.Success<Adventure>>(parseResult).value
        val compiled = assertIs<CompileResult.Success>(Engine.compile(adventure)).compiled

        val chapterEnd2 = assertNotNull(compiled.scenesById[SceneId("chapter_end_2")])
        assertEquals(1, chapterEnd2.choices.size)
        val continueChoice = chapterEnd2.choices.single()
        assertEquals("Продолжить", continueChoice.text)
        assertEquals(SceneId("final"), continueChoice.nextScene)
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: error("Resource '$path' not found on classpath")
        return stream.bufferedReader().use { it.readText() }
    }
}