package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declared type of a variable used in an adventure.
 *
 * The type is what the type checker (Ф2) uses to validate that references
 * in [Condition] and [Effect] are consistent with each other. For example,
 * a `has_sword` condition requires `sword` to be of type [Bool] or
 * [ItemCount]; a `health >= 50` comparison requires `health` to be
 * [Integer], [IntMin], or [IntRange].
 *
 * **Default values** (used when a variable is not present in
 * [InitialState], e.g. before the first scene that sets it):
 *
 * - [Bool] → `false`
 * - [Integer] → `0`
 * - [IntMin] → [IntMin.min] (the lowest allowed value)
 * - [IntRange] → [IntRange.min] (the lowest allowed value)
 * - [ItemCount] → `0`
 * - [ItemList] → `emptyList()`
 *
 * **Why three "numeric" variants.** They encode how the type checker and
 * the runtime should clamp / bound the value:
 *
 * - [Integer] — unbounded. Used for quantities that are not resource-like
 *   (score, computed totals) and for variables whose boundaries are not
 *   the author's concern. No clamp applied by the runtime.
 * - [IntMin] — lower bound only. Used for resources that cannot go
 *   negative but have no natural upper bound (`gold`, some turn counters
 *   before a limit is imposed). Clamp: `>= min`.
 * - [IntRange] — both bounds. Used for resources with an inherent cap
 *   (`health 0..100`, `skill 1..12`, `endurance 20..30`) and for bounded
 *   counters (`turns 0..30`). Clamp: `min..max`.
 *
 * **Why not `IntRange(min: Int, max: Int?)`.** Encoding "no upper bound"
 * as `null` would mix two different semantics in one variant and make the
 * "no lower bound, no upper bound" case ambiguous (both `null` — which is
 * what [Integer] already is). Three explicit variants make every case
 * readable at the JSON level: `"int"`, `"int_min": {min}`,
 * `"int_range": {min, max}`.
 *
 * **Authoring hint.** Ask yourself two questions about the variable:
 * "can it go below zero?" and "can it ever exceed some N?". No to both →
 * [Integer]; yes to the first, no to the second → [IntMin]; yes to both
 * → [IntRange].
 */
@Serializable
public sealed class VarType {

    /** Boolean flag. `true`/`false`. */
    @Serializable
    @SerialName("bool")
    public data object Bool : VarType()

    /**
     * Whole number without bounds. Used for stats like score, totals, or
     * quantities whose boundaries are not the author's concern.
     */
    @Serializable
    @SerialName("int")
    public data object Integer : VarType()

    /**
     * Whole number with a lower bound only ("at least [min], no upper
     * bound").
     *
     * Used for resources that cannot go negative but have no natural
     * upper bound: gold, inventory-like counters the author does not
     * want to cap, turn counters before a limit is imposed.
     *
     * @property min lowest allowed value (inclusive).
     */
    @Serializable
    @SerialName("int_min")
    public data class IntMin(
        public val min: Int,
    ) : VarType()

    /**
     * Whole number clamped to `[min, max]`. Used for stats like health
     * that must stay within a sensible range.
     *
     * @property min lowest allowed value (inclusive).
     * @property max highest allowed value (inclusive).
     */
    @Serializable
    @SerialName("int_range")
    public data class IntRange(
        public val min: Int,
        public val max: Int,
    ) : VarType()

    /**
     * Count of a stackable item (e.g. "3 potions"). Non-negative
     * [Integer] by semantics; kept as a separate type so the type checker
     * can distinguish "how many" from "any number".
     */
    @Serializable
    @SerialName("item_count")
    public data object ItemCount : VarType()

    /**
     * Unordered list of item identifiers. Used for inventory-like
     * variables when the order does not matter.
     *
     * Not to be confused with [ItemCount], which is a single number.
     */
    @Serializable
    @SerialName("item_list")
    public data object ItemList : VarType()
}