package com.myapp.adventure.utils

import com.myapp.adventure.data.OperationLogBuffer
import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.data.ProgressSnapshot
import com.myapp.adventure.data.SaveSlotPaths
import com.myapp.adventure.data.toDomainOrNull
import com.myapp.adventure.data.toDto
import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.EffectsApplier
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.StateDelta
import com.myapp.adventure.engine.StepResult
import com.myapp.adventure.engine.applyDeltas
import com.myapp.adventure.engine.effectsToDeltas
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Chapter
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.ChoiceId
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ф5.9: end-to-end persistence round trip.
 *
 * Drives a real compiled adventure through many `Engine.step` calls,
 * including scene effects and log compaction, then:
 * 1. saves autosave via [ProgressStore] (Gson → `progress.json`);
 * 2. saves a manual slot via [SlotStore];
 * 3. loads both back and replays `base + delta`;
 * 4. continues playing and proves the autosave does not touch the slot.
 *
 * This runs in the normal `:app:testDebugUnitTest` CI job.
 */
class SaveRoundTripIntegrationTest {

    private val fileName = "roundtrip.json"
    private val adventureKey = SaveSlotPaths.adventureKey(fileName)

    @Test
    fun stepsSaveLoadRoundTripAndSlotIsolation() = runBlocking {
        val storage = InMemorySaveStorage()
        val simulation = Simulation(compiledAdventure(), OperationLogBuffer())
        simulation.applyCurrentSceneIfNeeded()
        repeat(40) { simulation.step() }

        val checkpoint = simulation.toProgressData(fileName)
        assertTrue(checkpoint.delta.orEmpty().size <= OperationLogBuffer.MAX_DELTAS)
        assertTrue(checkpoint.baseSnapshot != null)

        // Autosave through the same cache/file path as the reader.
        val autosaveStore = ProgressStore(storage)
        assertTrue(autosaveStore.save(fileName, checkpoint))
        autosaveStore.flush()
        assertNotNull(storage.readDocument(listOf(ProgressStore.PROGRESS_FILE_NAME)))

        // Manual slot at the same point.
        val slotStore = SlotStore(storage)
        val slot = slotStore.create(adventureKey, "checkpoint", checkpoint)
        val slotPath = listOf(
            SaveSlotPaths.SAVES_DIRECTORY,
            adventureKey,
            SaveSlotPaths.slotFileName(slot.id),
        )
        val slotBytesBefore = storage.readDocument(slotPath)
        assertNotNull(slotBytesBefore)

        // Continue playing; autosave must change only progress.json.
        repeat(5) { simulation.step() }
        val later = simulation.toProgressData(fileName)
        assertTrue(autosaveStore.save(fileName, later))
        autosaveStore.flush()

        assertEquals(slotBytesBefore, storage.readDocument(slotPath))

        // Load autosave from a fresh cache (new process simulation).
        val loaded = ProgressStore(storage).load(fileName)
        assertNotNull(loaded)
        requireNotNull(loaded)
        assertTrue(loaded.delta.orEmpty().size <= OperationLogBuffer.MAX_DELTAS)
        assertEquals(later.stats, loaded.stats)
        assertEquals(later.flags, loaded.flags)
        assertEquals(later.currentSceneId, loaded.currentSceneId)
        assertEquals(later.itemOrder, loaded.itemOrder)
        assertEquals(later.appliedSceneEffects, loaded.appliedSceneEffects)
        assertEquals(later.passedGates, loaded.passedGates)
        assertReplayMatches(loaded)

        // Load the manual slot and assert the same exact state.
        val loadedSlot = slotStore.load(adventureKey, slot.id)
        assertNotNull(loadedSlot)
        requireNotNull(loadedSlot)
        assertEquals(checkpoint.stats, loadedSlot.state.stats)
        assertEquals(checkpoint.flags, loadedSlot.state.flags)
        assertEquals(checkpoint.currentSceneId, loadedSlot.state.currentSceneId)
        assertEquals(checkpoint.itemOrder, loadedSlot.state.itemOrder)
        assertEquals(checkpoint.appliedSceneEffects, loadedSlot.state.appliedSceneEffects)
        assertReplayMatches(loadedSlot.state)
    }

