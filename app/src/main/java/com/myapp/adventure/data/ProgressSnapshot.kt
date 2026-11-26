package com.myapp.adventure.data

import com.myapp.adventure.engine.StateDelta

/**
 * Ф5.2b: the base state of the operation log.
 *
 * The log (`delta`) changes flags/stats; cursor/flow fields are kept here
 * at the base point, while the current cursor remains in [ProgressData]
 * for backward compatibility. Manual slots can later store the same pair.
 */
data class ProgressSnapshot(
    val currentChapterIndex: Int = 0,
    val currentSceneId: String = "",
    val flags: List<String> = emptyList(),
    val stats: Map<String, Int> = emptyMap(),
    val itemOrder: List<String> = emptyList(),
    val appliedSceneEffects: Set<String> = emptySet(),
    val remainingSteps: Int = 0,
    val isGateActive: Boolean = false,
    val warningsFingerprint: String = "",
    val passedGates: List<String> = emptyList(),
)

/**
 * Gson-friendly wire form of [StateDelta].
 *
 * Engine deltas are a sealed Kotlin hierarchy; Gson cannot deserialize an
 * abstract sealed type by itself. This DTO keeps the on-disk JSON flat and
 * stable. Unknown `type` values are ignored (future compatibility) — the
 * current reader only writes the five known variants.
 */
data class StateDeltaDto(
    val type: String,
    val name: String? = null,
    val value: Int? = null,
    val delta: Int? = null,
)

fun StateDelta.toDto(): StateDeltaDto = when (this) {
    is StateDelta.SetFlag -> StateDeltaDto("set_flag", name = name)
    is StateDelta.RemoveFlag -> StateDeltaDto("remove_flag", name = name)
    is StateDelta.SetStat -> StateDeltaDto("set_stat", name = name, value = value)
    is StateDelta.AddStat -> StateDeltaDto("add_stat", name = name, delta = delta)
    is StateDelta.SubStat -> StateDeltaDto("sub_stat", name = name, delta = delta)
}

fun StateDeltaDto.toDomainOrNull(): StateDelta? = when (type) {
    "set_flag" -> name?.let(StateDelta::SetFlag)
    "remove_flag" -> name?.let(StateDelta::RemoveFlag)
    "set_stat" -> if (name != null && value != null) {
        StateDelta.SetStat(name, value)
    } else {
        null
    }
    "add_stat" -> if (name != null && delta != null) {
        StateDelta.AddStat(name, delta)
    } else {
        null
    }
    "sub_stat" -> if (name != null && delta != null) {
        StateDelta.SubStat(name, delta)
    } else {
        null
    }
    else -> null
}
