package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable

/**
 * A group of [Scene]s — **pure narrative grouping**.
 *
 * A chapter has no mechanical meaning of its own: it does not gate
 * anything, it does not impose a step requirement, it does not block
 * transitions. Its only jobs are:
 * 1. A human-readable [title] for the reader's chapter HUD and for the
 *    editor's map view.
 * 2. A stable [id] for logging and for `Analyzer` reports.
 * 3. Grouping scenes into a list for the UI.
 *
 * **Where gates live now.** Step gates moved from chapters to **scenes**
 * in Ф1.5.4: see [Scene.gate] and [Gate]. This means:
 * - A chapter may have zero, one, or several gated scenes.
 * - A gate may sit anywhere in a chapter (first, middle, last scene).
 * - A choice may lead to a scene in **another chapter** — chapters are
 *   not barriers.
 * - An adventure may have no gates at all: chapters are then just titles
 *   over a flat list of scenes.
 *
 * **Why the old `requiredSteps` field was removed.** It conflated two
 * unrelated concepts ("this is a boundary between narrative groups" and
 * "the player must walk N steps to proceed"). Authors asked for gates
 * in the middle of a chapter, or for chapter boundaries without gates,
 * or for multiple gates within one chapter — all impossible with the
 * old model. See the Progress log entry for Ф1.5.4 for the analysis.
 *
 * **Migration from v1 files** (which used `chapter.required_steps`):
 * `JsonToIr` moves `chapter[i].required_steps = N > 0` onto the **last
 * scene of chapter `i-1`** as `Gate(N)` — the same mechanical effect,
 * expressed on the scene that the player must leave to enter chapter
 * `i`. The first chapter's `required_steps` is dropped (it was always 0).
 *
 * **Chapters are ordered.** The order in [Adventure.chapters] determines
 * the reading sequence and the grouping in the UI. There is no explicit
 * "next chapter" reference; a scene transition to the first scene of a
 * later chapter is expressed via [Choice.nextScene].
 *
 * @property id stable chapter identifier (e.g. `"chapter_1"`).
 * @property title human-readable chapter title.
 * @property scenes scenes that belong to this chapter, in reading order.
 *   Must be non-empty; enforced by the parser (Ф1.3).
 */
@Serializable
public data class Chapter(
    public val id: String,
    public val title: String,
    public val scenes: List<Scene>,
)