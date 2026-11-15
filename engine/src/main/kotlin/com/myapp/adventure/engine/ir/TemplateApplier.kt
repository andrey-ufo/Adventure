package com.myapp.adventure.engine.ir

/**
 * Instantiates a [ChoiceTemplate] into a fully-expanded [Choice].
 *
 * **Design (Ф4.2 decision A1): expansion.** The returned [Choice] has
 * its `text`, `condition`, `visibilityCondition`, and `effects` filled
 * in with substituted values. The runtime (`Engine.step`,
 * `Evaluator`, `EffectsApplier`) and the analyzer see only expanded
 * choices; they never look at `Adventure.choiceTemplates` or
 * `Choice.templateRef`. The `templateRef` field on the result records
 * the source (for the editor, Ф4.3).
 *
 * **Substitution semantics (Ф4.2 decision B1).** Args are
 * `Map<String, String>` — parameter name → actual variable name.
 * Substitution is:
 * - **Text**: `{paramName}` placeholders in [ChoiceTemplate.text] are
 *   replaced with the corresponding arg value. Example:
 *   `"Use {itemId}"` + `args = {itemId: "potion"}` → `"Use potion"`.
 * - **AST name slots**: [Condition.Has], [Condition.VarRef],
 *   [Condition.Compare], [Condition.InRange], [Effect.SetFlag],
 *   [Effect.RemoveFlag], [Effect.ModifyStat], [Effect.Set] are
 *   checked for **exact** parameter-name matches. If the string equals
 *   a parameter name, it is replaced with the arg value; otherwise it
 *   is left as-is (a reference to a global variable).
 * - **Int slots are not parameterizable**: `value` / `delta` / `min` /
 *   `max` (all `Int`) are copied verbatim. Integer parameterization
 *   requires a placeholder syntax for ints and is out of scope.
 * - **`nextScene`**: normally copied verbatim. The sentinel value
 *   [ChoiceTemplate.SELF] (`SceneId("@self")`) resolves to the
 *   caller-supplied [currentSceneId] — "the scene where this template is
 *   applied". Used for "use item" / "drink potion" choices that apply
 *   effects in place. If a template uses `@self` and [currentSceneId]
 *   is `null`, [applyTemplate] throws (a caller that applies a `@self`
 *   template without scene context is a bug, not an author error).
 *
 * **Validation.** [applyTemplate] requires:
 * - every parameter name in `template.parameters` is a key in [args];
 * - `args` has no keys that are not parameters (catches typos early).
 * Missing or extra args throw [IllegalArgumentException]. If args are
 * built by the editor (Ф4.3) or the JSON parser (Ф4.3), validation is
 * a developer-facing invariant, not author-facing input.
 *
 * **Pure.** No I/O, no logging, no mutation of [template] or [args].
 *
 * @param template the template to instantiate.
 * @param args parameter name → actual variable name.
 * @param choiceId the id to assign to the resulting [Choice]. The
 *   caller owns id generation: the JSON parser (Ф4.3.3) will use
 *   `"${sceneId}_choice_${index}"`, the editor will use the same.
 * @param currentSceneId the scene where this template is being applied.
 *   Required if `template.nextScene == ChoiceTemplate.SELF`; ignored
 *   otherwise. The editor's "use template" flow (Ф4.3.2b) passes the
 *   parent scene's id here.
 * @return fully-expanded [Choice] with `templateRef` set.
 * @throws IllegalArgumentException if [args] does not match
 *   `template.parameters` (missing or extra keys).
 */
public fun applyTemplate(
    template: ChoiceTemplate,
    args: Map<String, String>,
    choiceId: ChoiceId,
    currentSceneId: SceneId? = null,
): Choice {
    val paramNames = template.parameters.map { it.name }.toSet()
    val missing = paramNames - args.keys
    require(missing.isEmpty()) {
        "Template '${template.id}': missing arguments for parameters $missing"
    }
    val extra = args.keys - paramNames
    require(extra.isEmpty()) {
        "Template '${template.id}': unexpected arguments (not in parameters): $extra"
    }
    return Choice(
        id = choiceId,
        text = substituteText(template.text, args),
        nextScene = resolveNextScene(template, currentSceneId),
        condition = template.availabilityCondition?.let {
            substituteCondition(it, args)
        },
        visibilityCondition = template.visibilityCondition?.let {
            substituteCondition(it, args)
        },
        effects = template.effects.map { substituteEffect(it, args) },
        templateRef = TemplateRef(template.id, args),
    )
}

