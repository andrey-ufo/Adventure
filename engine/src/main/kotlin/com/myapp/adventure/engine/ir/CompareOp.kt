package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Comparison operator in [Condition.Compare].
 *
 * `@SerialName` values are short (two letters) to keep the JSON compact;
 * they match the operator symbols (`<`, `<=`, `>`, `>=`, `==`, `!=`).
 */
@Serializable
public enum class CompareOp {
    @SerialName("lt")
    LT,

    @SerialName("le")
    LE,

    @SerialName("gt")
    GT,

    @SerialName("ge")
    GE,

    @SerialName("eq")
    EQ,

    @SerialName("ne")
    NE,
}