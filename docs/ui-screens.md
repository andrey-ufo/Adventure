# UI screens and navigation

**Status:** evolving (U11 baseline + U12 menu work, 2026-11-27).

This document maps the screens that exist **today**, how they connect, and
how Back behaves. It is a navigation baseline, not a visual redesign brief.
Phase 6 is expected to keep changing this map (U12 menu follow-ups, U14
theme, U9 accessibility); update this file in the same change as any new
screen or route.

## Scope

- The app is a **single Activity** (`MainActivity`) with one Compose
  `NavHost` (`AppNavHost`). There are no Fragments or XML screen layouts.
- The old scene-map screen (`AdventureMapFragment` / `AdventureMapAdapter`)
  was removed from scope. Translation context export (Ф7.6/L3) will
  traverse the IR/`EditorModels`, not a map screen.
- Full-screen reader overlays and dialogs are **not** navigation
  destinations; they are Compose state inside the reader.
- `Setup` is the only start destination. It is removed from the back stack
  after setup, so Back from `Menu` leaves the app.
- `MenuScreen` remains the default start screen. A setting to choose
  Menu / Library / last-read-scene on launch is a future task, not
  implemented here.

## Global kebab menu

The global entry point is a **flat `⋮` dropdown**, top-right on primary
screens. It contains only navigation, in this fixed order:

```text
Библиотека
Редактор
Читалка / Продолжить   (disabled when no last adventure)
Статистика шагов
Настройки
```

Rules:

- No category headers and no local actions in the global menu.
- Logs are **not** in the global menu. `Настройки → Открыть логи` is the
  only entry point.
- The item for the current section is disabled, not hidden, so the order
  stays stable.
- `Читалка / Продолжить` is disabled when `LastAdventureStore` has no URI.
- The global menu uses the same anchored `DropdownMenu` pattern as the
  editor's local actions; it is not a separate screen.
- Kebab is present on Library, Statistics, Logs, Settings, Editor, Reader
  and Adventure Details. It is absent on Menu (the hub) and Setup.

### Current kebab coverage

| Screen | Kebab | Current section |
|---|---|---|
| Menu | no | — |
| Setup | no | — |
| Library | yes | Library |
| Adventure Details | yes | none (Library remains enabled) |
| Statistics | yes | Statistics |
| Logs | yes | none (Settings remains enabled) |
| Settings | yes | Settings |
| Editor main | yes | Editor |
| Editor child routes | yes (via `EditorChildScope`) | Editor |
| Reader scene | yes | Reader (preview: Editor) |
| Reader overlays | yes (via `ReaderOverlayHeader`) | Reader (preview: Editor) |

U12 kebab coverage is complete. Child screens and overlays still keep
their Back action; the kebab is additive.

## Screen inventory

| Route | Composable | Role | Entered from |
|---|---|---|---|
| `setup` | `SetupScreen` | Folder selection and first-run permissions | App start / no saved folder |
| `menu` | `MenuScreen` | Default hub: library, editor, statistics, settings | Setup; Back from primary screens |
| `library` | `AdventureListScreen` | Import and select adventures | Menu or global kebab |
| `adventure_details` | `AdventureDetailsScreen` | Full adventure description, author, chapter/scene counts, «Читать» | Library row |
| `statistics` | `StatisticsScreen` | Step statistics | Menu or global kebab |
| `logs` | `LogViewerScreen` | View/copy/clear the app log | Settings only |
| `settings` | `SettingsScreen` | Permissions, Health Connect, theme, fonts, logging/logs | Menu or global kebab |
| `editor` | `EditorRoute` → `EditorScenesList` | Edit the current adventure | Menu → Create or global kebab |
| `editor_meta` | `EditMetaRoute` | Adventure metadata | Editor actions |
| `editor_variables` | `VariablesRoute` | Variable declarations | Editor actions |
| `editor_initial_state` | `InitialStateRoute` | Initial flags/items/stats | Editor actions |
| `editor_templates` | `TemplateListScreen` | Choice templates | Editor actions |
| `editor_subroutines` | `SubroutinesRoute` | Subroutines | Editor actions |
| `editor_help` | `HelpRoute` | Editor help | Editor actions |
| `editor_validation` | `ValidationIssuesRoute` | Validation issues | Editor actions |
| `editor_stats` | `StoryStatsRoute` | Story statistics | Editor actions |
| `reader` | `ReaderRoute` → `AdventureReaderScreen` | Read an adventure | Details («Читать») or editor preview |

### Search

`SceneSearchBlock` is the inline search above the active scene in the
editor. It is reachable and working.

The old full-screen `SearchScreen` / `SearchRoute` / `EditorDestination.Search`
was removed: after Ф4.13 the inline block replaced it, leaving an unused
route and duplicate UI. Do not reintroduce a separate search screen without
a new checklist task.

### Editor child routes

`EditorRoute` owns the activity-scoped `AdventureEditorViewModel`; every
child route uses that same ViewModel and returns with `popBackStack()`.
Children are not reachable as start destinations, so their Back always has
an editor entry to return to. Editor local actions are a grouped dropdown
(`Сцена` / `Приключение` / `Проверка`), opened from «Действия».

### Reader overlays

These replace the reader content and are closed with `BackHandler`; they
do not change the Navigation back stack.

| Overlay | Opened from | Closed by |
|---|---|---|
| Health | HUD heart | Back / header |
| Inventory | HUD bag | Back / header |
| Journal | HUD book | Back / header |
| Reader menu | HUD «Меню» (normal mode) | Back / header |
| Slots | Reader menu → «Сохранения» | Back / header |
| Warnings | Reader menu → warning item | Back / header |
| Debug | HUD debug (preview mode only) | Back / header |

