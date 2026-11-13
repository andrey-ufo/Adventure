package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.InitialState
import kotlinx.serialization.Serializable

/**
 * Declaration of a variable used by an adventure.
 *
 * Variables are declared once in [Adventure.variables] and referenced by
 * name from [Condition] and [Effect]. The type checker (Ф2) resolves
 * every reference against this map and reports unknown or mistyped
 * variables.
 *
 * A declaration does **not** carry a default value: defaults are
 * determined by [type] (see [VarType] KDoc for the per-type defaults).
 * Explicit initial values belong to [InitialState], not here.
 *
 * @property name stable, machine-readable variable name (e.g. `"health"`).
 * @property type declared type used by the type checker.
 */
@Serializable
public data class VarDecl(
    public val name: String,
    public val type: VarType,
)