package com.myapp.adventure.utils

import androidx.annotation.StringRes
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.ValidationIssue
import com.myapp.adventure.engine.CompileError
import com.myapp.adventure.engine.CompileResult
import com.myapp.adventure.engine.Engine
import com.myapp.adventure.engine.analyze.Analyzer
import com.myapp.adventure.engine.analyze.LintIssue
import com.myapp.adventure.engine.analyze.LintKind
import com.myapp.adventure.engine.analyze.Severity
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.types.TypeChecker
import com.myapp.adventure.engine.ir.types.TypeError

/**
 * Bridge between the editor UI and `:engine`'s validation pipeline.
 *
 * **Ф2.6.** Before Ф2.6, `StoryValidator` had its own parser and ten
 * hand-rolled checks. Nine of them duplicated what `:engine` already
 * does — but at different pipeline layers (parse / compile / analyze)
 * and with different error shapes — and one (a warning about a step
 * gate on an `isEnding` scene) was **wrong**: the reader handles gates
 * on ending scenes correctly (gate first, then `onGatePassed` checks
 * `isEnding`), so warning "the gate will not fire" was misleading.
 *
 * After Ф2.6, this object is a thin **bridge**: it takes the editor's
 * `Adventure` (built by `AdventureEditorViewModel.buildAdventure()`)
 * and runs the exact pipeline the reader will run:
 *
 * 1. [TypeChecker.check] — unknown variables, wrong types (Ф2.2).
 * 2. [Engine.compile] — duplicate scene ids, missing start scene,
 *    dangling `nextScene` (Ф1.5.5.1 / Ф1.6.2.1).
 * 3. [Analyzer.lint] — reachability, dead ends, unestablished items,
 *    contradictory endings (Ф1.6).
 *
 * The result is a `List<ValidationIssue>` ready for the editor's
 * validation dialog. The author sees **the same errors** that the
 * player would hit on open — not a similar-but-different set produced
 * by a parallel implementation.
 *
 * **Why `scenes: List<EditorScene>` is still passed.** The engine
 * reports problems by `SceneId` (the on-disk id). The editor's
 * navigation ("click the issue → jump to the scene") works by
 * `EditorScene.uid` (a per-scene UUID that survives renaming).
 * `StoryValidator` maps `SceneId.value` → `EditorScene.uid` once and
 * fills `ValidationIssue.sceneUid`. When the id is not in the map
 * (e.g. an `InitialState` error with `sceneId == null`), `sceneUid`
 * is `""` — the dialog's click handler is a no-op for that issue.
 *
 * **What this object does NOT do.**
 * - It does not parse conditions (Ф2.6 DoD: grep `extractHasFlagName`
 *   = 0, `StoryValidator` has no `ConditionParser`).
 * - It does not compute reachability (`Analyzer.reachable` owns it).
 * - It does not know about step gates on ending scenes — that
 *   combination works at runtime and produces no issue.
 *
 * **Deleted in Ф2.6** (and why):
 * - Checks #1–#9 — fully duplicated `:engine`:
 *   - #1 "empty choice text" — `ParseError.Structure` (JsonToIr).
 *   - #2 "choice without next_scene" — `ParseError.Structure`.
 *   - #3 "dangling next_scene" — `CompileError.MissingNextScene`.
 *   - #4 "condition on unestablished flag" — `LintKind.ConditionOnUnestablishedFlag`.
 *   - #5 "removing unestablished item" — `LintKind.RemovingUnestablishedItem`.
 *   - #6 "death scene without health" — `LintKind.DeathSceneWithoutHealth`.
 *   - #7 "death without death scene" — `LintKind.DeathWithoutDeathScene`.
 *   - #8 "dead end" — `LintKind.DeadEnd`.
 *   - #9 "unreachable scene" — `LintKind.UnreachableScene`.
 * - Check #10 "step gate on an ending scene" — **wrong**, removed
 *   without replacement. The reader's `loadCurrentScene` checks
 *   `scene.gate` before `scene.isEnding`; `onGatePassed` then
 *   evaluates `isEnding`. A gated ending is a legitimate mechanic
 *   ("walk N steps to reach the final scene").
 * - `extractHasFlagName` — the last piece of condition parsing in
 *   `:app`. Ф2.6 DoD: 0 occurrences.
 */
object StoryValidator {

