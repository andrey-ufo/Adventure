# Gamebook mechanics — coverage

**Status:** evolving. Reference, not a contract. Update when a mechanic moves
between statuses.

This file records **which gamebook mechanics from
[`research/gamebook-mechanics.md`](./research/gamebook-mechanics.md)
Adventure supports today, which are partial, and which are planned** — and
for the unsupported ones, what architectural piece is missing. It is the
answer to "does the checklist need new items for this?" — the short answer
is **no** (see §"Why the checklist does not change"), but the long answer
is the table below.

**Not to be confused with:**
- [`progress/progress-log.md`](./progress/progress-log.md) — chronological
  log of "what was done". This file is a static status map, updated only
  when a mechanic changes status.
- [`roadmap.md`](./roadmap.md) — the full post-Ф10 plan (Ф12+). This file
  points to it; the roadmap defines the order and content of those phases.

## How to read

| Status | Meaning |
|---|---|
| **Supported** | The mechanic is fully expressible with today's IR (`Condition`, `Effect`, `Scene`, `Choice`, `Gate`) and `Engine.step`. No future phase needed. |
| **Partial** | Expressible, but with caveats — e.g. no atomicity, no dedicated counter, semantics pushed onto author or reader. Usable today; a dedicated mechanic may follow in a later phase. |
| **Not supported** | Requires an architectural piece that does not exist yet. "Blocked by" names the piece; "Planned" points to the roadmap phase. |

**Blocked-by codes:**

| Code | What is missing | Where it would live |
|---|---|---|
| **RNG** | A `RandomSource` abstraction injected into `Engine.step` (deterministic in tests, real RNG in production) | `:engine` — Ф12 |
| **PROC** | A first-class `Procedure` node in IR for multi-step computations (combat rounds, complex crafting) | `:engine` — Ф13. Partially addressed by N6 (subroutines, Ф4.7–Ф4.8); not enough for combat |
| **EXT** | An "external outcome" hook on `Engine.step` (physical dexterity, visual puzzle) — engine receives a pre-decided boolean from `:app` | `:engine` + `:app` — Ф15 |
| **CROSS** | Cross-adventure / cross-run state: `LibraryState`, separate from `ProgressData` | `:engine` + `:app` — Ф16 |
| **MULTI** | Multiplayer mechanics (two-player simultaneous choices, shared state) | `:engine` + network — Ф17 |
| **UI** | Authoring UI for the mechanic (the engine may already support it, but authors cannot express it) | `:app` editor — Ф18 |
| **APP** | Concern that lives entirely in `:app` (wall-clock timing, notifications), not in `:engine` | `:app` — Ф15 or later |

## Summary

**37 entries** (source doc calls them "34 mechanics"; its tables enumerate
37 — 34 base mechanics + 3 sub-variants of `M-RES-RNG`:
`HIGH`/`LOW`/`DVD`).

| Class | Total | Supported | Partial | Not supported |
|---|---|---|---|---|
| M1 Navigation | 7 | 3 | 1 | 3 |
| M2 Resolution | 10 | 2 | 0 | 8 |
| M3 State | 7 | 6 | 0 | 1 |
| M4 Combat | 4 | 0 | 0 | 4 |
| M5 Meta | 9 | 2 | 2 | 5 |
| **Total** | **37** | **13** | **3** | **21** |

## M1 — Navigation

| Code | Name | Status | Blocked by | Planned |
|---|---|---|---|---|
| `M-NAV-BASE` | Basic choice | Supported | — | — |
| `M-NAV-DIRECT` | Direct link | Supported | — | — |
| `M-NAV-RANDOM` | Random link | Not supported | RNG | Ф12.1 |
| `M-NAV-COND` | Conditional / hidden link | Supported | — | — |
| `M-NAV-FREE` | Free movement / map / hub | Not supported | Dedicated navigation model (open-world) | Ф13.4 |
| `M-NAV-CYCLE` | Cyclic return | Partial | `nextScene` can point to an earlier scene, but there is no built-in "already visited" flag. Authors express it via a journal flag manually. | — |
| `M-NAV-EVENT` | Random content event | Not supported | RNG | Ф12.1 |

## M2 — Resolution

`M-RES-RNG` is the base mechanic; `HIGH`/`LOW`/`DVD` are its three
sub-variants (roll direction). All four are "not supported" for the same
reason (RNG), but the roadmap tracks them together as one block.

| Code | Name | Status | Blocked by | Planned |
|---|---|---|---|---|
| `M-RES-NONE` | No check | Supported | — | — |
| `M-RES-DET` | Deterministic check | Supported | — | — |
| `M-RES-RNG` | Stochastic skill check | Not supported | RNG | Ф12.2 |
| `M-RES-RNG-HIGH` | Roll-high | Not supported | RNG | Ф12.2 |
| `M-RES-RNG-LOW` | Roll-low | Not supported | RNG | Ф12.2 |
| `M-RES-RNG-DVD` | Die-vs-die | Not supported | RNG | Ф12.2 |
| `M-RES-HYBRID` | Hybrid resolution | Not supported | RNG | Ф12.3 |
| `M-RES-PHYS` | Physical dexterity | Not supported | EXT | Ф15.1 |
| `M-RES-PUZZLE` | Puzzle / perception | Not supported | EXT | Ф15.1 |
| `M-RES-BOOST` | Boost check with resource | Not supported | RNG | Ф12.4 |

## M3 — State

