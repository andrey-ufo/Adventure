package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.TagCategory

/**
 * Semantic type checking: verifies that every name referenced from a
 * [Condition] or an [Effect] is declared, and that the type of the
 * reference matches the operation.
 *
 * **Where this sits in the pipeline** (per [com.myapp.adventure.engine]
 * architecture, invariant I3 — parser vs compiler vs analyzer):
 *
 * - **Parser** (`JsonToIr.parse`): "can I turn this into IR?" — JSON
 *   syntax, condition syntax, required fields.
 * - **TypeChecker** (this class): "does every reference make sense
 *   against the declared variables?" — unknown names, wrong type, wrong
 *   effect on the wrong type.
 * - **Compiler** (`Engine.compile`): "can I build runtime indexes?" —
 *   duplicate scene ids, missing start scene, dangling `nextScene`.
 * - **Analyzer** (`Analyzer.lint`): "is the IR coherent?" — reachability,
 *   dead ends, unestablished items.
 *
 * `TypeChecker` sits between the parser and the compiler, but it is
 * **not wired into `JsonToIr.parse` yet** — that is Ф2.3. Ф2.2 (this
 * step) only defines the check itself, so that the pipeline change in
 * Ф2.3 is a one-line addition (`parse → TypeChecker.check → compile`).
 *
 * **Two modes** (developed from the C4 decision, 2026-11-12):
 *
 * 1. **Strict** — `Adventure.variables` is non-empty. Every name
 *    referenced from a condition or effect must appear in
 *    `Adventure.variables`. `tags` and `initialState` are ignored for
 *    type inference; they are UI data, not the source of truth. This is
 *    the mode that lands in Ф2.8 once `JsonToIr` starts populating
 *    `variables` from JSON.
 * 2. **Fallback** — `Adventure.variables` is empty (current state of
 *    every existing file, since the JSON field is not parsed yet). The
 *    checker **infers** a provisional type table from:
 *    - `Adventure.tags`: `HEALTH` → `IntMin(0)`, `ITEM` → `ItemCount`,
 *      `JOURNAL` → `Bool` (or `IntMin(0)` if the name appears in
 *      `initialState.stats` instead of `flags`);
 *    - `Adventure.initialState.flags`: any flag not in `tags` is `Bool`;
 *    - `Adventure.initialState.stats`: any stat not in `tags` is `IntMin(0)`.
 *
 *    If even that table is empty (no tags, no initial state), the
 *    checker returns an empty list — there is nothing to check against,
 *    and pretending there is would produce false positives. The "you
 *    have not declared variables" signal is a **UI concern** and will
 *    land in the editor warning panel (Ф9.2), not here.
 *
 *    Fallback mode is deliberately **not weaker** than strict mode: the
 *    inferred table is used exactly as if it were `variables`. So a typo
 *    (`has_swrod` when `sword` is a tag) still produces
 *    [TypeError.UnknownVariable], which is the DoD requirement
 *    ("опечатка `has_swrod` → ошибка").
 *
 * **What is NOT checked** (deferred):
 * - `IntRange(min > max)` — a range that can never be satisfied. That
 *   is reachability / dead-condition analysis (Ф3.6
 *   `Analyzer.deadConditions`), not type checking.
 * - `Compare` with a literal outside the declared range
 *   (`skill > 20` on `IntRange(1, 12)`) — same, Ф3.6.
 * - `InitialState.stats` values that exceed `IntRange.max` — clamping
 *   policy (Ф2.8) not yet type-aware; the runtime currently clamps all
 *   `ModifyStat` to `>= 0` regardless of declared type.
 * - Cross-field constraints between `TagCategory` and `VarType`
 *   (`HEALTH` + `Bool` is semantically nonsense but mechanically
 *   possible). Until Ф2.8 populates `variables`, this cannot be checked
 *   without false positives — an author who declares a `JOURNAL` tag and
 *   uses it as a flag is fine.
 *
 * **Deterministic output.** [check] returns errors sorted by
 * `(sceneId == null, sceneId.value, message)` — same convention as
 * `Analyzer.lint`. Repeated runs on the same input produce identical
 * output.
 *
 * **Pure.** No I/O, no logging, no mutation of [adventure]. Takes the IR
 * (which by definition is immutable) and returns a fresh list.
 */
public object TypeChecker {

