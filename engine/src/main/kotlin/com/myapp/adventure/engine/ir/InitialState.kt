package com.myapp.adventure.engine.ir

import kotlinx.serialization.Serializable

/**
 * Player state at the very start of an adventure, before the first scene
 * is entered.
 *
 * Corresponds to `:app/data/Adventure.kt`'s `PlayerState`, with one
 * difference: [flags] and [stats] are non-null with empty defaults, not
 * nullable. Nullable collections force every consumer to write
 * `?: emptyList()` at each call site; the IR instead says "no flags" as
 * `emptyList()`. The parser (Ф1.3) maps JSON's absent `null` / missing
 * fields to those defaults.
 *
 * **Precedence against [VarDecl]:** if a variable is declared in
 * [Adventure.variables] but not listed in [flags] / [stats], its value is
 * the type's default (see [VarType] KDoc). If listed, the value here wins.
 * This is the same as the current reader's `initNormalState()` behaviour.
 *
 * **Type consistency:** every flag / stat name here must be declared in
 * [Adventure.variables] — enforced by the type checker in Ф2.
 *
 * @property flags flags the player starts with.
 * @property stats numeric values the player starts with.
 */
@Serializable
public data class InitialState(
    public val flags: List<String> = emptyList(),
    public val stats: Map<String, Int> = emptyMap(),
)