| Code | Name | Status | Blocked by | Planned |
|---|---|---|---|---|
| `M-STATE-ATTR` | Attribute management | Supported | — | — |
| `M-STATE-POOL` | Resource pool management | Supported | — | — |
| `M-STATE-INV` | Inventory management | Supported | — | — |
| `M-STATE-KW` | Keywords / flags | Supported | — | — |
| `M-STATE-MIX` | Mixed control | Supported | — | — |
| `M-STATE-BOOK` | Character-sheet bookkeeping | Supported | — | — |
| `M-STATE-BUILD` | Character initialization (rolls at creation) | Not supported | RNG | Ф14 |

## M4 — Combat

| Code | Name | Status | Blocked by | Planned |
|---|---|---|---|---|
| `M-COMBAT-ROUND` | Round-based combat | Not supported | PROC + RNG | Ф13.1 |
| `M-COMBAT-CMP` | Combat-ratio comparison | Not supported | PROC + RNG | Ф13.2 |
| `M-COMBAT-DRAIN` | Combat resource drain | Not supported | PROC | Ф13.1 |
| `M-COMBAT-MULTI` | Multiplayer combat | Not supported | MULTI | Ф17 |

## M5 — Meta-mechanics

| Code | Name | Status | Blocked by | Planned |
|---|---|---|---|---|
| `M-META-TIME` | Turn / time limit | Partial | Authors implement via a counter stat + condition. No dedicated "turn" concept; no "wall-clock days". | Ф17 (turn counter as first-class) |
| `M-META-XFER` | Character transfer | Not supported | CROSS | Ф16.1 |
| `M-META-CRAFT` | Crafting / alchemy | Partial | Expressible as several `ModifyStat` + flag toggles; not atomic (a partial craft on failure can leave inventory inconsistent). | Ф13.3 (atomic `Procedure`) |
| `M-META-REP` | Reputation / social | Supported | — | — |
| `M-META-PERSIST` | Persistent consequences | Not supported | CROSS | Ф16.2 |
| `M-META-MOD` | Modular / DLC | Not supported | Module system (separate from core IR) | Ф17 |
| `M-META-LEGACY` | Legacy mechanics | Not supported | CROSS | Ф16.3 |
| `M-META-NONE` | No meta-mechanics | Supported | — | — |
| `M-META-REALTIME` | Real-time pacing | Not supported | APP | Ф15.2 |

## Architectural gaps (why "not supported")

Every "not supported" mechanic traces back to one of the six gaps below.
Closing a gap unlocks all mechanics that list it in "Blocked by".

| Gap | Mechanics it unblocks | Phase |
|---|---|---|
| **RNG** | `M-NAV-RANDOM`, `M-NAV-EVENT`, `M-RES-RNG*`, `M-RES-HYBRID`, `M-RES-BOOST`, `M-STATE-BUILD` | Ф12 |
| **PROC** | `M-COMBAT-ROUND`, `M-COMBAT-CMP`, `M-COMBAT-DRAIN`, `M-META-CRAFT` (atomicity) | Ф13 |
| **EXT** | `M-RES-PHYS`, `M-RES-PUZZLE` | Ф15.1 |
| **CROSS** | `M-META-XFER`, `M-META-PERSIST`, `M-META-LEGACY` | Ф16 |
| **MULTI** | `M-COMBAT-MULTI` | Ф17 |
| **APP** | `M-META-REALTIME` | Ф15.2 |

## What the foundation already supports (why no new Must)

Four pieces already in the plan make the roadmap feasible without
re-architecting:

1. **N6 (subroutines, Ф4.7–Ф4.8)** — a multi-step call/return mechanism
   from IR. Not enough for combat on its own (combat is a loop with state,
   not a single call), but it is the scaffolding for `Procedure` (Ф13).
2. **Sealed `Condition` / `Effect`** (Ф1.2) — a new variant (e.g.
   `Effect.Roll`) can be added without breaking existing code, and the
   parser rejects unknown variants via `@SerialName` rules. This is what
   makes "add a mechanic without rewriting the engine" possible.
3. **Versioned IR + migrations** (Ф5.3) — every new mechanic bumps
   `Adventure.CURRENT_VERSION` (v2 → v3 → …) and adds a migration for
   old files. `test_adventure.json` exercises each new mechanic (Rule 1
   of [`test-adventure-policy.md`](./test-adventure-policy.md)).
4. **`CompiledAdventure` with O(1) lookup** (Ф1.5.5.1) — the runtime
   indexes are the same shape regardless of how many mechanics exist.
   Combat, RNG, cross-adventure state do not change the "find scene by
   id" lookup — they change what happens **inside** a step, not how a
   scene is found.

**What would change the checklist:** if a future Must-hard requirement
("the app must support combat at release") appeared, Ф12–Ф13 would have
to be pulled before Ф10. There is no such requirement today. See
`docs/roadmap.md` for the full post-Ф10 plan.

## Update procedure

**When you implement a mechanic** (i.e. a gap closes or a new `Effect`/
`Condition`/`Procedure` variant lands):

1. Change its row's status: `Not supported` → `Supported` (or `Partial`).
2. Clear or adjust "Blocked by" (it may still be partial).
3. Move "Planned" to `—` (done).
4. **Add** a new entry to
   [`progress/progress-log.md`](./progress/progress-log.md) — this file
   is a snapshot, the progress log is the history.
5. If the mechanic has an authoring UI, update
   [`authoring-guide.md`](./authoring-guide.md) (Ф10.7) in the same PR.

**When you add a new mechanic to `research/gamebook-mechanics.md`:**

1. Add a row to the corresponding M-class table here with the initial
   status (`Not supported` unless it maps to existing IR).
2. Add it to the appropriate "Architectural gaps" bucket, or add a new
   bucket if no existing gap covers it.
3. Add a phase to [`roadmap.md`](./roadmap.md) if it is not covered by
   an existing one.