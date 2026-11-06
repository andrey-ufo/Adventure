package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Stable identifier of a scene within an adventure.
 *
 * Wraps a [String] so that `Choice.nextScene` (a scene reference) cannot be
 * accidentally confused with a flag name, a stat name, or any other plain
 * string. Zero runtime cost — `@JvmInline value class` is erased to the
 * underlying `String` on JVM.
 *
 * **Serialized transparently** as the underlying string. `SceneId("start")`
 * appears in JSON as `"start"`, not as `{"value":"start"}`. This is what
 * `kotlinx.serialization` does for `@JvmInline value class` by default and
 * matches the current adventure file format.
 *
 * **Uniqueness** is enforced by the type checker (Ф2), not by the type
 * itself: two scenes could still be constructed with the same `SceneId`.
 *
 * Other IDs (`ChoiceId`, `ChapterId`, `VariableName`) are intentionally
 * plain `String` for now — a `SceneId` is the most frequent source of
 * confusion (`nextScene` vs a flag/stat name), so it gets the strongest
 * typing. Add value classes to the others only if the same confusion
 * materializes there.
 */
@Serializable
@JvmInline
public value class SceneId(public val value: String)