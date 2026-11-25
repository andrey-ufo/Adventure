package com.myapp.adventure.engine.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single selectable action in a [Scene].
 *
 * Corresponds to `:app/data/Adventure.kt`'s `Choice`:
 * `text`, `next_scene`, `condition`, and the three effect fields
 * (`set_flags`, `remove_flags`, `modify_stats`) flattened into [effects].
 *
 * **Order matters.** When the player picks a choice, [effects] are applied
 * in list order (Ф1.5), matching the current reader behaviour where
 * `set_flags` run before `modify_stats`.
 *
 * **Availability.** A `null` [condition] means "always available". A
 * non-null [condition] is evaluated by the engine (Ф3); today's reader
 * (`AdventureReaderFragment.updateChoices`) hides unavailable choices.
 * The planned N4 / Ф4 behaviour is to show them as disabled with a
 * reason — that is a UI decision and does not affect this data model.
 * Ф4.2 adds [visibilityCondition] as AST-level support for the N4
 * "hidden vs disabled" distinction; Ф4.4 implements the reader
 * behaviour.
 *
 * **Ф4.2 additions.** Two fields are new since IR v5 (no version bump —
 * both optional, defaults `null`):
 * - [visibilityCondition] — "show this choice?" (distinct from
 *   [condition], which is "enable this choice?"). Populated by
 *   `applyTemplate` from `ChoiceTemplate.visibilityCondition`;
 *   consumed by the reader in Ф4.4.
 * - [templateRef] — if this choice came from a `ChoiceTemplate`, the
 *   reference (id + args). Metadata for the editor (Ф4.3); never
 *   touched by the runtime or the analyzer. See `TemplateRef` KDoc.
 *
 * @property nextScene scene the player transitions to when picking this
 *   choice. Required — there is no "do nothing" choice; use a self-loop
 *   (`nextScene = currentScene.id`) if you want the player to stay.
 * @property condition optional "enable" condition; `null` means always
 *   available. Evaluated by `Evaluator` (Ф3).
 * @property visibilityCondition optional "show" condition; `null` means
 *   always visible. Not yet used by the reader (Ф4.4 / N4). Populated by
 *   `applyTemplate` in Ф4.2.
 * @property effects state changes applied on pick, in order.
 * @property templateRef if this choice was expanded from a template,
 *   the reference. Metadata for the editor; `null` for hand-written
 *   choices.
 * @property call Ф4.7: optional atomic subroutine invoked **after**
 *   [effects] and **before** moving to [nextScene]. The subroutine runs
 *   with a local frame; its outputs are written back into global
 *   variables, then invariants are evaluated and the transition happens.
 *   [nextScene] is therefore the return target (use the current scene id
 *   to stay in place). See [Subroutine] KDoc.
 */
@Serializable
public data class Choice(
    public val id: ChoiceId,
    public val text: String,
    @SerialName("next_scene")
    public val nextScene: SceneId,
    public val condition: Condition? = null,
    @SerialName("visibility_condition")
    public val visibilityCondition: Condition? = null,
    public val effects: List<Effect> = emptyList(),
    @SerialName("template_ref")
    public val templateRef: TemplateRef? = null,
    public val call: SubroutineCall? = null,
)