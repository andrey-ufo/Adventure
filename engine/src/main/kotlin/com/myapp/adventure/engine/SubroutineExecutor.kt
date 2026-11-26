package com.myapp.adventure.engine

import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.ir.PlayerState
import com.myapp.adventure.engine.ir.Subroutine
import com.myapp.adventure.engine.ir.SubroutineCall
import com.myapp.adventure.engine.ir.types.VarDecl
import com.myapp.adventure.engine.ir.types.VarType

/**
 * Ф4.7 (N6.base): executes atomic subroutine calls with an explicit frame
 * stack.
 *
 * **Why a separate object.** `Engine.step` owns the transition; the
 * subroutine interpreter is a self-contained machine with its own stack and
 * local frames. Keeping it separate makes both testable in isolation and
 * prevents the transition logic from growing a second, ad-hoc interpreter.
 *
 * **Execution model.**
 * - A [Frame] holds one subroutine invocation: a private [PlayerState] for
 *   its locals, a step index, an output binding map, and a parent frame (or
 *   `null` for the top-level call from a choice).
 * - Steps are executed in order. A step whose condition is false is skipped.
 * - Effects apply to the frame's locals via [EffectsApplier].
 * - A nested call pushes a child frame. When the child finishes, its outputs
 *   are written into the **parent frame's** locals; the parent resumes at
 *   the next step. This is the explicit call/return stack.
 * - When the top-level frame finishes, its outputs are written into the
 *   global [PlayerState].
 *
 * **Scoping.** Inside a frame, only the subroutine's input/output parameters
 * exist. A nested call's `args` sources and `outputs` targets must be names
 * visible in the parent frame; a top-level call's names are global
 * variables. `Engine.compile` and `TypeChecker` validate the signatures
 * before the reader can run the adventure; this executor still reports
 * failures defensively (it can be invoked with hand-built IR).
 *
 * **No recursion.** `Engine.compile` rejects cyclic call graphs; the
 * executor additionally enforces [MAX_DEPTH] so a corrupted IR cannot hang
 * the reader.
 *
 * **Atomic.** The whole call completes within one [Engine.step]. No frame
 * or stack is persisted; undo returns to the state before the choice.
 */
internal object SubroutineExecutor {

    /** Hard safety limit for hand-built/corrupted IR. Cycles are compile errors. */
    internal const val MAX_DEPTH: Int = 64

    internal sealed class Result {
        internal data class Success(
            val state: PlayerState,
            /** Ф5.2: top-level output writes as player-state operations. */
            val globalDeltas: List<StateDelta> = emptyList(),
        ) : Result()
        internal data class Failure(val subroutineId: String, val reason: String) : Result()
    }

    /**
     * Executes [call] against [globalState] and returns the updated global
     * state. Never throws on malformed input: signature/nesting failures are
     * returned as [Result.Failure].
     */
    internal fun execute(
        adventure: Adventure,
        call: SubroutineCall,
        globalState: PlayerState,
    ): Result {
        val byId = adventure.subroutines.associateBy { it.id }
        val rootSubroutine = byId[call.subroutineId]
            ?: return Result.Failure(
                call.subroutineId,
                "subroutine '${call.subroutineId}' does not exist",
            )

        val root = when (val created = createFrame(
            subroutine = rootSubroutine,
            call = call,
            globalState = globalState,
            parent = null,
        )) {
            is FrameCreation.Success -> created.frame
            is FrameCreation.Failure ->
                return Result.Failure(call.subroutineId, created.reason)
        }

        var state = globalState
        val globalDeltas = mutableListOf<StateDelta>()
        val stack = ArrayDeque<Frame>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (frame.stepIndex >= frame.subroutine.body.size) {
                if (frame.parent == null) {
                    val (newState, deltas) = writeOutputsToGlobal(frame, state)
                    state = newState
                    globalDeltas += deltas
                } else {
                    writeOutputsToParent(frame)
                }
                stack.removeLast()
                continue
            }

            val step = frame.subroutine.body[frame.stepIndex]
            frame.stepIndex += 1

            val condition = step.condition
            if (condition != null && !Evaluator.evaluate(condition, frame.locals)) {
                continue
            }

            frame.locals = EffectsApplier.apply(frame.locals, step.effects)

            val nested = step.call ?: continue
            val callee = byId[nested.subroutineId]
                ?: return Result.Failure(
                    frame.subroutine.id,
                    "nested subroutine '${nested.subroutineId}' does not exist",
                )
            if (stack.size >= MAX_DEPTH) {
                return Result.Failure(
                    frame.subroutine.id,
                    "subroutine nesting exceeds $MAX_DEPTH frames " +
                            "(recursion is not allowed in N6.base)",
                )
            }
            when (val created = createFrame(
                subroutine = callee,
                call = nested,
                globalState = state,
                parent = frame,
            )) {
                is FrameCreation.Success -> stack.addLast(created.frame)
                is FrameCreation.Failure ->
                    return Result.Failure(callee.id, created.reason)
            }
        }

