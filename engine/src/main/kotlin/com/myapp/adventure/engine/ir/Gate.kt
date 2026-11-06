package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A step gate on a [Scene]: the player must walk at least [requiredSteps]
 * real-world steps **after entering this scene** before any exit is
 * allowed (picking a choice, or auto-advancing to the next scene/chapter).
 *
 * **Where gates live.** A gate belongs to the **scene it gates the exit
 * of**, not to the chapter it opens. This decouples the two concepts that
 * were previously glued together as `Chapter.requiredSteps`:
 *
 * - A **chapter** is pure narrative grouping (a title, a list of scenes).
 *   It carries no mechanical meaning.
 * - A **gate** is a mechanical requirement on a single scene. It can sit
 *   anywhere: on the first scene of a chapter, in the middle, on the last
 *   scene, or absent entirely.
 *
 * **What the author can now express that was previously impossible:**
 * - A gate in the middle of a chapter.
 * - A chapter boundary without a gate (just narrative grouping).
 * - A gate without a chapter boundary.
 * - Multiple gates in a single chapter (several scenes with `gate != null`).
 * - A choice whose `nextScene` points to a scene in **another chapter**
 *   (the target scene may itself be gated or not).
 *
 * **Serialized as a bare integer** (`"gate": 5`), not as an object.
 * `Gate(5)` on the JVM is erased to `Int`, and `@Serializable` on a
 * `@JvmInline value class` of a primitive serializes transparently.
 *
 * **Positive by construction.** `Gate(0)` is meaningless: "walk at least
 * 0 steps before exiting" = "no gate". The parser (`JsonToIr`) rejects
 * `gate <= 0` as a structural error; `Gate(0)` should never be
 * constructed. `Gate(null)` is not possible — "no gate" is
 * `Scene.gate == null`.
 *
 * **Not checked by `Engine.step`.** Step counting is a reader concern
 * (a foreground service in `:app`): the engine does not know how many
 * steps the player has walked. The reader checks the gate, shows the
 * waiting UI, and only calls `Engine.step` when `requiredSteps` have
 * been accumulated. This keeps the engine free of wall-clock and
 * sensor dependencies.
 *
 * @property requiredSteps number of steps to accumulate before the
 *   scene becomes exitable. Must be strictly positive.
 */
@Serializable
@JvmInline
public value class Gate(public val requiredSteps: Int)