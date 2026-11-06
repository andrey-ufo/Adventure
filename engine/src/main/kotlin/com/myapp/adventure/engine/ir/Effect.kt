package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single state-modifying effect attached to a [Scene] or [Choice].
 *
 * In `:app/data/Adventure.kt` effects are three parallel nullable fields
 * (`set_flags`, `remove_flags`, `modify_stats`). In the IR each individual
 * change is one [Effect], and the parser (Ф1.3) turns the three fields into
 * a flat `List<Effect>`. Runtime applies effects in declaration order
 * (Ф1.5), so reordering affects semantics — that is intentional and
 * matches the current reader behaviour.
 *
 * Effects are **applied once** when a scene is first entered or when a
 * choice is picked. See `AdventureReaderFragment.applyEffects` for the
 * reference semantics.
 */
@Serializable
public sealed class Effect {

    /**
     * `set_flags` entry: add [name] to the player's flag set (if not
     * already present). Idempotent.
     *
     * Only meaningful for [TagCategory.JOURNAL] tags; the type checker (Ф2)
     * will reject `set_flags` on `ITEM`/`HEALTH` tags.
     */
    @Serializable
    @SerialName("set_flag")
    public data class SetFlag(
        public val name: String,
    ) : Effect()

    /**
     * `remove_flags` entry: remove [name] from the player's flag set
     * (if present). Idempotent.
     *
     * Only meaningful for [TagCategory.JOURNAL] tags; `ITEM`/`HEALTH`
     * tags are removed by setting their value to zero via [ModifyStat].
     */
    @Serializable
    @SerialName("remove_flag")
    public data class RemoveFlag(
        public val name: String,
    ) : Effect()

    /**
     * `modify_stats` entry: add [delta] to the current value of [name].
     *
     * **Delta semantics, not absolute.** If the current value is `10` and
     * `delta` is `-3`, the new value is `7`. This matches the current
     * `AdventureReaderFragment.applyEffects`.
     *
     * For [TagCategory.HEALTH] and [TagCategory.ITEM] variables the result
     * is clamped to `>= 0` (current behaviour). For [TagCategory.JOURNAL]
     * numeric values there is no clamping. Clamping policy lives in the
     * runtime (Ф1.5), not here — [Effect] is a pure data description.
     */
    @Serializable
    @SerialName("modify_stat")
    public data class ModifyStat(
        public val name: String,
        public val delta: Int,
    ) : Effect()
}