# Adventure — Documentation

> Interactive text adventures for Android, gated by real-world steps.

**Status:** pre-alpha. These docs are a living tree; some sections are still
`draft` and will grow as the project stabilizes. See
[`documentation-rules.md`](./documentation-rules.md) for how docs are written
and maintained.

## Start here

Pick the section that matches your role.

### For authors

You write and edit interactive adventures inside the app.

- [Authoring guide](./authoring-guide.md) — how to build an adventure:
  scenes, choices, tags, chapters, step gates, death scenes.
- [Glossary](./glossary.md) — terms used in the app and in these docs.

### For developers

You extend the app, add features, or integrate new step sources.

- [Getting started](./getting-started.md) — clone, build, run, folder layout.
- [Architecture](./architecture.md) — high-level packages and data flow.
- [Documentation rules](./documentation-rules.md) — how docs are written.

### For everyone

- [`README.md`](../README.md) — short overview, quick start, links.
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — how to contribute to the project.
- [`ROADMAP.md`](../ROADMAP.md) — what's planned, one page.

## Status legend

Each doc begins with a `Status:` line. The values mean:

| Status       | Meaning                                                         |
|--------------|-----------------------------------------------------------------|
| **stable**   | Unlikely to change; safe to link from outside the docs.        |
| **evolving** | Correct today, but expected to change; check the date.         |
| **draft**    | Incomplete; may be missing sections or contain placeholders.    |

The pre-alpha status of the project means most docs are `evolving` or `draft`.

## Current structure

- `index.md` — this file (entry point, TOC).
- `documentation-rules.md` — the "constitution" of these docs.
- `glossary.md` — English ↔ Russian terminology reference.

Planned (deferred until the relevant feature stabilizes):

- `authoring-guide.md` — how to write adventures in the app.
- `getting-started.md` — developer onboarding.
- `architecture.md` — high-level architecture overview.
- `troubleshooting.md` — known setup and runtime issues.

The [documentation rules](./documentation-rules.md) explain *why* some docs
are intentionally deferred.

## Contributing to the docs

See [`documentation-rules.md`](./documentation-rules.md) and
[`CONTRIBUTING.md`](../CONTRIBUTING.md) at the repository root.