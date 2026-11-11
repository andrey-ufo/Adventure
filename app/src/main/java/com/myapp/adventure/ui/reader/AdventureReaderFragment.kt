package com.myapp.adventure.ui.reader

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.myapp.adventure.R
import com.myapp.adventure.data.PlayerStateSnapshot
import com.myapp.adventure.data.ProgressData
import com.myapp.adventure.databinding.FragmentAdventureReadBinding
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
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory
import com.myapp.adventure.engine.ir.TagDefinition
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.service.StepCounterConstants
import com.myapp.adventure.service.StepCounterService
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.ui.SettingsFragment
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.ProgressManager
import kotlinx.coroutines.launch

class AdventureReaderFragment : Fragment() {

    private var _binding: FragmentAdventureReadBinding? = null
    private val binding get() = _binding!!

    private lateinit var adventure: Adventure

    /**
     * Pre-compiled runtime view of [adventure] — indexes for O(1)
     * scene lookups. Built once in [onCreate] (see Engine.compile) and
     * never rebuilt. All `scenesById[...]` calls go through this instead
     * of a linear scan of `adventure.chapters`.
     */
    private lateinit var compiled: CompiledAdventure

    private var adventureFileUri: Uri? = null

    /**
     * Currently-rendered scene id. `String` (not `SceneId`) to minimize
     * the diff in this migration; `.value` when calling IR APIs, wrap
     * back in `SceneId(...)` for `step` / `findScene`. Full migration
     * to `SceneId` across the reader is out of scope here (Ф5.5, with
     * save/load) — the type is already correct at the boundary where
     * it matters.
     */
    private var currentSceneId = ""

    // ----- Steps gate state -----

    /** Steps remaining until the current scene's gate is satisfied. */
    private var remainingSteps = 0

    /** Whether a gate is currently active on the current scene. */
    private var isGateActive = false

    /**
     * Raw sensor reading at the moment this fragment last synchronized
     * with the sensor. -1 means "not yet known" — we wait for the first
     * broadcast or fall back to [lastAbsoluteAtSaveFromProgress] for
     * catch-up.
     */
    private var lastKnownAbsolute = -1

    /**
     * lastAbsoluteAtSave restored from ProgressData at load. Used once
     * to catch up steps taken while the app was closed.
     */
    private var lastAbsoluteAtSaveFromProgress = -1

    private var notificationPermissionGranted = false
    private var activityRecognitionGranted = false

    private var previewMode = false
    private var previewStartSceneId = ""
    // Chapter index is not used in preview anymore (startScene is
    // explicit in IR v3+); kept for ProgressData compatibility and
    // `advanceToNextChapter` has been removed — chapters are pure
    // narrative grouping. Kept to avoid touching ProgressData shape in
    // this migration (Ф5.5 will remove it).
    private var previewStartChapterIndex = 0

    private var isDead = false

    private var previousStepSnapshot: PlayerStateSnapshot? = null
    private var chapterStartSnapshot: PlayerStateSnapshot? = null

    private val flags = mutableListOf<String>()
    private val stats = mutableMapOf<String, Int>()
    private val itemOrder = mutableListOf<String>()
    private val appliedSceneEffects = mutableSetOf<String>()

    /**
     * Scene ids whose gates have been passed in this session
     * (`repeatable = false` only). An entry is added by [onGatePassed]
     * when a non-repeatable gate is satisfied; [loadCurrentScene]
     * checks this set before re-activating a gate. `repeatable = true`
     * gates are **never** added — they re-engage on every visit.
     *
     * **Not persisted** in this batch: in-memory only. Restarting the
     * adventure (`onRestartAdventure`) clears this set; restarting the
     * app loses it, so gates reset on app restart. Persistence goes
     * with `ProgressData.passedGates` in Ф1.7.1b.
     */
    private val passedGates = mutableSetOf<String>()

    /**
     * Player-visible analyzer warnings (currently only
     * [LintKind.DeadEnd]), computed in [onCreate] after the file is
     * parsed and compiled. Empty when the file is clean (or preview
     * mode). Shown once per new set via [showWarningDialog]; the player
     * can re-open the list from the reader menu ("Предупреждения (N)").
     */
    private var cachedPlayerWarnings: List<LintIssue> = emptyList()

    /**
     * Fingerprint of [cachedPlayerWarnings], computed once in [onCreate].
     * Stable across runs on the same file; changes whenever the set of
     * warnings changes (see [ProgressData.warningsFingerprint] KDoc).
     */
    private var currentWarningsFingerprint: String = ""

    /**
     * Fingerprint of the last set the player acknowledged
     * ("Продолжить игру"), restored from [ProgressData] in
     * [initNormalState]. Empty on first open, or after "restart
     * adventure" (which clears it so the player sees warnings for the
     * fresh playthrough).
     */
    private var savedWarningsFingerprint: String = ""

    /**
     * Tag lookup built from [adventure]. Non-nullable because IR's
     * `Adventure.tags` is a non-null list with default `emptyList()`.
     */
    private val tagRegistry: Map<String, TagDefinition>
        get() = adventure.tags.associateBy { it.name }

