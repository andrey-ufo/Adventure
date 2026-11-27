package com.myapp.adventure.data

import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.engine.StepResult
import com.myapp.adventure.engine.applyDelta
import com.myapp.adventure.engine.applyDeltas
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ф5.8: operation log stays bounded and exact over long play sessions. */
class OperationLogBufferTest {

    @Test
    fun thousandStepsKeepLogBoundedAndReplayExact() {
        val buffer = OperationLogBuffer()
        var state = PlayerState(
            flags = emptySet(),
            stats = mapOf("health" to 0),
        )
        var base = ProgressSnapshot(stats = state.stats)
        var log = emptyList<StateDelta>()
        var compactions = 0
        var maxLogSize = 0

        repeat(1_000) { step ->
            val delta = StateDelta.AddStat("health", 1)
            state = applyDelta(state, delta)

            val saveLog = buffer.append(
                base = base,
                current = log,
                newDeltas = listOf(delta),
                compactedBase = {
                    compactions++
                    ProgressSnapshot(
                        currentSceneId = "scene-$step",
                        stats = state.stats,
                    )
                },
            )
            base = saveLog.base
            log = saveLog.deltas
            maxLogSize = maxOf(maxLogSize, log.size)

            assertTrue(log.size <= OperationLogBuffer.MAX_DELTAS)
            val replayed = applyDeltas(
                PlayerState(flags = base.flags.toSet(), stats = base.stats),
                log,
            )
            assertEquals(state, replayed)
        }

        assertEquals(1_000, state.stats["health"])
        assertTrue(compactions > 0)
        assertEquals(OperationLogBuffer.MAX_DELTAS, maxLogSize)
    }

    @Test
    fun thousandRealEngineStepsKeepLogBoundedAndReplayExact() {
        val sceneId = SceneId("loop")
        val choiceId = ChoiceId("again")
        val scene = Scene(
            id = sceneId,
            text = "Loop",
            choices = listOf(
                Choice(
                    id = choiceId,
                    text = "Again",
                    nextScene = sceneId,
                    effects = listOf(Effect.ModifyStat("steps", 1)),
                ),
            ),
        )
        val adventure = Adventure(
            title = "Loop",
            startScene = sceneId,
            chapters = listOf(Chapter(id = "c1", title = "Loop", scenes = listOf(scene))),
        )
        val compiled = (Engine.compile(adventure) as CompileResult.Success).compiled

        val buffer = OperationLogBuffer()
        var engineState = PlayerState(stats = mapOf("steps" to 0))
        var base = ProgressSnapshot(stats = engineState.stats)
        var log = emptyList<StateDelta>()

        repeat(1_000) {
            val step = Engine.step(
                compiled = compiled,
                state = engineState,
                sceneId = sceneId,
                choiceId = choiceId,
            ) as StepResult.Success
            engineState = step.state

            val saveLog = buffer.append(
                base = base,
                current = log,
                newDeltas = step.appliedDeltas,
                compactedBase = {
                    ProgressSnapshot(
                        currentSceneId = sceneId.value,
                        stats = engineState.stats,
                    )
                },
            )
            base = saveLog.base
            log = saveLog.deltas
            assertTrue(log.size <= OperationLogBuffer.MAX_DELTAS)
        }

        assertEquals(1_000, engineState.stats["steps"])
        assertEquals(
            engineState,
            applyDeltas(
                PlayerState(flags = base.flags.toSet(), stats = base.stats),
                log,
            ),
        )
    }

    @Test
    fun exactlyMaxDeltasStaysAndOneMoreCompacts() {
        val buffer = OperationLogBuffer(maxDeltas = 2)
        val base = ProgressSnapshot(stats = mapOf("health" to 10))
        val compactedBase = ProgressSnapshot(stats = mapOf("health" to 12))
        var log = emptyList<StateDelta>()

        log = buffer.append(base, log, listOf(StateDelta.AddStat("health", 1))) {
            error("must not compact at 1")
        }.deltas
        log = buffer.append(base, log, listOf(StateDelta.AddStat("health", 1))) {
            error("must not compact at 2")
        }.deltas
        assertEquals(2, log.size)

        var compacted = false
        val result = buffer.append(base, log, listOf(StateDelta.AddStat("health", 1))) {
            compacted = true
            compactedBase
        }

        assertTrue(compacted)
        assertSame(compactedBase, result.base)
        assertTrue(result.deltas.isEmpty())
    }

