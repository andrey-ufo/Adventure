# Roadmap — post-Ф10

**Status:** evolving. Planning document, not a promise. Priorities may
change; the current contract is [`checklist-tracker.md`](./checklist-tracker.md).

This is the **detailed** roadmap for everything **after** the checklist
2.1 is accepted (after gate Ф10). It covers three groups:

1. **Gamebook mechanics** (Ф12–Ф18) — support for more formats and
   mechanics from [`research/gamebook-mechanics.md`](./research/gamebook-mechanics.md).
   Which mechanics are supported today and which gap blocks each one is
   recorded in [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md).
2. **Nice-to-have** items from Appendix B of the checklist (S3 undo,
   PWA, Steam, plugins, analytics, …).
3. **Distribution / community** — how the app reaches people once the
   core is stable.

**Not to be confused with:**
- [`../ROADMAP.md`](../ROADMAP.md) — one-page "current focus / next /
  later", human-readable for newcomers. It points here for depth.
- [`checklist-tracker.md`](./checklist-tracker.md) — the contract for
  Ф0–Ф10. This file starts where that one ends.
- [`research/gamebook-mechanics.md`](./research/gamebook-mechanics.md) —
  the source classification of gamebook mechanics. This roadmap does
  **not** redefine mechanics; it schedules them.
- [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md) —
  which mechanics are supported **today**. This roadmap says when the
  unsupported ones arrive.

## Where we are

The checklist 2.1 closes at gate Ф10 with 40 Must and 19 explicit Nice
items. See [`checklist-tracker.md`](./checklist-tracker.md). Everything
in this roadmap is **after** that point and is **not** required for
acceptance.

## Part 1 — Gamebook mechanics (Ф12–Ф18)

Seven phases. Six architectural gaps (RNG, PROC, EXT, CROSS, MULTI, APP)
from [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md)
close all 21 unsupported mechanics. The foundation that makes these
phases tractable without re-architecting already exists:

- sealed `Condition`/`Effect` (Ф1.2) — new variants are additive;
- N6 subroutines (Ф4.7–Ф4.8) — scaffolding for `Procedure`;
- versioned IR + migrations (Ф5.3) — every phase bumps
  `Adventure.CURRENT_VERSION`;
- `CompiledAdventure` O(1) lookup (Ф1.5.5.1) — the runtime does not
  change shape, only what happens inside a step.

**Ordering rationale:** RNG is first because most other gaps depend on
it (combat, character init, hybrid, boost). Procedures are second
because combat is the largest single feature. External outcomes and
cross-adventure state are independent and can be reordered. Authoring UI
is last, after the engine mechanics exist, mirroring the pattern of Ф9
(author tools after the engine and reader are stable).

### Ф12 — RNG foundation

**Goal:** a `RandomSource` abstraction + first IR nodes that consume it.

**Unlocks:** `M-NAV-RANDOM`, `M-NAV-EVENT`, `M-RES-RNG*` (HIGH / LOW /
DVD), `M-RES-HYBRID` (partial — with PROC in Ф13),
`M-RES-BOOST`.

