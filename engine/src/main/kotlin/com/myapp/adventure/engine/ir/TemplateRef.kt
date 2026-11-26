package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reference from a [Choice] back to the [ChoiceTemplate] it was expanded from.
 *
 * **Why it exists.** With the expansion approach (Ф4.2 decision A1), the
 * `Choice` carries fully-expanded content (`text`, `condition`,
 * `visibilityCondition`, `effects`), not a live reference. `TemplateRef`
 * is metadata for the editor: it records *which* template and *which*
 * args produced this choice. The editor (Ф4.3) uses it to show "this
 * choice uses template X" and to offer "update all uses" when the
 * template changes.
 *
 * **Not used by the runtime.** `Engine.step`, `Evaluator`,
 * `EffectsApplier`, `Analyzer` never look at `templateRef` — they see
 * only the expanded content. The reference is inert from the gameplay
 * point of view.
 *
 * **Serialized since Ф4.10.** `ChoiceDto.template_ref` is parsed by
 * `JsonToIr`, so a hand-edited file and the editor's round-trip both keep
 * the reference in the IR. The runtime still ignores it.
 *
 * @property templateId the `ChoiceTemplate.id` this choice was expanded
 *   from.
 * @property args the arguments used for substitution. Keys are parameter
 *   names (matching `ChoiceTemplate.parameters[i].name`); values are the
 *   actual variable names that were substituted in.
 */
@Serializable
public data class TemplateRef(
    @SerialName("template_id")
    public val templateId: String,
    public val args: Map<String, String> = emptyMap(),
)