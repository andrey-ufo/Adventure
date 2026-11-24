# i18n policy

**Status:** evolving. Governs where user-facing strings live and how they
are written. This file is the single source of truth until full
localization lands in Ф7; the language rules for documentation itself are
in [`documentation-rules.md §1`](./documentation-rules.md#1-language).

## Where strings live

| Kind of string | Where | Language |
|---|---|---|
| User-facing UI (labels, buttons, toasts, dialog titles, error messages) | `app/src/main/res/values/strings.xml` | RU (source key). EN added in Ф7.3. |
| Log/debug strings (`Logger.log(...)`) | inline in `.kt` | RU (temporary; Ф7.3 may move them). |
| Debug panel labels (`DebugPanelScreen.kt`) | inline in `.kt` | RU (temporary; debug-only, not user-facing). |
| Adventure content (scene text, choice text, tag display names) | `test_adventure.json` / user files | Per-author; **not** translated by the app. |
| Developer-facing strings (exception messages, code comments, KDoc) | `.kt` inline | EN (per `CONTRIBUTING.md`). |

**Why user-facing strings go into `strings.xml`.** Two reasons that matter
today: (a) **digest / source-control readability** — a `.kt` or `.xml`
file with Russian `setText("Меню")` / `android:text="Сохранить"`
some tools (gitingest, some diff viewers) have trouble with; moving the
Cyrillic into one small `strings.xml` keeps the source files largely
ASCII; (b) **ready for Ф7.3** — `values-en/` translation is a lookup
step, not a rewrite.

**Why log / debug / content strings stay outside.** Log strings are
diagnostic, not user-facing (the reader rarely sees them); translating
them to `strings.xml` adds ~80 keys with no reader benefit. Debug panel
labels are for developers only. Adventure content belongs to the author,
not the app's UI.

## Naming convention

- **`snake_case`, prefix by area:**

  | Prefix | Area |
    |---|---|
  | `app_*` | Application-wide (`app_name`). |
  | `menu_*` | Main menu (`MenuFragment`). |
  | `library_*` | Adventure list (`AdventureListFragment`). |
  | `editor_*` | Editor: toolbar, scene card, choice card, map. |
  | `template_*` | Template list screen and edit dialog (since Ф4.3.2a). |
  | `reader_*` | Reader (`AdventureReaderFragment`). |
  | `stats_*` | Statistics (`StatisticsFragment`). |
  | `settings_*` | Settings (`SettingsFragment`). |
  | `setup_*` | First-run setup (`SetupFragment`). |
  | `logs_*` | Log viewer (`LogViewerFragment`). |
  | `search_*` | Search dialog (`AdventureEditorFragment.showSearchDialog`). |
  | `validation_*` | Validation dialog (`AdventureEditorFragment.showValidationDialog`). |
  | `action_*` | Shared action buttons outside dialogs (`action_menu`, `action_back`, `action_undo`, `action_redo`, `action_debug`). |
  | `dialog_*` | Shared dialog buttons (`dialog_save`, `dialog_cancel`, …). |

- **Plurals** use `<plurals name="<area>_<noun>_plural">` — same pattern
  as the existing `steps` (one / few / many / other).

- **Placeholders** use positional format: `%1$d`, `%2$s` — same pattern
  as the existing `stats_rank_progress`.

- **Shared buttons** (`save`, `cancel`, `delete`, `ok`, …) live under the
  `dialog_*` prefix and are reused across screens — do not duplicate
  `dialog_cancel` as `editor_cancel` + `template_cancel`.

## What NOT to do

- **Do not inline user-facing Russian in `.kt` or `.xml`** — use
  `@string/…` in layouts and `getString(R.string.…)` in Kotlin. The
  only exceptions are the two rows in the table above marked
  "RU (temporary)".
- **Do not add `values-en/` yet.** The translation phase is Ф7.3. An
  empty `values-en/` with copies of Russian would make an English
  system show Russian UI with the false appearance that translations
  exist; an empty `values-en/` with no copies would show Russian UI
  anyway (Android falls back to `values/`). Either way, the directory
  buys nothing until real EN strings are ready.
- **Do not translate progress-log entries, KDoc, comments, or
  developer-facing strings** — those follow
  [`documentation-rules.md §1`](./documentation-rules.md#1-language).

## Migration status

- **Ф4-infra (batch 1, this batch):** policy file, `strings.xml` header
  comment + shared dialog buttons.
- **Ф4-infra (batch 2a):** `fragment_*.xml` layouts (editor, list,
  map, reader, log viewer, menu, settings, setup, template list) →
  `@string/…`. Also `action_*` shared button prefix introduced.
- **Ф4-infra (batch 2b):** `dialog_*.xml` and `item_*.xml` layouts →
  `@string/…`, except debug-panel layouts (`dialog_debug_*`,
  `item_debug_*`) which were deliberately left inline. Those layouts were
  deleted in Phase 3.4-e; the debug panel is now `DebugPanelScreen.kt`
  with inline RU labels.
- **Phase 5 (UI migration cleanup):** all remaining `res/layout/*.xml`
  files were deleted — the app is Compose-only, so the layout-related
  notes above are historical.
- **Ф4-infra (batch 3):** all user-facing `setText(...)` /
  `Toast.makeText(...)` / `AlertDialog` titles and buttons in Kotlin →
  `getString(R.string.…)`, except `Logger.log` and debug-panel labels.
- **Ф4-infra (batch 3b-5-fix):** `ruQuantityString` workaround
  (`app/.../utils/PluralHelper.kt`). `<plurals>` rules come from the
  **device** locale, not from the source `values/` directory — on a
  non-`ru` device, Russian plurals get wrong forms
  (`4882 шагов` instead of `4882 шага`). Forcing the Russian locale at
  the call site fixes it for every device until English defaults land.
  **Remove this workaround in Ф7.3**, when `values/` becomes English
  and `values-ru/` carries the Russian forms.
- **Ф7.3:** `values-en/` translation, generated from `strings.xml`.
  After this phase, `ruQuantityString` is deleted and call sites use
  `context.resources.getQuantityString(...)` — the device locale then
  picks the right rules automatically.

## See also

- [`documentation-rules.md §1`](./documentation-rules.md#1-language) —
  language rules for documentation itself.
- [`glossary.md`](./glossary.md) — Russian ↔ English term reference.
- [`checklist-tracker.md`](./checklist-tracker.md) §Ф7.3 — full
  localization phase.