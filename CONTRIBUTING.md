# Contributing to Adventure

Thanks for your interest in Adventure. The project is **pre-alpha**: features,
data formats, UI and public APIs may change without notice. Contributions are
welcome — see below for how to build the app, report issues, and open pull
requests.

By contributing you agree that your changes are licensed under the project's
[MIT License](./LICENSE).

## Ways to contribute

- **Report a bug** — open an issue using the bug report template.
- **Suggest a feature** — open an issue using the feature request template.
- **Improve the docs** — fixes and additions to `docs/` are welcome; follow
  [`docs/documentation-rules.md`](./docs/documentation-rules.md).
- **Send a pull request** — code, tests, or docs.

Please search existing issues and pull requests before opening a new one.

## Build and run

Requirements:

- Android Studio (latest stable)
- JDK 21 (the project's Gradle toolchain)
- Android device or emulator with `minSdk 26`

From a terminal:

```bash
git clone https://github.com/andrey-ufo/Adventure.git
cd Adventure
./gradlew :app:assembleDebug
```

On first launch the app asks you to choose a folder (via the Storage Access
Framework). The app creates `Adventures/` and `NewAdventures/` inside it, plus
`progress.json`, `step_history.json` and `debug_log.txt`.

## Report a bug

Use the **Bug report** issue template. Include:

- App version (`versionName`) and commit hash, if known.
- Android version and device/emulator model.
- Exact steps to reproduce, in numbered order.
- Expected behaviour vs. actual behaviour.
- Relevant lines from `debug_log.txt` (see below) and, if the bug is visible,
  a screenshot.

### Collecting the debug log

Enable logging in the app (Settings → «Включить запись лога в файл» / "Enable
file logging"), reproduce the bug, then use the in-app log viewer (Menu →
«Логи игры» / "Game logs") to copy the log or open `debug_log.txt` from the
chosen folder. Trim long logs — keep only the relevant window.

## Suggest a feature

Use the **Feature request** issue template. Focus on the user or author
problem you want to solve, not on the implementation. Link to related issues
or roadmap items if relevant.

## Pull requests

Before opening a pull request:

1. Read [`docs/documentation-rules.md`](./docs/documentation-rules.md) if you
   change anything under `docs/`.
2. Make sure the project builds: `./gradlew :app:assembleDebug`.
3. Keep changes focused. One logical change per pull request is easier to
   review and revert than a mixed batch.
4. Describe *what* changed and *why* in the PR description. Use the pull
   request template as a checklist.
5. Update documentation when the behaviour you changed is described in
   `docs/` or in `README.md`.

### Project conventions

- **Source-code comments are in English.** All Russian comments in the code
  must be translated to English.
- **Docs are in English.** See
  [`docs/documentation-rules.md`](./docs/documentation-rules.md) for file
  structure, status markers and link rules.
- **`test_adventure.json` is the reference sample adventure.** It must be
  updated whenever the app gains new features, so it always exercises the
  current feature set.
- **Domain terminology** — use the spellings in
  [`docs/glossary.md`](./docs/glossary.md). When adding a new term, add it to
  the glossary in the same change.
- **Progress log entries are in English.** From Ф2.8 onward. Rationale:
    (a) the project is MIT and its contributors are English-speaking;
    (b) `documentation-rules.md §1` already requires English docs;
    (c) dense Cyrillic content breaks automated digest tools (`gitingest`)
    and GitHub search. See `docs/progress/progress-log.md` for the format.

### Commit messages

Short imperative subject line (e.g. "Fix reader crash on empty chapter"), then
a blank line, then an optional body explaining *why*. No strict format — this
project is too early for conventional commits.

## Code of conduct

Be kind. Assume good faith. This is a small pre-alpha project; the focus is on
making the app better, not on winning arguments.

## Questions

If something is unclear, open a discussion or an issue. There is no private
channel for contributor questions.