/**
 * Convenience: looks up [templateId] in [Adventure.choiceTemplates] and
 * applies it. Returns `null` if the template is not found.
 *
 * Provided so that callers with only an [Adventure] in hand do not have
 * to write the lookup themselves. The core API is
 * [applyTemplate] with an explicit [ChoiceTemplate]; this overload is
 * sugar.
 */
public fun Adventure.applyTemplate(
    templateId: String,
    args: Map<String, String>,
    choiceId: ChoiceId,
    currentSceneId: SceneId? = null,
): Choice? {
    val template = choiceTemplates.firstOrNull { it.id == templateId } ?: return null
    return applyTemplate(template, args, choiceId, currentSceneId)
}

// ---------- Substitution helpers ----------

/**
 * Substitutes `{paramName}` placeholders in [text]. The `{...}` markers
 * make parameter references unambiguous: literal gamebook text is
 * unlikely to contain curly braces, and a bare name in text would be
 * indistinguishable from ordinary prose.
 */
private fun substituteText(text: String, args: Map<String, String>): String {
    if (args.isEmpty() || '{' !in text) return text
    var result = text
    for ((name, value) in args) {
        result = result.replace("{$name}", value)
    }
    return result
}

/**
 * Substitutes an **exact** parameter name in a name slot. Exact match
 * (no braces) is unambiguous here: a name slot contains either a
 * parameter name (substitute) or a global variable name (leave as-is).
 * Braces would be redundant and noisy in an AST.
 */
private fun substituteName(name: String, args: Map<String, String>): String =
    args[name] ?: name

private fun substituteCondition(
    condition: Condition,
    args: Map<String, String>,
): Condition = when (condition) {
    is Condition.Literal -> condition
    is Condition.VarRef -> Condition.VarRef(substituteName(condition.name, args))
    is Condition.Has -> Condition.Has(substituteName(condition.name, args))
    is Condition.Not -> Condition.Not(substituteCondition(condition.inner, args))
    is Condition.And -> Condition.And(
        substituteCondition(condition.left, args),
        substituteCondition(condition.right, args),
    )
    is Condition.Or -> Condition.Or(
        substituteCondition(condition.left, args),
        substituteCondition(condition.right, args),
    )
    is Condition.Compare -> Condition.Compare(
        varName = substituteName(condition.varName, args),
        op = condition.op,
        value = condition.value,
    )
    is Condition.InRange -> Condition.InRange(
        varName = substituteName(condition.varName, args),
        min = condition.min,
        max = condition.max,
    )
}

private fun substituteEffect(
    effect: Effect,
    args: Map<String, String>,
): Effect = when (effect) {
    is Effect.SetFlag -> Effect.SetFlag(substituteName(effect.name, args))
    is Effect.RemoveFlag -> Effect.RemoveFlag(substituteName(effect.name, args))
    is Effect.ModifyStat -> Effect.ModifyStat(substituteName(effect.name, args), effect.delta)
    is Effect.Set -> Effect.Set(substituteName(effect.name, args), effect.value)
}


/**
 * Resolves `template.nextScene` to the concrete scene id of the
 * resulting [Choice]:
 *
 * - normal `SceneId` → copied as-is;
 * - [ChoiceTemplate.SELF] → `currentSceneId` (required);
 * - `@self` without `currentSceneId` → [IllegalArgumentException].
 *
 * The exception is a **caller** error (misuse of the API), not an
 * author error: a template legitimately using `@self` requires the
 * caller to know the context. The editor (Ф4.3.2b) and the JSON parser
 * (Ф4.3.3) always know it; hand-written tests that do not use `@self`
 * never hit this branch.
 */
private fun resolveNextScene(
    template: ChoiceTemplate,
    currentSceneId: SceneId?,
): SceneId {
    if (template.nextScene != ChoiceTemplate.SELF) return template.nextScene
    return requireNotNull(currentSceneId) {
        "Template '${template.id}' uses @self, but no currentSceneId was provided. " +
                "Pass the scene id where this template is applied."
    }
}