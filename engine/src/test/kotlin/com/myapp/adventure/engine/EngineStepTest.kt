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
import com.myapp.adventure.engine.ir.CompareOp
import com.myapp.adventure.engine.ir.Invariant
import com.myapp.adventure.engine.ir.Outcome
import com.myapp.adventure.engine.ir.Subroutine
import com.myapp.adventure.engine.ir.SubroutineCall
import com.myapp.adventure.engine.ir.SubroutineStep
import com.myapp.adventure.engine.ir.types.VarDecl
import com.myapp.adventure.engine.ir.types.VarType
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.engine.CompiledAdventure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        isMerge: Boolean = false,
        mergeSources: List<SceneId> = emptyList(),
    ): Scene = Scene(
        id = SceneId(id),
        text = "text of $id",
        effects = effects,
        choices = choices,
        gate = gate,
        isMerge = isMerge,
        mergeSources = mergeSources,
    )

    private fun choice(
        id: String,
        next: String,
        condition: Condition? = null,
        effects: List<Effect> = emptyList(),
        call: SubroutineCall? = null,
    ): Choice = Choice(
        id = ChoiceId(id),
        text = "choice $id",
        nextScene = SceneId(next),
        condition = condition,
        effects = effects,
        call = call,
    )

    private fun chapter(id: String, vararg scenes: Scene): Chapter =
        Chapter(id = id, title = id, scenes = scenes.toList())

    private fun adventure(
        vararg chapters: Chapter,
        subroutines: List<Subroutine> = emptyList(),
    ): Adventure = Adventure(
        title = "T",
        startScene = chapters.firstOrNull()?.scenes?.firstOrNull()?.id ?: SceneId(""),
        subroutines = subroutines,
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
    fun `choice effects kill player - isDead true via fallback`() {
        // No invariants in this hand-built Adventure → Engine.evaluateInvariants
        // falls back to health <= 0. Same behaviour as pre-Ф3.5.
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
        assertEquals(Outcome.Death, success.outcome)
        assertEquals(0, success.state.stats["health"]) // clamped
    }

    @Test
    fun `explicit invariant fires - death`() {
        // Adventure declares an explicit invariant. The fallback is
        // bypassed; the declared rule fires.
        val a = scene(
            "a",
            listOf(
                choice("c", next = "b", effects = listOf(Effect.ModifyStat("health", -999))),
            ),
        )
        val b = scene("b")
        val adv = Adventure(
            title = "T",
            startScene = SceneId("a"),
            invariants = listOf(
                Invariant(
                    condition = Condition.Compare("health", CompareOp.LE, 0),
                    outcome = Outcome.Death,
                ),
            ),
            chapters = listOf(chapter("ch", a, b)),
        )
        val compiled = compile(adv)

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100)),
            SceneId("a"),
            ChoiceId("c"),
        )
        val success = assertIs<StepResult.Success>(result)
        assertTrue(success.isDead)
    }

    @Test
    fun `explicit invariants replace fallback - no death when health zero but no matching invariant`() {
        // Author declared only a stamina invariant. Health=0 does not
        // kill because the fallback is bypassed when invariants is
        // non-empty. This is the "explicit replaces implicit" contract.
        val a = scene(
            "a",
            listOf(
                choice("c", next = "b", effects = listOf(Effect.ModifyStat("health", -999))),
            ),
        )
        val b = scene("b")
        val adv = Adventure(
            title = "T",
            startScene = SceneId("a"),
            invariants = listOf(
                Invariant(
                    condition = Condition.Compare("stamina", CompareOp.LE, 0),
                    outcome = Outcome.Death,
                ),
            ),
            chapters = listOf(chapter("ch", a, b)),
        )
        val compiled = compile(adv)

        val result = Engine.step(
            compiled,
            state(stats = mapOf("health" to 100, "stamina" to 50)),
            SceneId("a"),
            ChoiceId("c"),
        )
        val success = assertIs<StepResult.Success>(result)
        assertFalse(success.isDead)
        assertEquals(null, success.outcome)
        assertEquals(0, success.state.stats["health"]) // clamped, but no death
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

        // Gate 1 (Ф1.7.0.4 simplified all test gates to 1 step so the
        // reference sample can be checked by walking — one step is
        // enough to exercise the mechanic). stepsInScene = 0 → blocked.
        val blocked = rejected(
            Engine.step(
                compiled,
                state = adventure.initialState.toPlayerState(),
                sceneId = SceneId("chapter_end_1"),
                choiceId = continueChoice.id,
                stepsInScene = 0,
            ),
        )
        val blockedReason = assertIs<RejectReason.Blocked>(blocked)
        assertEquals(1, (blockedReason.blockers.single() as Blocker.GateNotPassed).remainingSteps)

        // Gate 1, stepsInScene = 1 → success, moves to conditions_test.
        val success = assertIs<StepResult.Success>(
            Engine.step(
                compiled,
                state = adventure.initialState.toPlayerState(),
                sceneId = SceneId("chapter_end_1"),
                choiceId = continueChoice.id,
                stepsInScene = 1,
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

    // ---------- Ф4.5: merge points ----------

    @Test
    fun `merge point receives accumulated delta from either branch`() {
        val left = scene(
            "left",
            choices = listOf(
                choice(
                    id = "take_left",
                    next = "merge",
                    effects = listOf(
                        Effect.ModifyStat("health", -10),
                        Effect.SetFlag("chose_left"),
                    ),
                ),
            ),
        )
        val right = scene(
            "right",
            choices = listOf(
                choice(
                    id = "take_right",
                    next = "merge",
                    effects = listOf(
                        Effect.ModifyStat("health", 5),
                        Effect.SetFlag("chose_right"),
                    ),
                ),
            ),
        )
        val merge = scene(
            id = "merge",
            isMerge = true,
            mergeSources = listOf(SceneId("left"), SceneId("right")),
        )
        val compiled = compile(adventure(chapter("ch", left, right, merge)))
        val initial = state(stats = mapOf("health" to 100))

        val fromLeft = assertIs<StepResult.Success>(
            Engine.step(compiled, initial, SceneId("left"), ChoiceId("take_left")),
        )
        assertEquals(SceneId("merge"), fromLeft.currentScene.id)
        assertEquals(90, fromLeft.state.stats["health"])
        assertEquals(setOf("chose_left"), fromLeft.state.flags)

        val fromRight = assertIs<StepResult.Success>(
            Engine.step(compiled, initial, SceneId("right"), ChoiceId("take_right")),
        )
        assertEquals(SceneId("merge"), fromRight.currentScene.id)
        assertEquals(105, fromRight.state.stats["health"])
        assertEquals(setOf("chose_right"), fromRight.state.flags)
    }

    // ---------- Ф4.7: subroutines ----------

    private fun consumeSubroutine(): Subroutine = Subroutine(
        id = "consume",
        inputs = listOf(
            VarDecl("item", VarType.ItemCount),
            VarDecl("health", VarType.IntMin(0)),
        ),
        outputs = listOf(
            VarDecl("item", VarType.ItemCount),
            VarDecl("health", VarType.IntMin(0)),
        ),
        body = listOf(
            SubroutineStep(
                condition = Condition.Compare("item", CompareOp.GT, 0),
                effects = listOf(
                    Effect.ModifyStat("item", -1),
                    Effect.ModifyStat("health", 5),
                ),
            ),
        ),
    )

    @Test
    fun `subroutine called from two scenes returns state to each caller`() {
        val consume = consumeSubroutine()
        val a = scene(
            "a",
            choices = listOf(
                choice(
                    id = "use_a",
                    next = "a",
                    call = SubroutineCall(
                        subroutineId = "consume",
                        args = mapOf("item" to "potion", "health" to "health"),
                        outputs = mapOf("item" to "potion", "health" to "health"),
                    ),
                ),
            ),
        )
        val b = scene(
            "b",
            choices = listOf(
                choice(
                    id = "use_b",
                    next = "b",
                    call = SubroutineCall(
                        subroutineId = "consume",
                        args = mapOf("item" to "elixir", "health" to "health"),
                        outputs = mapOf("item" to "elixir", "health" to "health"),
                    ),
                ),
            ),
        )
        val compiled = compile(
            adventure(chapter("ch", a, b), subroutines = listOf(consume)),
        )
        val initial = state(
            stats = mapOf("potion" to 1, "elixir" to 2, "health" to 10),
        )

        val fromA = assertIs<StepResult.Success>(
            Engine.step(compiled, initial, SceneId("a"), ChoiceId("use_a")),
        )
        assertEquals(SceneId("a"), fromA.currentScene.id)
        assertEquals(0, fromA.state.stats["potion"])
        assertEquals(2, fromA.state.stats["elixir"])
        assertEquals(15, fromA.state.stats["health"])

        val fromB = assertIs<StepResult.Success>(
            Engine.step(compiled, initial, SceneId("b"), ChoiceId("use_b")),
        )
        assertEquals(SceneId("b"), fromB.currentScene.id)
        assertEquals(1, fromB.state.stats["potion"])
        assertEquals(1, fromB.state.stats["elixir"])
        assertEquals(15, fromB.state.stats["health"])
    }

    @Test
    fun `nested subroutine calls preserve the frame stack`() {
        val addFive = Subroutine(
            id = "add_five",
            inputs = listOf(VarDecl("health", VarType.IntMin(0))),
            outputs = listOf(VarDecl("health", VarType.IntMin(0))),
            body = listOf(
                SubroutineStep(effects = listOf(Effect.ModifyStat("health", 5))),
            ),
        )
        val addTen = Subroutine(
            id = "add_ten",
            inputs = listOf(VarDecl("health", VarType.IntMin(0))),
            outputs = listOf(VarDecl("health", VarType.IntMin(0))),
            body = listOf(
                SubroutineStep(
                    call = SubroutineCall(
                        subroutineId = "add_five",
                        args = mapOf("health" to "health"),
                        outputs = mapOf("health" to "health"),
                    ),
                ),
                SubroutineStep(
                    call = SubroutineCall(
                        subroutineId = "add_five",
                        args = mapOf("health" to "health"),
                        outputs = mapOf("health" to "health"),
                    ),
                ),
            ),
        )
        val s = scene(
            "s",
            choices = listOf(
                choice(
                    id = "boost",
                    next = "s",
                    call = SubroutineCall(
                        subroutineId = "add_ten",
                        args = mapOf("health" to "health"),
                        outputs = mapOf("health" to "health"),
                    ),
                ),
            ),
        )
        val compiled = compile(
            adventure(chapter("ch", s), subroutines = listOf(addFive, addTen)),
        )

        val result = assertIs<StepResult.Success>(
            Engine.step(
                compiled,
                state(stats = mapOf("health" to 10)),
                SceneId("s"),
                ChoiceId("boost"),
            ),
        )
        assertEquals(20, result.state.stats["health"])
    }

    @Test
    fun `invariants are evaluated after subroutine outputs`() {
        val kill = Subroutine(
            id = "kill",
            inputs = listOf(VarDecl("health", VarType.IntMin(0))),
            outputs = listOf(VarDecl("health", VarType.IntMin(0))),
            body = listOf(
                SubroutineStep(effects = listOf(Effect.Set("health", 0))),
            ),
        )
        val s = scene(
            "s",
            choices = listOf(
                choice(
                    id = "die",
                    next = "s",
                    call = SubroutineCall(
                        subroutineId = "kill",
                        args = mapOf("health" to "health"),
                        outputs = mapOf("health" to "health"),
                    ),
                ),
            ),
        )
        val compiled = compile(
            adventure(chapter("ch", s), subroutines = listOf(kill)),
        )

        val result = assertIs<StepResult.Success>(
            Engine.step(
                compiled,
                state(stats = mapOf("health" to 10)),
                SceneId("s"),
                ChoiceId("die"),
            ),
        )
        assertEquals(0, result.state.stats["health"])
        assertEquals(Outcome.Death, result.outcome)
    }

    @Test
    fun `guarded subroutine step is skipped when condition is false`() {
        val consume = consumeSubroutine()
        val s = scene(
            "s",
            choices = listOf(
                choice(
                    id = "try",
                    next = "s",
                    call = SubroutineCall(
                        subroutineId = "consume",
                        args = mapOf("item" to "potion", "health" to "health"),
                        outputs = mapOf("item" to "potion", "health" to "health"),
                    ),
                ),
            ),
        )
        val compiled = compile(
            adventure(chapter("ch", s), subroutines = listOf(consume)),
        )

        val result = assertIs<StepResult.Success>(
            Engine.step(
                compiled,
                state(stats = mapOf("potion" to 0, "health" to 10)),
                SceneId("s"),
                ChoiceId("try"),
            ),
        )
        assertEquals(0, result.state.stats["potion"])
        assertEquals(10, result.state.stats["health"])
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: error("Resource '$path' not found on classpath")
        return stream.bufferedReader().use { it.readText() }
    }
}