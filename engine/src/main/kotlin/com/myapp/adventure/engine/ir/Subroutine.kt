package com.myapp.adventure.engine.ir

import com.myapp.adventure.engine.ir.types.VarDecl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ф4.7 (N6.base): a reusable, atomic procedure.
 *
 * **Atomic by design.** A subroutine runs to completion inside a single
 * [com.myapp.adventure.engine.Engine.step] call; the player never sees
 * intermediate screens or choices. This is what keeps the reader fast and
 * the save format unchanged: there is no persisted call stack, no
 * mid-subroutine save, and undo returns to the state before the choice
 * that invoked the subroutine. Player-visible multi-step content belongs
 * to reusable scenes (a separate feature), not here.
 *
 * **Local frame (decision A1 of Ф4.7).** A call creates a private frame:
 * - [inputs] are copied **into** the frame from the caller before the body
 *   starts. Inside the frame these names are read-only.
 * - [outputs] are initialised to their type defaults, may be written by the
 *   body, and are copied **out** to the caller's target variables when the
 *   frame returns. A name that appears in both [inputs] and [outputs] is an
 *   in-out parameter: it starts with the caller's value and is written back
 *   on return (the types must match; checked by the parser/type checker).
 * - The body can access **only** these local names. It cannot read or
 *   mutate global `Adventure.variables` directly; anything the procedure
 *   needs must be passed as an input, and anything it changes must be
 *   exposed as an output. This is what makes a subroutine a predictable
 *   contract instead of a hidden side effect.
 *
 * **Nesting and the call stack.** A body step may [SubroutineStep.call]
 * another subroutine. The runtime keeps an explicit stack of frames, so a
 * callee's outputs can be written into the caller's locals and the caller
 * resumes at the next body step. Recursion and cycles are **not** allowed in
 * N6.base: `Engine.compile` rejects a cyclic call graph
 * ([com.myapp.adventure.engine.CompileError.SubroutineCycle]). Bounded loops
 * and local variables beyond parameters belong to Ф13 `Procedure`.
 *
 * **No version bump.** `Adventure.subroutines` is an optional list with an
 * empty default; identity migration, same reasoning as `variables` (Ф2.8),
 * `invariants` (Ф3.5), `choice_templates` (Ф4.1), `merge` (Ф4.5).
 *
 * @property id stable, machine-readable identifier (e.g. `"drink_potion"`).
 *   Unique within an adventure; checked by the parser.
 * @property inputs typed parameters copied into the frame before the body.
 *   Order is meaningful for documentation and for the editor's parameter
 *   list. Duplicate names are a structural error.
 * @property outputs typed parameters written back to the caller on return.
 *   May overlap [inputs] by name (in-out parameter), but the overlapping
 *   types must be identical.
 * @property body ordered steps executed top to bottom. Each step may be
 *   guarded by a [SubroutineStep.condition], apply effects, and/or call
 *   another subroutine.
 */
@Serializable
public data class Subroutine(
    public val id: String,
    public val inputs: List<VarDecl> = emptyList(),
    public val outputs: List<VarDecl> = emptyList(),
    public val body: List<SubroutineStep> = emptyList(),
)

/**
 * One step of a [Subroutine] body.
 *
 * **Execution order inside a step.** If [condition] is non-null and
 * evaluates to `false`, the step is skipped entirely. Otherwise [effects] are
 * applied to the current frame in list order, then [call] (if present) is
 * executed. A step with both effects and a call is the canonical
 * "consume the input, then run the shared logic" pattern.
 *
 * **Why a flat list of guarded steps, not a nested `if` tree.** The authoring
 * UI (Ф4.8) is a table: condition + effects + optional call per row. That is
 * easier to read and edit than a nested block editor, and it covers the same
 * finite branching: an `else` branch is a following step with the negated
 * condition. Loops and nested control flow are explicitly out of N6.base
 * (N6.advanced / Ф13).
 *
 * @property condition optional guard; `null` means "always run".
 * @property effects state changes applied to the frame, in order.
 * @property call optional nested subroutine call executed after [effects].
 */
@Serializable
public data class SubroutineStep(
    public val condition: Condition? = null,
    public val effects: List<Effect> = emptyList(),
    public val call: SubroutineCall? = null,
)

/**
 * A call to a [Subroutine], used either on a [Choice] (top-level call) or
 * inside a [SubroutineStep] (nested call).
 *
 * **Name resolution depends on context.**
 * - On a [Choice], [args] sources and [outputs] targets are names of
 *   **global** variables from `Adventure.variables`.
 * - Inside a subroutine body, they are names of **local** parameters of the
 *   enclosing frame ([Subroutine.inputs] / [Subroutine.outputs]).
 *
 * This is a small, deliberate scoping rule: nested calls compose local
 * frames; top-level calls connect frames to the player's state. It keeps the
 * runtime predictable and lets the type checker validate both directions.
 *
 * **Arguments and return values.**
 * - Every declared input must have an entry in [args]; extra entries are
 *   errors. The type of the source must match the parameter's declared type.
 * - [outputs] may cover a subset of the callee's outputs; an unmapped output
 *   is simply discarded. Extra target names are errors. The type of the
 *   target must match the output's declared type.
 * - Values are copied, never aliased: the callee starts with the current
 *   value of each source and writes its outputs back when it returns.
 *
 * @property subroutineId id of the callee; resolved by `Engine.compile`.
 * @property args callee input name → caller variable/local name.
 * @property outputs callee output name → caller variable/local name.
 */
@Serializable
public data class SubroutineCall(
    @SerialName("subroutine_id")
    public val subroutineId: String,
    public val args: Map<String, String> = emptyMap(),
    public val outputs: Map<String, String> = emptyMap(),
)