    @Test
    fun thousandStepsKeepPersistedSaveBounded() = runBlocking {
        val simulation = Simulation(compiledAdventure(), OperationLogBuffer())
        simulation.applyCurrentSceneIfNeeded()
        repeat(1_000) { simulation.step() }

        val progress = simulation.toProgressData(fileName)
        assertTrue(progress.delta.orEmpty().size <= OperationLogBuffer.MAX_DELTAS)
        assertTrue(progress.baseSnapshot != null)

        val storage = InMemorySaveStorage()
        val store = ProgressStore(storage)
        assertTrue(store.save(fileName, progress))
        store.flush()
        val json = requireNotNull(storage.readDocument(listOf(ProgressStore.PROGRESS_FILE_NAME)))
        assertTrue("progress.json grew to ${json.length} chars", json.length < 20_000)
    }

    @Test
    fun everyProgressFieldSurvivesAutosaveAndSlot() = runBlocking {
        val storage = InMemorySaveStorage()
        val previous = PlayerStateSnapshot(
            sceneId = "previous",
            chapterIndex = 1,
            flags = listOf("prev_flag"),
            stats = mapOf("health" to 9),
            itemOrder = listOf("potion"),
            appliedSceneEffects = setOf("previous"),
        )
        val chapterStart = previous.copy(sceneId = "chapter_start")
        val base = ProgressSnapshot(
            currentChapterIndex = 1,
            currentSceneId = "base_scene",
            flags = listOf("base_flag"),
            stats = mapOf("base_stat" to 1),
            itemOrder = listOf("base_item"),
            appliedSceneEffects = setOf("base_scene"),
            remainingSteps = 99,
            isGateActive = true,
            warningsFingerprint = "base_fp",
            passedGates = listOf("base_gate"),
        )
        val progress = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = fileName,
            baseSnapshot = base,
            delta = listOf(
                StateDelta.AddStat("gold", 2).toDto(),
                StateDelta.SetFlag("sword").toDto(),
            ),
            currentChapterIndex = 3,
            currentSceneId = "current_scene",
            flags = listOf("base_flag", "sword"),
            stats = mapOf("base_stat" to 1, "gold" to 2),
            appliedSceneEffects = setOf("base_scene", "current_scene"),
            itemOrder = listOf("base_item", "sword"),
            previousStepSnapshot = previous,
            chapterStartSnapshot = chapterStart,
            remainingSteps = 7,
            isGateActive = true,
            lastAbsoluteAtSave = 12345,
            warningsFingerprint = "warnings_fp",
            passedGates = listOf("base_gate", "gate_2"),
        )

        val autosave = ProgressStore(storage)
        assertTrue(autosave.save(fileName, progress))
        autosave.flush()
        val loaded = ProgressStore(storage).load(fileName)
        assertNotNull(loaded)
        assertEquals(progress, loaded)

