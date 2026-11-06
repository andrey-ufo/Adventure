package com.myapp.adventure.engine.ir

/**
 * Player state during an adventure: what the player has right now.
 *
 * **Immutable.** Every change produces a new instance via [copy]. This
 * makes three things trivial that would otherwise be error-prone:
 *
 * 1. **Undo / "step back" after death.** Snapshot the state before the
 *    step; restoring it is `state = savedState`, no deep copy, no
 *    rollback logic. Already used by the reader
 *    (`previousStepSnapshot`, `chapterStartSnapshot`).
 * 2. **Save / load.** A state is a value; serializing it (Ф5/S5) is
 *    straightforward. Snapshots can be compared with `==`.
 * 3. **Unit testing.** `applyEffects(state, effects)` returns a new state;
 *    no shared mutable state between tests, no setup/teardown.
 *
 * The cost — one allocation per change — is negligible in the reader:
 * at most one change per user tap. The editor does not use
 * [PlayerState] at all (it works with `EditorModels`).
 *
 * **Distinct from [InitialState].**
 * - [InitialState] is part of the adventure data: "with what flags/stats
 *   does the player start". Serialized in JSON.
 * - [PlayerState] is runtime state: "where the player is now". Never
 *   serialized as part of [Adventure]; persists via the separate progress
 *   file (Ф5/S5).
 *
 * The reader creates a `PlayerState` from an `InitialState` at adventure
 * start; the analyzer (Ф1.6) uses both to reason about reachable states.
 *
 * **Flags vs stats.**
 * - [flags] — boolean markers ("visited_forest", "knows_secret").
 * - [stats] — numeric values ("health" = 100, "gold" = 5).
 *
 * Which side a variable lives on is determined by its declared type
 * ([VarType.Bool] → [flags]; anything numeric → [stats]). If the same
 * name appears in both, the analyzer reports it — either the author has
 * a genuine naming collision or the type checker (Ф2) will catch it.
 *
 * **No clamping here.** This class is a pure value: it does not enforce
 * that "health" stays non-negative. That policy lives in `Engine.applyEffects`
 * (Ф1.5), because clamping depends on the variable's declared type
 * (`HEALTH`/`ITEM` → `>= 0` after Ф2.8; unbounded for `Integer`). Keeping
 * the policy out of the data type keeps [PlayerState] reusable across
 * reader, analyzer, and any future UI preview.
 *
 * @property flags boolean flags currently true.
 * @property stats numeric values currently held.
 */
public data class PlayerState(
    public val flags: Set<String> = emptySet(),
    public val stats: Map<String, Int> = emptyMap(),
)

/**
 * Creates a [PlayerState] from the adventure's [InitialState].
 *
 * The reader calls this once at adventure start (and on "restart
 * adventure") to build the first [PlayerState] from the JSON-declared
 * starting flags/stats. The engine does not depend on this function:
 * `Engine.step` always takes a [PlayerState], never an [InitialState].
 *
 * Collections are copied (`toSet`/`toMap`) so the [PlayerState] does not
 * alias the [InitialState] lists. Immutability is the whole point of
 * [PlayerState]; sharing a mutable backing would break that invariant.
 */
public fun InitialState.toPlayerState(): PlayerState =
    PlayerState(flags = flags.toSet(), stats = stats.toMap())