    /**
     * Checks all condition and effect references in [adventure].
     *
     * @return a list of [TypeError]s, empty if the adventure type-checks
     *   cleanly (or if there is nothing to check against — see the class
     *   KDoc, "fallback" mode).
     */
    public fun check(adventure: Adventure): List<TypeError> {
        val typeTable = buildTypeTable(adventure)
        if (typeTable.isEmpty()) return emptyList()

        val errors = mutableListOf<TypeError>()

        // 1. InitialState — must match the declared type (or inferred, in
        //    fallback mode). These are the only errors without a sceneId;
        //    they are reported first by the sort at the end.
        for (flag in adventure.initialState.flags) {
            val type = typeTable[flag] ?: continue
            if (type != VarType.Bool) {
                errors += TypeError.InitialFlagOnNonBool(flag, type)
            }
        }
        for (stat in adventure.initialState.stats.keys) {
            val type = typeTable[stat] ?: continue
            if (!type.isNumeric()) {
                errors += TypeError.InitialStatOnNonNumeric(stat, type)
            }
        }

        // 2. Scenes: scene effects, then each choice's condition and effects.
        //    Order within a scene follows the source (scene first, then
        //    choices), so that when the author opens the editor, the
        //    first reported error is the one "highest" in the visual
        //    hierarchy. Stable-sorted at the end.
        for (chapter in adventure.chapters) {
            for (scene in chapter.scenes) {
                checkEffects(scene.effects, scene.id, typeTable, errors)
                for (choice in scene.choices) {
                    choice.condition?.let {
                        checkCondition(it, scene.id, typeTable, errors)
                    }
                    checkEffects(choice.effects, scene.id, typeTable, errors)
                }
            }
        }

        return errors.sortedWith(
            // `sceneId != null` (initial-state errors have sceneId == null)
            // → false → 0 → sorted first. Scene errors → true → 1 →
            // sorted after. This matches "show the highest-level problems
            // first" in the editor error panel (Ф9.2): initial-state errors
            // are not tied to a specific scene, so they appear above the
            // per-scene ones.
            compareBy<TypeError> { it.sceneId != null }
                .thenBy { it.sceneId?.value ?: "" }
                .thenBy { it.message },
        )
    }

    // ---------- Type table construction ----------

    /**
     * Builds the name → type table used for checking.
     *
     * In **strict mode** (`variables` is non-empty), the table is exactly
     * `variables` — `tags` and `initialState` are ignored, because the
     * author declared the types explicitly.
     *
     * In **fallback mode** (`variables` is empty), the table is inferred
     * from `tags` + `initialState`. See class KDoc for the mapping.
     */
    private fun buildTypeTable(adventure: Adventure): Map<String, VarType> {
        if (adventure.variables.isNotEmpty()) {
            return adventure.variables.mapValues { it.value.type }
        }

        val table = mutableMapOf<String, VarType>()

        // 1. Tags: category determines the type, initial state refines
        //    JOURNAL (flag vs numeric).
        for (tag in adventure.tags) {
            val type = when (tag.category) {
                TagCategory.HEALTH -> VarType.IntMin(0)
                TagCategory.ITEM -> VarType.ItemCount
                TagCategory.JOURNAL -> when {
                    // A JOURNAL tag that appears in `initialState.flags` is
                    // a boolean marker (visible journal entry). One that
                    // appears in `initialState.stats` is a numeric journal
                    // value (counters, quest progress). If it is not in
                    // either, default to Bool — the most common case for
                    // journals.
                    tag.name in adventure.initialState.flags -> VarType.Bool
                    tag.name in adventure.initialState.stats -> VarType.IntMin(0)
                    else -> VarType.Bool
                }
            }
            table[tag.name] = type
        }

        // 2. Initial state: any name not covered by a tag is inferred from
        //    the side of `initialState` it appears in.
        for (flag in adventure.initialState.flags) {
            table.putIfAbsent(flag, VarType.Bool)
        }
        for (stat in adventure.initialState.stats.keys) {
            table.putIfAbsent(stat, VarType.IntMin(0))
        }

        return table
    }

    // ---------- Condition checking ----------