        return Result.Success(state, globalDeltas)
    }

    // ---------- frames ----------

    private class Frame(
        val subroutine: Subroutine,
        var locals: PlayerState,
        val outputTargets: Map<String, OutputTarget>,
        var stepIndex: Int = 0,
        val parent: Frame? = null,
    )

    private sealed class OutputTarget {
        data class Global(val name: String) : OutputTarget()
        data class Local(val name: String) : OutputTarget()
    }

    private sealed class FrameCreation {
        data class Success(val frame: Frame) : FrameCreation()
        data class Failure(val reason: String) : FrameCreation()
    }

    private fun createFrame(
        subroutine: Subroutine,
        call: SubroutineCall,
        globalState: PlayerState,
        parent: Frame?,
    ): FrameCreation {
        val sourceState = parent?.locals ?: globalState
        val parentLocals = parent?.let { localNames(it.subroutine) }

        var locals = PlayerState()
        val inputNames = mutableSetOf<String>()

        for (input in subroutine.inputs) {
            if (input.type is VarType.ItemList) {
                return FrameCreation.Failure(
                    "ItemList parameter '${input.name}' is not supported in N6.base",
                )
            }
            val sourceName = call.args[input.name]
                ?: return FrameCreation.Failure(
                    "missing argument '${input.name}' for subroutine '${subroutine.id}'",
                )
            if (parent != null && sourceName !in parentLocals.orEmpty()) {
                return FrameCreation.Failure(
                    "argument '${input.name}' references unknown local " +
                            "'$sourceName' in subroutine '${parent.subroutine.id}'",
                )
            }
            locals = copyIn(locals, input, sourceState, sourceName)
            inputNames.add(input.name)
        }

        for (output in subroutine.outputs) {
            if (output.type is VarType.ItemList) {
                return FrameCreation.Failure(
                    "ItemList parameter '${output.name}' is not supported in N6.base",
                )
            }
            if (output.name !in inputNames) {
                locals = initialiseDefault(locals, output)
            }
        }

        val targets = mutableMapOf<String, OutputTarget>()
        for (output in subroutine.outputs) {
            val targetName = call.outputs[output.name] ?: continue
            if (parent != null) {
                if (targetName !in parentLocals.orEmpty()) {
                    return FrameCreation.Failure(
                        "output '${output.name}' targets unknown local " +
                                "'$targetName' in subroutine '${parent.subroutine.id}'",
                    )
                }
                targets[output.name] = OutputTarget.Local(targetName)
            } else {
                targets[output.name] = OutputTarget.Global(targetName)
            }
        }

        return FrameCreation.Success(
            Frame(
                subroutine = subroutine,
                locals = locals,
                outputTargets = targets,
                parent = parent,
            ),
        )
    }

    private fun localNames(subroutine: Subroutine): Set<String> =
        (subroutine.inputs + subroutine.outputs).map { it.name }.toSet()

    /** Copies the caller's value into the local frame using the callee's type. */
    private fun copyIn(
        locals: PlayerState,
        input: VarDecl,
        source: PlayerState,
        sourceName: String,
    ): PlayerState = when (input.type) {
        is VarType.Bool -> {
            val flags = locals.flags.toMutableSet()
            if (sourceName in source.flags) flags.add(input.name) else flags.remove(input.name)
            locals.copy(flags = flags)
        }
        is VarType.Integer, is VarType.IntMin, is VarType.IntRange, is VarType.ItemCount ->
            locals.copy(
                stats = locals.stats + (input.name to (source.stats[sourceName] ?: 0)),
            )
        is VarType.ItemList -> locals
    }

    private fun initialiseDefault(locals: PlayerState, output: VarDecl): PlayerState =
        when (output.type) {
            is VarType.Bool -> locals
            is VarType.Integer, is VarType.IntMin, is VarType.IntRange, is VarType.ItemCount ->
                locals.copy(stats = locals.stats + (output.name to 0))
            is VarType.ItemList -> locals
        }

    private fun writeOutputsToGlobal(
        frame: Frame,
        global: PlayerState,
    ): Pair<PlayerState, List<StateDelta>> {
        var state = global
        val deltas = mutableListOf<StateDelta>()
        for (output in frame.subroutine.outputs) {
            val target = frame.outputTargets[output.name] as? OutputTarget.Global ?: continue
            when (output.type) {
                is VarType.Bool -> deltas += if (output.name in frame.locals.flags) {
                    StateDelta.SetFlag(target.name)
                } else {
                    StateDelta.RemoveFlag(target.name)
                }
                is VarType.Integer, is VarType.IntMin, is VarType.IntRange, is VarType.ItemCount ->
                    deltas += StateDelta.SetStat(
                        target.name,
                        frame.locals.stats[output.name] ?: 0,
                    )
                is VarType.ItemList -> Unit
            }
            state = writeOut(state, output, target.name, frame.locals)
        }
        return state to deltas
    }

    private fun writeOutputsToParent(frame: Frame) {
        val parent = frame.parent ?: return
        var state = parent.locals
        for (output in frame.subroutine.outputs) {
            val target = frame.outputTargets[output.name] as? OutputTarget.Local ?: continue
            state = writeOut(state, output, target.name, frame.locals)
        }
        parent.locals = state
    }

    /** Writes a local value into [target] using the output parameter's type. */
    private fun writeOut(
        target: PlayerState,
        output: VarDecl,
        targetName: String,
        source: PlayerState,
    ): PlayerState = when (output.type) {
        is VarType.Bool -> {
            val flags = target.flags.toMutableSet()
            if (output.name in source.flags) flags.add(targetName) else flags.remove(targetName)
            target.copy(flags = flags)
        }
        is VarType.Integer, is VarType.IntMin, is VarType.IntRange, is VarType.ItemCount ->
            target.copy(
                stats = target.stats + (targetName to (source.stats[output.name] ?: 0)),
            )
        is VarType.ItemList -> target
    }
}
