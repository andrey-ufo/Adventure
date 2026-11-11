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

### S3a — Step back from any scene

**Status:** shipped in Ф1.7.1b-followup (2026-11-08). Recorded here
for traceability.

**Goal:** the player can step back one scene at any point, not
only after death — e.g. walked into a gated scene, decided not to
walk N steps, wants to undo.

**What it needed (small — ~30 lines):**
- **Single-level undo.** `previousStepSnapshot` already holds the
  previous scene's state (saved in `updateChoices` before the
  transition). No `ArrayDeque` is needed: a book-game allows only
  one step back, plus "to chapter start" and "restart adventure"
  — all three snapshots already exist.
- **`previousStepSnapshot = null` after a step back.** Prevents
  chaining. Done in `restoreSnapshot` (single source of truth for
  any caller).
- **UI:** menu item "↩ Шаг назад", visible only when
  `previousStepSnapshot != null`, not in preview mode, and not on
  the death screen (the death screen has its own button).
- **Gate interaction:** `restoreSnapshot` already called
  `resetGate()` — with this feature, that call became functional
  (no longer a no-op). Leaving a gated scene discards its gate
  progress; returning re-engages the gate (repeatable) or shows it
  already passed (non-repeatable).

**Depends on:** nothing new — the state was already there.

**Entry trigger:** shipped alongside Ф1.7.1b (persistence of
`passedGates`) — the two are complementary: without persistence,
a non-repeatable gate's "passed" state would not survive a step
back across app restarts; without step back, the gate progress
could only be reset by restarting the adventure.

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

### Additional Nice items (identified 2026-11-07)

These two blocks were identified during the UI/UX foundation (U11–U14)
and localization (L1–L4) discussions in the checklist. They are Nice-class
work **after** Ф10 and do **not** block acceptance. They live here rather
than in Appendix B because Appendix B is the **frozen** checklist 2.1 list
— new items found later belong to the roadmap, not to the contract.

#### Author themes (U15)

**Goal:** let an author attach a visual style to their adventure —
fonts, colours, background, spacing — so that a horror adventure looks
different from a cozy one, without the user having to change system
settings per book.

**What U13/U14 (Must, Ф6.3/Ф6.4) already provide:** the **mechanism** —
`theme.json` schema, `ThemeManager`, levels (user / author / default),
priority rules (especially with reduced motion and high contrast),
whether the theme travels with the adventure or lives separately. U13/U14
are Must because they define **how** theming works. U15 is the **author
feature** built on top.

**Not to be confused with:** U1 (dark/light as a user preference) — that
is a per-user setting, part of the default theme, not an author feature.

| ID | Task | Days | Depends on |
|---|---|---|---|
| U15.1 | Editor UI: author picks a theme (from a shipped set), overrides individual properties (font, colours, background), previews the reader with the theme applied | 3 | U14 (must), Ф9.3 (preview) |
| U15.2 | Theme distribution: package `theme.json` inside the adventure ZIP (E7), or as a sidecar file `Themes/<adventure-id>.json` — pick one, document in `docs/theming.md` (U13 already reserves the decision) | 1.5 | U13, U14 |
| U15.3 | Inheritance and precedence tests: user's reduced motion and high contrast **win** over the author's theme (accessibility cannot be silently overridden); document the exact rules; automated tests | 2 | U15.1 |
| U15.4 | Docs: extend `docs/theming.md` and `docs/authoring-guide.md` with the author-facing workflow | 1.5 | U15.3 |

**Gate U15:** author can attach a theme to an adventure; the reader uses
it; the user's accessibility settings still win where they should; a
"default" adventure and a "themed" adventure side by side demonstrate the
difference; documentation is stable.

**Entry trigger:** after Ф10 (checklist accepted) **and** after U14 has
shipped and seen at least one release cycle — otherwise we would be
designing the author UI against an unstable user-facing theme mechanism.

#### Translation workflow (T1–T4)

**Goal:** let an adventure be translated into multiple languages, shipped
with all of them, and updated when the source changes — without the
translator needing to work inside the Android app.

**What L1–L4 (Must, Ф7) already provide:** string extraction
(`extractStrings` → `StringCatalog`), stable `StringId` (structural
path, survives text edits), context export, PO/XLIFF round-trip. These
are **file-level** primitives. What they do **not** provide: how multiple
translations of the **same** adventure live together, how a translation
is updated when the source changes, how to validate a translation before
shipping.

