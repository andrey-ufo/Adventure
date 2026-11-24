package com.myapp.adventure.ui.reader

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.engine.ChoiceState
import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.EffectsApplier
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.Evaluator
import com.myapp.adventure.engine.analyze.Analyzer
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.engine.analyze.LintKind
import com.myapp.adventure.engine.analyze.Severity
import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Choice
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.Outcome
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory
import com.myapp.adventure.engine.ir.types.TypeError
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ProgressManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adventure reader state holder (Phase 3.4 complete).
 *
 * **State ownership.** Every field of [ReaderUiState] is mutated only
 * inside this class: loading, choices, step back, gates, death, restart,
 * snapshots, progress, warning acknowledgment and debug actions.
 * [AdventureReaderScreen] and its overlays are pure renderers; they route
 * events back into this VM.
 *
 * **NavBackStackEntry-scoped (Phase 4-c).** `AppNavHost` creates it with
 * `viewModel()` inside the reader destination, so popping the reader
 * disposes it and a fresh open re-runs [init] with the new arguments. This
 * preserves the old Fragment-scoped behaviour (state must not leak between
 * reader sessions).
 *
 * **Host logic lives in [ReaderRoute].** Init, step receiver, progress
 * save/flush, permissions, startup warning and blocking error dialogs are
 * owned by the route.
 */
class AdventureReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val app: Application get() = getApplication()

    // ==================================================================
    // INIT (from 3.4-a-1) + loadCurrentScene at the end
    // ==================================================================

    fun init(arguments: Bundle?): ReaderInitResult {
        val adventureJson = readAdventureJsonFromFile(arguments)
            ?: arguments?.getString("adventure_json")
            ?: return ReaderInitResult.NoAdventureData

        val adventure: Adventure = when (val parseResult = JsonToIr.parse(adventureJson)) {
            is ParseResult.Failure -> {
                if (parseResult.typeErrors.isNotEmpty()) {
                    return ReaderInitResult.TypeErrors(parseResult.typeErrors)
                }
                val firstError = parseResult.errors.firstOrNull()?.message ?: "unknown error"
                return ReaderInitResult.ParseError(firstError)
            }
            is ParseResult.Success -> parseResult.value
        }

        val compiled: CompiledAdventure = when (val compileResult = Engine.compile(adventure)) {
            is CompileResult.Failure -> {
                val firstError = compileResult.errors.firstOrNull()?.message ?: "unknown error"
                return ReaderInitResult.CompileError(firstError)
            }
            is CompileResult.Success -> compileResult.compiled
        }

        val previewMode = arguments?.getBoolean("preview_mode", false) ?: false
        val previewStartSceneId = arguments?.getString("preview_scene_id") ?: ""
        val previewStartChapterIndex = arguments?.getInt("preview_chapter_index", 0) ?: 0
        val adventureFileUri = arguments?.getString("adventure_file_uri")?.let { Uri.parse(it) }

        var cachedPlayerWarnings: List<LintIssue> = emptyList()
        var currentWarningsFingerprint = ""
        if (!previewMode) {
            val allIssues = Analyzer.lint(compiled)
            val errors = allIssues.filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                return ReaderInitResult.BrokenAdventure(errors)
            }
            cachedPlayerWarnings = allIssues.filter {
                it.severity == Severity.WARNING && it.kind is LintKind.DeadEnd
            }
            currentWarningsFingerprint = computeWarningsFingerprint(cachedPlayerWarnings)
        }

        val baseState = ReaderUiState(
            adventure = adventure,
            compiled = compiled,
            previewMode = previewMode,
            adventureFileUri = adventureFileUri,
            previewStartSceneId = previewStartSceneId,
            previewStartChapterIndex = previewStartChapterIndex,
            cachedPlayerWarnings = cachedPlayerWarnings,
            currentWarningsFingerprint = currentWarningsFingerprint,
        )

        _state.value = if (previewMode) {
            initPreviewState(baseState)
        } else {
            initNormalState(baseState)
        }

        Logger.log(
            app,
            "ReaderViewModel initialized: preview=$previewMode, " +
                    "scene='${_state.value.currentSceneId}', " +
                    "warnings=${cachedPlayerWarnings.size}",
        )

        // Phase 3.4-a-2a: compute the full initial scene state (effects,
        // death, gate, ending) so readers see a fully ready
        // ReaderUiState right after init.
        loadCurrentScene()

        return ReaderInitResult.Success
    }

    // ==================================================================
    // Runtime transitions
    // ==================================================================

    /** User tapped a (visible) choice. Mirrors the old inline path. */
    fun onChoicePicked(choice: Choice) {
        val current = _state.value
        if (current.isDead) return
        if (current.adventure == null || current.compiled == null) return

        val previous = captureSnapshot()

        // Apply choice effects
        var next = applyEffectsToState(
            current.copy(previousStepSnapshot = previous),
            choice.effects,
        )
        next = next.copy(currentSceneId = choice.nextScene.value)
        _state.value = next
        saveProgress()

        if (checkDeathInternal(next)) {
            _state.value = _state.value.copy(isDead = true)
            saveProgress()
            return
        }

        loadCurrentScene()
    }

    fun onStepBack() {
        val snapshot = _state.value.previousStepSnapshot ?: return
        restoreSnapshot(snapshot)
    }

    fun onChapterRestart() {
        val snapshot = _state.value.chapterStartSnapshot ?: return
        restoreSnapshot(snapshot)
    }

    fun onAdventureRestart() {
        val current = _state.value
        if (current.previewMode) {
            // Preview: no progress persistence, reset in memory.
            val adventure = current.adventure ?: return
            val initial = adventure.initialState
            _state.value = current.copy(
                flags = initial.flags.toList(),
                stats = initial.stats,
                itemOrder = rebuildItemOrderList(adventure, initial.stats),
                appliedSceneEffects = emptySet(),
                passedGates = emptySet(),
                currentSceneId = current.previewStartSceneId,
                isDead = false,
                remainingSteps = 0,
                isGateActive = false,
                lastKnownAbsolute = -1,
                lastAbsoluteAtSaveFromProgress = -1,
                previousStepSnapshot = null,
                chapterStartSnapshot = null,
                storyEndedMessage = null,
            )
            loadCurrentScene()
        } else {
            onRestartAdventure()
        }
    }

    fun onRestartAdventure() {
        val current = _state.value
        val adventure = current.adventure ?: return
        val compiled = current.compiled ?: return
        val uri = current.adventureFileUri
        if (uri != null) {
            ProgressManager.deleteProgress(app, uri)
        }
        val initial = adventure.initialState
        _state.value = current.copy(
            flags = initial.flags.toList(),
            stats = initial.stats,
            itemOrder = rebuildItemOrderList(adventure, initial.stats),
            appliedSceneEffects = emptySet(),
            passedGates = emptySet(),
            currentSceneId = adventure.startScene.value,
            isDead = false,
            remainingSteps = 0,
            isGateActive = false,
            lastKnownAbsolute = -1,
            lastAbsoluteAtSaveFromProgress = -1,
            previousStepSnapshot = null,
            chapterStartSnapshot = null,
            savedWarningsFingerprint = "",
            storyEndedMessage = null,
        )
        saveProgress()
        loadCurrentScene()
    }

    // ==================================================================
    // Steps / gate
    // ==================================================================

    /** Called by `ReaderRoute`'s BroadcastReceiver on each sensor update. */
    fun onStepsUpdate(absoluteSteps: Int) {
        val state = _state.value
        if (!state.isGateActive) return
        val scene = state.compiled?.scenesById?.get(SceneId(state.currentSceneId)) ?: return
        val gate = scene.gate ?: return

        val newRemaining: Int
        val newLastKnown: Int
        if (state.lastKnownAbsolute < 0) {
            // First event since fragment opened. Catch up from saved baseline.
            if (state.lastAbsoluteAtSaveFromProgress >= 0) {
                val delta = (absoluteSteps - state.lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
                newRemaining = (state.remainingSteps - delta).coerceAtLeast(0)
            } else {
                newRemaining = state.remainingSteps
            }
            newLastKnown = absoluteSteps
        } else {
            val delta = (absoluteSteps - state.lastKnownAbsolute).coerceAtLeast(0)
            newRemaining = (state.remainingSteps - delta).coerceAtLeast(0)
            newLastKnown = absoluteSteps
        }

        val updated = state.copy(
            remainingSteps = newRemaining,
            lastKnownAbsolute = newLastKnown,
        )
        _state.value = updated

        if (newRemaining <= 0) {
            onGatePassedInternal(updated, scene)
        }
    }

    /** Called from onViewCreated / onResume to catch up if sensor already alive. */
    fun tryCatchUpGate() {
        val state = _state.value
        if (!state.isGateActive || state.previewMode) return
        if (state.lastKnownAbsolute >= 0) return

        val scene = state.compiled?.scenesById?.get(SceneId(state.currentSceneId))
        val gate = scene?.gate
        if (scene == null || gate == null) {
            _state.value = state.copy(
                isGateActive = false, remainingSteps = 0,
                lastKnownAbsolute = -1, lastAbsoluteAtSaveFromProgress = -1,
            )
            return
        }
        if (!gate.repeatable && scene.id.value in state.passedGates) {
            _state.value = state.copy(
                isGateActive = false, remainingSteps = 0,
                lastKnownAbsolute = -1, lastAbsoluteAtSaveFromProgress = -1,
            )
            return
        }

        val abs = com.myapp.adventure.service.StepCounterService.absoluteSteps
        val hasFirst = com.myapp.adventure.service.StepCounterService.hasFirstEvent
        if (hasFirst && state.lastAbsoluteAtSaveFromProgress >= 0) {
            val delta = (abs - state.lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
            val newRemaining = (state.remainingSteps - delta).coerceAtLeast(0)
            _state.value = state.copy(
                remainingSteps = newRemaining,
                lastKnownAbsolute = abs,
            )
        }
    }

    /** Debug-only: set remaining steps manually. */
    fun setDebugRemainingSteps(remaining: Int) {
        val state = _state.value
        val scene = state.compiled?.scenesById?.get(SceneId(state.currentSceneId)) ?: return
        if (scene.gate == null) return
        val newRemaining = remaining.coerceAtLeast(0)
        _state.value = state.copy(remainingSteps = newRemaining)
        if (newRemaining <= 0) {
            onGatePassedInternal(_state.value, scene)
        }
    }

    // ==================================================================
    // Warnings
    // ==================================================================

    /**
     * True when the startup warning dialog must be shown. `ReaderRoute`
     * shows the Compose dialog, then calls either [acknowledgeWarnings]
     * ("Продолжить игру") or pops the reader ("Выйти из игры", no
     * acknowledge).
     */
    fun shouldShowWarningDialog(): Boolean {
        val state = _state.value
        return !state.previewMode &&
                state.cachedPlayerWarnings.isNotEmpty() &&
                state.currentWarningsFingerprint != state.savedWarningsFingerprint
    }

    /**
     * Player tapped "Продолжить игру" in the warning dialog: persist the
     * acknowledgment (fingerprint) so subsequent opens of the same file
     * skip the dialog. No-op in preview mode (no progress is persisted).
     */
    fun acknowledgeWarnings() {
        val state = _state.value
        if (state.previewMode) return
        if (state.currentWarningsFingerprint == state.savedWarningsFingerprint) return
        _state.value = state.copy(savedWarningsFingerprint = state.currentWarningsFingerprint)
        saveProgress()
    }

    /** Read-only view of the cached warning list (used by [WarningInfoScreen]). */
    fun getCachedWarnings(): List<LintIssue> = _state.value.cachedPlayerWarnings

    // ==================================================================
    // Overlay navigation coordination
    // ==================================================================

    private var closeOverlayOnNextResume = false

    /**
     * The reader menu navigates to Menu/Settings. The menu overlay must stay
     * visible during the exit transition — clearing it first would briefly
     * flash the reader scene. The screen sets this flag before navigating
     * and closes the overlay when the reader is shown again.
     *
     * Lives in the entry-scoped VM so it survives navigation away and does
     * not fire on rotation (where the overlay should be preserved).
     */
    fun requestOverlayCloseOnNextResume() {
        closeOverlayOnNextResume = true
    }

    /** One-shot: true only on the composition that returns to the reader. */
    fun consumeOverlayCloseOnNextResume(): Boolean {
        val pending = closeOverlayOnNextResume
        closeOverlayOnNextResume = false
        return pending
    }

    // ==================================================================
    // Debug panel actions (preview mode only)
    //
    // All actions mutate stats/flags/itemOrder and then re-run
    // `loadCurrentScene()` so that death checks, gates and endings
    // re-evaluate against the new state — mirrors the old debug panel
    // behaviour. `saveProgress()` is a no-op in preview mode, so these
    // do not persist (as before).
    // ==================================================================

    /** Sets `stats[name]` to [value]. Syncs [itemOrder] for ITEM stats. */
    fun debugSetStat(name: String, value: Int) {
        val state = _state.value
        val newStats = state.stats + (name to value)
        val newItemOrder = if (getCategory(name) == TagCategory.ITEM) {
            when {
                value > 0 && !state.itemOrder.contains(name) -> state.itemOrder + name
                value <= 0 -> state.itemOrder - name
                else -> state.itemOrder
            }
        } else state.itemOrder
        _state.value = state.copy(
            stats = newStats,
            itemOrder = newItemOrder,
            isDead = false,
            storyEndedMessage = null,
        )
        loadCurrentScene()
    }

    /** Alias for [debugSetStat]; the old "add stat" dialog did the same. */
    fun debugAddStat(name: String, value: Int) = debugSetStat(name, value)

    /** Removes `stats[name]` and its `itemOrder` entry. */
    fun debugDeleteStat(name: String) {
        val state = _state.value
        _state.value = state.copy(
            stats = state.stats - name,
            itemOrder = state.itemOrder - name,
            isDead = false,
            storyEndedMessage = null,
        )
        loadCurrentScene()
    }

    /** Adds [name] to flags if absent. */
    fun debugAddFlag(name: String) {
        val state = _state.value
        if (state.flags.contains(name)) return
        _state.value = state.copy(flags = state.flags + name)
        loadCurrentScene()
    }

    /** Removes [name] from flags (no-op if absent). */
    fun debugDeleteFlag(name: String) {
        val state = _state.value
        if (!state.flags.contains(name)) return
        _state.value = state.copy(flags = state.flags - name)
        loadCurrentScene()
    }

    /** Resets flags/stats/itemOrder to `adventure.initialState`. */
    fun debugResetState() {
        val current = _state.value
        val adventure = current.adventure ?: return
        val initial = adventure.initialState
        _state.value = current.copy(
            flags = initial.flags.toList(),
            stats = initial.stats,
            itemOrder = rebuildItemOrderList(adventure, initial.stats),
            isDead = false,
            storyEndedMessage = null,
        )
        loadCurrentScene()
    }

    /**
     * Debug: mark the current scene's gate as passed immediately
     * ("Открыть главу сейчас"). Returns early if the scene has no gate.
     */
    fun debugPassGate() {
        val state = _state.value
        val scene = state.compiled?.scenesById?.get(SceneId(state.currentSceneId)) ?: return
        if (scene.gate == null) return
        _state.value = onGatePassedInternal(state, scene)
    }

    // ==================================================================
    // HUD
    // ==================================================================

    fun getHealthValue(): Int {
        val state = _state.value
        val adventure = state.adventure ?: return 0
        val healthDef = adventure.tags.firstOrNull { it.name == "health" && it.category == TagCategory.HEALTH }
            ?: adventure.tags.firstOrNull { it.category == TagCategory.HEALTH }
        return healthDef?.let { state.stats[it.name] ?: 0 } ?: 0
    }

    fun getInventoryCount(): Int =
        _state.value.itemOrder.count { (_state.value.stats[it] ?: 0) > 0 }

    fun getJournalCount(): Int = getJournalItems().size

    fun getHealthItems(): List<Pair<String, Int>> {
        val state = _state.value
        val adventure = state.adventure ?: return emptyList()
        return adventure.tags
            .filter { it.category == TagCategory.HEALTH }
            .map { def ->
                val display = def.displayName.ifBlank { def.name }
                display to (state.stats[def.name] ?: 0)
            }
    }

    fun getInventoryItems(): List<Pair<String, Int>> {
        val state = _state.value
        return state.itemOrder.asReversed()
            .filter { (state.stats[it] ?: 0) > 0 }
            .map { name -> getDisplayName(name) to (state.stats[name] ?: 0) }
    }

    fun getJournalItems(): List<String> {
        val state = _state.value
        val adventure = state.adventure ?: return emptyList()
        val result = mutableListOf<String>()
        for (name in state.flags.asReversed()) {
            if (isVisible(name) && getCategory(name) == TagCategory.JOURNAL) {
                result.add(getDisplayName(name))
            }
        }
        adventure.tags
            .filter { it.category == TagCategory.JOURNAL && it.visible }
            .forEach { def ->
                if (!state.flags.contains(def.name) && (state.stats[def.name] ?: 0) != 0) {
                    val display = def.displayName.ifBlank { def.name }
                    result.add("$display: ${state.stats[def.name]}")
                }
            }
        return result
    }

    /** Returns (choice, display state) for the current scene, hidden ones omitted. */
    fun getChoiceStates(): List<Pair<Choice, ChoiceState>> {
        val state = _state.value
        val scene = currentScene() ?: return emptyList()
        val playerState = PlayerState(flags = state.flags.toSet(), stats = state.stats)
        return scene.choices.map { choice ->
            choice to Evaluator.choiceState(choice, playerState)
        }.filter { it.second !is ChoiceState.Hidden }
    }

    fun currentScene(): Scene? {
        val state = _state.value
        return state.compiled?.scenesById?.get(SceneId(state.currentSceneId))
    }

    // ==================================================================
    // Progress persistence
    // ==================================================================

    fun saveProgress() {
        val state = _state.value
        if (state.previewMode) return
        val uri = state.adventureFileUri ?: return
        val compiled = state.compiled ?: return

        val abs = if (com.myapp.adventure.service.StepCounterService.hasFirstEvent)
            com.myapp.adventure.service.StepCounterService.absoluteSteps else -1

        val progress = ProgressData(
            adventureFileName = "",
            currentChapterIndex = compiled.chapterIndexBySceneId[SceneId(state.currentSceneId)] ?: 0,
            currentSceneId = state.currentSceneId,
            flags = state.flags,
            stats = state.stats,
            appliedSceneEffects = state.appliedSceneEffects,
            itemOrder = state.itemOrder,
            previousStepSnapshot = state.previousStepSnapshot,
            chapterStartSnapshot = state.chapterStartSnapshot,
            remainingSteps = state.remainingSteps,
            isGateActive = state.isGateActive,
            lastAbsoluteAtSave = abs,
            warningsFingerprint = state.savedWarningsFingerprint,
            passedGates = state.passedGates.toList(),
        )
        ProgressManager.saveProgress(app, uri, progress)
    }

    /** Called from `ReaderRoute`'s ON_STOP observer; flushes to disk. */
    suspend fun flushProgress() {
        ProgressManager.flush(app)
    }

    // ==================================================================
    // Scene transitions (private)
    // ==================================================================

    private fun loadCurrentScene() {
        val state = _state.value
        if (state.isDead) return
        val compiled = state.compiled ?: return
        val adventure = state.adventure ?: return

        val scene = compiled.scenesById[SceneId(state.currentSceneId)] ?: run {
            _state.value = state.copy(
                storyEndedMessage = "scene not found: ${state.currentSceneId}",
            )
            return
        }

        var next = state

        if (scene.id.value !in next.appliedSceneEffects) {
            next = applyEffectsToState(next, scene.effects)
            next = next.copy(appliedSceneEffects = next.appliedSceneEffects + scene.id.value)
            Logger.log(app, "Scene '${scene.id.value}' loaded for the first time")
        }

        if (checkDeathInternal(next)) {
            next = next.copy(isDead = true)
            _state.value = next
            saveProgress()
            return
        }

        next = captureChapterStartIfNeeded(next)

        val gate = scene.gate
        if (gate != null && scene.id.value !in next.passedGates) {
            if (next.previewMode) {
                next = next.copy(
                    isGateActive = true,
                    remainingSteps = gate.requiredSteps,
                    lastKnownAbsolute = -1,
                )
                _state.value = next
                return
            }
            next = when {
                !next.isGateActive -> startSceneGate(next, scene)
                next.remainingSteps <= 0 -> onGatePassedInternal(next, scene)
                next.remainingSteps > gate.requiredSteps -> startSceneGate(next, scene)
                else -> next.copy(isGateActive = true)
            }
            _state.value = next
            saveProgress()
            return
        }

        if (scene.isEnding) {
            _state.value = next.copy(storyEndedMessage = "Конец")
            return
        }

        if (scene.choices.isEmpty()) {
            _state.value = next.copy(storyEndedMessage = "Конец (preview)")
            return
        }

        _state.value = next
    }

    private fun applyEffectsToState(base: ReaderUiState, effects: List<Effect>): ReaderUiState {
        if (effects.isEmpty()) return base
        val adventure = base.adventure ?: return base
        val playerState = PlayerState(flags = base.flags.toSet(), stats = base.stats)
        val newPlayerState = EffectsApplier.apply(playerState, effects)

        // Sync item order for ITEM stats touched by effects
        var itemOrder = base.itemOrder
        for (effect in effects) {
            val name = when (effect) {
                is Effect.ModifyStat -> effect.name
                is Effect.Set -> effect.name
                else -> null
            } ?: continue
            if (getCategory(name) == TagCategory.ITEM) {
                val newValue = newPlayerState.stats[name] ?: 0
                itemOrder = if (newValue > 0 && !itemOrder.contains(name)) {
                    itemOrder + name
                } else if (newValue <= 0) {
                    itemOrder - name
                } else itemOrder
            }
        }

        return base.copy(
            flags = newPlayerState.flags.toList(),
            stats = newPlayerState.stats,
            itemOrder = itemOrder,
        )
    }

    private fun checkDeathInternal(state: ReaderUiState): Boolean {
        val adventure = state.adventure ?: return false
        val playerState = PlayerState(flags = state.flags.toSet(), stats = state.stats)
        val outcome = Engine.evaluateInvariants(adventure, playerState)
        return outcome == Outcome.Death
    }

    private fun startSceneGate(state: ReaderUiState, scene: Scene): ReaderUiState {
        val gate = scene.gate ?: return state
        val abs = if (com.myapp.adventure.service.StepCounterService.hasFirstEvent)
            com.myapp.adventure.service.StepCounterService.absoluteSteps else -1
        return state.copy(
            isGateActive = true,
            remainingSteps = gate.requiredSteps,
            lastKnownAbsolute = abs,
            lastAbsoluteAtSaveFromProgress = -1,
        )
    }

    private fun onGatePassedInternal(state: ReaderUiState, scene: Scene): ReaderUiState {
        val newPassed = if (scene.gate?.repeatable != true) {
            state.passedGates + scene.id.value
        } else state.passedGates

        val next = state.copy(
            passedGates = newPassed,
            isGateActive = false,
            remainingSteps = 0,
            storyEndedMessage = when {
                scene.isEnding -> "Конец"
                scene.choices.isEmpty() -> "Конец (preview)"
                else -> null
            },
        )
        saveProgress()
        return next
    }

    // ==================================================================
    // Snapshots
    // ==================================================================

    private fun captureSnapshot(): PlayerStateSnapshot {
        val state = _state.value
        val compiled = state.compiled
        val chapterIndex = compiled?.chapterIndexBySceneId?.get(SceneId(state.currentSceneId)) ?: 0
        return PlayerStateSnapshot(
            sceneId = state.currentSceneId,
            chapterIndex = chapterIndex,
            flags = state.flags,
            stats = state.stats,
            itemOrder = state.itemOrder,
            appliedSceneEffects = state.appliedSceneEffects,
        )
    }

    private fun restoreSnapshot(snapshot: PlayerStateSnapshot) {
        val current = _state.value
        _state.value = current.copy(
            previousStepSnapshot = null,
            isGateActive = false,
            remainingSteps = 0,
            lastKnownAbsolute = -1,
            lastAbsoluteAtSaveFromProgress = -1,
            currentSceneId = snapshot.sceneId,
            flags = snapshot.flags,
            stats = snapshot.stats,
            itemOrder = snapshot.itemOrder,
            appliedSceneEffects = snapshot.appliedSceneEffects,
            isDead = false,
            storyEndedMessage = null,
        )
        saveProgress()
        loadCurrentScene()
    }

    private fun captureChapterStartIfNeeded(state: ReaderUiState): ReaderUiState {
        val compiled = state.compiled ?: return state
        val chapterIndex = compiled.chapterIndexBySceneId[SceneId(state.currentSceneId)] ?: return state
        val chapter = compiled.adventure.chapters.getOrNull(chapterIndex) ?: return state
        val firstSceneId = chapter.scenes.firstOrNull()?.id?.value ?: return state
        if (state.currentSceneId != firstSceneId) return state
        if (state.chapterStartSnapshot != null &&
            state.chapterStartSnapshot.chapterIndex == chapterIndex
        ) return state
        val health = state.stats["health"]
        if (health == null || health > 0) {
            return state.copy(chapterStartSnapshot = captureSnapshot())
        }
        return state
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private fun getCategory(name: String): TagCategory {
        if (name == "health") return TagCategory.HEALTH
        return _state.value.adventure?.tags?.firstOrNull { it.name == name }?.category
            ?: TagCategory.JOURNAL
    }

    private fun getDisplayName(name: String): String {
        val def = _state.value.adventure?.tags?.firstOrNull { it.name == name }
        return def?.displayName?.ifBlank { name } ?: name
    }

    private fun isVisible(name: String): Boolean {
        return _state.value.adventure?.tags?.firstOrNull { it.name == name }?.visible ?: false
    }

    private fun rebuildItemOrderList(adventure: Adventure, stats: Map<String, Int>): List<String> {
        return adventure.tags
            .filter { it.category == TagCategory.ITEM }
            .map { it.name }
            .filter { (stats[it] ?: 0) > 0 }
    }

    // ==================================================================
    // Loading / init helpers (from 3.4-a-1)
    // ==================================================================

    private fun initPreviewState(base: ReaderUiState): ReaderUiState {
        val adventure = requireNotNull(base.adventure)
        val initial = adventure.initialState
        return base.copy(
            currentSceneId = base.previewStartSceneId,
            flags = initial.flags.toList(),
            stats = initial.stats,
            itemOrder = rebuildItemOrderList(adventure, initial.stats),
            appliedSceneEffects = emptySet(),
            passedGates = emptySet(),
            isDead = false,
            remainingSteps = 0,
            isGateActive = false,
            lastKnownAbsolute = -1,
            lastAbsoluteAtSaveFromProgress = -1,
            previousStepSnapshot = null,
            chapterStartSnapshot = null,
            savedWarningsFingerprint = "",
            storyEndedMessage = null,
        )
    }

    private fun initNormalState(base: ReaderUiState): ReaderUiState {
        val adventure = requireNotNull(base.adventure)
        val saved = base.adventureFileUri?.let {
            ProgressManager.loadProgress(app, it)
        }

        if (saved != null) {
            val itemOrder = if (saved.itemOrder.isNotEmpty()) {
                saved.itemOrder.filter { (saved.stats[it] ?: 0) > 0 }
            } else {
                rebuildItemOrderList(adventure, saved.stats)
            }
            return base.copy(
                currentSceneId = saved.currentSceneId,
                flags = saved.flags,
                stats = saved.stats,
                itemOrder = itemOrder,
                appliedSceneEffects = saved.appliedSceneEffects,
                passedGates = saved.passedGates.toSet(),
                isDead = false,
                remainingSteps = saved.remainingSteps,
                isGateActive = saved.isGateActive,
                lastKnownAbsolute = -1,
                lastAbsoluteAtSaveFromProgress = saved.lastAbsoluteAtSave,
                previousStepSnapshot = saved.previousStepSnapshot,
                chapterStartSnapshot = saved.chapterStartSnapshot,
                savedWarningsFingerprint = saved.warningsFingerprint,
                storyEndedMessage = null,
            )
        }

        val initial = adventure.initialState
        return base.copy(
            currentSceneId = adventure.startScene.value,
            flags = initial.flags.toList(),
            stats = initial.stats,
            itemOrder = rebuildItemOrderList(adventure, initial.stats),
            appliedSceneEffects = emptySet(),
            passedGates = emptySet(),
            isDead = false,
            remainingSteps = 0,
            isGateActive = false,
            lastKnownAbsolute = -1,
            lastAbsoluteAtSaveFromProgress = -1,
            previousStepSnapshot = null,
            chapterStartSnapshot = null,
            savedWarningsFingerprint = "",
            storyEndedMessage = null,
        )
    }

    private fun readAdventureJsonFromFile(arguments: Bundle?): String? {
        val uriString = arguments?.getString("adventure_file_uri") ?: return null
        return try {
            val uri = Uri.parse(uriString)
            app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Logger.log(app, "Failed to read adventure file: ${e.message}")
            null
        }
    }

    private fun computeWarningsFingerprint(warnings: List<LintIssue>): String {
        if (warnings.isEmpty()) return ""
        return warnings
            .sortedWith(compareBy({ it.sceneId?.value ?: "" }, { it.message }))
            .joinToString("|") { "${it.sceneId?.value ?: ""}:${it.message}" }
    }
}

sealed interface ReaderInitResult {
    data object Success : ReaderInitResult
    data object NoAdventureData : ReaderInitResult
    data class ParseError(val message: String) : ReaderInitResult
    data class TypeErrors(val errors: List<TypeError>) : ReaderInitResult
    data class CompileError(val message: String) : ReaderInitResult
    data class BrokenAdventure(val errors: List<LintIssue>) : ReaderInitResult
}