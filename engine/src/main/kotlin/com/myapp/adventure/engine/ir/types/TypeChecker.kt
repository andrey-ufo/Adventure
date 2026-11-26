package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.SceneId
import com.myapp.adventure.engine.ir.Subroutine
import com.myapp.adventure.engine.ir.SubroutineCall
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
 * `TypeChecker` sits between the parser and the compiler and **is wired
 * into `JsonToIr.parse`** (Ф2.3): a successful parse runs this check and
 * returns `ParseResult.Failure(typeErrors = …)` if anything is wrong, so
 * `Engine.compile` only ever sees type-clean IR.
 *
 * **Ф4.7 subroutine scopes.** Subroutine bodies are checked against their
 * own local table (`inputs + outputs`), not `Adventure.variables`; a body
 * sees only its parameters. Top-level `Choice.call` args/targets are
 * checked against the global table, nested calls against the enclosing
 * frame. These checks run even when the global type table is empty,
 * because local types are explicit.
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
        val errors = mutableListOf<TypeError>()
        val subroutinesById = adventure.subroutines.associateBy { it.id }

        // `typeTable.isEmpty()` means there is nothing declared or
        // inferable to check scene/global references against. Do **not**
        // report every name as unknown — that would reject every legacy
        // file that relies on the implicit zero-value fallback. Subroutine
        // bodies are still checked below because their scope is explicit
        // (inputs + outputs), independent of the global table.
        if (typeTable.isNotEmpty()) {
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

            // 2. Scenes: scene effects, then each choice's condition and
            //    effects. Order within a scene follows the source (scene
            //    first, then choices), so that when the author opens the
            //    editor, the first reported error is the one "highest" in the
            //    visual hierarchy. Stable-sorted at the end.
            for (chapter in adventure.chapters) {
                for (scene in chapter.scenes) {
                    checkEffects(scene.effects, scene.id, typeTable, errors)
                    for (choice in scene.choices) {
                    choice.condition?.let {
                        checkCondition(it, scene.id, typeTable, errors)
                    }
                    choice.visibilityCondition?.let {
                        checkCondition(it, scene.id, typeTable, errors)
                    }
                    checkEffects(choice.effects, scene.id, typeTable, errors)
                        choice.call?.let {
                            checkTopLevelSubroutineCall(
                                call = it,
                                sceneId = scene.id,
                                globalTypes = typeTable,
                                subroutinesById = subroutinesById,
                                errors = errors,
                            )
                        }
                    }
                }
            }
        }

        // Ф4.7: subroutine bodies have their own local scope
        // (inputs + outputs), not the global variable table. Always check
        // them: their types are explicit even when the adventure declares
        // no global variables.
        for (subroutine in adventure.subroutines) {
            checkSubroutine(subroutine, subroutinesById, errors)
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
        sceneId: SceneId?,
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
        sceneId: SceneId?,
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

    // ---------- Subroutine checking (Ф4.7) ----------

    /**
     * Checks one subroutine in its **local scope**: only `inputs` and
     * `outputs` are visible, never `Adventure.variables`. This enforces the
     * pure-function contract described in `Subroutine` KDoc.
     *
     * Body errors are wrapped in [TypeError.InSubroutine] so the message
     * names the subroutine even though there is no scene to jump to.
     */
    private fun checkSubroutine(
        subroutine: Subroutine,
        subroutinesById: Map<String, Subroutine>,
        errors: MutableList<TypeError>,
    ) {
        val localErrors = mutableListOf<TypeError>()
        val localTypes = mutableMapOf<String, VarType>()
        val inputNames = mutableSetOf<String>()

        for (input in subroutine.inputs) {
            if (input.type is VarType.ItemList) {
                localErrors += TypeError.InvalidSubroutineSignature(
                    subroutineId = subroutine.id,
                    detail = "ItemList input '${input.name}' is not supported in N6.base",
                )
            }
            if (localTypes.containsKey(input.name)) {
                localErrors += TypeError.InvalidSubroutineSignature(
                    subroutineId = subroutine.id,
                    detail = "duplicate parameter '${input.name}'",
                )
            } else {
                localTypes[input.name] = input.type
            }
            inputNames += input.name
        }

        for (output in subroutine.outputs) {
            if (output.type is VarType.ItemList) {
                localErrors += TypeError.InvalidSubroutineSignature(
                    subroutineId = subroutine.id,
                    detail = "ItemList output '${output.name}' is not supported in N6.base",
                )
            }
            val existing = localTypes[output.name]
            if (existing != null && existing != output.type) {
                localErrors += TypeError.InvalidSubroutineSignature(
                    subroutineId = subroutine.id,
                    detail = "parameter '${output.name}' has input type " +
                            "${existing.displayName()} but output type " +
                            output.type.displayName(),
                )
            } else {
                localTypes[output.name] = output.type
            }
        }

        val inputOnly = inputNames - subroutine.outputs.map { it.name }.toSet()

        for (step in subroutine.body) {
            step.condition?.let {
                checkCondition(it, null, localTypes, localErrors)
            }
            checkEffects(step.effects, null, localTypes, localErrors)
            checkInputOnlyWrites(step.effects, inputOnly, subroutine.id, localErrors)
            step.call?.let {
                checkNestedSubroutineCall(
                    call = it,
                    callerSubroutineId = subroutine.id,
                    localTypes = localTypes,
                    inputOnly = inputOnly,
                    subroutinesById = subroutinesById,
                    errors = localErrors,
                )
            }
        }

        errors += localErrors.map { TypeError.InSubroutine(subroutine.id, it) }
    }

    private fun checkInputOnlyWrites(
        effects: List<Effect>,
        inputOnly: Set<String>,
        subroutineId: String,
        errors: MutableList<TypeError>,
    ) {
        for (effect in effects) {
            val name = when (effect) {
                is Effect.SetFlag -> effect.name
                is Effect.RemoveFlag -> effect.name
                is Effect.ModifyStat -> effect.name
                is Effect.Set -> effect.name
            }
            if (name in inputOnly) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = subroutineId,
                    detail = "effect writes input-only parameter '$name'",
                )
            }
        }
    }

    private fun checkNestedSubroutineCall(
        call: SubroutineCall,
        callerSubroutineId: String,
        localTypes: Map<String, VarType>,
        inputOnly: Set<String>,
        subroutinesById: Map<String, Subroutine>,
        errors: MutableList<TypeError>,
    ) {
        val callee = subroutinesById[call.subroutineId] ?: return
        val inputs = callee.inputs.associateBy { it.name }
        for ((parameter, source) in call.args) {
            val input = inputs[parameter] ?: continue
            val sourceType = localTypes[source]
            if (sourceType == null) {
                errors += TypeError.UnknownVariable(source, null)
            } else if (sourceType != input.type) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = callee.id,
                    detail = "argument '$parameter' expects " +
                            "${input.type.displayName()}, but local '$source' is " +
                            sourceType.displayName(),
                    callerSubroutineId = callerSubroutineId,
                )
            }
        }

        val outputs = callee.outputs.associateBy { it.name }
        for ((parameter, target) in call.outputs) {
            val output = outputs[parameter] ?: continue
            val targetType = localTypes[target]
            if (targetType == null) {
                errors += TypeError.UnknownVariable(target, null)
                continue
            }
            if (target in inputOnly) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = callee.id,
                    detail = "output '$parameter' targets input-only local '$target'",
                    callerSubroutineId = callerSubroutineId,
                )
            }
            if (targetType != output.type) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = callee.id,
                    detail = "output '$parameter' expects " +
                            "${output.type.displayName()}, but local '$target' is " +
                            targetType.displayName(),
                    callerSubroutineId = callerSubroutineId,
                )
            }
        }
    }

    /**
     * Top-level call from a [com.myapp.adventure.engine.ir.Choice]: argument
     * sources and output targets are global variables from
     * [Adventure.variables] (or the inferred fallback table).
     */
    private fun checkTopLevelSubroutineCall(
        call: SubroutineCall,
        sceneId: SceneId,
        globalTypes: Map<String, VarType>,
        subroutinesById: Map<String, Subroutine>,
        errors: MutableList<TypeError>,
    ) {
        val callee = subroutinesById[call.subroutineId] ?: return

        val inputs = callee.inputs.associateBy { it.name }
        for ((parameter, source) in call.args) {
            val input = inputs[parameter] ?: continue
            val sourceType = globalTypes[source]
            if (sourceType == null) {
                errors += TypeError.UnknownVariable(source, sceneId)
            } else if (sourceType != input.type) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = callee.id,
                    detail = "argument '$parameter' expects " +
                            "${input.type.displayName()}, but variable '$source' is " +
                            sourceType.displayName(),
                    sceneId = sceneId,
                )
            }
        }

        val outputs = callee.outputs.associateBy { it.name }
        for ((parameter, target) in call.outputs) {
            val output = outputs[parameter] ?: continue
            val targetType = globalTypes[target]
            if (targetType == null) {
                errors += TypeError.UnknownVariable(target, sceneId)
            } else if (targetType != output.type) {
                errors += TypeError.InvalidSubroutineSignature(
                    subroutineId = callee.id,
                    detail = "output '$parameter' expects " +
                            "${output.type.displayName()}, but variable '$target' is " +
                            targetType.displayName(),
                    sceneId = sceneId,
                )
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