| ID | Task | Days | Depends on |
|---|---|---|---|
| T1 | Multi-language packaging: one adventure × N languages, linked by `StringId`. Format: either `adventure.json` + `strings/<locale>.json` inside the ZIP (E7), or a parallel `adventure.<locale>.json` — pick one, document in `docs/localization.md` | 3 | L1–L4 (Ф7), E7 (ZIP) |
| T2 | Translation update flow: diff current source against the last translated source, list stale `StringId`s, mark them in the editor (U10 realtime-linter picks them up). "Update translation" button in the editor | 2.5 | T1, Ф9.2 (linter) |
| T3 | Placeholder validation: `%d`, `{name}`, `${var}` — check that translations contain the same placeholders as the source, in the same positions; reject translations that drop or duplicate a placeholder; automated test in the exporter/importer | 2 | L4 (PO/XLIFF), T1 |
| T4 | Translator UI (minimal): import `.po`, apply to the current IR, show completeness ("142/150 strings translated"), export back; not a full CAT tool, just a round-trip in-app | 2 | T3, Ф9 (editor infrastructure) |

**Gate T1–T4:** an adventure with two translations (ru/en) ships as one
ZIP; the reader picks the language from the user's UI locale; the editor
shows stale strings after a source edit; placeholder mismatches are
blocked at import; a translator can round-trip `.po` inside the app.

**Entry trigger:** after Ф10 (checklist accepted) **and** after L1–L4
have shipped; T1 depends on E7 (ZIP) being stable.
### Data path cleanup (after Ф1.7.5)

The same adventure JSON has **two representations** in the codebase
— `:app/data/Adventure` (Gson POJO, lossy) and `:engine/ir/Adventure`
(kotlinx.serialization, full). IR-only fields (`is_ending`, `gate`,
`gate_repeatable`, `version`, `start_scene`, `variables`) silently
vanish whenever data passes through `:app/data/Adventure`. This is
the class of bug behind Ф1.7.1a-fix (`AdventureListFragment` →
reader) and the `is_ending`-in-preview limitation of Ф1.7.5 (fixed
by adding the field to the POJO, but `gate` / `gate_repeatable`
still drop on the same path — **gates in preview still do not
work**).

Three steps, in order. Each is independently shippable; b3 is
blocked on the exporter (Ф8).

#### b1 — `AdventureListFragment` reads IR

**Goal:** the library list stops going through
`:app/data/Adventure` for reading. `parseAdventureFromUri` →
`JsonToIr.parse` → `:engine/ir/Adventure`. `AdventureAdapter` takes
`List<:engine/ir/Adventure>` (fields `title` / `author` /
`description` are identical to the POJO).

**Why now, why safe:** the list already does not pass
`adventure_json` to the reader (Ф1.7.1a-fix — only
`adventure_file_uri`). So the POJO is used solely for the list
display, and its lossy nature has no observable effect on the
list. Migrating removes a latent path, not a live bug.

**Scope:** ~15 lines, `:app/ui/list/`. No new tests — the existing
`:engine` tests (`RealAdventureTest`, `GoldenIrTest`) already cover
`JsonToIr.parse` on `test_adventure.json`. Smoke: the list still
shows the same title / author / description.

**Depends on:** Ф1.7.5 (shipped 2026-11-09).

#### b2 — `:app/data` gets `gate`, `gate_repeatable`, `version`, `start_scene`

**Goal:** the editor write path emits enough fields for the reader
to see gates and `is_ending` in preview. **No IR migration** — the
`:app/data` POJO is extended to match the adventure JSON exactly
for the fields the reader needs.

**What it does:**
- `:app/data/Scene` += `gate: Int?` (`@SerializedName("gate")`),
  `gateRepeatable: Boolean?`
  (`@SerializedName("gate_repeatable")`).
- `:app/data/Adventure` += `version: Int?`
  (`@SerializedName("version")`), `startScene: String?`
  (`@SerializedName("start_scene")`).
- `EditorScene` += `gateRequiredSteps: Int = 0`,
  `gateRepeatable: Boolean = false` (the editor already has these
  conceptually — `requiredSteps` was for the legacy chapter-level
  gate; move to per-scene).