    private val stepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isGateActive) return
            val absoluteSteps = intent?.getIntExtra(StepCounterConstants.EXTRA_ABSOLUTE_STEPS, 0) ?: 0
            val scene = compiled.scenesById[SceneId(currentSceneId)] ?: return
            // `gate` is guaranteed non-null because `isGateActive` is
            // set true only by `startSceneGate`, which requires a gate.
            scene.gate ?: return

            if (lastKnownAbsolute < 0) {
                // First event since fragment opened. Catch up from saved baseline.
                if (lastAbsoluteAtSaveFromProgress >= 0) {
                    val delta = (absoluteSteps - lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
                    remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
                }
                lastKnownAbsolute = absoluteSteps
            } else {
                val delta = (absoluteSteps - lastKnownAbsolute).coerceAtLeast(0)
                lastKnownAbsolute = absoluteSteps
                if (delta > 0) {
                    remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
                }
            }

            if (remainingSteps <= 0) {
                onGatePassed(scene)
            } else {
                updateStepsProgressUi(scene)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the adventure JSON. When `adventure_file_uri` is present,
        // prefer reading the file over the `adventure_json` argument:
        // `AdventureListFragment` builds that argument by round-tripping
        // through `:app/data/Adventure` (Gson), which silently drops all
        // IR-only fields — `is_ending`, `version`, `gate_repeatable`, …
        // The reader would then see `isEnding = false` on every scene and
        // the analyzer would (correctly) reject the file as a DeadEnd.
        // Reading from the URI avoids that lossy path. The argument is
        // kept as a fallback for callers that only pass JSON (preview
        // from the editor, Ф1.7.5).
        //
        // Related: `AdventureListFragment` still caches `:app/data/Adventure`
        // for the list display (title/author). That cache is **not**
        // invalidated when the file changes — a separate issue, not the
        // one this fix addresses.
        val adventureJsonFromFile = readAdventureJsonFromFile()
        val adventureJsonFromArgs = arguments?.getString("adventure_json")
        val adventureJson = adventureJsonFromFile ?: adventureJsonFromArgs
        if (adventureJson == null) {
            Toast.makeText(requireContext(), "Ошибка: нет данных приключения", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        // Step 1: parse JSON → IR. `JsonToIr` is the single entry point
        // for JSON in the whole project (Ф1.3); the reader never parses
        // JSON by hand.
        when (val parseResult = JsonToIr.parse(adventureJson)) {
            is ParseResult.Failure -> {
                val firstError = parseResult.errors.firstOrNull()?.message ?: "unknown error"
                Logger.log(requireContext(), "Parse error: $firstError")
                Toast.makeText(
                    requireContext(),
                    "Ошибка разбора файла: $firstError",
                    Toast.LENGTH_LONG,
                ).show()
                parentFragmentManager.popBackStack()
                return
            }
            is ParseResult.Success -> adventure = parseResult.value
        }

        // Step 2: compile IR → CompiledAdventure. `Engine.compile` is the
        // only place that can reject a well-formed file (duplicate scene
        // ids, missing start scene, dangling nextScene — Ф1.6.2.1).
        when (val compileResult = Engine.compile(adventure)) {
            is CompileResult.Failure -> {
                val firstError = compileResult.errors.firstOrNull()?.message ?: "unknown error"
                Logger.log(requireContext(), "Compile error: $firstError")
                Toast.makeText(
                    requireContext(),
                    "Ошибка компиляции: $firstError",
                    Toast.LENGTH_LONG,
                ).show()
                parentFragmentManager.popBackStack()
                return
            }
            is CompileResult.Success -> compiled = compileResult.compiled
        }

        previewMode = arguments?.getBoolean("preview_mode", false) ?: false

        // Step 3: lint. In preview mode we **skip** the whole block —
        // the author is still editing, and Ф9.2 (editor realtime linter)
        // is the proper channel for surfacing errors to them.
        //
        // In normal mode, two severities are handled separately:
        // - ERROR (EndingSceneWithChoices): the file is genuinely broken
        //   — the player would see buttons that do nothing. We refuse to
        //   load and show a blocking dialog. This is the only remaining
        //   ERROR after the 2026-11-07 revision: DeadEnd moved to
        //   WARNING (see LintKind.DeadEnd KDoc).
        // - WARNING (DeadEnd today; other WARNING kinds in the future):
        //   cached here and shown once per new set in `onViewCreated`
        //   (after `initNormalState` has restored `savedWarningsFingerprint`).
        //
        // TODO(Ф9.2): in preview mode, once the realtime linter lands,
        // replace "skip" with "N issues — continue anyway?" dialog.
        if (!previewMode) {
            val allIssues = Analyzer.lint(compiled)
            val errors = allIssues.filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                showBrokenAdventureDialog(errors)
                return
            }
            // For now only DeadEnd is surfaced here; other WARNING kinds
            // (UnreachableScene, RemovingUnestablishedItem, …) are
            // author-facing and stay in the editor. The filter is
            // explicit so that adding a new player-visible WARNING kind
            // is a one-line change here — no risk of accidentally
            // spamming the player with every author-facing warning.
            cachedPlayerWarnings = allIssues.filter {
                it.severity == Severity.WARNING && it.kind is LintKind.DeadEnd
            }
            currentWarningsFingerprint = computeWarningsFingerprint(cachedPlayerWarnings)
            // The dialog itself is shown in `onViewCreated`, after
            // `initNormalState` has restored `savedWarningsFingerprint`
            // from saved progress.
        }

        if (previewMode) {
            initPreviewState()
        } else {
            initNormalState()
        }
    }

    /**
     * Stable fingerprint of a warning set. Used to detect "the author
     * changed the file since the player last acknowledged warnings"
     * — see [ProgressData.warningsFingerprint] KDoc.
     *
     * Sorted by (sceneId, message) so that the same set produces the
     * same string regardless of `Analyzer.lint`'s traversal order
     * (today that order is deterministic, but this costs nothing and
     * removes a future footgun).
     */
    private fun computeWarningsFingerprint(warnings: List<LintIssue>): String {
        if (warnings.isEmpty()) return ""
        return warnings
            .sortedWith(compareBy({ it.sceneId?.value ?: "" }, { it.message }))
            .joinToString("|") { "${it.sceneId?.value ?: ""}:${it.message}" }
    }

    /**
     * Shows an [android.app.AlertDialog] with up to five error messages
     * when the loaded file fails the analyzer's ERROR check. The
     * fragment pops itself when the player taps "ОК".
     *
     * A dialog (not a Toast) is used on purpose: the player needs to
     * be able to read the errors — a Toast disappears after three
     * seconds. The dialog is not cancellable, so the only way out is
     * "ОК" → back to the library. The file itself is not modified; the
     * author opens it in the editor and fixes the errors.
     */

    /**
     * Reads the adventure JSON from the file pointed to by
     * `adventure_file_uri`, if present. Returns `null` when the URI is
     * absent (preview mode) or when the read fails (file deleted,
     * permission revoked) — the caller falls back to the
     * `adventure_json` argument in both cases.
     *
     * **Why the reader does not trust `adventure_json` from
     * `AdventureListFragment`.** That fragment parses the file into the
     * `:app/data/Adventure` model (which carries only the fields needed
     * for the list display), then re-serializes it with Gson. The
     * round-trip silently drops every IR-only field — `is_ending`,
     * `version`, `gate_repeatable`. Sending that through would make
     * `JsonToIr` see `isEnding = false` everywhere, and the analyzer's
     * `DeadEnd` check would reject valid files. See Ф1.7.1a-fix in the
     * Progress log.
     */
    private fun readAdventureJsonFromFile(): String? {
        val uriString = arguments?.getString("adventure_file_uri") ?: return null
        return try {
            val uri = Uri.parse(uriString)
            requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Failed to read adventure file: ${e.message}")
            null
        }
    }

    private fun showBrokenAdventureDialog(errors: List<LintIssue>) {
        val maxShow = 5
        val message = buildString {
            append("Файл повреждён (${errors.size} ошибок):\n\n")
            errors.take(maxShow).forEach { append("• ${it.message}\n") }
            if (errors.size > maxShow) {
                append("\n…и ещё ${errors.size - maxShow}")
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Не удалось открыть")
            .setMessage(message)
            .setPositiveButton("ОК") { _, _ -> parentFragmentManager.popBackStack() }
            .setCancelable(false)
            .show()
    }

    /**
     * Non-blocking warning dialog for player-visible `WARNING`-severity
     * issues (currently only [LintKind.DeadEnd]; the caller filters).
     *
     * The player decides whether to continue. "Продолжить" does nothing
     * — the fragment is already building the UI underneath (see
     * `onCreate`), and the player will land on the story's first scene.
     * "Отмена" pops the fragment back to the library, so the player can
     * choose a different adventure. "Назад" (system button) is treated
     * as "Отмена" because the dialog is cancellable.
     *
     * A `WARNING` here is **not** an error: the story is readable up to
     * the point the warning names. The message includes the scene id,
     * so a player who chooses to report the issue has a concrete
     * pointer.
     */
    private fun showWarningDialog(warnings: List<LintIssue>) {
        val maxShow = 5
        val message = buildString {
            append("В приключении ${warnings.size} предупреждений:\n\n")
            warnings.take(maxShow).forEach { append("• ${it.message}\n") }
            if (warnings.size > maxShow) {
                append("\n…и ещё ${warnings.size - maxShow}")
            }
            append("\nПродолжить игру?")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Предупреждение")
            .setMessage(message)
            .setPositiveButton("Продолжить игру") { _, _ ->
                // Acknowledge: remember this exact set for this
                // adventure. Subsequent opens of the same file will not
                // show the dialog; the player can still reach the list
                // via the reader menu ("Предупреждения (N)").
                savedWarningsFingerprint = currentWarningsFingerprint
                saveProgress()
            }
            .setNegativeButton("Выйти из игры") { _, _ ->
                // Do NOT acknowledge. The player chose not to play, so
                // they have not seen the story — they should see the
                // warning again on the next open.
                parentFragmentManager.popBackStack()
            }
            .setCancelable(true)
            .setOnCancelListener {
                // System back = "Выйти из игры" (same reasoning).
                parentFragmentManager.popBackStack()
            }
            .show()
    }

    /**
     * Read-only view of the warning list, opened from the reader menu
     * ("Предупреждения (N)"). The player is already playing, so this
     * dialog has only a "Закрыть" button — there is no decision to make.
     *
     * Distinct from [showWarningDialog]: that one is shown **once per
     * fingerprint** and asks the player to decide whether to play.
     */
    private fun showWarningInfoDialog(warnings: List<LintIssue>) {
        val maxShow = 5
        val message = buildString {
            append("${warnings.size} предупреждений:\n\n")
            warnings.take(maxShow).forEach { append("• ${it.message}\n") }
            if (warnings.size > maxShow) {
                append("\n…и ещё ${warnings.size - maxShow}")
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Предупреждения")
            .setMessage(message)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // ==================== State init ====================

    private fun addFlagIfMissing(flag: String) {
        if (!flags.contains(flag)) flags.add(flag)
    }

    private fun getCategory(name: String): TagCategory {
        if (name == "health") return TagCategory.HEALTH
        return tagRegistry[name]?.category ?: TagCategory.JOURNAL
    }

    private fun getDisplayName(name: String): String {
        val def = tagRegistry[name]
        return def?.displayName?.ifBlank { name } ?: name
    }

    private fun isVisible(name: String): Boolean {
        return tagRegistry[name]?.visible ?: false
    }

    private fun registerItem(name: String, newCount: Int) {
        if (getCategory(name) != TagCategory.ITEM) return
        if (newCount > 0 && !itemOrder.contains(name)) {
            itemOrder.add(name)
        } else if (newCount <= 0) {
            itemOrder.remove(name)
        }
    }

    private fun rebuildItemOrder() {
        itemOrder.clear()
        adventure.tags?.filter { it.category == TagCategory.ITEM }?.forEach { def ->
            if ((stats[def.name] ?: 0) > 0) {
                itemOrder.add(def.name)
            }
        }
    }

    private fun captureSnapshot(): PlayerStateSnapshot = PlayerStateSnapshot(
        sceneId = currentSceneId,
        // `chapterIndex` in PlayerStateSnapshot is preserved for save
        // compatibility (see ProgressData). The reader computes it on
        // demand from `compiled.chapterIndexBySceneId`; chapters are
        // pure narrative grouping (Ф1.5.4).
        chapterIndex = compiled.chapterIndexBySceneId[SceneId(currentSceneId)] ?: 0,
        flags = flags.toList(),
        stats = stats.toMap(),
        itemOrder = itemOrder.toList(),
        appliedSceneEffects = appliedSceneEffects.toSet()
    )

    private fun restoreSnapshot(snapshot: PlayerStateSnapshot) {
        // S3a: single-step back, per book-game tradition. After a
        // step back, the player cannot step back again — the only
        // ways forward are: make a choice (which captures a fresh
        // snapshot), restart the chapter, or restart the adventure.
        // `previousStepSnapshot` points at the scene we are about to
        // restore, so calling step back twice would be a no-op on the
        // second tap — not a bug per se, but it would let the player
        // "chain" back through the whole story in reverse. Traditional
        // gamebooks do not allow that, and the user explicitly
        // confirmed the same policy (2026-11-07). Clearing it here is
        // the single source of truth: any caller of `restoreSnapshot`
        // (step back, chapter restart) leaves the player with no undo
        // history.
        previousStepSnapshot = null
        // Ф1.7.1b: reset the gate state. We are leaving the current
        // scene and will re-evaluate on the target scene:
        //  - Non-repeatable passed gate: protected by `passedGates`,
        //    stays passed (`loadCurrentScene` skips gate block).
        //  - Repeatable gate: re-engages fresh on the target scene —
        //    "step back" is a new visit, per `repeatable` semantics.
        //  - In-progress gate on the *target* scene: starts fresh.
        //
        // With S3a, this reset is no longer a no-op: the player can
        // step back from any scene, including a gated one. Leaving a
        // gated scene mid-walk discards its gate progress — the
        // player left the scene, so the "walk N steps here" no longer
        // applies. Returning re-engages the gate (repeatable) or
        // shows it already passed (non-repeatable).
        resetGate()
        currentSceneId = snapshot.sceneId
        flags.clear()
        flags.addAll(snapshot.flags)
        stats.clear()
        stats.putAll(snapshot.stats)
        itemOrder.clear()
        itemOrder.addAll(snapshot.itemOrder)
        appliedSceneEffects.clear()
        appliedSceneEffects.addAll(snapshot.appliedSceneEffects)
        isDead = false
        saveProgress()
        loadCurrentScene()
    }

    private fun captureChapterStartIfNeeded() {
        // "Chapter start" = current scene is the first scene of its
        // chapter. Chapters are pure narrative grouping, but the reader
        // still offers "restart chapter" on the death screen — that
        // needs a snapshot of the state at chapter start. Computed via
        // `compiled.chapterIndexBySceneId` (no linear scan).
        val chapterIndex = compiled.chapterIndexBySceneId[SceneId(currentSceneId)] ?: return
        val chapter = compiled.adventure.chapters.getOrNull(chapterIndex) ?: return
        val firstSceneId = chapter.scenes.firstOrNull()?.id?.value ?: return
        if (currentSceneId != firstSceneId) return
        if (chapterStartSnapshot != null && chapterStartSnapshot!!.chapterIndex == chapterIndex) return
        val health = stats["health"]
        if (health == null || health > 0) {
            chapterStartSnapshot = captureSnapshot()
        }
    }

    private fun checkDeath(): Boolean {
        if (isDead) return false
        val health = stats["health"] ?: return false
        if (health > 0) return false
        isDead = true
        return true
    }

    private fun initPreviewState() {
        previewStartSceneId = arguments?.getString("preview_scene_id") ?: ""
        previewStartChapterIndex = arguments?.getInt("preview_chapter_index", 0) ?: 0
        adventureFileUri = null

        // IR: initialState is non-null with empty defaults.
        val initialState = adventure.initialState
        flags.clear()
        initialState.flags.forEach { addFlagIfMissing(it) }
        stats.clear()
        stats.putAll(initialState.stats)
        appliedSceneEffects.clear()
        rebuildItemOrder()

        currentSceneId = previewStartSceneId
        // Preview mode does not show the warning dialog; the field is
        // cleared so that a preview → library transition does not
        // accidentally "acknowledge" the warnings (there is no
        // progress file to save to in preview anyway).
        savedWarningsFingerprint = ""
        resetGate()
        isDead = false
        previousStepSnapshot = null
        chapterStartSnapshot = null

        Logger.log(
            requireContext(),
            "Preview: scene '$previewStartSceneId' (hint chapter ${previewStartChapterIndex + 1})"
        )
    }

    private fun initNormalState() {
        val uriString = arguments?.getString("adventure_file_uri")
        adventureFileUri = if (uriString != null) Uri.parse(uriString) else null

        val saved = if (adventureFileUri != null) {
            ProgressManager.loadProgress(requireContext(), adventureFileUri!!)
        } else null

        if (saved != null) {
            // `currentChapterIndex` is not a field anymore — chapters are
            // pure narrative grouping (Ф1.5.4); the reader gets the
            // current chapter on demand from
            // `compiled.chapterIndexBySceneId`. `saved.currentChapterIndex`
            // is still written by the editor's map view; the reader
            // ignores it.
            currentSceneId = saved.currentSceneId
            remainingSteps = saved.remainingSteps
            isGateActive = saved.isGateActive
            lastAbsoluteAtSaveFromProgress = saved.lastAbsoluteAtSave
            lastKnownAbsolute = -1

            flags.clear()
            saved.flags.forEach { addFlagIfMissing(it) }
            stats.clear()
            stats.putAll(saved.stats)
            appliedSceneEffects.clear()
            appliedSceneEffects.addAll(saved.appliedSceneEffects)
            itemOrder.clear()
            if (saved.itemOrder.isNotEmpty()) {
                for (name in saved.itemOrder) {
                    if ((stats[name] ?: 0) > 0) itemOrder.add(name)
                }
            } else {
                rebuildItemOrder()
            }
            previousStepSnapshot = saved.previousStepSnapshot
            chapterStartSnapshot = saved.chapterStartSnapshot
            savedWarningsFingerprint = saved.warningsFingerprint
            // Ф1.7.1b: restore one-shot-gate progress across app restarts.
            // Without this, a player who passed `chapter_end_1`'s gate,
            // closed the app, and reopened it would see the gate active
            // again — the entire "unlock the next chapter" mechanic
            // would be broken across sessions.
            passedGates.clear()
            passedGates.addAll(saved.passedGates)
            isDead = false
            Logger.log(
                requireContext(),
                "Progress loaded: scene '$currentSceneId', " +
                        "gate=$isGateActive, remaining=$remainingSteps, " +
                        "lastAbsAtSave=$lastAbsoluteAtSaveFromProgress"
            )
        } else {
            // IR: initialState is non-null; startScene is explicit (v3+).
            val initialState = adventure.initialState
            flags.clear()
            initialState.flags.forEach { addFlagIfMissing(it) }
            stats.clear()
            stats.putAll(initialState.stats)
            appliedSceneEffects.clear()
            rebuildItemOrder()
            currentSceneId = adventure.startScene.value
            // Fresh adventure: no acknowledgment yet, the warning dialog
            // will be shown if `cachedPlayerWarnings` is non-empty.
            savedWarningsFingerprint = ""
            resetGate()
            previousStepSnapshot = null
            chapterStartSnapshot = null
            isDead = false
            Logger.log(
                requireContext(),
                "Adventure '${adventure.title}' loaded, start scene '$currentSceneId'"
            )
        }

        tryCatchUpGate()
    }

    // ==================== Lifecycle ====================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureReadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Guard: if `onCreate` failed to parse/compile the adventure, it
        // already showed a dialog and called `popBackStack()`. Do not
        // build the UI on top of an uninitialized fragment.
        if (!::adventure.isInitialized || !::compiled.isInitialized) return

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        try {
            if (!previewMode) checkPermissions()

            if (previewMode) {
                binding.btnMenu.text = "Назад"
                binding.btnMenu.setOnClickListener { parentFragmentManager.popBackStack() }
                binding.btnDebug.visibility = View.VISIBLE
                binding.btnDebug.setOnClickListener { showDebugPanel() }
            } else {
                binding.btnMenu.setOnClickListener { showReaderMenuDialog() }
                binding.btnDebug.visibility = View.GONE
            }

            binding.btnHealth.setOnClickListener { showHealthDialog() }
            binding.btnInventory.setOnClickListener { showInventoryDialog() }
            binding.btnJournal.setOnClickListener { showJournalDialog() }

            binding.btnNextChapter.visibility = View.GONE

            // If the sensor was already alive when the fragment opened, catch up
            // immediately so the player sees the correct remaining count without
            // waiting for the next broadcast.
            tryCatchUpGate()

            loadCurrentScene()
            updateHud()

            // Show the warning dialog once per "new warnings fingerprint".
            // `savedWarningsFingerprint` is restored in `initNormalState`
            // (called from `onCreate`), and `currentWarningsFingerprint`
            // is computed there too. If they match, the player already
            // acknowledged this exact set for this adventure.
            if (!previewMode &&
                cachedPlayerWarnings.isNotEmpty() &&
                currentWarningsFingerprint != savedWarningsFingerprint
            ) {
                showWarningDialog(cachedPlayerWarnings)
            }

            if (!previewMode) {
                ContextCompat.registerReceiver(
                    requireContext(),
                    stepReceiver,
                    IntentFilter(StepCounterConstants.ACTION_UPDATE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Error in onViewCreated: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка запуска читалки: ${e.message}", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    override fun onStop() {
        super.onStop()
        if (!previewMode && adventureFileUri != null) {
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                ProgressManager.flush(appContext)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!previewMode) {
            try {
                requireContext().unregisterReceiver(stepReceiver)
            } catch (_: Exception) {
            }
        }
        _binding = null
    }

    private fun updateHud() {
        if (_binding == null) return

        val healthDef = adventure.tags?.firstOrNull { it.name == "health" && it.category == TagCategory.HEALTH }
            ?: adventure.tags?.firstOrNull { it.category == TagCategory.HEALTH }
        val healthValue = healthDef?.let { stats[it.name] ?: 0 } ?: 0
        binding.btnHealth.text = "❤️ $healthValue"

        val itemCount = itemOrder.count { (stats[it] ?: 0) > 0 }
        binding.btnInventory.text = "🎒 $itemCount"

        val journalCount = getJournalItems().size
        binding.btnJournal.text = "📖 $journalCount"
    }

    // ==================== Lists / dialogs (HUD) ====================

    private fun getHealthItems(): List<Pair<String, Int>> {
        return adventure.tags.orEmpty()
            .filter { it.category == TagCategory.HEALTH }
            .map { def ->
                val display = def.displayName.ifBlank { def.name }
                display to (stats[def.name] ?: 0)
            }
    }

    private fun getInventoryItems(): List<Pair<String, Int>> {
        return itemOrder.asReversed()
            .filter { (stats[it] ?: 0) > 0 }
            .map { name -> getDisplayName(name) to (stats[name] ?: 0) }
    }

    private fun getJournalItems(): List<String> {
        val result = mutableListOf<String>()
        for (name in flags.asReversed()) {
            if (isVisible(name) && getCategory(name) == TagCategory.JOURNAL) {
                result.add(getDisplayName(name))
            }
        }
        adventure.tags.orEmpty()
            .filter { it.category == TagCategory.JOURNAL && it.visible }
            .forEach { def ->
                if (!flags.contains(def.name) && (stats[def.name] ?: 0) != 0) {
                    val display = def.displayName.ifBlank { def.name }
                    result.add("$display: ${stats[def.name]}")
                }
            }
        return result
    }

    private fun showHealthDialog() {
        showListDialog("❤️ Здоровье", getHealthItems())
    }

    private fun showInventoryDialog() {
        showListDialog("🎒 Рюкзак", getInventoryItems())
    }

    private fun showJournalDialog() {
        showListDialog("📖 Журнал", getJournalItems().map { it to null })
    }

    private fun showListDialog(title: String, items: List<Pair<String, Int?>>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_item_list, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_items)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_dialog_empty)

        container.removeAllViews()
        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            for ((label, value) in items) {
                val rowView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dialog_row, container, false)
                val tvLabel = rowView.findViewById<TextView>(R.id.tv_row_label)
                val tvValue = rowView.findViewById<TextView>(R.id.tv_row_value)
                tvLabel.text = label
                if (value != null) {
                    tvValue.text = "×$value"
                    tvValue.visibility = View.VISIBLE
                } else {
                    tvValue.visibility = View.GONE
                }
                container.addView(rowView)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // ==================== Scene loading ====================

    private fun loadCurrentScene() {
        if (isDead) return

        val scene = compiled.scenesById[SceneId(currentSceneId)] ?: run {
            showEnd("Сцена не найдена: $currentSceneId")
            return
        }

        binding.tvStoryText.text = scene.text

        if (scene.id.value !in appliedSceneEffects) {
            applyEffects(scene.effects)
            appliedSceneEffects.add(scene.id.value)
            Logger.log(
                requireContext(),
                "Scene '${scene.id.value}' loaded for the first time"
            )
        }

        updateHud()

        if (checkDeath()) {
            saveProgress()
            showDeathScreen()
            return
        }

        captureChapterStartIfNeeded()

        // Gate check: scene-scoped (Ф1.7.0.4). If the scene has a gate
        // and it has not been passed in this playthrough (per
        // `passedGates` for repeatable=false) — activate the gate.
        // `repeatable = true` gates are never in `passedGates`, so they
        // re-engage on every visit.
        //
        // Ф1.7.1b: `remainingSteps` and `isGateActive` may have been
        // restored from a save (in-progress gate). Do **not** reset them
        // unconditionally: that would discard the player's steps every
        // time they close and reopen the app. Four cases:
        //   1. No active gate (`!isGateActive`): fresh gate, start with
        //      full requiredSteps.
        //   2. In-progress (`isGateActive && 0 < remainingSteps <=
        //      requiredSteps`): restore — the player was walking when
        //      they closed the app, and the walk counts.
        //   3. Completed via catch-up (`isGateActive && remainingSteps
        //      <= 0`): the player walked enough steps while the app was
        //      closed; complete the gate now.
        //   4. Stale (`remainingSteps > requiredSteps`): author edited
        //      the file while a playthrough was saved. Reset rather than
        //      show "walked -X of Y".
        val gate = scene.gate
        if (gate != null && scene.id.value !in passedGates) {
            if (previewMode) {
                startSceneGateInPreview(scene)
                return
            }
            when {
                !isGateActive -> startSceneGate(scene)
                remainingSteps <= 0 -> onGatePassed(scene)
                remainingSteps > gate.requiredSteps -> startSceneGate(scene)
                else -> showGateWaiting(scene)
            }
            return
        }

        // Ending: explicit isEnding (Ф1.7.0). Analyzer blocks non-preview
        // files where a scene has no choices and is not marked isEnding.
        if (scene.isEnding) {
            showEnd("🎉 Конец истории")
            return
        }

        // Defensive: post-analyzer, unreachable in non-preview. In
        // preview mode the author may be mid-edit — show the preview
        // end marker so they know the scene has no exits.
        if (scene.choices.isEmpty()) {
            showEnd("▶ Конец предпросмотра")
            return
        }

        updateChoices(scene.choices)
    }

    private fun applyEffects(effects: List<Effect>) {
        if (effects.isEmpty()) return
        // Single source of truth: EffectsApplier (Ф1.5) handles ordering
        // (list order, matching parser order set→remove→modify) and
        // clamping (>= 0 for all ModifyStat today; type-aware clamp
        // arrives in Ф2.8).
        val newState = EffectsApplier.apply(
            PlayerState(flags = flags.toSet(), stats = stats.toMap()),
            effects,
        )
        flags.clear()
        flags.addAll(newState.flags)
        stats.clear()
        stats.putAll(newState.stats)
        // Item order is a reader concern (itemOrder is used by the HUD
        // to keep the player's most-recent items at the top). EffectsApplier
        // does not touch it. Sync after the fact for every ITEM stat that
        // changed via an effect.
        for (effect in effects) {
            if (effect is Effect.ModifyStat) {
                val newValue = newState.stats[effect.name] ?: 0
                if (getCategory(effect.name) == TagCategory.ITEM) {
                    registerItem(effect.name, newValue)
                }
            }
        }
    }

    /**
     * Renders [choices] as buttons. Two kinds of unavailable choice are
     * handled uniformly:
     *
     * - **[forceDisabled] = true** — the current scene has an active
     *   gate. All choices are disabled; the player sees the labels but
     *   cannot pick until the gate is passed. The "Пройдено X из Y"
     *   indicator lives in `tvStepsRequired` (see `showGateWaiting`).
     * - **`choice.condition` evaluates to `false`** — the choice is
     *   visible but disabled. Reason ("requires sword", "need 3 gold")
     *   is planned for Ф4/N4; today it is a plain grey button.
     *
     * **Visible, not hidden.** Previously unavailable choices were
     * removed from the list; the player could not tell whether an
     * option was missing or just not shown. Showing them disabled is
     * more honest and lets the player see what they are missing.
     *
     * Behaviour is identical in preview mode: the same visual, the
     * same disabled state. The editor uses preview mode to test the
     * reader, and the two should match.
     */
    private fun updateChoices(
        choices: List<Choice>,
        forceDisabled: Boolean = false,
    ) {
        // Ф1.7.1b-fix: the "Пройдено X из Y" indicator is
        // scene-scoped — it belongs to the gate of the scene the
        // player is currently in. When we render choices for a scene
        // **without** an active gate (either no gate, or the gate was
        // just passed), the previous scene's text is stale and must be
        // hidden. Without this, walking `health_test` (gate 50) →
        // `start` → `journal_test` would show `health_test`'s
        // "Пройдено 30 из 50" on `journal_test` — visible bug,
        // reported in smoke. `showGateWaiting` passes
        // `forceDisabled = true` and sets `VISIBLE` itself, so the
        // indicator still shows during a gate.
        if (!forceDisabled) {
            binding.tvStepsRequired.visibility = View.GONE
        }
        val container = binding.choicesContainer
        container.removeAllViews()

        for (choice in choices) {
            val available = !forceDisabled && isConditionMet(choice.condition)
            val button = Button(requireContext()).apply {
                text = choice.text
                if (available) {
                    setOnClickListener {
                        previousStepSnapshot = captureSnapshot()
                        applyEffects(choice.effects)
                        currentSceneId = choice.nextScene.value
                        saveProgress()

                        if (checkDeath()) {
                            showDeathScreen()
                            return@setOnClickListener
                        }

                        loadCurrentScene()
                    }
                } else {
                    isEnabled = false
                    alpha = 0.5f
                }
            }
            container.addView(button)
        }
    }

    private fun isConditionMet(condition: Condition?): Boolean {
        if (condition == null) return true
        return try {
            // Single source of truth: Evaluator (Ф1.5). All callers —
            // `Engine.step`, this reader, the editor analyzer — use the
            // same evaluator, so a choice the reader shows as available
            // is the same one `Engine.step` would allow.
            //
            // TODO(Ф2.5 / N8): the `catch { true }` fallback is a
            // leftover from the pre-typed-string parser. Now that
            // conditions are a typed AST, `Evaluator.evaluate` should
            // never throw on valid input. Remove this fallback when
            // the type checker (Ф2.2) guarantees that no invalid
            // condition can reach the reader — that's N8's job.
            Evaluator.evaluate(condition, PlayerState(flags.toSet(), stats.toMap()))
        } catch (e: Exception) {
            Logger.log(requireContext(), "isConditionMet failed: ${e.message}")
            true
        }
    }

    // ==================== Death screen ====================

    private fun showDeathScreen() {
        binding.tvStepsRequired.visibility = View.GONE
        binding.choicesContainer.removeAllViews()

        val deathText = adventure.deathScene?.text ?: "💀 Вы погибли."
        binding.tvStoryText.text = deathText

        val allowStepBack = adventure.deathScene?.allowStepBack ?: true
        val allowChapter = adventure.deathScene?.allowChapterRestart ?: true
        val allowAdventure = adventure.deathScene?.allowAdventureRestart ?: true

        if (allowStepBack && previousStepSnapshot != null) {
            val btn = Button(requireContext()).apply {
                text = "↩ Один шаг назад"
                setOnClickListener { onStepBack() }
            }
            binding.choicesContainer.addView(btn)
        }

        val currentChapterIdx = compiled.chapterIndexBySceneId[SceneId(currentSceneId)] ?: 0
        if (allowChapter && chapterStartSnapshot != null && chapterStartSnapshot!!.chapterIndex == currentChapterIdx) {
            val btn = Button(requireContext()).apply {
                text = "📖 В начало главы"
                setOnClickListener { onChapterRestart() }
            }
            binding.choicesContainer.addView(btn)
        }

        if (allowAdventure) {
            val btn = Button(requireContext()).apply {
                text = if (previewMode) "🔄 Начать предпросмотр заново" else "🏠 Начать приключение заново"
                setOnClickListener { onAdventureRestart() }
            }
            binding.choicesContainer.addView(btn)
        }
    }

    private fun onStepBack() {
        val snapshot = previousStepSnapshot ?: return
        restoreSnapshot(snapshot)
    }

    private fun onChapterRestart() {
        val snapshot = chapterStartSnapshot ?: return
        // `previousStepSnapshot` is cleared inside `restoreSnapshot`
        // (single source of truth, S3a). No need to repeat it here.
        restoreSnapshot(snapshot)
    }

    private fun onAdventureRestart() {
        if (previewMode) {
            flags.clear()
            stats.clear()
            itemOrder.clear()
            appliedSceneEffects.clear()
            val initialState = adventure.initialState
            initialState?.flags?.forEach { addFlagIfMissing(it) }
            if (initialState?.stats != null) stats.putAll(initialState.stats)
            rebuildItemOrder()
            currentSceneId = previewStartSceneId
            resetGate()
            isDead = false
            previousStepSnapshot = null
            chapterStartSnapshot = null
            loadCurrentScene()
        } else {
            isDead = false
            previousStepSnapshot = null
            chapterStartSnapshot = null
            onRestartAdventure()
        }
    }

    // ==================== Steps gate ====================

    private fun resetGate() {
        isGateActive = false
        remainingSteps = 0
        lastKnownAbsolute = -1
        lastAbsoluteAtSaveFromProgress = -1
    }

    /**
     * If a gate is restored from saved progress and the sensor is already alive,
     * subtract the delta between the last save and the current absolute reading.
     * Idempotent: safe to call from onCreate and onViewCreated.
     */
    private fun tryCatchUpGate() {
        if (!isGateActive || previewMode) return
        if (lastKnownAbsolute >= 0) return // already caught up or just started

        val scene = compiled.scenesById[SceneId(currentSceneId)] ?: run {
            resetGate()
            return
        }
        // `gate` is a public property from another module (`:engine`) —
        // Kotlin cannot smart-cast it after `scene.gate == null`, so we
        // hoist it into a local val.
        val gate = scene.gate ?: run {
            resetGate()
            return
        }
        // If the gate was already passed in this session, do not
        // re-engage on app restart — `isGateActive` may be stale.
        if (!gate.repeatable && scene.id.value in passedGates) {
            resetGate()
            return
        }

        if (StepCounterService.hasFirstEvent && lastAbsoluteAtSaveFromProgress >= 0) {
            val delta = (StepCounterService.absoluteSteps - lastAbsoluteAtSaveFromProgress).coerceAtLeast(0)
            remainingSteps = (remainingSteps - delta).coerceAtLeast(0)
            lastKnownAbsolute = StepCounterService.absoluteSteps
            // Ф1.7.1b: do NOT call `onGatePassed` here. Leave the
            // decision "is the gate done?" to `loadCurrentScene`,
            // which is called right after both `initNormalState` and
            // `onViewCreated`. Centralising this check in one place
            // avoids a subtle bug: for a `repeatable = true` gate,
            // `onGatePassed` does not add the scene to `passedGates`,
            // so a separate "did the gate just complete?" check in
            // `loadCurrentScene` would re-engage it immediately.
        }
        // else: sensor is not awake yet; catch-up will happen on the first broadcast.
    }

    private fun startSceneGate(scene: Scene) {
        val gate = scene.gate ?: return
        isGateActive = true
        remainingSteps = gate.requiredSteps
        lastKnownAbsolute = if (StepCounterService.hasFirstEvent) StepCounterService.absoluteSteps else -1
        lastAbsoluteAtSaveFromProgress = -1
        if (_binding != null) showGateWaiting(scene)
        saveProgress()
    }

    private fun startSceneGateInPreview(scene: Scene) {
        val gate = scene.gate ?: return
        isGateActive = true
        remainingSteps = gate.requiredSteps
        lastKnownAbsolute = -1
        showGateWaiting(scene)
    }

    private fun showGateWaiting(scene: Scene) {
        if (_binding == null) return
        // Show the scene's choices, all disabled. The player sees what
        // they will be able to pick once the gate is passed — better
        // UX than an empty container. When the gate is passed,
        // `onGatePassed` calls `updateChoices(scene.choices)` (no
        // `forceDisabled`), re-rendering the same buttons as available.
        updateChoices(scene.choices, forceDisabled = true)
        if (lastKnownAbsolute < 0) {
            binding.tvStepsRequired.text = "Ожидание данных шагомера…"
        } else {
            updateStepsProgressUi(scene)
        }
        if (com.myapp.adventure.step.StepSourceManager.isHealthConnectActive(requireContext())) {
            binding.tvStepsRequired.append("\n(Health Connect: обновление раз в ~1 минуту)")
        }
        binding.tvStepsRequired.visibility = View.VISIBLE
    }

    private fun updateStepsProgressUi(scene: Scene) {
        if (_binding == null) return
        val total = scene.gate?.requiredSteps ?: return
        val done = (total - remainingSteps).coerceIn(0, total)
        binding.tvStepsRequired.text =
            "Пройдено $done из $total шагов (осталось $remainingSteps)."
        binding.tvStepsRequired.visibility = View.VISIBLE
    }

    private fun onGatePassed(scene: Scene) {
        // Persist the pass only for non-repeatable gates. `repeatable =
        // true` gates must re-engage on every visit, so they are never
        // recorded here.
        if (scene.gate?.repeatable != true) {
            passedGates.add(scene.id.value)
        }
        isGateActive = false
        remainingSteps = 0
        saveProgress()

        if (scene.isEnding) {
            showEnd("🎉 Конец истории")
            return
        }
        if (scene.choices.isEmpty()) {
            // Post-analyzer: impossible in non-preview (ERROR blocks the
            // load). In preview: show the preview-end marker.
            showEnd("▶ Конец предпросмотра")
            return
        }
        updateChoices(scene.choices)
    }

    private fun setDebugRemainingSteps(remaining: Int) {
        remainingSteps = remaining.coerceAtLeast(0)
        val scene = compiled.scenesById[SceneId(currentSceneId)] ?: return
        if (scene.gate == null) return
        if (remainingSteps <= 0) {
            onGatePassed(scene)
        } else {
            updateStepsProgressUi(scene)
        }
    }

    // ==================== End / restart ====================

    private fun showEnd(message: String) {
        binding.tvStoryText.text = if (previewMode) "▶ Конец предпросмотра\n\n$message" else "Конец\n\n$message"
        binding.btnNextChapter.visibility = View.GONE
        binding.tvStepsRequired.visibility = View.GONE

        val container = binding.choicesContainer
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val restartButton = Button(requireContext()).apply {
            text = if (previewMode) "Вернуться в редактор" else "Начать заново"
            setOnClickListener {
                if (previewMode) parentFragmentManager.popBackStack()
                else onRestartAdventure()
            }
        }
        container.addView(restartButton)
    }

    private fun onRestartAdventure() {
        if (adventureFileUri != null) {
            ProgressManager.deleteProgress(requireContext(), adventureFileUri!!)
        }
        flags.clear()
        stats.clear()
        itemOrder.clear()
        appliedSceneEffects.clear()
        // `passedGates` must be cleared too: a "restart" puts the player
        // back at the start, so every non-repeatable gate must re-engage
        // on the next visit. Without this, a "one-shot" gate that was
        // passed before the restart would stay passed — the player would
        // get a free pass through the whole story. Bug was caught during
        // the migration review (see Progress log 2026-11-07).
        passedGates.clear()
        // The warning dialog is shown again on the first scene after a
        // restart: the player is re-entering the story from scratch, so
        // it makes sense to remind them of any warnings once more.
        savedWarningsFingerprint = ""
        // IR: initialState is non-null with empty defaults.
        val initialState = adventure.initialState
        initialState.flags.forEach { addFlagIfMissing(it) }
        stats.putAll(initialState.stats)
        rebuildItemOrder()

        currentSceneId = adventure.startScene.value
        resetGate()
        isDead = false
        previousStepSnapshot = null
        chapterStartSnapshot = null
        saveProgress()
        loadCurrentScene()
        Toast.makeText(requireContext(), "Приключение начато заново", Toast.LENGTH_SHORT).show()
    }

    // ==================== Progress persistence ====================

    private fun saveProgress() {
        if (previewMode) return
        if (adventureFileUri == null) return

        val absoluteAtSave = if (StepCounterService.hasFirstEvent) StepCounterService.absoluteSteps else -1
        val progress = ProgressData(
            adventureFileName = "",
            currentChapterIndex = compiled.chapterIndexBySceneId[SceneId(currentSceneId)] ?: 0,
            currentSceneId = currentSceneId,
            flags = flags.toList(),
            stats = stats.toMap(),
            appliedSceneEffects = appliedSceneEffects.toSet(),
            itemOrder = itemOrder.toList(),
            previousStepSnapshot = previousStepSnapshot,
            chapterStartSnapshot = chapterStartSnapshot,
            remainingSteps = remainingSteps,
            isGateActive = isGateActive,
            lastAbsoluteAtSave = absoluteAtSave,
            warningsFingerprint = savedWarningsFingerprint,
            passedGates = passedGates.toList()
        )
        ProgressManager.saveProgress(requireContext(), adventureFileUri!!, progress)
    }

    // ==================== Permissions ====================

    private fun checkPermissions() {
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!notificationPermissionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на уведомления.", Toast.LENGTH_LONG).show()
        }
        if (!activityRecognitionGranted) {
            Toast.makeText(requireContext(), "Нет разрешения на физическую активность.", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== Menu ====================

    private fun showReaderMenuDialog() {
        // Build the item list dynamically: "↩ Шаг назад" appears only
        // when an undo is available, "Предупреждения (N)" only when
        // there are warnings. Each item is a Pair(label, action), so
        // adding/removing/reordering entries does not require touching
        // the index-based dispatch.
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items.add("В главное меню" to {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        })
        // S3a: step back from any scene. Not shown on the death
        // screen (`isDead`) — the death screen has its own explicit
        // "↩ Один шаг назад" button (see `showDeathScreen`), and
        // duplicating it in the menu is noise. Not shown in preview
        // mode either — undo is a player affordance, not an
        // authoring tool.
        if (!previewMode && !isDead && previousStepSnapshot != null) {
            items.add("↩ Шаг назад" to {
                onStepBack()
            })
        }
        items.add("Начать приключение заново" to {
            confirmRestartAdventure()
        })
        if (cachedPlayerWarnings.isNotEmpty()) {
            items.add("Предупреждения (${cachedPlayerWarnings.size})" to {
                showWarningInfoDialog(cachedPlayerWarnings)
            })
        }
        items.add("Настройки" to {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        })

        AlertDialog.Builder(requireContext())
            .setTitle("Меню")
            .setItems(items.map { it.first }.toTypedArray()) { _, which ->
                items[which].second.invoke()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmRestartAdventure() {
        AlertDialog.Builder(requireContext())
            .setTitle("Начать заново?")
            .setMessage("Текущий прогресс по этому приключению будет удалён. Это действие нельзя отменить.")
            .setPositiveButton("Начать заново") { _, _ -> onRestartAdventure() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ==================== Debug panel (preview) ====================

    private fun showDebugPanel() {
        if (!previewMode) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_panel, null)

        val tvSceneInfo = dialogView.findViewById<TextView>(R.id.tv_debug_scene_info)
        val containerStats = dialogView.findViewById<LinearLayout>(R.id.container_stats)
        val containerFlags = dialogView.findViewById<LinearLayout>(R.id.container_flags)
        val btnAddStat = dialogView.findViewById<Button>(R.id.btn_add_stat)
        val btnAddFlag = dialogView.findViewById<Button>(R.id.btn_add_flag)
        val btnReset = dialogView.findViewById<Button>(R.id.btn_reset)

        val sectionSteps = dialogView.findViewById<LinearLayout>(R.id.section_steps)
        val tvStepsStatus = dialogView.findViewById<TextView>(R.id.tv_steps_status)
        val etStepsValue = dialogView.findViewById<EditText>(R.id.et_steps_value)
        val btnApplySteps = dialogView.findViewById<Button>(R.id.btn_apply_steps)
        val btnOpenChapterNow = dialogView.findViewById<Button>(R.id.btn_open_chapter_now)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("🐞 Отладка")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .create()

        val currentSceneForPanel = compiled.scenesById[SceneId(currentSceneId)]
        if (isGateActive && currentSceneForPanel?.gate != null) {
            etStepsValue.setText(remainingSteps.toString())
        }

        fun refreshStepsSection() {
            val sceneForGate = compiled.scenesById[SceneId(currentSceneId)]
            val gate = sceneForGate?.gate
            if (isGateActive && gate != null) {
                sectionSteps.visibility = View.VISIBLE
                val total = gate.requiredSteps
                val done = (total - remainingSteps).coerceIn(0, total)
                tvStepsStatus.text =
                    "Сцена «${sceneForGate.id.value}»: пройдено $done из $total (осталось $remainingSteps)"
            } else {
                sectionSteps.visibility = View.GONE
            }
        }

        fun refresh() {
            tvSceneInfo.text = "Текущая сцена: $currentSceneId"

            containerStats.removeAllViews()
            if (stats.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = "(нет статов)"
                    setTextColor(Color.parseColor("#9E9E9E"))
                    textSize = 12f
                }
                containerStats.addView(tv)
            } else {
                for ((name, value) in stats.toSortedMap()) {
                    val rowView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_debug_stat, containerStats, false)
                    val tvName = rowView.findViewById<TextView>(R.id.tv_stat_name)
                    val tvValue = rowView.findViewById<TextView>(R.id.tv_stat_value)
                    val btnEdit = rowView.findViewById<View>(R.id.btn_edit_stat)
                    val btnDelete = rowView.findViewById<View>(R.id.btn_delete_stat)

                    tvName.text = name
                    tvValue.text = value.toString()

                    btnEdit.setOnClickListener {
                        showEditStatDialog(name, value) { newValue ->
                            stats[name] = newValue
                            if (getCategory(name) == TagCategory.ITEM) registerItem(name, newValue)
                            isDead = false
                            loadCurrentScene()
                            refresh()
                        }
                    }
                    btnDelete.setOnClickListener {
                        stats.remove(name)
                        itemOrder.remove(name)
                        isDead = false
                        loadCurrentScene()
                        refresh()
                    }
                    containerStats.addView(rowView)
                }
            }

            containerFlags.removeAllViews()
            if (flags.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = "(нет флагов)"
                    setTextColor(Color.parseColor("#9E9E9E"))
                    textSize = 12f
                }
                containerFlags.addView(tv)
            } else {
                for (flag in flags.sorted()) {
                    val rowView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_debug_flag, containerFlags, false)
                    val tvName = rowView.findViewById<TextView>(R.id.tv_flag_name)
                    val btnDelete = rowView.findViewById<View>(R.id.btn_delete_flag)

                    tvName.text = flag
                    btnDelete.setOnClickListener {
                        flags.remove(flag)
                        loadCurrentScene()
                        refresh()
                    }
                    containerFlags.addView(rowView)
                }
            }

            refreshStepsSection()
        }

        btnAddStat.setOnClickListener {
            showAddStatDialog { name, value ->
                stats[name] = value
                if (getCategory(name) == TagCategory.ITEM) registerItem(name, value)
                isDead = false
                loadCurrentScene()
                refresh()
            }
        }

        btnAddFlag.setOnClickListener {
            showAddFlagDialog { name ->
                addFlagIfMissing(name)
                loadCurrentScene()
                refresh()
            }
        }

        btnApplySteps.setOnClickListener {
            val value = etStepsValue.text.toString().toIntOrNull()
            if (value == null || value < 0) {
                Toast.makeText(requireContext(), "Введите целое число ≥ 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setDebugRemainingSteps(value)
            refresh()
        }

        btnOpenChapterNow.setOnClickListener {
            val sceneForGate = compiled.scenesById[SceneId(currentSceneId)]
            if (sceneForGate?.gate == null) {
                Toast.makeText(requireContext(), "Шлюз неактивен", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onGatePassed(sceneForGate)
            refresh()
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Сбросить?")
                .setMessage("Сбросить флаги и статы к начальному состоянию?")
                .setPositiveButton("Сбросить") { _, _ ->
                    flags.clear()
                    stats.clear()
                    itemOrder.clear()
                    val initialState = adventure.initialState
                    initialState?.flags?.forEach { addFlagIfMissing(it) }
                    if (initialState?.stats != null) stats.putAll(initialState.stats)
                    rebuildItemOrder()
                    isDead = false
                    loadCurrentScene()
                    refresh()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        refresh()
        dialog.show()
    }

    private fun showEditStatDialog(statName: String, currentValue: Int, onResult: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_stat, null)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_stat_value)
        val tvName = dialogView.findViewById<TextView>(R.id.tv_debug_stat_name)

        tvName.text = statName
        etValue.setText(currentValue.toString())
        etValue.setSelection(etValue.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить значение")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val v = etValue.text.toString().toIntOrNull() ?: return@setPositiveButton
                onResult(v)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddStatDialog(onResult: (String, Int) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_add, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_debug_name)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_value)

        etValue.visibility = View.VISIBLE
        etValue.setText("0")

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить стат")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().toIntOrNull() ?: 0
                if (name.isNotEmpty()) onResult(name, value)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddFlagDialog(onResult: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_debug_add, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_debug_name)
        val etValue = dialogView.findViewById<EditText>(R.id.et_debug_value)

        etValue.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить флаг")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) onResult(name)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}