**Depends on:** Ф10 (stable engine); no dependency on Ф13–Ф17.

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф12.1 | `RandomSource` interface + `Engine.step(randomSource, …)` overload. Production: `SecureRandom`; tests: deterministic `SeededRandomSource`. | 2 | `Engine.step` accepts `RandomSource`; existing tests unchanged (overload with default); one test proves two runs with the same seed produce the same outcomes |
| Ф12.2 | `Effect.Roll` / `Condition.Roll` — the three roll directions (HIGH, LOW, DVD). IR v3 + migration v2 → v3 (identity — old files simply have no rolls). | 3 | `ConditionParser` accepts `roll_high(2d6 + skill >= 10)`, `roll_low(2d6 <= luck)`, `roll_dvd(2d6 + spd vs 2d6 + spd)`; golden updated; analyzer rejects malformed roll specs |
| Ф12.3 | `M-RES-HYBRID` — a roll combined with a condition (`if has_sword && roll_high(...)`) | 1.5 | A scene with `has_sword` AND a roll works; analyzer warns when the roll depends on a stat that is never set |
| Ф12.4 | `M-RES-BOOST` — resource-spending reroll / modifier (`spend 1 luck to reroll`) | 2 | A choice with `boost(luck, 1, reroll)` either spends and rerolls, or is `Rejected(Blocked(...))` when the resource is missing |
| Ф12.5 | `M-NAV-RANDOM` — random link: `next_scene` determined by a roll | 1 | A scene's exit rolls a die, result indexes into a list of scene ids; both branches playable; `test_adventure.json` has a demo |
| Ф12.6 | `M-NAV-EVENT` — random content event: RNG picks an **effect list**, not a transition | 1.5 | A scene rolls, applies one of two effect lists; state differs measurably between seeds |
| Ф12.7 | Docs + `test_adventure.json` extended with one demo per roll type + `docs/engine-rng.md` | 1.5 | All M-RNG mechanics in `gamebook-mechanics-coverage.md` marked Supported; golden updated; `docs/engine-rng.md` stable |

**Gate Ф12:** all six sub-mechanics work end-to-end on `test_adventure.json`; `RandomSource` is injectable; the same seed produces byte-identical `PlayerState` after a full playthrough.

**Scope guard.** Do **not** add "fudge" / "reroll entire playthrough" / hidden luck counters. Only what the coverage map lists under RNG. Anything else is a new mechanic → new roadmap row.

### Ф13 — Procedures and advanced navigation

**Goal:** a first-class `Procedure` node in IR, plus open-world / hub navigation.

**Unlocks:** `M-COMBAT-ROUND`, `M-COMBAT-CMP`, `M-COMBAT-DRAIN`,
`M-META-CRAFT` (atomicity), `M-NAV-FREE`.

**Depends on:** Ф12 (combat needs RNG for to-hit rolls).

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф13.1 | `Procedure` node in IR (call / return with local state, bounded loop count). Reuses the stack idea from N6 but adds local variables and a step counter. | 4 | A `Procedure` with a loop and local state compiles, runs, and is testable in isolation; analyzer rejects unbounded loops |
| Ф13.2 | `M-COMBAT-ROUND` — one round: attacker roll, defender roll, loser takes damage. Implemented as a `Procedure`. | 3 | A combat scene runs until one side reaches 0; each round visible in tests; `test_adventure.json` has a demo combat with a win and a loss path |
| Ф13.3 | `M-COMBAT-CMP` — combat-ratio table (Lone Wolf style): a `Procedure` that maps `(attacker_skill − defender_skill, roll)` to damage | 2.5 | Table-driven combat works; the table is IR data (not code); analyzer checks the table is complete for all ratios |
| Ф13.4 | `M-META-CRAFT` — atomic crafting: a `Procedure` that checks ingredients, consumes them, produces the result, or rolls back on failure | 2 | A failed craft leaves the state unchanged (test: 3 ingredients, fail, 3 remain); a successful craft is atomic |
| Ф13.5 | `M-NAV-FREE` — open-world / hub navigation: a scene with a list of "rooms" (scene ids) reachable from it; a persistent "current room" in progress | 3 | A hub scene with 4 rooms playable; returning to the hub remembers where the player was; `docs/engine-navigation.md` |
| Ф13.6 | Docs + `test_adventure.json` combat + hub demos + golden | 2 | All PROC + `M-NAV-FREE` mechanics marked Supported; `docs/engine-procedures.md` stable |

**Gate Ф13:** combat, crafting, and open-world navigation all work on `test_adventure.json`; the `Procedure` node is analysable (no infinite loops, no unbounded locals); analyzer reports combat-table gaps as errors, not warnings.

**Scope guard.** Combat is **single-player, turn-based**. No real-time, no grid, no spell-crafting loop. `M-COMBAT-MULTI` is Ф17. Crafting is **atomic one-step**, not a tree of recipes.

### Ф14 — Character initialization

**Goal:** build the starting `PlayerState` from rolls / choices, not from a hardcoded `initial_state`.