- `loadFromUri` reads them; `buildAdventure()` writes them.
- UI: a "🎯 Шаговый гейт на этой сцене" checkbox + a steps input,
  mirroring `cb_chapter_end` / `layout_steps` (which currently write
  to `chapter.required_steps` — legacy — and should be **replaced**
  by per-scene `gate`).

**Result:** preview shows the gate indicator for `health_test`
(`gate: 1`), and the editor saves files with `gate` /
`gate_repeatable` / `version` / `start_scene` intact.

**Why not full IR migration now:** `loadFromUri` iterates
`:app/data/Scene` and calls `extractTags(scene, tagDefs)`, which
reads `scene.setFlags` / `removeFlags` / `modifyStats`. IR's
`Scene` has `effects: List<Effect>` instead. Migrating
`loadFromUri` to IR is **~50 lines of logic rewriting**, not a
call-site swap. b2 achieves the observable goal (gates in preview)
without touching that logic. Full migration goes to b3.

**Scope:** ~40 lines, `:app/data/` + `:app/ui/editor/`.
**No unit tests** — `:app/data` is a transitional model that will
be deleted in b3 (after Ф8); unit tests for it would be investment
in throwaway code. Smoke covers it: preview of `health_test`
shows "Пройдено 0 из 1"; editor saves and loads files without
losing `gate` / `gate_repeatable`.

**Depends on:** b1 (independently shippable, but ships right after).

#### b3 — editor on IR; delete `:app/data/Adventure`

**Goal:** the last consumer of `:app/data/Adventure` — the editor
write path — moves to `:engine/ir/Adventure` + `IrToJson` (for
writes) / `JsonToIr.parse` (for reads). `:app/data/Adventure.kt` is
deleted.

**Blocked on:** **Ф8 (`IrToJson`)**. Without an exporter that
serialises IR back to the adventure JSON format (`"has_sword"`,
not `{"type": "has", "name": "sword"}`),
`buildAdventure()` cannot produce a file that `JsonToIr.parse`
will read back. `Adventure.serializer()` is **not** a substitute —
it emits the IR shape, not the adventure shape.

**What it does (after Ф8):**
- `buildAdventure()` returns `:engine/ir/Adventure`; writes via
  `IrToJson.encode(adventure)`.
- `loadFromUri` uses `JsonToIr.parse`; iterates
  `:engine/ir/Chapter` / `:engine/ir/Scene`; `extractTags` reads
  `scene.effects` (`Effect.SetFlag` / `RemoveFlag` / `ModifyStat`).
- `:app/data/Adventure.kt` deleted.

**Pre-written round-trip tests** (in `:engine`, before this
starts):
- `Adventure` (constructed by `test_adventure.json`) →
  `IrToJson.encode` → JSON → `JsonToIr.parse` → IR → structurally
  equal to input.
- These tests will fail to compile before `IrToJson` exists —
  which is exactly the point of "blocked on Ф8".

**Scope:** ~200 lines, 3–4 sections. Requires a **full editor
smoke pass** afterwards: create / load / edit / save / preview /
map / statistics. The editor was not smoke-tested during Ф1.7.1a —
this is the first time we actively test it.

**What NOT to do (for all three):**
- Do not delete `:app/data/Adventure.kt` before b3.
- Do not merge b1 / b2 / b3 into one PR — three independently
  testable steps.
- Do not merge with `format/json/` restructure — different modules,
  different scope.
- Do not write unit tests for `:app/data` in b2 — it is a
  transitional model, deleted in b3.

#### `format/json/` restructure

**Goal:** move JSON-format files under `format/json/` so the
directory layout self-documents the format boundary. No behaviour
change — pure `git mv` + package renames.

**What it touches:**
- `format/JsonToIr.kt` → `format/json/JsonToIr.kt`
- `format/dto/` → `format/json/dto/`
- `format/JsonToIrTest.kt`, `RealAdventureTest.kt`, `GoldenIrTest.kt`,
  `GoldenWriter.kt` → `format/json/`
- `format/SchemaFingerprintTest.kt` → `ir/` — it pins the IR
  schema, not JSON, so it belongs next to the classes it guards.
- `test/resources/golden_ir.json` → `test/resources/json/golden_ir.json`
- `test_adventure.json` is still copied from `:app` — into
  `test/resources/json/`
- `engine/build.gradle.kts`: `processTestResources { into("json") }`;
  `updateGolden` path and `GoldenWriter.GOLDEN_PATH` updated to match.
