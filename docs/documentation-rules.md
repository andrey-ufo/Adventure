# Documentation rules

**Status:** stable. These rules apply to every document under `docs/`.

This file is the "constitution" of the documentation: it defines how docs are
written, structured and maintained. It is expected to change rarely.

## 1. Language

- **All documentation is written in English.**
- Russian is used only for:
    - UI strings that appear in the app, quoted as `«Русский текст»` with an
      English gloss sourced from [`glossary.md`](./glossary.md);
    - domain terms referenced from the glossary.
- Source-code comments are written in English (see `CONTRIBUTING.md`).

## 2. File format and naming

- Plain **Markdown** (GitHub-flavoured). No HTML unless strictly needed.
- File names are **kebab-case** and end with `.md`, e.g.
  `authoring-guide.md`, `documentation-rules.md`.
- One topic per file. If a file grows past ~15 KB or starts mixing topics,
  split it.
- Images live in `docs/images/`. Only add them when a screenshot or diagram is
  strictly necessary — screenshots go stale quickly in a pre-alpha project.

## 3. Structure of every document

Each document starts with:

1. `# Title` — a human title.
2. `**Status:** stable | evolving | draft` — one line, on its own.
3. One or two sentences of purpose — what this document is for, and what it
   is *not* for.

Then the body, then optional "See also" links at the end.

## 4. Status markers

| Status       | Meaning                                                         |
|--------------|-----------------------------------------------------------------|
| **stable**   | Unlikely to change; safe to link from outside the docs.        |
| **evolving** | Correct today, but expected to change; check the date.         |
| **draft**    | Incomplete; may be missing sections or contain placeholders.    |

Guidance:

- Rules, glossaries, onboarding for things that don't churn → **stable**.
- Feature descriptions whose behaviour may still shift → **evolving**.
- Half-written pages, or placeholders that exist only to reserve a slot →
  **draft**, or don't create the file at all.

## 5. Links

- Use **relative links** only (`./other.md`, `../README.md`,
  `./images/foo.png`). Never absolute URLs to the repository.
- Why: the repository name, owner and hosting may change; relative links keep
  working. External URLs (Android docs, Health Connect, etc.) are fine.
- Every doc should be reachable from `docs/index.md`.

## 6. Glossary is authoritative

Domain terms, UI strings and naming conventions are defined in
[`glossary.md`](./glossary.md). When in doubt, follow the glossary. When a new
term is introduced, add it to the glossary in the same change.

## 7. What goes in, what stays out

**Include** (safe, useful):

- Things that are stable or that future contributors will need from day one:
  rules, glossary, onboarding, high-level architecture, invariants.
- Behaviour that users and authors can rely on.

**Defer** (write only when the feature stabilizes):

- Deep per-class documentation, sequence diagrams, call graphs.
- Format specifications that are still changing (e.g. the JSON adventure
  format until it freezes).
- Debug-only features (debug panel, internal simulations).
- Tutorials and walkthroughs built on UI that may still change.

**Never** document:

- Ephemeral state: "currently in progress", "will be finished next week".
  That belongs in issues and `ROADMAP.md`.
- Secrets, tokens, machine-specific paths.
- Contradictory statements with other docs — one source of truth per topic.

## 8. Architecture docs: high level only, for now

`architecture.md` (when written) stays high level:

- Layers and responsibilities.
- Data flow at a coarse grain (sensor → service → history → game).
- Invariants that features must not violate.

It does **not** describe individual classes, method signatures, or internal
algorithms. Those may change as the project evolves; when they stabilize,
promote them to `stable` and document them in depth.

## 9. Keeping docs honest

When you change a behaviour that a document describes:

- Update the document in the same change.
- If the change is too large for a quick update, mark the document as
  `evolving` or `draft` and add a short TODO note at the top of the affected
  section.

Every document is either **truthful today** or **marked as out of date**.
There is no third state.

## 10. Growth path

When a feature or document stabilizes:

1. Promote its `Status:` from `evolving` to `stable`.
2. Add deep content (diagrams, per-class notes, worked examples).
3. Add it to the "See also" links in related docs.

Migration to a static-site generator (MkDocs, Docusaurus, …) happens only if
`docs/` exceeds roughly fifteen files or needs full-text search. Until then,
plain Markdown on GitHub is the source of truth.