**Unlocks:** `M-STATE-BUILD`.

**Depends on:** Ф12 (rolls).

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф14.1 | `Adventure.startBuild: BuildSpec?` in IR — rolls and/or choice menus (`roll 1d6+6 for skill`, `choose one of 4 disciplines`); replaces `InitialState` when present, augments when absent | 3 | A v4 adventure with `start_build` produces a `PlayerState` from rolls/choices; legacy `initial_state` still works unchanged |
| Ф14.2 | Editor UI: "Character creation" tab in `AdventureEditorFragment` | 2 | Author can add rolls / choice menus; preview shows a sample `PlayerState` generated by three different seeds |
| Ф14.3 | Docs + `test_adventure.json` demo + golden | 1.5 | `M-STATE-BUILD` marked Supported; migration v3 → v4 (identity: old files have no `start_build`) |

**Gate Ф14:** a new adventure can define `start_build` with a stat roll and a choice menu; three different seeds produce three different, valid starting states; a legacy adventure without `start_build` is unchanged.

### Ф15 — External outcomes and real-time pacing

**Goal:** hooks for mechanics that the engine cannot decide on its own —
physical dexterity, puzzles, wall-clock timing.

**Unlocks:** `M-RES-PHYS`, `M-RES-PUZZLE`, `M-META-REALTIME`.

**Depends on:** Ф10 only (independent of Ф12–Ф14).

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф15.1 | `Condition.ExternalOutcome(name)` — a condition the engine treats as "decided by the caller"; `:app` provides the boolean (mini-game result, drawn line, found object) | 2 | A scene gated by `external_outcome("draw_line")` plays: `:app` shows the mini-game, returns the result, engine advances. Test uses a stubbed outcome |
| Ф15.2 | `M-META-REALTIME` — wall-clock pacing (delay before a choice, timer countdown) as an `:app`-level feature that gates the `StepResult` | 2 | A choice with `delay_seconds(120)` is disabled until the timer expires; `:app` shows countdown; engine knows nothing about wall clock |
| Ф15.3 | Docs + `test_adventure.json` demo + golden | 1.5 | `M-RES-PHYS`, `M-RES-PUZZLE`, `M-META-REALTIME` marked Supported; `docs/engine-external.md` |

**Gate Ф15:** an external outcome can gate a choice; the engine never sees the mini-game code; a wall-clock timer can gate a choice without keeping the app awake.

### Ф16 — Cross-adventure state

**Goal:** state that survives across adventures and playthroughs.

**Unlocks:** `M-META-XFER`, `M-META-PERSIST`, `M-META-LEGACY`.

**Depends on:** Ф10 (saves, S1–S6) + Ф13 (procedures, for skill carry-over if any).

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф16.1 | `LibraryState` (separate from `ProgressData`) — per-library, not per-adventure. Fields: which books finished, which endings seen, per-book "persistent" flags | 3 | Two adventures in a library share `LibraryState`; progress of one does not overwrite the other |
| Ф16.2 | `M-META-XFER` — character transfer between adventures of a series: `Adventure.accepts_transfer_from: List<String>` | 2 | Playing "Book 2" can import the final `PlayerState` of "Book 1"; conflicts (skill missing) reported, not silently defaulted |
| Ф16.3 | `M-META-PERSIST` / `M-META-LEGACY` — flags set in one playthrough that persist to the next; legacy modifiers | 3 | A flag set in run 1 changes the start of run 2 (test with two linked runs) |
| Ф16.4 | Docs + `test_adventure.json` demo (two-adventure series) + `docs/engine-library-state.md` | 2 | `M-META-XFER` / `PERSIST` / `LEGACY` marked Supported; migration of any new IR fields |

**Gate Ф16:** two adventures in one library can transfer a character; a persistent flag set in one is visible in the next; `LibraryState` is not lost on app restart.

### Ф17 — Meta-mechanics

**Goal:** the remaining exotic mechanics.

**Unlocks:** `M-COMBAT-MULTI`, `M-META-MOD`, `M-META-TIME` (turn counter as first-class).