Dialogs (restart confirmation, slot delete, blocking init errors, startup
warnings, clear-log, draft restore) are transient and have no route.

## Primary transitions

```text
Setup ──(folder + permissions)──> Menu          [Setup removed]
Menu ──> Library
Menu ──> Editor ──> editor child routes          [child Back → Editor]
Menu ──> Statistics
Menu ──> Settings ──> Logs
Library ──> Adventure Details ──> Reader          [Reader exit → Details]
Library ──> Adventure Details ──Back──> Library
Editor  ──> Reader(previewId)                    [preview exit → Editor]
Editor  ──> Menu                                 [Back → Editor]
Reader  ──> Menu                                 [Back → Reader]
Reader  ──> Settings                             [Back → Reader]

Global kebab ──> Library / Editor / Reader / Statistics / Settings
Reader overlay ──Back──> Reader scene
```

## Back stack rules

1. **Setup is not resumable.** `Setup → Menu` uses
   `popUpTo<Setup> { inclusive = true }`; Back from Menu exits the app.
2. **Menu remains the hub, but Reader/Editor are preserved behind it.** The
   reader/editor «Главное меню» action navigates to `menu` without popping
   the current screen. Back from Menu returns to the game/editor. This is
   intentional for “continue” and is a candidate for a later hub model.
3. **Global navigation is additive.** `⋮` navigates with
   `launchSingleTop`; it does not clear the stack. The current section is
   disabled. This keeps the previous screen available to Back.
4. **Top-level Back.** Library/Statistics/Logs/Settings have a visible
   Back or valid system Back; their old «Меню» buttons were replaced by the
   kebab. `navigateBackOrMenu()` remains the fallback when there is no
   previous entry.
5. **Adventure Details sits between Library and Reader.** Back returns to
   Library; «Читать» opens the reader. The last opened URI is stored for
   «Читалка / Продолжить».
6. **Editor children pop.** EditMeta, Variables, InitialState, Templates,
   Subroutines, Help, Validation and StoryStats call `popBackStack()`.
7. **Reader overlays are local state.** `BackHandler` closes the overlay
   first; it does not pop the reader. A second Back from the scene exits
   the reader.
8. **Preview reader returns to the editor.** Its exit action pops the
   reader entry; the editor ViewModel is Activity-scoped and keeps the
   working draft.

### Example stacks

| Action path | Stack after the last action | Back result |
|---|---|---|
| Setup → Menu | `Menu` | exits app |
| Menu → Library → Details → Reader | `Menu, Library, Details, Reader` | Reader → Details → Library |
| Menu → Library → Details → Settings | `Menu, Library, Details, Settings` | Settings → Details |
| Menu → Reader → Menu | `Menu, Library, Details, Reader, Menu` | Menu → Reader |
| Menu → Editor → Menu | `Menu, Editor, Menu` | Menu → Editor |
| Menu → Editor → Meta | `Menu, Editor, Meta` | Meta → Editor |
| Menu → Settings → Logs | `Menu, Settings, Logs` | Logs → Settings |
| Reader → global → Settings | previous stack + `Settings` | Settings → Reader |

## Dead-end audit

- ✅ Every primary screen has a visible Back, a kebab, or a valid system
  Back to its parent.
- ✅ Library/Statistics use the kebab instead of the old «Меню» text
  button; Logs/Settings show Back; Editor/Reader show the kebab.
- ✅ Logs is reachable only from Settings, as agreed; Settings is reachable
  from every primary screen's kebab in two clicks.
- ✅ Reader overlays close before the reader; no overlay traps Back.
- ✅ Editor child routes always have the editor below them.
- ✅ Details has Back and «Читать»; it does not replace Library in the
  stack unexpectedly.
- ✅ Setup cannot be returned to with Back.
- ✅ Menu Back exits the app (root).
- ⚠️ Kebab has no Menu/Home item. Menu remains reachable via Back or the
  reader's local «В главное меню»; start-screen preference is future work.
- ⚠️ Menu duplication from Reader/Editor remains intentional for resume.

## Future UI work tracked here

- **U12 follow-up (outside this phase):** possibly a Menu/Home item and
  richer local-menu grouping; current kebab coverage is complete.
- **Start screen preference:** Menu / Library / last-read scene. Not
  implemented; decide persistence and startup routing first.
- **Project Manager (U18, planned):** list JSON files in `NewAdventures`,
  create a blank project, duplicate, delete, and open in the editor. It
  needs a project-file binding in the editor before implementation.
- **Adventure Details actions (U16, partial):** «Начать заново»,
  «Загрузить слот», «Удалить приключение» are planned; the current screen
  only shows the description/counts and «Читать».
- **Achievements/other screens:** add to the global flat menu and this map
  when they exist.
- **U14:** theme selection in Settings; no new top-level screen expected.
- **U9:** accessibility controls in Settings; no new top-level screen
  expected.
- **Rule:** a new screen must be recorded in the checklist/roadmap first
  and added to this file with its transitions. Do not add a screen only
  inside code.

## See also

- [`checklist-tracker.md`](./checklist-tracker.md) — U11, U12, U16 and the
  Phase 6 gate.
- [`architecture.md`](./architecture.md) — app layers and the reader route.
- [`engine-saves.md`](./engine-saves.md) — reader slots overlay and save
  behaviour.
