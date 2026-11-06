package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Scene
import com.myapp.adventure.engine.ir.SceneId

/**
 * Pre-compiled form of an [Adventure] for the runtime.
 *
 * **What this is.** The raw [Adventure] from `JsonToIr` is **structural**:
 * chapters, scenes, choices — exactly what is on disk. The runtime needs
 * **fast lookup**: "give me the scene with this id", "which chapter is this
 * scene in". Building those indexes on every `Engine.step` call would be
 * O(n) per step, which becomes a visible stutter on adventures with
 * thousands of scenes. [CompiledAdventure] builds the indexes **once**
 * and then answers all queries in O(1).
 *
 * **Where the indexes live.** Not on [Adventure] itself. The IR is the
 * on-disk shape; adding a derived map there would (a) duplicate data
 * inside the golden, (b) let a `@Serializable` `Map` drift from the
 * `chapters` list. Separation of concerns: `Adventure` = data,
 * `CompiledAdventure` = runtime cache. `GoldenIrTest` stays clean.
 *
 * **What this is NOT.** `CompiledAdventure` does not validate the
 * adventure — that is `Analyzer` (Ф1.6). It only builds indexes; the
 * only failure it can produce is **duplicate `SceneId`** (because two
 * scenes with the same id cannot both live in a `Map`). All other
 * semantic checks — `nextScene` exists, `has_X` on numeric variables,
 * reachability — belong to `Analyzer`, not here.
 *
 * **Immutable.** Once compiled, the maps never change. `Engine.step` is
 * pure: it takes `CompiledAdventure` + `PlayerState`, returns
 * `StepResult`. This makes `Engine.step` trivially thread-safe and
 * trivial to unit-test.
 *
 * @property adventure the source [Adventure]. Kept so callers (reader,
 *   analyzer, editor preview) can reach chapter titles, tags, death
 *   scene, etc., without a separate reference.
 * @property scenesById scene id → scene. O(1) lookup for transitions.
 *   Every scene in [Adventure.chapters] is here.
 * @property chapterIndexBySceneId scene id → index in
 *   [Adventure.chapters]. Used by the reader to track "current chapter"
 *   and by the analyzer to reason about chapter boundaries.
 */
public data class CompiledAdventure(
    public val adventure: Adventure,
    public val scenesById: Map<SceneId, Scene>,
    public val chapterIndexBySceneId: Map<SceneId, Int>,
)