**Depends on:** Ф12 (RNG), Ф13 (combat), Ф16 (library).

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф17.1 | `M-META-TIME` — a first-class turn / day counter with an author-visible action cost per step; exhaustion is `StepResult.Rejected(Blocked(...))` or `Success(isDead = true)` per author spec | 2 | A playthrough with a 30-day limit ends correctly; the analyzer shows estimated "days to finish" per branch |
| Ф17.2 | `M-META-MOD` — modular content: a side IR fragment (`Module`) that an adventure can opt into; collision on ids reported | 2.5 | A small module adds one scene and one item to a base adventure; test verifies the merge and a collision report |
| Ф17.3 | `M-COMBAT-MULTI` — two-player simultaneous combat | 5 | Two devices, one match, both see the same outcome. **Only if** Ф17.1 / Ф17.2 land and there is demand. Otherwise parked. |
| Ф17.4 | Docs + `test_adventure.json` demo + `docs/engine-meta.md` | 2 | All `M-META-*` marked Supported (except multi, which may stay Partial) |

**Gate Ф17:** `M-META-TIME`, `M-META-MOD` work end-to-end; `M-COMBAT-MULTI` either lands or is explicitly parked in `gamebook-mechanics-coverage.md` with a rationale.

**Scope guard.** Multiplayer is **offline two-device** (local network, no server). If it needs a backend, it moves out of the roadmap into a separate product decision.

### Ф18 — Authoring UI for new mechanics

**Goal:** bring the author tools up to date with Ф12–Ф17, mirroring what
Ф9 did for the base feature set.

**Depends on:** Ф12–Ф17.

| ID | Task | Days | Done when |
|---|---|---|---|
| Ф18.1 | Editor for `Procedure` (combat and crafting): a visual loop editor, not a text DSL | 4 | Author builds a 3-round combat with two effects in one scene; preview shows the effect on `PlayerState` |
| Ф18.2 | Editor for rolls: choose direction, dice, threshold; live preview with a "roll 10 times" button | 2 | Author sees the distribution of outcomes before playing |
| Ф18.3 | Editor for cross-adventure links: pick `accepts_transfer_from`; show `LibraryState` keys available | 2 | Author links book 2 to book 1; a "test transfer" button shows the resulting state |
| Ф18.4 | Editor for external outcomes: pick mini-game template; define the boolean mapping | 2 | Author picks "draw line" mini-game, defines "success = line does not intersect"; preview stubs the result |
| Ф18.5 | End-to-end audit: a full authoring scenario covering RNG + combat + transfer | 2 | The scenario runs without leaving the app; results in `docs/authoring-tools.md` |
| Ф18.6 | Docs | 1.5 | `docs/authoring-tools.md` and `docs/authoring-guide.md` cover every new mechanic |

**Gate Ф18:** the editor exposes every mechanic that Ф12–Ф17 added; the end-to-end authoring scenario succeeds; the analyzer is up to date (all new checks surface inline, per A5 from Ф9).

## Part 2 — Nice-to-have from Appendix B

17 explicit Nice items + 10 `.advanced` extensions. Grouped by domain;
each row says what makes it actionable (the "entry trigger"). None of
these block acceptance.

| ID | Item | Trigger |
|---|---|---|
| S3 | Undo | After Ф5 (delta model exists) |
| S4 | Backlog (history viewer) | After S3 |
| S7 | Compact history | After S5 + P2 (i.e. after Ф5) |
| P3 | Compact snapshots | After S5 + P2 |
| U1 | Themes (dark / light, separately from system) | After U9.base (Ф6) |
| U8 | Reduced motion (extended) | After U9.base |
| U10 | Lint-choice | After A5 (Ф9) |
| L5 | Plurals | After L1–L4; StringId already designed for plural forms |
| L7 | Incremental localization | After L4, when the catalog exceeds ~500 strings |
| E2 | PWA | If E1.base returns to scope, or as a standalone export once Ф7 stable |
| E3 | Mobile distribution (Play Store / F-Droid) | After Ф10, once the app is stable |
| E4 | Steam | Only if E1.base returns to scope (needs JS runtime) |
| E6 | Embedding (integrate the reader into another app) | After E5 (schema + round-trip) |
| A2 | Graph visualisation | After A5 (realtime linter); it is a visualisation of the analyzer, not the other way around |
| A7 | Merge tool | After N5 (explicit merge) and A2 |
| X1 | Plugins | After X3.base (stable docs) and A5 |
| Q3 | Analytics | ⚠ Conflicts with Q2.base (no telemetry). Only opt-in, local, with explicit consent |