- Package declarations in the moved files updated
  (`…format` → `…format.json`, `…format.dto` → `…format.json.dto`,
  `SchemaFingerprintTest` → `…ir`).

**What it does NOT change:**
- `ConditionParser` stays in `parse/` — moving it to `ir/` would
  create an `ir → parse` dependency (data depending on parsing
  infrastructure), which inverts the layer direction.
- No `AdventureFormat` / `AdventureImporter` / `FormatDetector`.
  Those are multi-format abstractions (see next entry).
- No `IrToJson`. That arrives with the exporter (Ф8).

**Depends on:** Ф1.7.5 (`is_ending` in the editor) — the same PR
that changes `AdventureEditorViewModel.buildAdventure()` to write
`is_ending` also touches the `:app → :engine` boundary, so doing
`format/json/` in the same window avoids two separate `:app`
round-trips. But they are separate PRs.

**Entry trigger:** immediately after Ф1.7.5 ships. Small, ~1 hour,
no behaviour change (`./gradlew :engine:test` proves it).

#### Multi-format support (Twine / Ink / ChoiceScript)

**Goal:** let Adventure import adventures written in other
gamebook / interactive-fiction formats — Twine (Twee, HTML,
Twison), Ink, ChoiceScript, Yarn, Adventuron.

**Not yet.** This is a **post-Ф10** feature with a concrete trigger:
the first time a real author brings a non-JSON file and asks for
it. Until then, the entire abstraction (`AdventureFormat`,
`AdventureImporter`, `ImportResult`) is designed against **zero**
real formats beyond JSON — the classic YAGNI violation. When the
first non-JSON format arrives, we will have **two** real cases, and
the right abstraction will be obvious; designing now is guessing.

**When the trigger fires, do this** (design reviewed 2026-11-07):

- **Layout: one format = one folder.** `format/json/`, `format/twine/`,
  `format/ink/`. Shared code stays in `format/` (or `format/common/`).
  Do **not** create `parsers/` + `converters/` + `models/` — the
  anti-pattern is a horizontal split that loses the format boundary.
- **`AdventureFormat` interface:** `id: String`,
  `detect(content: String): Boolean` (content sniffing only —
  no hint/extension; those are `AdventureImporter`'s concern),
  `parse(content: String): ImportResult`.
- **`AdventureImporter`:** hint → extension → content sniffing;
  `≥2` detects → `AmbiguousFormat` (never pick silently);
  `0` → `NoFormatFound`.
- **`ImportResult`:** `format`, `adventure: Adventure?`,
  `warnings: List<ImportWarning>`, `errors: List<ImportError>`.
  `ImportError` is **not** `ParseError`: add
  `ImportError.Unsupported(feature, location, detail)` — "parsed
  but IR cannot express" (Twine `<<if>>`, ChoiceScript
  `*gosub_scene`). Wrapping `ParseError.FromParser(errors)` keeps
  parser errors intact.
- **Lossy policy.** Macros → error (not silent loss). Presentation
  (inline HTML/CSS, base64 images) → warning + strip
  (`ImportWarning.PresentationStripped`). Decided inside
  `<Format>ToIr`, never in sub-parsers.
- **Intermediate model** only when (a) the format has >1 sub-format
  (Twee / HTML / Twison) **or** (b) it needs normalisation before IR
  (Ink knot/stitch). Otherwise direct `<Format>ToIr`.
- **`FormatDetector`** is created **only when the second format
  lands** — with one format there is nothing to detect.
- **`docs/import-formats.md`** is written at the same time as the
  first non-JSON format — not before (see
  [`documentation-rules.md`](../documentation-rules.md) on
  not documenting what does not yet exist).

**What NOT to do before the trigger:**
- Do not create empty `twine/`, `ink/` folders "for the future" —
  dead code.
- Do not add `extensions` to IR "so Twine round-trips" — only if a
  concrete round-trip requirement arrives.
- Do not unify the editor write path (`:app/data/Adventure` →
  `:engine/ir/Adventure` + `Adventure.serializer()`) as part of
  multi-format work — that is Ф1.7.5's job, done already.
- Do not build `IrToJson` before Ф8 (export).

**Entry trigger:** a real author brings a non-JSON file. Not
before.

**Depends on:** Ф10 (checklist accepted) **and** the trigger above.

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