    /**
     * Validates [adventure] against the reader's pipeline.
     *
     * @param adventure the IR built by the editor (via
     *   `AdventureEditorViewModel.buildAdventure()`). Should be built
     *   **after** `freezeAllPendingIds()` — see `showValidationDialog`
     *   in `AdventureEditorFragment` — so that pending scene IDs
     *   are stable.
     * @param scenes the editor scenes, used only to map
     *   `SceneId.value` → `EditorScene.uid` for UI navigation.
     * @return issues in the order they were found. Type errors first,
     *   then compile errors (if any — otherwise lint), then lint
     *   issues. Each issue's `sceneUid` is `""` when the underlying
     *   engine error has no scene (e.g. `InitialState` type errors,
     *   `MissingStartScene`).
     */
    fun validate(
        adventure: Adventure,
        scenes: List<EditorScene>,
    ): List<ValidationIssue> {
        val uidBySceneId: Map<String, String> = scenes
            .filter { it.id.isNotEmpty() }
            .associate { it.id to it.uid }

        val issues = mutableListOf<ValidationIssue>()

        // 1. Type checking (Ф2.2). Usually empty — the editor builds a
        //    mostly-valid Adventure — but a typo in a condition or a
        //    stat-effect on a Bool is easy to make and impossible to
        //    see without this.
        for (typeError in TypeChecker.check(adventure)) {
            issues += ValidationIssue(
                severity = IssueSeverity.ERROR,
                sceneUid = typeError.sceneId?.let { uidBySceneId[it.value] } ?: "",
                titleRes = titleFor(typeError),
                description = typeError.message,
            )
        }

        // 2. Compile. If the file cannot be compiled, lint is not run —
        //    same short-circuit as `JsonToIr.parse` → `TypeChecker` in
        //    Ф2.3. A `CompiledAdventure` cannot even be built, so
        //    `Analyzer.reachable` / `Analyzer.lint` have nothing to
        //    work on.
        when (val compileResult = Engine.compile(adventure)) {
            is CompileResult.Failure -> {
                for (compileError in compileResult.errors) {
                    issues += ValidationIssue(
                        severity = IssueSeverity.ERROR,
                        sceneUid = sceneUidOf(compileError, uidBySceneId),
                        titleRes = titleFor(compileError),
                        description = compileError.message,
                    )
                }
            }
            is CompileResult.Success -> {
                // 3. Lint (Ф1.6). `Severity.INFO` issues are not shown
                //    in the editor validation dialog — they are
                //    informational, not actionable. If a future
                //    `LintKind` gains an INFO variant that *is*
                //    actionable, teach this `when` to render it.
                for (lintIssue in Analyzer.lint(compileResult.compiled)) {
                    val severity = when (lintIssue.severity) {
                        Severity.ERROR -> IssueSeverity.ERROR
                        Severity.WARNING -> IssueSeverity.WARNING
                        Severity.INFO -> continue
                    }
                    issues += ValidationIssue(
                        severity = severity,
                        sceneUid = lintIssue.sceneId?.let { uidBySceneId[it.value] } ?: "",
                        titleRes = titleFor(lintIssue),
                        description = lintIssue.message,
                    )
                }
            }
        }

        return issues
    }

    // ---------- Scene id → editor uid ----------

    private fun sceneUidOf(
        compileError: CompileError,
        uidBySceneId: Map<String, String>,
    ): String = when (compileError) {
        is CompileError.DuplicateSceneId ->
            uidBySceneId[compileError.sceneId.value] ?: ""
        is CompileError.MissingStartScene -> ""
        is CompileError.MissingNextScene ->
            uidBySceneId[compileError.sceneId.value] ?: ""
    }

    // ---------- Titles (Russian UI) ----------
    //
    // Why titles live here, not in :engine: LintKind / TypeError are
    // language-agnostic. The Russian title is a UI concern of the
    // editor dialog. Keeping the `when` exhaustive means: adding a new
    // LintKind / TypeError in :engine breaks `:app` compilation until
    // someone adds a title here — intentional, no silent English text
    // sneaking into a Russian-language dialog.

    @StringRes
    private fun titleFor(typeError: TypeError): Int = when (typeError) {
        is TypeError.UnknownVariable -> R.string.validation_title_unknown_variable
        is TypeError.HasOnNonBool -> R.string.validation_title_has_on_non_bool
        is TypeError.SetFlagOnNonBool -> R.string.validation_title_set_flag_on_non_bool
        is TypeError.RemoveFlagOnNonBool -> R.string.validation_title_remove_flag_on_non_bool
        is TypeError.ModifyStatOnNonNumeric -> R.string.validation_title_modify_stat_on_non_numeric
        is TypeError.CompareOnNonNumeric -> R.string.validation_title_compare_on_non_numeric
        is TypeError.InRangeOnNonNumeric -> R.string.validation_title_in_range_on_non_numeric
        is TypeError.InitialFlagOnNonBool -> R.string.validation_title_initial_flag_on_non_bool
        is TypeError.InitialStatOnNonNumeric -> R.string.validation_title_initial_stat_on_non_numeric
    }

    @StringRes
    private fun titleFor(compileError: CompileError): Int = when (compileError) {
        is CompileError.DuplicateSceneId -> R.string.validation_title_duplicate_scene_id
        is CompileError.MissingStartScene -> R.string.validation_title_missing_start_scene
        is CompileError.MissingNextScene -> R.string.validation_title_missing_next_scene
    }

    @StringRes
    private fun titleFor(lintIssue: LintIssue): Int = when (lintIssue.kind) {
        is LintKind.UnreachableScene -> R.string.validation_title_unreachable_scene
        is LintKind.RemovingUnestablishedItem -> R.string.validation_title_removing_unestablished_item
        is LintKind.DeathSceneWithoutHealth -> R.string.validation_title_death_scene_without_health
        is LintKind.ConditionOnUnestablishedFlag -> R.string.validation_title_condition_on_unestablished_flag
        is LintKind.DeathWithoutDeathScene -> R.string.validation_title_death_without_death_scene
        is LintKind.DeadEnd -> R.string.validation_title_dead_end
        is LintKind.EndingSceneWithChoices -> R.string.validation_title_ending_scene_with_choices
        is LintKind.UnreachableInvariant -> R.string.validation_title_unreachable_invariant
        is LintKind.DeadCondition -> R.string.validation_title_dead_condition
    }
}