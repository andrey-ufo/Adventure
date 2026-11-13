package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What happens when an [Invariant]'s condition fires.
 *
 * **One variant today: [Death].** The player dies, the reader shows
 * the death screen (`Adventure.deathScene` if present, else the
 * built-in default), and the usual death-screen choices (step back /
 * chapter restart / adventure restart) apply — exactly as if
 * `health <= 0` had fired under the pre-Ф3.5 hardcoded rule.
 *
 * **Why a sealed class, not an enum.** A future variant
 * `Custom(name: String)` (Ф12/Ф13: "if `roll(1d6) == 1`, jump to scene
 * `X`") carries data. A sealed class absorbs that without breaking
 * `when (outcome)` callers; an enum would force a parallel map from
 * enum value to data. One variant is fine to start with — the sealed
 * class grows additively.
 *
 * **Why not `Ending` as a second variant today.** "Win the game" is
 * already expressed by `Scene.isEnding = true` — the reader shows the
 * "the end" screen, no death, no rollback. Adding `Outcome.Ending`
 * would create two mechanisms for the same player outcome and make it
 * ambiguous which one wins when both apply. If a future feature needs
 * an invariant that ends the adventure **at the current scene** with
 * a different flavour ("you retire rich"), it will arrive as
 * `Outcome.Ending(sceneId)` — a dedicated jump, not a synonym for
 * `Scene.isEnding`.
 *
 * **Serialized** as `{"type": "death"}` in IR JSON (polymorphic
 * envelope from `kotlinx.serialization`). In the adventure file
 * format (`:app` writes / `JsonToIr` reads) the outcome is a flat
 * string `"outcome": "death"` — see `InvariantDto` in
 * `engine/.../format/dto/Dto.kt`. The two shapes differ on purpose:
 * IR is an internal cache, adventure JSON is what the author sees.
 */
@Serializable
public sealed class Outcome {

    /**
     * The player dies. The reader shows the death screen
     * ([Adventure.deathScene] if present, else the built-in default
     * "💀 Вы погибли."), and the usual recovery options apply
     * (see [DeathScene.allowStepBack] and siblings).
     *
     * Semantically identical to the pre-Ф3.5 hardcoded
     * `health <= 0 => death` rule: when `Invariant(health <= 0,
     * Death)` is present, the behaviour is byte-for-byte the same as
     * before. The advantage: the author can add **more** invariants
     * (`stamina <= 0`, `has_poison && health < 50`, …) without
     * changing the engine.
     */
    @Serializable
    @SerialName("death")
    public data object Death : Outcome()
}