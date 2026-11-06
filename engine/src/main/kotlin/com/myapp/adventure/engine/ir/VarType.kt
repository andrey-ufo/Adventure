package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declared type of a variable used in an adventure.
 *
 * The type is what the type checker (Ф2) uses to validate that references
 * in [Condition] and [Effect] are consistent with each other. For example,
 * a `has_sword` condition requires `sword` to be of type [Bool] or
 * [ItemCount]; a `health >= 50` comparison requires `health` to be
 * [Integer] or [IntRange].
 *
 * **Default values** (used when a variable is not present in
 * [InitialState], e.g. before the first scene that sets it):
 *
 * - [Bool] → `false`
 * - [Integer] → `0`
 * - [IntRange] → [IntRange.min] (the lowest allowed value)
 * - [ItemCount] → `0`
 * - [ItemList] → `emptyList()`
 */
@Serializable
public sealed class VarType {

    /** Boolean flag. `true`/`false`. */
    @Serializable
    @SerialName("bool")
    public data object Bool : VarType()

    /**
     * Whole number without bounds. Used for stats like stamina or mana
     * that are allowed to go negative.
     */
    @Serializable
    @SerialName("int")
    public data object Integer : VarType()

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