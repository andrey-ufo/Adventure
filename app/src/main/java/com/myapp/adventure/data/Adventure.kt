package com.myapp.adventure.data

import com.google.gson.annotations.SerializedName

data class Adventure(
    /**
     * b2: adventure-format schema version, written as `"version": 5`.
     * `null` on files that predate v3 (no `version` field) — Gson
     * omits it, and `JsonToIr.parse` treats it as v1 (identity
     * migration). The editor writes `5` from `buildAdventure()`
     * because it emits v5-shaped JSON (`scene.gate`,
     * `gate_repeatable`, `is_ending`, `start_scene`).
     */
    @SerializedName("version")
    val version: Int? = null,
    val title: String,
    val author: String = "Неизвестен",
    val description: String = "",
    @SerializedName("initial_state")
    val initialState: PlayerState? = null,
    /**
     * Ф2.7: объявления переменных (`{"health": {"type": "int_range",
     * "min": 0, "max": 100}}`). Nullable, чтобы Gson **опускал** поле
     * в JSON, если автор не объявил ни одной переменной — не мусорить
     * `"variables": {}` в каждом файле.
     *
     * **Формат (A1 из Ф2.7):** map `name → {type, min?, max?}`.
     * `type` — строка (`"bool"`, `"int"`, `"int_min"`, `"int_range"`,
     * `"item_count"`, `"item_list"`), совпадает с `@SerialName` в
     * `:engine/ir/types/VarType`. `min`/`max` — только для `int_min`
     * (только `min`) и `int_range` (оба), `null` для остальных.
     *
     * **Парсинг на :engine-стороне** — Ф2.8 (`JsonToIr` → `AdventureDto`
     * → `VarDecl`). Сейчас `:engine` видит `variables: emptyMap()` и
     * работает в fallback-режиме `TypeChecker` (см. Ф2.2/C4).
     */
    @SerializedName("variables")
    val variables: Map<String, VariableDto>? = null,
    val tags: List<TagDefinition>? = null,
    @SerializedName("death_scene")
    val deathScene: DeathScene? = null,
    /**
     * b2: explicit start scene id (`"start_scene"`). Required from
     * v3; `JsonToIr.parse` rejects v5 files without it. The editor
     * writes it from `buildAdventure()` (first scene of the first
     * chapter, or the first scene overall — the editor has no
     * explicit "start" affordance yet).
     */
    @SerializedName("start_scene")
    val startScene: String? = null,
    val chapters: List<Chapter>? = null
)

data class DeathScene(
    val id: String = "death_scene",
    val text: String = "💀 Вы погибли.",
    @SerializedName("allow_step_back")
    val allowStepBack: Boolean = true,
    @SerializedName("allow_chapter_restart")
    val allowChapterRestart: Boolean = true,
    @SerializedName("allow_adventure_restart")
    val allowAdventureRestart: Boolean = true
)

data class Chapter(
    val id: String,
    val title: String = "",
    /**
     * **Legacy (v1) chapter-level gate.** In v1 files this field was
     * `"required_steps": N` and meant "walk N steps to enter this
     * chapter"; `JsonToIr.parse` migrates it to `Gate(N)` on the last
     * scene of the previous chapter. Since b2 the editor emits
     * per-scene `gate` instead and never writes `required_steps`.
     *
     * **Nullable**, not `Int = 0`: Gson omits `null` fields on
     * serialization, so a freshly-written v5 file does not carry
     * `"required_steps": 0` on every chapter. Old v1 files still
     * load correctly — `loadFromUri` reads `requiredSteps ?: 0` for
     * the v1 → v5 migration.
     */
    @SerializedName("required_steps")
    val requiredSteps: Int? = null,
    val scenes: List<Scene>? = null
)

data class Scene(
    val id: String,
    val text: String,
    @SerializedName("set_flags")
    val setFlags: List<String>? = null,
    @SerializedName("remove_flags")
    val removeFlags: List<String>? = null,
    @SerializedName("modify_stats")
    val modifyStats: Map<String, Int>? = null,
    val choices: List<Choice>? = null,
    /**
     * Ф1.7.5: `"is_ending"` from the adventure JSON. `null` on files
     * that predate v4 (or on `:app/data/Adventure` produced by code
     * that does not yet write the field — sections 2–3 add that).
     *
     * **Nullable, not `Boolean = false`.** Gson does not honour
     * Kotlin defaults; a non-nullable `Boolean` would silently be
     * `false` for old files, indistinguishable from "explicitly not
     * an ending". `null` keeps the "field absent" semantics clear.
     * Existing call sites read it as `scene.isEnding ?: false`.
     */
    @SerializedName("is_ending")
    val isEnding: Boolean? = null,
    /**
     * b2: per-scene step gate (`"gate": N`). `null` = no gate. The
     * editor writes it from `EditorScene.gateRequiredSteps > 0`;
     * `JsonToIr.parse` reads it as `Gate(requiredSteps = N,
     * repeatable = …)` in the IR.
     *
     * **Distinct from `Chapter.required_steps`** (legacy v1): a
     * chapter-level gate is migrated to `Gate(N)` on the *last scene
     * of the previous chapter* by `JsonToIr.parse`. The editor no
     * longer emits chapter-level gates — only per-scene ones.
     */
    @SerializedName("gate")
    val gate: Int? = null,
    /**
     * b2: whether the gate re-engages on every visit
     * (`"gate_repeatable": true`). Only meaningful when [gate] is
     * non-null. `null` = "absent" → `JsonToIr` defaults to `false`
     * ("once passed, stays passed in this playthrough"). Emitted by
     * the editor only when the author ticks "повторять" AND a gate
     * is set — omitting the field when `false` keeps the JSON clean
     * (matches the `is_ending` pattern).
     */
    @SerializedName("gate_repeatable")
    val gateRepeatable: Boolean? = null
)

data class Choice(
    val text: String,
    @SerializedName("next_scene")
    val nextScene: String,
    val condition: String? = null,
    @SerializedName("set_flags")
    val setFlags: List<String>? = null,
    @SerializedName("remove_flags")
    val removeFlags: List<String>? = null,
    @SerializedName("modify_stats")
    val modifyStats: Map<String, Int>? = null
)

data class PlayerState(
    val flags: List<String>? = null,
    val stats: Map<String, Int>? = null
)

/**
 * Ф2.7 — DTO переменной для Gson-save.
 *
 * `type` — одна из строк `@SerialName` в
 * `com.myapp.adventure.engine.ir.types.VarType`:
 * `"bool"`, `"int"`, `"int_min"`, `"int_range"`, `"item_count"`,
 * `"item_list"`. `min`/`max` — только для `int_min` / `int_range`,
 * `null` для остальных (Gson опускает `null`-поля при
 * `setPrettyPrinting()` только если настроено `serializeNulls = false`,
 * что по умолчанию true — то есть `null` **пишутся** как `"min": null`.
 * Для чистоты JSON в `saveAdventure()` лучше собирать `VariableDto`
 * через `when` с явным отбрасыванием `min`/`max` для Bool/ItemCount/…
 * — либо настроить `GsonBuilder().serializeNulls(false)`, но это
 * затронет все поля `Adventure`. Оставлено как есть: `"min": null`
 * в JSON валидно и не мешает парсингу Ф2.8).
 */
data class VariableDto(
    val type: String,
    val min: Int? = null,
    val max: Int? = null,
)