package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.CompiledAdventure
import com.myapp.adventure.engine.ir.SceneId

/**
 * Semantic analysis of an adventure that **compiles successfully**.
 *
 * **Scope.** The analyzer works on a
 * [com.myapp.adventure.engine.CompiledAdventure] — that is, after
 * [com.myapp.adventure.engine.Engine.compile] has built the indexes.
 * Anything that prevents compilation is
 * [com.myapp.adventure.engine.CompileError], not a [LintIssue]. Anything
 * that prevents **parsing** is
 * [com.myapp.adventure.engine.parse.ParseError]. The analyzer is the third
 * and last layer: "the file opens and plays, but is it coherent?" — same
 * boundary fixed in Ф1.3.4.1.
 *
 * **What the analyzer does not check (yet).**
 * - `nextScene` pointing to a scene that does not exist. It is checked
 *   implicitly by [reachable]: a `nextScene` to a missing scene is
 *   added to [reachable] (the id is recorded), but the scene is not in
 *   [CompiledAdventure.scenesById], so it is not further traversed. This
 *   is the same model as the current `:app` reader: it fails at the
 *   moment of transition, not at open time. A dedicated check
 *   ([LintKind.MissingNextScene]) will be added in Ф1.6.2 together with
 *   the `StoryValidator` port.
 * - `has_X` on numeric variable, unknown variable — type checker, Ф2.2.
 * - Duplicate `ChoiceId` within a scene — the reader looks choices up by
 *   id; a duplicate would be resolved to the first one. Will be a
 *   `LintKind.DuplicateChoiceId` in Ф1.6.2.
 *
 * **Determinism.** Both methods return values sorted by scene id, so
 * repeated runs on the same input produce identical output. This is a
 * prerequisite for snapshot tests of analyzer output in a later phase.
 */
public object Analyzer {

    /**
     * Builds a [ReachabilityReport] for [compiled].
     *
     * **BFS** from [com.myapp.adventure.engine.ir.Adventure.startScene]
     * along `choice.nextScene` links. Every entry in
     * [ReachabilityReport.reachable] is guaranteed to exist in
     * [CompiledAdventure.scenesById] — so the UI can safely jump to any
     * scene id it finds there.
     *
     * **Missing references are not reachability.** A `nextScene` that
     * points to a scene not present in [CompiledAdventure.scenesById] is
     * **not** added to [ReachabilityReport.reachable] — "reachable" means
     * "the player can visit this scene", and a scene that does not exist
     * cannot be visited. The missing reference itself is a separate
     * problem ([LintKind.MissingNextScene], coming in Ф1.6.2), not a
     * reachability question.
     *
     * **Missing start scene.** If
     * [com.myapp.adventure.engine.ir.Adventure.startScene] itself is not
     * in [CompiledAdventure.scenesById] — a bug in the file, not caught
     * by `JsonToIr` because reference-integrity belongs to the analyzer —
     * [ReachabilityReport.reachable] is empty and every existing scene is
     * reported as unreachable. The missing start scene will surface as a
     * separate lint issue ([LintKind.MissingStartScene], Ф1.6.2).
     *
     * **Termination.** The BFS visits each scene at most once; `nextScene`
     * cycles (self-loops, `A → B → A`) are safe.
     */
    public fun reachable(compiled: CompiledAdventure): ReachabilityReport {
        val start = compiled.adventure.startScene
        val reachable = mutableSetOf<SceneId>()
        val queue = ArrayDeque<SceneId>()
        // Only seed the BFS if the start scene actually exists. If it does
        // not, `reachable` stays empty and every existing scene is reported
        // as unreachable — which is exactly the author's situation: nothing
        // can be visited. The missing start scene itself will be a lint
        // issue (`LintKind.MissingStartScene`, Ф1.6.2), not a reachability
        // answer.
        if (start in compiled.scenesById) {
            reachable.add(start)
            queue.addLast(start)
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val scene = compiled.scenesById[current] ?: continue
            for (choice in scene.choices) {
                // Only follow edges to scenes that actually exist. A
                // `nextScene` pointing to a missing scene is a separate
                // problem (missing-reference), not a reachability one; it
                // will be a lint issue in Ф1.6.2. Keeping missing ids out
                // of `reachable` means UI can safely jump to anything in
                // the set.
                if (choice.nextScene in compiled.scenesById &&
                    reachable.add(choice.nextScene)
                ) {
                    queue.addLast(choice.nextScene)
                }
            }
        }

        val allSceneIds = compiled.scenesById.keys
        val unreachable = (allSceneIds - reachable)
            .sortedBy { it.value }

        val deadEnds = reachable
            .filter { compiled.scenesById[it]?.choices?.isEmpty() == true }
            .sortedBy { it.value }

        return ReachabilityReport(
            reachable = reachable,
            unreachable = unreachable,
            deadEnds = deadEnds,
        )
    }

    /**
     * Returns all semantic issues found in [compiled].
     *
     * Today this is exactly [LintKind.UnreachableScene] for each scene in
     * [ReachabilityReport.unreachable]. Ф1.6.2 will add the existing
     * `StoryValidator` checks (dead ends without `isChapterEnd`, missing
     * `nextScene`, `set_flags` on `ITEM`/`HEALTH`, etc.) as additional
     * [LintKind] variants.
     *
     * **No false positives.** An empty list means the adventure is
     * semantically clean (by the current set of checks). The analyzer
     * never returns `WARNING`s for things the author cannot control.
     */
    public fun lint(compiled: CompiledAdventure): List<LintIssue> {
        val report = reachable(compiled)
        val startId = compiled.adventure.startScene.value
        return report.unreachable.map { sceneId ->
            LintIssue(
                kind = LintKind.UnreachableScene,
                message = "Scene '${sceneId.value}' is not reachable from " +
                        "start scene '$startId'",
                sceneId = sceneId,
                severity = Severity.WARNING,
            )
        }
    }
}