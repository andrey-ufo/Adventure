package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Stable identifier of a [Choice] within a scene.
 *
 * Wraps a [String] so that a choice reference cannot be confused with a
 * scene id ([SceneId]), a flag name, a stat name, or any other plain
 * string. Zero runtime cost — `@JvmInline value class` is erased to the
 * underlying `String` on JVM.
 *
 * **Serialized transparently** as the underlying string (same as
 * [SceneId]): `ChoiceId("c1")` appears in JSON as `"c1"`, not as
 * `{"value":"c1"}`.
 *
 * **Why a separate id at all.** Before Ф1.5 a choice had no id: the reader
 * identified it by index in `scene.choices`. That is fragile: inserting a
 * choice in the editor shifts every later index, and an old save that
 * referenced "the 3rd choice" now points at a different choice. The player
 * returns next day, presses "continue", and lands somewhere they never
 * picked. A stable [ChoiceId] makes saves survive edits.
 *
 * **Uniqueness** is enforced by the analyzer (Ф1.6), not by the type:
 * two `ChoiceId("c1")` are the same value. Duplicate detection within a
 * scene is an analyzer concern.
 *
 * **Generation.** When the JSON does not carry `id` for a choice,
 * `JsonToIr` generates `"<scene_id>_choice_<index>"` — deterministic and
 * stable for a given scene layout. Adding an explicit `id` in JSON
 * overrides the generated one.
 */
@Serializable
@JvmInline
public value class ChoiceId(public val value: String)