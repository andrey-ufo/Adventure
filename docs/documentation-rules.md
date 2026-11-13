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

**Exception:** `docs/progress/progress-log-*.md` files created **before
2026-11-12** are kept in their original Russian for historical accuracy.
New progress log entries (from Ф2.8 onward) are in English.

## 2. File format and naming

- **GitHub Flavored Markdown (GFM)**. This is what GitHub renders, and it
  is what every document in `docs/` and `README.md` must use. GFM includes
  tables, task lists, fenced code blocks with language hints, strikethrough.
  No raw HTML unless strictly needed (e.g. a complex table that GFM cannot
  express).
- File names are **kebab-case** and end with `.md`, e.g.
  `authoring-guide.md`, `documentation-rules.md`.
- One topic per file. If a file grows past ~15 KB or starts mixing topics,
  split it.
- Images live in `docs/images/`. Only add them when a screenshot or diagram is
  strictly necessary — screenshots go stale quickly in a pre-alpha project.
- **Progress log files: one phase per file, from `progress-log-3.md` onward.**
  Each `progress-log-N.md` (N ≥ 3) covers **exactly one phase** of
  [`checklist-tracker.md`](./checklist-tracker.md). Sub-steps within a
  phase (e.g. Ф2.8.1, Ф3.5.2) are **entries inside the phase's file**, not
  new files. When a phase gate closes, the current log file is **sealed**
  (last entry: "Gate ФX closed. This file is now sealed. Continuation in
  `progress-log-(N+1).md`.") and the next phase starts a fresh file with
  a "Continuation from `progress-log-N.md` (phases X–Y)" header.
  **The only historical exception** is `progress-log-3.md` itself, which
  begins at Ф2.8 (the last sub-step of Ф2) to coincide with the Russian →
  English language switch; Ф2.4–Ф2.7 (Russian) live in
  `progress-log-2.md`. See [`progress/progress-log.md`](./progress/progress-log.md)
  for the file index and reading order.
- **Quoting a whole file inside another code block** (a PR description,
  review comment, chat, digest tool, or another markdown document): use a
  **longer** fence than any fence the file itself contains. If the file
  contains three-backtick fences (` ``` `), wrap it in four
  (` ````markdown … ```` `). Markdown closes the outer block on the first
  inner fence of the same length or longer; using one extra backtick
  prevents the outer block from closing prematurely. This is the general
  rule for **any** nested code block, not just `docs/` files.

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

## 11. Research docs

`docs/research/` holds background material — classifications of external
systems, surveys, glossaries of other projects' vocabulary — that informs
Adventure but is **not part of its user-facing or developer-facing
documentation**. Research docs are **exempt** from three rules above:

| Rule | Exemption | Why |
|---|---|---|
| §1 Language — English | May be in any language, including Russian | Often a survey of Russian-language sources; translation loses precision on the source's own terms |
| §3 Status marker | `**Status:**` optional; `**Status:** reference` recommended when present | Research is not a contract; it can be stale without being wrong |
| §6 Glossary is authoritative | Not required to add its terms to the main glossary | It is **about** external systems, not about this project's own vocabulary |

Still required:

- **GFM** (§2) — same rendering on GitHub as everything else.
- **File naming** (§2) — kebab-case `.md`.
- **Reachable from `docs/index.md`** (§5) — under a **"Research"** section,
  not in the "Start here" sections.

Research docs are **not** linked from `CONTRIBUTING.md` or `README.md`.
They are for readers who want background, not for those who want to use or
contribute to Adventure. If a research doc's content becomes load-bearing
for the project (a term enters the glossary, a mechanic enters the roadmap),
the **relevant fragment** is copied into the appropriate non-research doc;
the research doc itself stays as the source.

### 11.1. When to promote a research doc

A research doc stays under `docs/research/` while it is:
- background context for design decisions, **or**
- a reference for external systems, **or**
- a survey whose subject may change independently of Adventure.

It is promoted to `docs/` (and must then follow all rules) when it becomes:
- a specification Adventure code must conform to, **or**
- user-facing documentation, **or**
- an authoritative glossary of Adventure's own terms.