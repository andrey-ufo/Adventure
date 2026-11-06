package com.myapp.adventure.engine.analyze

import com.myapp.adventure.engine.ir.SceneId

/**
 * Result of [Analyzer.reachable].
 *
 * Three disjoint (well, almost — see below) sets, each answering a
 * different author question:
 *
 * - **[reachable]** — "which scenes can the player actually visit?" This
 *   is the graph reachable from
 *   [com.myapp.adventure.engine.ir.Adventure.startScene] via
 *   `choice.nextScene` links.
 * - **[unreachable]** — "which scenes are written but never seen?" The
 *   author almost certainly forgot to wire them up. Empty is good.
 * - **[deadEnds]** — "which scenes end the adventure?" Scenes reachable
 *   from the start with no `choices`. **These are not problems** — an
 *   adventure has to end somewhere — but the author wants a list, because
 *   an *unintended* dead end (a scene they thought had choices) is a bug.
 *
 * **Overlap.** [reachable] and [deadEnds] are **not** disjoint: every dead
 * end is reachable (by definition — an unreachable dead end is just
 * unreachable, and we do not list it twice). [unreachable] is disjoint
 * from the other two by construction.
 *
 * **Missing start scene.** If
 * [com.myapp.adventure.engine.ir.Adventure.startScene] is not in
 * [com.myapp.adventure.engine.CompiledAdventure.scenesById] (a bug in
 * the file, not caught by `JsonToIr` because the analyzer is where
 * reference-integrity lives — see Ф1.6.2), every scene is reported as
 * [unreachable] and [reachable] is empty. The analyzer does **not**
 * throw; a bug is a bug, and the reader will show an error screen if the
 * player opens the file anyway.
 *
 * @property reachable scenes the player can visit from the start scene.
 * @property unreachable scenes not reachable from the start scene, sorted
 *   by id for stable output.
 * @property deadEnds reachable scenes with no choices, sorted by id.
 */
public data class ReachabilityReport(
    public val reachable: Set<SceneId>,
    public val unreachable: List<SceneId>,
    public val deadEnds: List<SceneId>,
)