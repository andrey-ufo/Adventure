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
 *
 * **Added in Ф3.1:** [Set] — set a stat to an absolute value. Not
 * produced by the parser today (the adventure JSON format has no field
 * for absolute set; `"modify_stats"` is delta-only). Runtime
 * (`EffectsApplier`) and type checker (`TypeChecker`) already handle it,
 * so it can appear in hand-built IR (tests, future tooling) without any
 * "unsupported effect" branch. Ф8 (`IrToJson`) will decide whether to
 * add a JSON field for it.
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
     * tags are removed by setting their value to zero via [ModifyStat]
     * or [Set].
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

    /**
     * Set [name] to [value] (absolute, not delta).
     *
     * **Difference from [ModifyStat].** `ModifyStat(name, -5)` means
     * "subtract 5"; `Set(name, 0)` means "reset to 0 regardless of the
     * current value". Authors use `Set` when they do not (or cannot)
     * know the current value — e.g. "health is restored to 100" in a
     * shrine scene, regardless of how wounded the player is.
     *
     * **Not produced by the parser today.** The adventure JSON format
     * (`"modify_stats": {"name": delta}`) does not have a syntax for
     * absolute set. This node exists for two reasons:
     *
     * 1. **Completeness of the effect model** — `Set` is a distinct
     *    semantic operation, and modelling it now (with runtime + type
     *    checker support) means Ф8 (`IrToJson`) can add a JSON field
     *    for it without touching every consumer of `Effect`.
     * 2. **Hand-built IR and tests** — `EngineStepTest`, the future
     *    editor "Suggest effect" tooling (Ф9.2), and any tool that
     *    constructs `Adventure` programmatically can emit `Set` and
     *    get the correct behaviour without an "unsupported effect"
     *    branch.
     *
     * **Runtime clamp.** Like [ModifyStat], the runtime clamps the
     * result to `>= 0` today (same policy — see `EffectsApplier` KDoc).
     * Type-aware clamping lands in Ф5.
     *
     * **Type checking.** Requires a numeric variable — `Bool` and
     * `ItemList` are rejected with [com.myapp.adventure.engine.ir.types.TypeError.ModifyStatOnNonNumeric]
     * (same variant as [ModifyStat] — the error is "numeric operation on
     * a non-numeric variable", the distinction between `Set` and
     * `ModifyStat` is not the point).
     */
    @Serializable
    @SerialName("set")
    public data class Set(
        public val name: String,
        public val value: Int,
    ) : Effect()
}