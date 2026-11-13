package com.myapp.adventure.engine.ir.types

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.Condition
import com.myapp.adventure.engine.ir.Effect
import com.myapp.adventure.engine.ir.TagCategory

/**
 * Infers variable types from usage across [adventure].
 *
 * **Purpose.** Ф2.8 DoD: "скрипт/утилита `inferTypes(ir)` предлагает
 * типы по использованию". This is the migration tool for adventures
 * written before explicit variable declarations existed (or for files
 * where the author has not yet declared types). Given a fully-parsed
 * [Adventure], it returns a **suggestion** — the caller decides whether
 * to apply it (write into `Adventure.variables`) or to compare it with
 * the existing declarations to spot conflicts.
 *
 * **What it does.** Scans every place a variable can appear and
 * collects two kinds of signals:
 *
 * 1. **Explicit tag categories** — the strongest signal, because the
 *    author wrote them on purpose:
 *    - `HEALTH` → [VarType.IntMin]`(0)` (health-like, cannot go negative);
 *    - `ITEM` → [VarType.ItemCount] (stackable item);
 *    - `JOURNAL` → [VarType.Bool] if the name is **not** in
 *      [Adventure.initialState.stats], otherwise [VarType.IntMin]`(0)`.
 *      Rationale: a JOURNAL variable that starts as a number
 *      (`quest_steps = 3`) is a counter, not a flag; one that starts
 *      as a flag, or is only ever set, is a flag.
 * 2. **Usage signals** — what the author actually does with the name:
 *    - [Adventure.initialState.flags] → [VarType.Bool];
 *    - [Adventure.initialState.stats] → [VarType.IntMin]`(0)`;
 *    - [Effect.SetFlag] / [Effect.RemoveFlag] / [Condition.Has] →
 *      [VarType.Bool];
 *    - [Effect.ModifyStat] / [Condition.Compare] / [Condition.InRange]
 *      → [VarType.IntMin]`(0)`.
 *
 * **Priority when signals conflict.**
 * `HEALTH tag > ITEM tag > numeric usage > Bool usage`.
 *
 * Why:
 * - **`tags[].category`** is an explicit, deliberate declaration by the
 *   author. It beats incidental usage. If an item is declared `ITEM` and
 *   also appears in `set_flag`, the author made a mistake — but the
 *   intent (`ITEM`) is clearer than the accident (`set_flag`), and the
 *   type checker will report the mistake as
 *   `SetFlagOnNonBool`.
 * - **`numeric usage > Bool usage`** because "used in `modify_stat` /
 *   `compare`" is a stronger indicator of "this is a number" than "used
 *   in `set_flag`" is of "this is a flag" — a Bool cannot appear in
 *   `modify_stat` at all (it is a type error), while a numeric can
 *   legitimately appear in `set_flag` **only** by mistake.
 *
 * `inferTypes` does not try to resolve contradictions; it proposes the
 * most likely type so that migration succeeds in the common case. When
 * the result contradicts `tags` or `initialState`, the type checker
 * (which runs after migration) reports the contradiction with a
 * concrete fix-hint — that is the correct place to surface it.
 *
 * **Not `IntRange`.** We do not guess upper bounds. An author who wants
 * a bounded variable (`health 0..100`) declares `IntRange` explicitly
 * in `variables`. Inferred types are always either `Bool`,
 * `ItemCount`, or `IntMin(0)` — the safe, non-negotiable bounds.
 * `inferTypes` never returns `Integer` or `IntRange`: "no bounds known"
 * is not the same as "unbounded on purpose", and `IntMin(0)` is a
 * strict superset of `Integer` for the vast majority of game variables
 * (health, gold, ammo, items — none go negative).
 *
 * **Pure.** No I/O, no logging, no mutation of [adventure]. Safe to call
 * from tests, CLI tooling, or a future editor "Suggest types" button.
 *
 * **Not part of `TypeChecker`.** `TypeChecker` validates **declared**
 * types; `inferTypes` proposes **missing** ones. Keeping them separate
 * means the migration tool does not accidentally weaken validation:
 * `TypeChecker` still rejects references to names that no one ever
 * declared, even if `inferTypes` could "invent" a type for them.
 *
 * **Deterministic output.** The returned map has no meaningful order
 * guarantee (it is a `Map`, not a list), but two calls on the same
 * [Adventure] produce an `==`-equal map.
 *
 * @param adventure a fully-parsed IR. The parser is expected to have
 *   already succeeded — `inferTypes` does not validate.
 * @return `name → VarDecl` for every name that appears at least once
 *   in `tags`, `initialState`, or usage. Names that appear **nowhere**
 *   cannot be inferred and are not included; a variable that only the
 *   author knows about must be declared explicitly.
 */
