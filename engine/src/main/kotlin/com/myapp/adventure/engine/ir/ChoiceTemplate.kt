package com.myapp.adventure.engine.ir

import com.myapp.adventure.engine.ir.types.VarDecl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ф4.1: a reusable choice template — an authoring entity that can be
 * instantiated in many scenes with different parameter values.
 *
 * **Status (Ф4.1).** AST-only. Parsed from JSON into
 * `Adventure.choiceTemplates`, but not yet instantiated into a
 * `Choice`. Instantiation lands in Ф4.2 (`applyTemplate(templateId,
 * args)`). Until then, `Adventure.choiceTemplates` is parsed
 * infrastructure with no consumer — a `Choice` in any scene still has
 * its own `text` / `condition` / `effects`.
 *
 * **Why a template.** Suppose three scenes have a "Use potion" choice:
 * same text, same effect (`potion: -1`, `health: +20`), same
 * availability (`potion > 0`), same target (back to the current scene).
 * Without a template, the author duplicates five fields across three
 * scenes, and any change (e.g. `potion: -2` instead of `-1`) must be
 * repeated in every copy — easy to miss one.
 *
 * A `ChoiceTemplate` is the single source of truth; a scene uses it via
 * `Choice.templateRef` (Ф4.2) and provides only the args
 * (`{"itemId": "potion", "quantity": 1}`).
 *
 * **Not bound to a scene.** A template is declared once at the adventure
 * level (`Adventure.choiceTemplates`) and can be used by any number of
 * scenes, any number of times. This is the "reusable" part of "reusable
 * choice template".
 *
 * **Not checked by TypeChecker today.** `TypeChecker.check` walks
 * `Adventure.chapters` only. Templates are skipped in Ф4.1 — their
 * conditions reference parameters (`itemId`, `quantity`) that are not in
 * `Adventure.variables`, and checking them against the global scope
 * would produce false `UnknownVariable` errors. Ф4.2 will extend
 * `TypeChecker` with a `checkChoiceTemplate(template, globalVariables)`
 * that builds a combined scope (`parameters + globalVariables`) and runs
 * the same checks.
 *
 * @property id unique identifier within `Adventure.choiceTemplates`.
 *   Duplicates are a `ParseError.Structure` (checked by `JsonToIr`).
 *   Referenced by `Choice.templateRef` in Ф4.2.
 * @property parameters typed parameter declarations. Each `VarDecl` has
 *   a `name` (unique within the template) and a `VarType`. The parameter
 *   type participates in the template's own type-scope: a
 *   `Compare("itemId", GT, 0)` in `visibilityCondition` must resolve
 *   `itemId` to the declared type. Type-checking of templates lands in
 *   Ф4.2 (the `TypeChecker` extension needs to know the local scope;
 *   today it only knows `Adventure.variables`).
 * @property text literal display string. **No placeholder substitution
 *   in Ф4.1** — `text` is used verbatim when the template is
 *   instantiated in Ф4.2; the substitution syntax (`{itemId}` vs
 *   `%s` vs `$itemId`) is a Ф4.2 decision.
 * @property visibilityCondition when to **show** the choice. `null` =
 *   always visible. Distinct from `availabilityCondition` (below); the
 *   reader (N4 / Ф4.4) shows a hidden choice **not at all**, but shows
 *   an unavailable choice **disabled**, with a reason. The two-condition
 *   split is the AST-level support for N4; Ф4.4 implements the UI
 *   behaviour.
 * @property availabilityCondition when the choice is **clickable**.
 *   `null` = always clickable (subject to `visibilityCondition` being
 *   shown). This is the role that `Choice.condition` plays today in the
 *   reader; Ф4.4 will split it into two at the `Choice` level too.
 * @property effects applied when the choice is picked, in list order.
 *   Same `Effect` model as `Choice.effects`.
 * @property nextScene fixed target scene. Parameterization (e.g. "go
 *   back to the scene the choice was picked in") is **out of scope for
 *   Ф4.1**; if it is needed, a `targetParamName: String?` field will be
 *   added in Ф4.2, defaulting to `null` = "use `nextScene`".
 */
@Serializable
public data class ChoiceTemplate(
    public val id: String,
    public val parameters: List<VarDecl> = emptyList(),
    public val text: String,
    @SerialName("visibility_condition")
    public val visibilityCondition: Condition? = null,
    @SerialName("availability_condition")
    public val availabilityCondition: Condition? = null,
    public val effects: List<Effect> = emptyList(),
    @SerialName("next_scene")
    public val nextScene: SceneId,
)