**`.advanced` extensions** (do not have separate rows; they are extensions
of the parent Must and are picked up opportunistically):

| Extension | Parent | When it becomes actionable |
|---|---|---|
| N6.advanced (loops in subroutines) | N6 | With Ф13 (`Procedure` already needs bounded loops) |
| N7.advanced (beyond AND/OR/count) | N7.base | If authors ask; needs an ADR |
| U4.advanced (automatic contrast adjustment) | U4.base | If users ask |
| L8.advanced (dynamic language loading) | L8.base | If more than 3 languages are needed |
| E1.advanced (full PWA offline) | E1 | If E2 (PWA) enters scope |
| A6.advanced (random-walk test generator) | A6 | If authoring needs more test data than the sample file |
| P1.advanced (10k+ scenes) | P1.base | If a real adventure hits the 2k ceiling; needs a measurement first |
| P4.advanced (incremental / cancellable analysis) | P4.base | If the analyzer becomes a UI bottleneck on a 2k-scene project |
| X3.advanced (community docs) | X3.base | If contributors ask for an external wiki |
| Q2.advanced (IndexedDB / local cache) | Q2.base | Only with Q3 (analytics), which is opt-in and unlikely |

## Part 3 — Distribution / community

Not a phase; a set of decisions that open after acceptance (Ф10).

- **E3 Mobile distribution** — Play Store and F-Droid. Needs: signed
  release build, privacy policy, store screenshots. Triggers only after
  Ф10 + a stable `docs/` set (X3.base from Ф10 already covers this).
- **E4 Steam** — only if E1.base (single-file HTML) returns to scope.
  Without a JS runtime, there is no Steam build.
- **Community docs** — see X3.advanced in Part 2. Only when contributors
  ask for a wiki or forum; a pre-alpha project does not need a community
  platform.

No dates. No promises. Every item here is triggered by a concrete
question, not by a calendar.

## How to update this file

- **Add a mechanic to Ф12–Ф18** — first add the mechanic to
  [`research/gamebook-mechanics.md`](./research/gamebook-mechanics.md),
  then add the status row to
  [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md)
  with "Planned: Фxx" — this roadmap already has the phase.
  Only if the mechanic does not fit **any** phase, add a new phase here.
- **Change a phase's content** — update the phase table; also update
  the "Unlocks" list; also update the corresponding rows in
  [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md)
  so the roadmap and coverage map stay in sync.
- **Close a phase's gate** — add an entry to
  [`progress/progress-log.md`](./progress/progress-log.md); update the
  affected rows in coverage map from "Planned: Фxx" to "Supported".
- **Add a Nice-to-have** — add a row to Part 2 with its trigger;
  if the Nice-to-have implies new code in `:engine`, add a phase.
- **Promote a Nice-to-have into a phase** — move the row from Part 2
  to the appropriate phase; update coverage map if it unlocks a mechanic.

## See also

- [`../ROADMAP.md`](../ROADMAP.md) — one-page summary
- [`checklist-tracker.md`](./checklist-tracker.md) — Ф0–Ф10
- [`gamebook-mechanics-coverage.md`](./gamebook-mechanics-coverage.md) —
  what works today
- [`research/gamebook-mechanics.md`](./research/gamebook-mechanics.md) —
  source classification
- [`research/gamebooks-classification.md`](./research/gamebooks-classification.md) —
  source classification of gamebooks (7 axes)
- [`progress/progress-log.md`](./progress/progress-log.md) — the log