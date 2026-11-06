# test_adventure.json policy

**Status:** stable. Enforced by CI.

This document defines the role of `app/src/main/res/raw/test_adventure.json`
in the Adventure project. It exists because the file has historically been
treated as a compatibility anchor, which slowed down engine development.
That treatment is wrong.

## Rule 1 — test_adventure.json is a living example, not an anchor

`test_adventure.json` is the **reference sample adventure**. It must always
reflect the **current** feature set of the engine. It is **not** required to
stay compatible with older IR versions, older parser behaviour, or older
`test_adventure.json` files.

When the engine gains a new feature (new condition operator, new effect
type, new IR field), `test_adventure.json` is updated **in the same pull
request** to exercise that feature.

When the engine **removes** or **changes** a feature, `test_adventure.json`
is updated accordingly. Old constructs are not kept "for compatibility" in
this file.

## Rule 2 — CI enforces it via the golden test

The golden test (`:engine:test`, added in Ф1.4) parses
`test_adventure.json` and compares the result against
`engine/src/test/resources/golden_ir.json`.

If `test_adventure.json` does not parse under the current engine, or if its
IR differs from the golden snapshot, **CI fails**. The pull request cannot
be merged.

This makes Rule 1 mechanical: it is impossible to change the engine without
updating `test_adventure.json` and the golden snapshot.

## Rule 3 — legacy compatibility lives elsewhere

Backward compatibility with older adventure files is **not**
`test_adventure.json`'s job. It is covered by:

- `engine/src/test/resources/legacy/` — hand-crafted files in older
  formats (`v0_adventure.json`, `v1_adventure.json`, …).
- `MigrationTest` in `:engine` — parses each legacy file through the
  migration chain and asserts the result matches the current IR.

Legacy files are updated only when the migration code changes. They do not
block new features. `test_adventure.json` and the legacy files are
independent.

## Rule 4 — what "update test_adventure.json" means

Updating does not mean "make it parse". It means:

1. **New feature** — add a scene / choice / effect / condition that uses
   it, so the feature is exercised end-to-end.
2. **Changed feature** — rewrite the example to use the new form (e.g.
   `has_sword` → `sword > 0` when `Has` becomes Bool-only in Ф2).
3. **Removed feature** — remove the example, or replace it with the
   replacement form.
4. **Golden snapshot** — regenerate `golden_ir.json` from the updated
   `test_adventure.json` and commit it in the same PR.

## Rule 5 — CONTRIBUTING.md and PR template reference this file

The checklist item in `CONTRIBUTING.md` ("update `test_adventure.json`
whenever the app gains new features") and the corresponding item in
`.github/PULL_REQUEST_TEMPLATE.md` both point here. Reviewers reject PRs
where the engine changed but `test_adventure.json` (and the golden
snapshot) did not.

## Rule 6 — compact sample vs volume stress tests vs TestAdventures

`test_adventure.json` is a **compact** reference sample: 20–30 scenes that
exercise every current feature. It is **not** a volume test. Three things
are deliberately separated:

| Purpose | Where | Why |
|---|---|---|
| Compact reference sample, ships to users as an example | `test_adventure.json` → `TestAdventures/` on the user's device (debug builds only, see below) | Golden IR stays readable; first-launch UX stays clean |
| Volume stress tests (1k / 5k / 10k scenes) | `StressAdventureGenerator` in `engine/src/test/kotlin`, generated in memory | No 50–250 MB JSON in git, no slow CI, no unreadable golden diffs |
| Backward-compat legacy files | `engine/src/test/resources/legacy/` | See Rule 3 |

**Never** grow `test_adventure.json` to thousands of scenes. A file with
10 000 scenes is ~3 000 000 words — five times "War and Peace", ten times
more than the largest published gamebook — and would: (a) make every
`git diff` of the golden unreadable, (b) slow every CI run, (c) reach the
user's first launch as a megabyte monster. The realistic one-author ceiling
is ~500–2000 scenes; "10 000+" is a Nice-to-have target for `P1.advanced`
(`ROADMAP.md`), tested via the generator, not via the sample file.

**TestAdventures instead of Adventures:** the sample is copied into a
separate `TestAdventures/` folder (or equivalent), **not** into the user's
`Adventures/` library, and **only in debug builds**. The user's library is
theirs; the sample is ours. This is tracked as a small `:app` task on the
roadmap, to be done when `SetupFragment` is next touched.

## Rule 7 — chapters are pure grouping, gates live on scenes

Since Ф1.5.4 (IR version 2) a **chapter** is pure narrative grouping: id,
title, list of scenes. It carries **no step gate**. A **gate** is a
property of a single **scene** (`scenes[].gate: Gate?`): the player must
walk N steps after entering the scene before any exit is allowed.

**What this gives authors that was previously impossible:**
- A gate in the middle of a chapter.
- A chapter boundary without a gate (chapters are then just titles over a
  flat list of scenes).
- Multiple gates within one chapter.
- A choice leading to a scene in **another chapter** (chapters are not
  barriers). The analyzer (Ф1.6) warns about suspicious cross-chapter
  jumps, but does not forbid them.

`test_adventure.json` exercises all four: a 100-step gate in the middle of
chapter 1 (`health_test`), a 5-step gate on the last scene of chapter 1,
a 4000-step gate on the last scene of chapter 2, and a cross-chapter
choice (`conditions_test` → `final`). Keep it that way when you add
features — new gate-related features must be reflected in the sample.

**Backward compatibility.** Adventure files created before Ф1.5.4 use
`chapters[].required_steps`. On load, `JsonToIr` migrates a non-zero value
onto the last scene of the **previous** chapter as `Gate(requiredSteps)`
(the same mechanical effect, expressed in the new model). Do **not** add
`required_steps` to new chapters — it is legacy and ignored in v2 files.

**Two safety nets you should know about:**
- `GoldenIrTest` compares **values** (via `golden_ir.json`), catching
  value changes and renames.
- `SchemaFingerprintTest` pins **field names and sealed variants**, catching
  **added or removed** fields even when they carry a default (which would
  otherwise be invisible in the golden, because `encodeDefaults = false`).

When you add a field, a sealed variant, or a new flat IR class, update the
corresponding map in `SchemaFingerprintTest` in the same PR. That update is
the review signal: "the on-disk schema changed intentionally".

## Why this exists

The project is **pre-alpha**. The engine is expected to evolve. Anchoring
its sample data to an old format would mean either (a) freezing features
that could otherwise ship, or (b) maintaining two parallel sample files
forever. Both are wrong for a pre-alpha. Rule 1–6 above keep the sample
file live, the legacy tests separate, the golden readable, and the volume
stress tests out of git.