    private fun checkCondition(
        condition: Condition,
        sceneId: SceneId,
        typeTable: Map<String, VarType>,
        errors: MutableList<TypeError>,
    ) {
        when (condition) {
            is Condition.Literal -> {
                // Ф3.1: `Literal` is Bool by construction. No variable
                // references, nothing to check against the type table.
                // Not produced by the parser (see `Condition.Literal`
                // KDoc) — appears only in hand-built IR.
            }
            is Condition.VarRef -> {
                // Ф3.1: `VarRef` was added to the AST as a forward-looking
                // node for Ф3.2 (`A && B` where `B` is a bare variable).
                // The parser does not produce it yet — a bare `"x"` in
                // JSON is still a `ConditionError` (see
                // `ConditionParser`). When Ф3.2 extends the grammar, this
                // branch must grow two checks:
                //   1. `TypeError.UnknownVariable` if `name` is not in
                //      `typeTable`.
                //   2. `TypeError.VarRefOnNonBool` if `name`'s type is
                //      not `Bool` (a new variant; `HasOnNonBool`'s
                //      message references `has_X` syntax specifically,
                //      which a bare `VarRef` does not have).
                // Until then: no-op, and safe by omission.
            }
            is Condition.Has -> {
                val type = typeTable[condition.name]
                if (type == null) {
                    errors += TypeError.UnknownVariable(condition.name, sceneId)
                } else if (type != VarType.Bool) {
                    errors += TypeError.HasOnNonBool(condition.name, type, sceneId)
                }
            }
            // Ф2.4 note: `Not` / `And` / `Or` recurse into their operands.
            // The parser does not produce these nodes today — `!X`, `A && B`,
            // `A || B` are rejected at parse time as reserved characters
            // (Ф1.3.3.2). They will start appearing once Ф3.2 (N7.base)
            // extends `ConditionParser` with the logical operators.
            //
            // At that point **two things must be added here**:
            //   1. `InvalidBooleanOperand(name, actualType)` — a check that
            //      each operand of `And` / `Or` / `Not` is Bool. Today the
            //      only condition nodes the parser can produce are
            //      `Has` / `Compare` / `InRange`, all three of which return
            //      Bool by AST shape — so no operand-type check is needed
            //      yet.
            //   2. A `Condition.VarRef(name)` branch — Ф3.2 will add a bare
            //      variable reference to the AST, at which point `A && B`
            //      where `B` is `VarRef("health")` becomes possible and
            //      **must** be rejected unless `health` is Bool.
            //
            // Do not add either pre-emptively: there is no `VarRef` node to
            // check, and adding a "Bool operand" check against the existing
            // AST would be a no-op (all current nodes are Bool). The
            // DoD-closing test for Ф2.4 (`ConditionParserTest >
            // has_sword_and_health is rejected`) pins the current
            // behaviour at the parse layer.
            is Condition.Not -> checkCondition(condition.inner, sceneId, typeTable, errors)
            is Condition.And -> {
                checkCondition(condition.left, sceneId, typeTable, errors)
                checkCondition(condition.right, sceneId, typeTable, errors)
            }
            is Condition.Or -> {
                checkCondition(condition.left, sceneId, typeTable, errors)
                checkCondition(condition.right, sceneId, typeTable, errors)
            }
            is Condition.Compare -> {
                val type = typeTable[condition.varName]
                if (type == null) {
                    errors += TypeError.UnknownVariable(condition.varName, sceneId)
                } else if (!type.isNumeric()) {
                    errors += TypeError.CompareOnNonNumeric(condition.varName, type, sceneId)
                }
            }
            is Condition.InRange -> {
                val type = typeTable[condition.varName]
                if (type == null) {
                    errors += TypeError.UnknownVariable(condition.varName, sceneId)
                } else if (!type.isNumeric()) {
                    errors += TypeError.InRangeOnNonNumeric(condition.varName, type, sceneId)
                }
            }
        }
    }

    // ---------- Effect checking ----------

    private fun checkEffects(
        effects: List<Effect>,
        sceneId: SceneId,
        typeTable: Map<String, VarType>,
        errors: MutableList<TypeError>,
    ) {
        for (effect in effects) {
            when (effect) {
                is Effect.SetFlag -> {
                    val type = typeTable[effect.name]
                    if (type == null) {
                        errors += TypeError.UnknownVariable(effect.name, sceneId)
                    } else if (type != VarType.Bool) {
                        errors += TypeError.SetFlagOnNonBool(effect.name, type, sceneId)
                    }
                }
                is Effect.RemoveFlag -> {
                    val type = typeTable[effect.name]
                    if (type == null) {
                        errors += TypeError.UnknownVariable(effect.name, sceneId)
                    } else if (type != VarType.Bool) {
                        errors += TypeError.RemoveFlagOnNonBool(effect.name, type, sceneId)
                    }
                }
                is Effect.ModifyStat -> {
                    val type = typeTable[effect.name]
                    if (type == null) {
                        errors += TypeError.UnknownVariable(effect.name, sceneId)
                    } else if (!type.isNumeric()) {
                        errors += TypeError.ModifyStatOnNonNumeric(effect.name, type, sceneId)
                    }
                }
                is Effect.Set -> {
                    // Ф3.1: `Set` shares the "numeric required" rule with
                    // `ModifyStat`. Same `TypeError` variant — the type
                    // checker cares that the operation requires a number,
                    // not whether it is a delta or an absolute value.
                    val type = typeTable[effect.name]
                    if (type == null) {
                        errors += TypeError.UnknownVariable(effect.name, sceneId)
                    } else if (!type.isNumeric()) {
                        errors += TypeError.ModifyStatOnNonNumeric(effect.name, type, sceneId)
                    }
                }
            }
        }
    }

    // ---------- Helpers ----------

    /**
     * True for the numeric [VarType]s (`Integer`, `IntMin`, `IntRange`,
     * `ItemCount`). False for `Bool` and `ItemList` — both non-numeric
     * operands.
     */
    private fun VarType.isNumeric(): Boolean = when (this) {
        is VarType.Bool, is VarType.ItemList -> false
        is VarType.Integer, is VarType.IntMin, is VarType.IntRange, is VarType.ItemCount -> true
    }
}