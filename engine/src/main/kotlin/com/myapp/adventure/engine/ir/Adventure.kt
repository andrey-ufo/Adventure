package com.myapp.adventure.engine.ir

import com.myapp.adventure.engine.ir.types.VarDecl
import com.myapp.adventure.engine.ir.types.VarType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of the IR: a complete adventure.
 *
 * Produced by the JSON parser (Ф1.3), consumed by the runtime (Ф1.5), the
 * analyzer (Ф1.6), and the exporter (Ф8). There is exactly one `Adventure`
 * per file; there are no cross-adventure references.
 *
 * **Not the same as `:app/data/Adventure.kt`.** The IR is typed
 * (see [Condition], [Effect], [VarType], [SceneId]), non-null where
 * sensible, and carries a [version] for migration. The Android Gson-POJO
 * remains as the on-disk DTO for the editor; the adapter (Ф1.7) converts
 * between the two.
 *
 * @property version IR schema version. The parser (Ф1.3) reads it from the
 *   file; if absent, assumes [CURRENT_VERSION]. Migrations live alongside
 *   the parser and are applied on load (Ф5.3). Bumping this is a breaking
 *   change to the on-disk format and requires a migration entry.
 * @property title adventure title; shown in the library and reader.
 * @property author author name; informational.
 * @property description short blurb; shown in the library.
 * @property variables registry of variables used by [Condition] and
 *   [Effect]. Empty means "no declared variables" — such an adventure
 *   type-checks only against [InitialState] (Ф2 migration will populate
 *   this for existing files; new files are expected to declare variables
 *   explicitly). The type of each variable determines its default value
 *   (see [VarType] KDoc); an entry in [InitialState] overrides the
 *   default.
 * @property invariants global rules evaluated after every step. When
 *   an invariant's condition holds, its outcome fires (Ф3.5). Empty
 *   means "no explicit invariants" — the runtime falls back to the
 *   pre-Ф3.5 hardcoded `health <= 0 => death` rule. A non-empty list
 *   **replaces** that fallback; authors who want health-death plus
 *   custom rules declare both. See [Invariant] KDoc.
 * @property tags registry of tags with display metadata; used by the
 *   reader's HUD to decide what to show and how.
 * @property initialState player state before the first scene.
 * @property deathScene optional death screen; `null` means the runtime
 *   uses built-in defaults.
 * @property startScene scene the player begins in. Explicit since IR v3;
 *   in v1/v2 files the field was absent and the parser inferred it from
 *   `chapters.first().scenes.first()`. Making it explicit lets authors
 *   start in a scene that is not the first in the first chapter (for
 *   example: a dramatic teaser, then a prologue via `nextScene`). The
 *   analyzer's reachability (Ф1.6.1) uses this as the root of the graph.
 * @property chapters ordered list of chapters. Must be non-empty; enforced
 *   by the parser (Ф1.3).
 */
@Serializable
public data class Adventure(
    public val version: Int = CURRENT_VERSION,
    public val title: String,
    public val author: String = "",
    public val description: String = "",
    public val variables: Map<String, VarDecl> = emptyMap(),
    @SerialName("choice_templates")
    public val choiceTemplates: List<ChoiceTemplate> = emptyList(),
    public val invariants: List<Invariant> = emptyList(),
    public val tags: List<TagDefinition> = emptyList(),
    @SerialName("initial_state")
    public val initialState: InitialState = InitialState(),
    @SerialName("death_scene")
    public val deathScene: DeathScene? = null,
    @SerialName("start_scene")
    public val startScene: SceneId,
    public val chapters: List<Chapter>,
) {
    public companion object {
        /**
         * Current IR schema version. Increment when the on-disk format
         * changes in a way that old files cannot be read as-is; add a
         * migration in the parser at the same time.
         *
         * Version history:
         * - `1` — initial IR (Ф1.2): typed `Condition`/`Effect`,
         *   `SceneId` value class, `variables` registry, `version` field.
         *   Files that predate IR have no `version`; they are treated as
         *   `1` and migrated by the parser if needed. Gates lived on
         *   chapters as `chapter.required_steps`.
         * - `2` (Ф1.5.4) — gates moved from chapters to scenes.
         *   `Chapter.required_steps` is removed; `Scene.gate: Gate?`
         *   is added. Migration in `JsonToIr`: `chapter[i].required_steps
         *   = N > 0` becomes `Gate(N)` on the last scene of chapter `i-1`.
         *   Files without `version` are migrated from v1.
         * - `3` (Ф1.6.0) — `Adventure.start_scene: SceneId` added.
         *   Before v3, the start scene was inferred from
         *   `chapters.first().scenes.first()`; the analyzer's reachability
         *   (Ф1.6.1) needs an explicit root, because an author may start
         *   in a scene that is not the first in the first chapter.
         *   Migration in `JsonToIr`: `start_scene` absent → inferred from
         *   `chapters.first().scenes.first()` (identity for all existing
         *   files). Files without `version` are migrated from v1.
         * - `4` (Ф1.7.0) — `Scene.is_ending: Boolean` added. The reader
         *   no longer guesses "no choices = end of chapter, auto-advance";
         *   an ending **must** be explicit. Multiple scenes may be marked
         *   `is_ending = true` (multiple endings). Migration v3 → v4:
         *   identity — v3 files have no `is_ending`, so the field defaults
         *   to `false`. Old files that relied on "no choices = end" now
         *   produce `DeadEnd` ERROR (per project decision 2026-11-07:
         *   pre-alpha drops backward compatibility, authors fix files).
         *   `test_adventure.json` is updated in the same release to mark
         *   its `final` scene as `is_ending = true`.
         * - `5` (Ф1.7.0.4) — `Gate.repeatable: Boolean` added. Default
         *   `false` ("once walked, stays open in this playthrough"),
         *   matching the common case "walk N steps to unlock the next
         *   chapter". `true` = per-visit ("travelled a path; the same
         *   path back costs the same steps"). The author picks the
         *   mode in the editor. `Gate` changed from `value class` to
         *   `data class`; IR serialization wraps the gate as
         *   `{"required_steps": N, "repeatable": bool}`. Adventure file
         *   format stays flat: `"gate": N` + optional
         *   `"gate_repeatable": true`. Migration v4 → v5: identity —
         *   v4 files have no `gate_repeatable`, so `repeatable = false`.
         * - Still `5` (Ф3.5) — `Adventure.invariants: List<Invariant>`
         *   added, `Outcome.Death`, `Invariant(condition, outcome)`.
         *   **No version bump**: the field is optional and defaults to
         *   an empty list; old files fall back to the hardcoded
         *   `health <= 0 => death` rule in `Engine.evaluateInvariants`.
         *   Migration v5 → v5+invariants is identity. The decision to
         *   keep the version at 5 follows the same reasoning as
         *   `variables` in Ф2.8: adding an optional list with a
         *   documented fallback is backward-compatible in both
         *   directions (old readers ignore the field; new readers
         *   apply the fallback when it is empty).
         * - Still `5` (Ф4.1) — `Adventure.choice_templates:
         *   List<ChoiceTemplate>` added. `ChoiceTemplate(id,
         *   parameters, text, visibilityCondition,
         *   availabilityCondition, effects, nextScene)` — reusable
         *   choice templates, no scene binding. **No version bump**:
         *   optional list, empty default, identity migration. Same
         *   reasoning as `variables` (Ф2.8) and `invariants` (Ф3.5).
         */
        public const val CURRENT_VERSION: Int = 5
    }
}