    @Test
    fun fiveHundredSceneAdventureSurvivesThousandSteps() {
        val sceneCount = 500
        val scenes = (0 until sceneCount).map { i ->
            Scene(
                id = SceneId("s$i"),
                text = "Scene $i",
                choices = listOf(
                    Choice(
                        id = ChoiceId("next"),
                        text = "Next",
                        nextScene = SceneId("s${(i + 1) % sceneCount}"),
                        effects = listOf(Effect.ModifyStat("steps", 1)),
                    ),
                ),
            )
        }
        val adventure = Adventure(
            title = "Large",
            startScene = SceneId("s0"),
            chapters = listOf(
                Chapter(id = "c1", title = "Large", scenes = scenes),
            ),
        )
        val compiled = (Engine.compile(adventure) as CompileResult.Success).compiled

        val buffer = OperationLogBuffer()
        var state = PlayerState(stats = mapOf("steps" to 0))
        var scene = SceneId("s0")
        var base = ProgressSnapshot(stats = state.stats)
        var log = emptyList<StateDelta>()

        repeat(1_000) {
            val step = Engine.step(
                compiled = compiled,
                state = state,
                sceneId = scene,
                choiceId = ChoiceId("next"),
            ) as StepResult.Success
            state = step.state
            scene = step.currentScene.id

            val saveLog = buffer.append(
                base = base,
                current = log,
                newDeltas = step.appliedDeltas,
                compactedBase = {
                    ProgressSnapshot(
                        currentSceneId = scene.value,
                        stats = state.stats,
                    )
                },
            )
            base = saveLog.base
            log = saveLog.deltas
            assertTrue(log.size <= OperationLogBuffer.MAX_DELTAS)
        }

        assertEquals(1_000, state.stats["steps"])
        assertEquals(
            state,
            applyDeltas(
                PlayerState(flags = base.flags.toSet(), stats = base.stats),
                log,
            ),
        )
    }

    @Test
    fun appendWithNoDeltasDoesNotCopyOrCompact() {
        val buffer = OperationLogBuffer()
        val base = ProgressSnapshot(stats = mapOf("health" to 10))
        val current = listOf(StateDelta.SetFlag("seen"))
        var compacted = false

        val result = buffer.append(
            base = base,
            current = current,
            newDeltas = emptyList(),
            compactedBase = {
                compacted = true
                base
            },
        )

        assertSame(base, result.base)
        assertSame(current, result.deltas)
        assertFalse(compacted)
    }

    @Test
    fun loadingOversizedLogFoldsItIntoCurrentSnapshot() {
        val buffer = OperationLogBuffer()
        val initial = ProgressSnapshot(
            currentSceneId = "start",
            stats = mapOf("health" to 100),
        )
        val deltas = (1..100).map { StateDelta.AddStat("health", -1) }
        val current = ProgressSnapshot(
            currentSceneId = "end",
            stats = mapOf("health" to 0),
        )
        var compacted = false

        val result = buffer.compactIfNeeded(initial, deltas) {
            compacted = true
            current
        }

        assertTrue(compacted)
        assertSame(current, result.base)
        assertTrue(result.deltas.isEmpty())
    }

    @Test
    fun loadingBoundedLogKeepsItAsIs() {
        val buffer = OperationLogBuffer()
        val base = ProgressSnapshot(stats = mapOf("health" to 10))
        val deltas = listOf(StateDelta.AddStat("health", -1))
        var compacted = false

        val result = buffer.compactIfNeeded(base, deltas) {
            compacted = true
            base
        }

        assertFalse(compacted)
        assertSame(base, result.base)
        assertSame(deltas, result.deltas)
    }
}
