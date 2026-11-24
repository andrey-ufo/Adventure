package com.myapp.adventure.ui.reader

import android.net.Uri
import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.engine.ir.Adventure

/**
 * Phase 3.4-a-1: immutable snapshot of the reader's runtime state.
 *
 * **Immutable.** Every mutation creates a new instance via `copy(...)`
 * and is published through `AdventureReaderViewModel.state`. The
 * Compose reader collects it via `collectAsStateWithLifecycle`.
 *
 * **Nullable `adventure` / `compiled`.** Before `vm.init(...)` runs,
 * there is no parsed file. After `init` returns `Success`, both are
 * non-null and stay non-null for the rest of the ViewModel's lifetime.
 * All methods that mutate state check `adventure != null` (or are
 * called only after `Success` — the reader-route contract).
 *
 * **String scene ids, not `SceneId`.** Matches the reader route:
 * `.value` at IR boundaries, `SceneId(x)` when indexing
 * `compiled.scenesById`. A full `SceneId` migration is deferred to
 * Phase 5; the type is already correct where it matters.
 */
data class ReaderUiState(
    val adventure: Adventure? = null,
    val compiled: CompiledAdventure? = null,
    val previewMode: Boolean = false,
    val adventureFileUri: Uri? = null,
    val previewStartSceneId: String = "",
    val previewStartChapterIndex: Int = 0,

    val currentSceneId: String = "",

    val flags: List<String> = emptyList(),
    val stats: Map<String, Int> = emptyMap(),
    val itemOrder: List<String> = emptyList(),
    val appliedSceneEffects: Set<String> = emptySet(),
    val passedGates: Set<String> = emptySet(),

    val isDead: Boolean = false,
    /**
     * Non-null when the story has reached an end (isEnding scene,
     * no-choices scene, or missing scene). Rendering shows this as the
     * "Конец" body + restart button. Cleared on `onRestartAdventure`.
     */
    val storyEndedMessage: String? = null,

    // ----- Steps gate -----
    val remainingSteps: Int = 0,
    val isGateActive: Boolean = false,
    val lastKnownAbsolute: Int = -1,
    val lastAbsoluteAtSaveFromProgress: Int = -1,

    // ----- Snapshots -----
    val previousStepSnapshot: PlayerStateSnapshot? = null,
    val chapterStartSnapshot: PlayerStateSnapshot? = null,

    // ----- Warnings -----
    val cachedPlayerWarnings: List<LintIssue> = emptyList(),
    val currentWarningsFingerprint: String = "",
    val savedWarningsFingerprint: String = "",
)