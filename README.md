# Adventure

> Text adventures for walkers — a step-gated interactive fiction engine and
> authoring tool for Android.

**Status:** pre-alpha. Features, data formats, UI and public APIs may change
without notice. The repository is under active development.

Сейчас работа над приложением ведется по плану в файле docs/checklist-tracker.md

## What is this?

Adventure is an Android app for people who enjoy text adventures (such as
*Fighting Fantasy* and similar gamebooks) and walking — and for authors who
want to write text quests. Chapters are unlocked by real-world steps counted
by the phone's step sensor (or Health Connect), so reading progress is tied
to physical activity.

Two primary audiences:

- **Authors** — write, edit and preview interactive adventures inside the app.
- **Developers** — extend the app, add features, integrate new step sources.

End users of the app find everything they need in the in-app UI; this README
and `docs/` are for authors and developers.

## Features (current)

- Adventure editor: scenes, choices, conditional branching, tags
  (health / items / journal), chapter ends with step gates, death scenes,
  initial player state.
- Reader with HUD (health, inventory, journal), save/resume progress, death
  handling (step back / restart chapter / restart adventure).
- Step counter: foreground service using `TYPE_STEP_COUNTER` plus optional
  Health Connect source; 30-day history; monotonic all-time counter.
- Utilities: validator, search, story statistics, map view, JSON
  import/export via the Storage Access Framework.
- Adjustable font size and custom fonts, in-app debug log viewer.

See [ROADMAP.md](./ROADMAP.md) for what is planned.

## Quick start (developers)

Requirements:

- Android Studio (latest stable)
- JDK 21 (the project's Gradle toolchain)
- Android device or emulator with `minSdk 26`

Build and run:

```bash
git clone https://github.com/andrey-ufo/Adventure.git
cd Adventure
./gradlew :app:assembleDebug
```

On first launch the app asks you to choose a folder (via the Storage Access
Framework). Adventure creates two sub-folders inside it:

- `Adventures/` — library of finished works
- `NewAdventures/` — drafts

and a few service files (`progress.json`, `step_history.json`,
`debug_log.txt`) at the root of the chosen folder.

## Documentation

Full documentation lives in [`docs/`](./docs/index.md). Start there.

- **For authors** — [Authoring guide](./docs/authoring-guide.md)
- **For developers** — [Getting started](./docs/getting-started.md),
  [Architecture overview](./docs/architecture.md)
- **Documentation rules** — [docs/documentation-rules.md](./docs/documentation-rules.md)

## Project structure (high level)

- `app/src/main/java/com/myapp/adventure/` — application code
  - `data/` — domain models (Adventure, Scene, Choice, Tag, …) and editor models
  - `service/` — `StepCounterService` (foreground sensor service)
  - `step/` — step source abstraction, Health Connect integration
  - `ui/` — fragments, adapters, editor, reader
  - `utils/` — persistence (`ProgressManager`, `StepHistoryManager`,
    `Logger`), helpers
- `app/src/main/res/` — layouts, drawables, strings, themes
- `app/src/main/res/raw/test_adventure.json` — sample adventure used for testing
- `docs/` — documentation

## Architecture invariants

The step pipeline has three layers, from lowest to highest:

1. **Source** — `StepCounterService` (sensor) or optional
   `HealthConnectStepSource`. Emits step *deltas*; never touches game state
   or `progress.json`.
2. **History** — `StepHistoryManager`, the single owner of step history
   (30 daily buckets + monotonic `totalStepsAllTime`; in-memory cache,
   batched disk writes, `Mutex`-protected).
3. **Game** — `AdventureReaderFragment` reads only aggregates from
   `StepHistoryManager` and owns chapter-gate state (`remainingSteps`,
   `isGateActive`).

Future features (achievements, step-gated choices, adventure unlocks, ranks)
build on `StepHistoryManager`'s read APIs and must not open `progress.json`
or `step_history.json` directly. Full details will be documented in
[docs/architecture.md](./docs/architecture.md).

## Contributing

Read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a Pull Request.

Project conventions:

- **Source-code comments are in English.** All Russian comments in the code
  must be translated to English.
- `test_adventure.json` is the reference sample adventure. It must be
  updated whenever the app gains new features, so it always exercises the
  current feature set.

## License

MIT — see [LICENSE](./LICENSE).