public fun inferTypes(adventure: Adventure): Map<String, VarDecl> {
    val healthSignals = mutableSetOf<String>()
    val itemSignals = mutableSetOf<String>()
    val numericSignals = mutableSetOf<String>()
    val boolSignals = mutableSetOf<String>()

    // 1. Explicit tag categories.
    for (tag in adventure.tags) {
        when (tag.category) {
            TagCategory.HEALTH -> healthSignals.add(tag.name)
            TagCategory.ITEM -> itemSignals.add(tag.name)
            TagCategory.JOURNAL -> {
                // A JOURNAL that starts as a number is a counter; one that
                // starts as a flag, or is not in stats at all, is a flag.
                if (tag.name in adventure.initialState.stats) {
                    numericSignals.add(tag.name)
                } else {
                    boolSignals.add(tag.name)
                }
            }
        }
    }

    // 2. Initial state.
    boolSignals.addAll(adventure.initialState.flags)
    numericSignals.addAll(adventure.initialState.stats.keys)

    // 3. Usage in scenes and choices.
    for (chapter in adventure.chapters) {
        for (scene in chapter.scenes) {
            collectEffectSignals(scene.effects, boolSignals, numericSignals)
            for (choice in scene.choices) {
                choice.condition?.let {
                    collectConditionSignals(it, boolSignals, numericSignals)
                }
                collectEffectSignals(choice.effects, boolSignals, numericSignals)
            }
        }
    }

    // 4. Resolve by priority: HEALTH > ITEM > numeric > Bool.
    val allNames = healthSignals + itemSignals + numericSignals + boolSignals
    val result = mutableMapOf<String, VarDecl>()
    for (name in allNames) {
        val type: VarType = when {
            name in healthSignals -> VarType.IntMin(0)
            name in itemSignals -> VarType.ItemCount
            name in numericSignals -> VarType.IntMin(0)
            name in boolSignals -> VarType.Bool
            else -> continue // unreachable: allNames is the union of the four sets
        }
        result[name] = VarDecl(name = name, type = type)
    }
    return result
}

private fun collectEffectSignals(
    effects: List<Effect>,
    boolSignals: MutableSet<String>,
    numericSignals: MutableSet<String>,
) {
    for (effect in effects) {
        when (effect) {
            is Effect.SetFlag -> boolSignals.add(effect.name)
            is Effect.RemoveFlag -> boolSignals.add(effect.name)
            is Effect.ModifyStat -> numericSignals.add(effect.name)
            // Ф3.1: `Set` is a numeric operation (absolute value), same
            // signal class as `ModifyStat`.
            is Effect.Set -> numericSignals.add(effect.name)
        }
    }
}

private fun collectConditionSignals(
    condition: Condition,
    boolSignals: MutableSet<String>,
    numericSignals: MutableSet<String>,
) {
    when (condition) {
        // Ф3.1: `Literal` has no variable reference — no signal.
        is Condition.Literal -> Unit
        // Ф3.1: `VarRef` reads a Bool variable, same signal class as `Has`.
        is Condition.VarRef -> boolSignals.add(condition.name)
        is Condition.Has -> boolSignals.add(condition.name)
        is Condition.Not -> collectConditionSignals(
            condition.inner, boolSignals, numericSignals,
        )
        is Condition.And -> {
            collectConditionSignals(condition.left, boolSignals, numericSignals)
            collectConditionSignals(condition.right, boolSignals, numericSignals)
        }
        is Condition.Or -> {
            collectConditionSignals(condition.left, boolSignals, numericSignals)
            collectConditionSignals(condition.right, boolSignals, numericSignals)
        }
        is Condition.Compare -> numericSignals.add(condition.varName)
        is Condition.InRange -> numericSignals.add(condition.varName)
    }
}