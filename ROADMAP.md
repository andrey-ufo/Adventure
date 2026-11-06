# Roadmap

**Status:** evolving. This is a one-page planning document, not a promise.
Priorities may change as the project evolves.

Known issues live in the repository's [issue tracker](https://github.com/andrey-ufo/Adventure/issues).
This file only describes direction; it does not track every task.

## Current focus

The project is **pre-alpha**. The current focus is:

- Stabilising the editor and reader UX so that authors can build and play
  adventures end-to-end without surprises.
- Stabilising the step pipeline (sensor and Health Connect) so that step
  gates behave predictably across reboots and app restarts.
- Keeping the documentation foundation honest and up to date
  (see [`docs/documentation-rules.md`](./docs/documentation-rules.md)).

## Next

Planned next, once the current focus is stable enough:

- **Step-gated choices** — per-choice `remainingSteps`, the same mechanism as
  chapter gates.
- **Achievements** — a `StepAchievementEngine` that reads from
  `StepHistoryManager` (e.g. "≥50 000 steps in the last 7 days → +10 stamina").
- **Adventure unlocks** — compare `StepHistoryManager.getTotalSteps()` against
  per-adventure thresholds.
- **Ranks** — a pure function of `getTotalSteps()`; the statistics screen
  already shows a rank progress bar as a template.

These build on the read APIs of `StepHistoryManager` and must not open
`progress.json` or `step_history.json` directly.

## Later

Desirable, but dependent on earlier work:

- **JSON adventure format specification** — a formal spec for the adventure
  file format, written only once the format stabilises.
- **Deep architecture docs** — sequence diagrams and per-class notes, written
  once the feature set stabilises.
- **English UI locale** — the Russian → English mapping is being prepared in
  [`docs/glossary.md`](./docs/glossary.md); the actual Android resources
  (`values-en/`) will follow.
- **Screenshots and tutorials** for the authoring guide — deliberately
  deferred until the UI stabilises.

## Post-Ф10

Everything after the checklist 2.1 is accepted lives in
[`docs/roadmap.md`](./docs/roadmap.md). It covers three groups:

- **Gamebook mechanics** (Ф12–Ф18) — support for more formats and
  mechanics from gamebooks beyond the current feature set: RNG, combat,
  character initialization, external outcomes (physical dexterity,
  puzzles), cross-adventure state, meta-mechanics, authoring UI for all
  of the above. Each mechanic's current status is in
  [`docs/gamebook-mechanics-coverage.md`](./docs/gamebook-mechanics-coverage.md).
- **Nice-to-have** — the 17 explicit Nice items + 10 `.advanced`
  extensions from the checklist, each with an entry trigger.
- **Distribution / community** — Play Store, F-Droid, Steam (conditional
  on a JS runtime), community docs.

No dates. No promises. Every item there is triggered by a concrete
question, not by a calendar. This one-page `ROADMAP.md` stays the
high-level summary; `docs/roadmap.md` is the full plan.

## Not planned (for now)

Explicitly out of scope at this stage:

- Public release to app stores.
- Backend services or cloud sync of adventures or progress.
- Migration of the docs to MkDocs / Docusaurus / any static-site generator.
- A `CHANGELOG.md` — there are no releases yet.
- A `CODE_OF_CONDUCT.md` — the project is too small for a formal policy;
  common-sense kindness is expected (see `CONTRIBUTING.md`).

If you think something belongs on this list, open an issue.