# Progress log — index

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Per-task statuses in the phase tables of
[`../checklist-tracker.md`](../checklist-tracker.md) are **not maintained
per-step** — they reflect gate closure only, and may lag behind the logs.
Same for Appendices A–D: update them only when a whole phase gate closes.

## File structure

From `progress-log-3.md` onward, **one log file per phase** — see
[`../documentation-rules.md §2`](../documentation-rules.md#2-file-format-and-naming).

| File | Phases covered | Language |
|---|---|---|
| `progress-log.md` | Ф0.1 (this index) | Russian |
| `progress-log-1.md` | Ф0.x – Ф1.x | Russian (legacy) |
| `progress-log-2.md` | Ф2.4 – Ф2.7 | Russian (legacy) |
| `progress-log-3.md` | Ф2.8 + Ф3 (all sub-steps) | English |
| `progress-log-4.md` | Ф4 (from Ф4.1) | English |
| `progress-log-5.md` | Ф5 | English |
| … | … | … |

**Rule (from `documentation-rules.md §2`).** Each `progress-log-N.md`
from `-3.md` onward covers exactly one phase. Sub-steps stay in the
phase's file. When a phase gate closes, the current file is **sealed**
(last entry names the next file) and the next phase starts a fresh file.
`progress-log.md`, `-1.md`, `-2.md` are Russian-language legacy from
before the Ф2.8 switch to English; they cover multiple phases because
the one-phase-per-file rule did not exist yet.

## Reading the logs

- **Newest entries** are in the highest-numbered file. To know what is
  happening **right now**, read the highest-numbered `progress-log-N.md`
  from the bottom.
- **Grep / search** across all files works (all are plain UTF-8 text).
  `progress-log-1.md` / `-2.md` are Russian, `progress-log-3.md` onward
  are English.
- **Gitingest and similar tools** sometimes misdetect dense Russian text
  as binary. `progress-log-1.md` / `-2.md` may be unreadable by those
  tools; open them in any text editor. `progress-log-3.md` and later are
  pure English and read cleanly.

## Entry format

```
- **YYYY-MM-DD · ФX.Y — short title**

  **DoD.** Quoted from `checklist-tracker.md`.

  **What changed (N files).**

  1. `path/to/file.kt` — description.
  2. ...

  **Decisions.** Agreed-before-coding forks (D1–Dn), one line each.

  **User benefit.** What the author or reader can do now that they
  could not before. Not "code is cleaner" — a concrete capability.

  **What was NOT done.** Explicit deferrals, with the phase that will
  pick each one up.

  **Verified.** `./gradlew …` commands + result counts. Smoke
  scenarios (phone) with ✅ marks.

  **Next — ФX.Y+1.** What the next batch starts with.
```