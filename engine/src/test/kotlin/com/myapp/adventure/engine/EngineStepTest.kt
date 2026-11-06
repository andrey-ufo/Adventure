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

    private fun adventure(vararg chapters: Chapter): Adventure =
        Adventure(title = "T", chapters = chapters.toList())

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
        // choice.nextScene points to a nonexistent scene. The engine returns
        // Rejected(SceneNotFound) BEFORE applying choice effects, so nothing
        // happens to the state.
        val a = scene("a", listOf(choice("c", next = "ghost", effects = listOf(Effect.ModifyStat("health", -50)))))
        val compiled = compile(adventure(chapter("ch", a)))

        val reason = rejected(
            Engine.step(compiled, state(stats = mapOf("health" to 100)), SceneId("a"), ChoiceId("c")),
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
    fun `chapter_end_1 has no choices yet - player cannot proceed (until Continue is added in F1_5_5_0)`() {
        // Ф1.5.5.0 will add an explicit "Continue" choice to chapter_end_1
        // and chapter_end_2 in test_adventure.json. Until then, step() from
        // chapter_end_1 returns ChoiceNotFound for any choice id. This test
        // documents the current state; it will be rewritten in Ф1.5.5.0.
        //
        // If this test starts failing because someone added a choice to
        // chapter_end_1 without updating the test, update this test in the
        // same PR (that's the whole point of "test first, then fix").
        val json = loadResource("/test_adventure.json")
        val parseResult = JsonToIr.parse(json)
        val adventure = assertIs<ParseResult.Success<Adventure>>(parseResult).value
        val compiled = assertIs<CompileResult.Success>(Engine.compile(adventure)).compiled

        val chapterEnd1 = assertNotNull(compiled.scenesById[SceneId("chapter_end_1")])
        assertTrue(
            chapterEnd1.choices.isEmpty(),
            "chapter_end_1 currently has no choices; Ф1.5.5.0 will add them",
        )

        val result = Engine.step(
            compiled,
            state = adventure.initialState.toPlayerState(),
            sceneId = SceneId("chapter_end_1"),
            choiceId = ChoiceId("anything"),
            stepsInScene = 5,
        )
        assertIs<RejectReason.ChoiceNotFound>(rejected(result))
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: error("Resource '$path' not found on classpath")
        return stream.bufferedReader().use { it.readText() }
    }
}