        val slotStore = SlotStore(storage)
        val slot = slotStore.create(adventureKey, "all fields", progress, now = 42)
        val loadedSlot = slotStore.load(adventureKey, slot.id)
        assertNotNull(loadedSlot)
        assertEquals(progress, loadedSlot!!.state)
    }

    private fun assertReplayMatches(progress: ProgressData) {
        val base = requireNotNull(progress.baseSnapshot)
        val deltas = progress.delta.orEmpty().mapNotNull { it.toDomainOrNull() }
        val replayed = applyDeltas(
            PlayerState(flags = base.flags.toSet(), stats = base.stats),
            deltas,
        )
        assertEquals(progress.stats, replayed.stats)
        assertEquals(progress.flags.toSet(), replayed.flags)
    }

    private fun compiledAdventure(): CompiledAdventure {
        val s1 = SceneId("s1")
        val s2 = SceneId("s2")
        val adventure = Adventure(
            title = "Round trip",
            startScene = s1,
            chapters = listOf(
                Chapter(
                    id = "c1",
                    title = "Chapter",
                    scenes = listOf(
                        Scene(
                            id = s1,
                            text = "Start",
                            effects = listOf(
                                Effect.SetFlag("started"),
                                Effect.ModifyStat("health", 5),
                            ),
                            choices = listOf(
                                Choice(
                                    id = ChoiceId("to_s2"),
                                    text = "Go",
                                    nextScene = s2,
                                    effects = listOf(Effect.ModifyStat("gold", 1)),
                                ),
                            ),
                        ),
                        Scene(
                            id = s2,
                            text = "Second",
                            effects = listOf(Effect.ModifyStat("gold", 1)),
                            choices = listOf(
                                Choice(
                                    id = ChoiceId("to_s1"),
                                    text = "Back",
                                    nextScene = s1,
                                    effects = listOf(
                                        Effect.ModifyStat("gold", 2),
                                        Effect.ModifyStat("potion", 1),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return (Engine.compile(adventure) as CompileResult.Success).compiled
    }

    /**
     * Mirrors the reader's persistence-relevant state transitions: engine
     * choice step, first-entry scene effects, item order, bounded log.
     */
    private class Simulation(
        private val compiled: CompiledAdventure,
        private val logBuffer: OperationLogBuffer,
    ) {
        private var playerState = PlayerState(
            stats = mapOf("health" to 10, "gold" to 0, "potion" to 0),
        )
        private var sceneId = compiled.adventure.startScene
        private var appliedSceneEffects = emptySet<String>()
        private var itemOrder = emptyList<String>()

        private var base = ProgressSnapshot(
            currentSceneId = sceneId.value,
            stats = playerState.stats,
            appliedSceneEffects = appliedSceneEffects,
        )
        private var log = emptyList<StateDelta>()

        fun applyCurrentSceneIfNeeded() {
            val scene = compiled.scenesById[sceneId] ?: return
            if (scene.id.value in appliedSceneEffects) return
            playerState = EffectsApplier.apply(playerState, scene.effects)
            appliedSceneEffects = appliedSceneEffects + scene.id.value
            append(effectsToDeltas(scene.effects))
            syncItemOrder()
        }

        fun step() {
            val scene = compiled.scenesById[sceneId] ?: error("scene missing")
            val choice = scene.choices.first()
            val result = Engine.step(
                compiled = compiled,
                state = playerState,
                sceneId = sceneId,
                choiceId = choice.id,
            ) as StepResult.Success

            playerState = result.state
            sceneId = result.currentScene.id
            append(result.appliedDeltas)
            applyCurrentSceneIfNeeded()
        }

        fun toProgressData(fileName: String): ProgressData = ProgressData(
            version = ProgressMigration.CURRENT_VERSION,
            adventureFileName = fileName,
            baseSnapshot = base,
            delta = log.map { it.toDto() },
            currentChapterIndex = 0,
            currentSceneId = sceneId.value,
            flags = playerState.flags.toList(),
            stats = playerState.stats,
            appliedSceneEffects = appliedSceneEffects,
            itemOrder = itemOrder,
            remainingSteps = 0,
            isGateActive = false,
            lastAbsoluteAtSave = -1,
            warningsFingerprint = "",
            passedGates = emptyList(),
        )

        private fun append(deltas: List<StateDelta>) {
            if (deltas.isEmpty()) return
            val saveLog = logBuffer.append(
                base = base,
                current = log,
                newDeltas = deltas,
                compactedBase = { snapshot() },
            )
            base = saveLog.base
            log = saveLog.deltas
        }

        private fun syncItemOrder() {
            if ((playerState.stats["potion"] ?: 0) > 0 && "potion" !in itemOrder) {
                itemOrder = itemOrder + "potion"
            }
        }

        private fun snapshot(): ProgressSnapshot = ProgressSnapshot(
            currentSceneId = sceneId.value,
            flags = playerState.flags.toList(),
            stats = playerState.stats,
            itemOrder = itemOrder,
            appliedSceneEffects = appliedSceneEffects,
            warningsFingerprint = "",
            passedGates = emptyList(),
        )
    }
}
