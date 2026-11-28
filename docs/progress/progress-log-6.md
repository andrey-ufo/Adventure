# Progress log — Ф6 onward

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Continuation from [`progress-log-5.md`](./progress-log-5.md), which was
sealed when the Ф5 gate closed. File structure follows the
**one-phase-per-file** rule in
[`../documentation-rules.md §2`](../documentation-rules.md#2-file-format-and-naming):
this file covers **Ф6 (UI/UX foundation, reader and accessibility)** and
only Ф6.

**Phases in this file:**

- **Ф6 — UI/UX foundation, reader and accessibility.** U11–U14 and
  U2–U7, U9.base, U4.base. U1, U4.advanced, U8, U10 and U15 remain
  Nice/out of scope.

## File boundary

Read this file **from the bottom** to see what is currently in progress.
Entry format is the same as in
[`progress-log.md`](./progress-log.md#entry-format).

---

- **2026-11-27 · Ф6.1 — U11 screen map and navigation baseline**

  **DoD** *(translated from `checklist-tracker.md` §Ф6.1; original is
  Russian)*. `docs/ui-screens.md` lists the actual screens, has a
  transition diagram and back-stack description, and a manual audit shows
  that a user can return to the menu from every screen without dead-end
  Back presses. The scene-map screen was removed from scope; U11 does not
  create new screens.

  **What changed (13 files).**

  1. `docs/ui-screens.md` — new evolving baseline: Compose screen
     inventory, primary transitions, back-stack rules/examples, reader
     overlays, dead-end audit, known gaps, and the Phase 6 redesign hook.
  2. `docs/index.md` — developer link to the UI map.
  3. `app/.../ui/settings/SettingsScreen.kt` — visible «← Назад» header
     action; `onBack` callback.
  4. `app/.../ui/logs/LogViewerScreen.kt` — visible «← Назад» header
     action; `onBack` callback.
  5. `app/.../ui/AppNavHost.kt` — Logs/Settings use the existing
     `navigateBackOrMenu()` fallback.
  6. `app/.../data/EditorUiState.kt` — removed unused
     `expandedMapChapters`.
  7. `app/.../ui/editor/AdventureEditorViewModel.kt` — removed unused map
     accordion state and methods.
  8. `app/.../data/EditorModels.kt` — removed the stale "editor's map"
     reference from KDoc.
  9. `app/.../res/values/strings.xml` — removed the empty MAP string
     section.
  10. `docs/checklist-tracker.md` — actual Compose screen names in U11;
      removed `AdventureMapFragment` from U11 and L3/Ф7.6 (L3 now
      traverses IR/`EditorModels`).
  11. `docs/roadmap.md` — removed stale `AdventureMap*` file rows.
  12. `docs/progress/progress-log-6.md` — this log.
  13. `docs/progress/progress-log.md` — added the Ф6 log to the index.

  **Decisions.**

  - **D1 — minimal U11.** Preserve the current Reader/Editor → Menu
    semantics: Back from Menu returns to the reader/editor, so the user
    can continue. Do not redesign the menu hub here; that is U12.
  - **D2 — scene map removed.** No `AdventureMapFragment` replacement.
    L3 translation-context export will walk the IR/editor models.
  - **D3 — visible Back on Settings/Logs.** System Back already worked;
    the explicit action makes the path discoverable and uses the same
    pop-or-Menu fallback as Library/Statistics.
  - **D4 — the map is evolving, not stable.** U12/U14/U9 may change the
    screen set; every new screen must update `docs/ui-screens.md` and the
    checklist/roadmap first.

  **User benefit.** Every top-level screen now has a visible way back or
  to the menu, and the navigation/back-stack contract is written down
  before Phase 6 changes the interface.

  **What was NOT done.**

  - U12 unified menus; the current Reader/Editor → Menu push semantics
    and possible duplicate Menu entry are documented, not changed.
  - The unreachable `editor_search` route is documented as a known gap;
    no toolbar entry was added.
  - No new screens, no visual redesign, no automated navigation test.
  - Reader-screen smoke was not re-run on this build; the reader route
    is unchanged and remains covered by its unit/instrumented tests.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, 9 existing warnings.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - Manual smoke on Realme (debug, seeded debug SAF tree):
    - Menu → Settings → «← Назад» → Menu ✅
    - Menu → Logs → «← Назад» → Menu ✅
    - Menu → Library → «Меню» → Menu ✅
    - Menu → Editor → «Меню» → system Back → Editor ✅

  **Next — Ф6.2.** U12 unified app/reader/editor menu; decide whether the
  menu hub keeps the reader/editor underneath and resolve the
  `editor_search` entry gap.

---

- **2026-11-27 · Ф6.2 / Ф6.2a — flat global kebab, quest details, Search cleanup (U12 partial, U16 done)**

  **DoD** *(translated from `checklist-tracker.md`)*. One flat global
  navigation entry point; Settings reachable from primary screens in ≤2
  clicks; Logs only from Settings; editor/reader local actions are
  grouped dropdowns, not duplicate global items. A new details screen
  sits between Library and Reader and shows the full adventure description.

  **What changed.**

  1. `ui/GlobalMenu.kt` — new flat `⋮` dropdown: Library, Editor,
     Reader/Continue (disabled without a last adventure), Statistics
     steps, Settings. Current section disabled; `DropdownMenu`, not a
     separate screen.
  2. `utils/LastAdventureStore.kt` — persists the last reader URI so
     «Читалка / Продолжить» can resume; `AppNavHost` also keeps it in
     Compose state.
  3. `ui/AppNavHost.kt` — global navigation helper, details route,
     reader opening updates the last URI, Settings → Logs, Menu loses
     Logs, Editor/Reader new callbacks.
  4. `ui/list/AdventureListScreen.kt` — kebab replaces «Меню»; row click
     opens details.
  5. `ui/list/AdventureDetailsScreen.kt` + `AdventureDetailsViewModel.kt` —
     full description, author, chapter/scene counts, «Читать», loading/
     error/retry; route `adventure_details`.
  6. `ui/statistics/StatisticsScreen.kt` — kebab replaces «Меню».
  7. `ui/settings/SettingsScreen.kt` — kebab; «Открыть логи» next to the
     logging toggle.
  8. `ui/logs/LogViewerScreen.kt` — kebab; copy/clear stay local.
  9. `ui/editor/EditorToolbar.kt` — «Действия» is now a grouped anchored
     dropdown (`Сцена` / `Приключение` / `Проверка`); global kebab is
     separate; save status stays in the top bar.
  10. `ui/editor/EditorRoute.kt`, `EditorDestination.kt` — dropped the
      duplicate main-menu destination/callback.
  11. `ui/reader/ReaderRoute.kt`, `AdventureReaderScreen.kt` — kebab in
      the HUD; preview marks Editor as current, normal reading marks Reader.
  12. Legacy full-screen search removed: `SearchScreen.kt`, `SearchRoute`,
      `EditorDestination.Search`, `AppRoutes.Search`, its strings and
      stale comments. `SceneSearchBlock` and the extracted
      `SearchResultRow.kt` remain; inline search is untouched.
  13. `ui/menu/MenuScreen.kt`, `MenuDestination.kt` — Logs removed from
      the main menu; only Settings opens it.
  14. `docs/ui-screens.md` — new flat menu, details route, Search
      clarification, kebab coverage, known gaps.
  15. `docs/checklist-tracker.md` — U12 `◐`, new U16 `☑`, Ф6.2/Ф6.2a,
      updated Phase 6 gate; `docs/roadmap.md` — U17 start-screen
      preference (Menu / Library / last-read scene), not implemented.
  16. `docs/progress/progress-log-6.md` — this entry.

  **Decisions.**

  - **Flat menu without categories**, exactly as agreed. No Logs item;
    Logs is a Settings child.
  - **Kebab = global navigation only.** Local actions stay local:
    editor grouped «Действия», reader local menu, Logs copy/clear,
    Library import.
  - **MenuScreen stays the default hub** for now; kebab is absent there,
    present on Library. Start-screen choice is U17, later.
  - **Details screen added now**; it is a separate U16 task per the U11
    rule about new screens.
  - **Search**: only the inline `SceneSearchBlock` remains. The old
    full-screen route was dead after Ф4.13 and was deleted.

  **User benefit.** From any primary screen the user can reach another
  primary screen in two clicks; the library now leads to a readable
  adventure page instead of jumping straight into the reader; logging is
  in one predictable place; the editor no longer has a long horizontal
  action strip.

  **What was NOT done.**

  - Kebab is not yet on editor child screens and reader overlays; they
    still use Back to the parent. U12 remains `◐`.
  - U12 local-menu dedupe is partial: reader local menu still has
    Settings/Main, which can be cleaned up with the child-kebab pass.
  - Start-screen preference (U17) is roadmap-only.
  - No automated navigation tests; smoke was manual.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, **8 warnings**
    (down from 9; no new warnings).
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - Manual smoke on Realme (debug; real adventure in the selected SAF
    folder):
    - Menu shows Library/Editor/Statistics/Settings; Logs no longer in Menu ✅
    - Library has `⋮`; flat menu shows the five destinations ✅
    - Library → Details: full description, counts, «Читать» ✅
    - Details → Reader; Reader HUD has `⋮`; Reader → Settings → Back → Reader ✅
    - Settings → «Открыть логи» → Logs; Logs has `⋮` ✅
    - Editor: `Действия ▾` shows grouped sections and actions; `⋮` present ✅
    - Editor `⋮` → Reader/Statistics/Settings paths available ✅

  **Next — finish U12.** Add the kebab to editor child screens and reader
  overlays; then remove remaining duplicate local entries (reader
  Settings/Main) and close U12 before U13.

---

- **2026-11-27 · U12 completion + Logs/empty-state polish**

  **What changed.**

  1. `GlobalMenu.kt` — `GlobalMenuController` + `LocalGlobalMenu` + no-arg
     `GlobalMenuButton()` for child route scopes.
  2. `AppNavHost.kt` — `EditorChildScope` provides the kebab for every
     editor child route.
  3. Editor child headers — kebab added to EditMeta, Variables, Initial
     State, Templates, Subroutines, Help, Validation and Story Stats.
  4. `ReaderOverlayHeader.kt` — kebab added; all reader overlays now carry
     `currentSection`/`canOpenReader`/`onNavigateGlobal`.
  5. `ReaderMenuScreen` — removed the duplicate «Настройки» local item;
     settings is global-only. Main menu stays local because the global
     menu has no Home item.
  6. `LogViewerScreen` — removed the «Логи» heading; Back, Copy, Clear and
     kebab remain.
  7. `EditorScenesList` — empty state is a `TextButton`: «Сцен пока нет.
     Нажмите, чтобы добавить первую»; it calls the same add-scene action.
  8. `EditorRoute` — shared `onAddScene` used by toolbar and empty state.
  9. Removed now-unused strings `logs_title` and `reader_menu_settings`.
  10. Checklist: U12 `☑`, U16 `◐` (new detail actions pending), U18
      project manager `☐`; `docs/ui-screens.md` kebab coverage updated.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED** (Slots UI test updated
    for the new overlay parameters).
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, **8 warnings**.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - Device smoke: kebab on Library/Details/Reader/Settings/Logs, Logs
    entry only from Settings, clickable empty-state text, editor actions.
    The Realme dropped USB twice during the last checks; Logs-title
    removal and the new child kebab are verified by compile/code audit but
    not by a final screenshot.

  **Still open from the same request.**

  - Save-status indicator redesign (diskette vs alternatives) — needs a
    decision.
  - Details actions «Начать заново», «Загрузить слот», «Удалить
    приключение» — U16 `◐`.
  - Project Manager in `NewAdventures` — U18, new task Ф6.14.
  - Point 4 of the request was cut off (“Нам нужен экран …”); needs
    clarification.

---

- **2026-11-27 · Save-safety decision (U19 open)**

  Agreed model:

  - Draft autosave and file state are **independent**.
  - Leaving the editor with a stale file shows only **«Сохранить» /
    «Выйти»**. «Сохранить» writes the file; «Выйти» leaves the draft
    recoverable.
  - No file write on `ON_STOP` until atomic write/backup exists. If added
    later, it must write a separate emergency file, never overwrite the
    primary.
  - SVG sources go to `design/icons/` (24×24, `#1F1F1F`, 2px strokes,
    transparent); converted to `app/src/main/res/drawable/*.xml`.
  - U19/Ф6.15 added; U18 now depends on it.

---

- **2026-11-27 · U19 save safety implemented**

  **What changed.**

  1. `design/icons/` — four source SVGs added by the owner; converted to
     `app/src/main/res/drawable/ic_editor_draft_syncing.xml`,
     `ic_editor_draft_saved.xml`, `ic_editor_file_dirty.xml`,
     `ic_editor_file_saved.xml` (`24×24`, `#1F1F1F`, single-colour).
  2. `EditorUiState` — added `FileSaveStatus` (`CLEAN`/`DIRTY`) alongside
     the draft `SaveStatus`; the toolbar now has two independent
     indicators with `contentDescription` and tint.
  3. `EditorToolbar` — removed the wide «Сохранено» text; draft icon
     cycles arrows/check, file icon is floppy/check.
  4. `EditorRouteSupport` — draft autosave/undo/redo/restore mark the file
     dirty; file save/load sets it clean; `saveAdventureToFile` returns
     Boolean and updates file status on success/failure.
  5. `EditorRoute` — BackHandler and global-navigation guard show
     «Файл не сохранён» with only «Сохранить» / «Выйти»; dismiss cancels.
  6. `AppNavHost` — central guard also covers global kebab from editor
     child screens.
  7. `design/icons/README.md` — `file_saved` clarified as a check with
     underline, distinct from the draft check.
  8. Checklist: U19/Ф6.15 `☑`; U18 now unblocked.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — 0 errors, **8 warnings**.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - USB smoke: initial editor shows «Черновик сохранён»/«Файл сохранён»;
    adding a scene switches only the file icon to «Файл не сохранён»;
    Back opens the two-button dialog; «Сохранить» writes the file and
    exits; «Выйти» returns to Menu with the draft still restored on the
    next editor open.

  **Still open.** U16 detail actions and U18 project manager; neither is
  implemented yet.

---

- **2026-11-28 · Floppy save button + empty-editor hint**

  - The dirty-file floppy is now an `IconButton` with a 48dp target;
    tapping it calls `saveAdventureToFile`. Its content description is
    just «Сохранить файл», without a persistent warning.
  - The clean-file state remains a non-clickable green file check.
  - Empty editor adds the hint «Чтобы сохранить свои правки в файл,
    нажмите на значок дискеты.» under the add-first-scene action.
  - `design/icons/README.md`, U19/Ф6.15 wording updated.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL; unused `editor_file_dirty`
    string removed.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - USB smoke: adding a scene switches the file icon to «Сохранить файл»;
    tapping it changes it to «Файл сохранён» without leaving the editor.

---

- **2026-11-28 · U16 details actions complete**

  **What changed.**

  1. `AdventureDetailsScreen` now has «Читать», «Начать заново»,
     «Загрузить слот» and «Удалить приключение»; content scrolls.
  2. «Начать заново» confirms, deletes autosave progress, flushes the
     progress store, keeps manual slots, and opens the reader.
  3. «Загрузить слот» opens the reader with the slots overlay already
     visible (`Reader(openSlots = true)`), reusing `ReaderSlotsScreen`.
  4. «Удалить приключение» confirms, deletes the JSON document, autosave
     and all slots, clears the last-adventure URI if it matches, and
     rebuilds the Library entry so the deleted row disappears.
  5. `SlotStore.deleteAll` + `SlotManager.deleteAllSlots` added for a
     direct, non-decoding cleanup.
  6. `AppRoutes.Reader` gained `openSlots`; `ReaderRoute` /
     `AdventureReaderScreen` initialize the slots overlay from it.
  7. U16/Ф6.2a marked `☑`; UI map updated.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, 8 warnings.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - USB smoke: details shows all four actions; restart and delete
    confirmations open with the expected text; «Загрузить слот» opens the
    reader directly on «Сохранения». Delete was cancelled in smoke to
    keep the test adventure.

---

- **2026-11-28 · U18 project manager (A, C-ready seams)**

  **What changed.**

  1. `projects/ProjectRef` — stable project handle: id, file URI, file
     name, title, author, modified time.
  2. `projects/ProjectRepository` — C-facing seam: list/create/duplicate/
     delete; no SAF in UI.
  3. `projects/SafProjectRepository` — `NewAdventures` implementation:
     JSON list, title/author metadata, sorted by modification, template
     creation with one start scene, unique filenames, duplicate copy,
     delete.
  4. `projects/ProjectDraftManager` — drafts keyed by `ProjectRef.id`.
  5. `projects/EditorProjectSession` — current bound project persisted
     across process recreation.
  6. `EditorRouteSupport` — project-aware draft save/restore; direct save
     writes to the bound file; new/unbound saves bind to the created file;
     unrelated loads can preserve the previous project draft.
  7. `ProjectManagerViewModel` + `ProjectManagerScreen` — list, create,
     duplicate, delete confirmation, loading/empty/error messages, open.
  8. Navigation: Menu → Projects, Editor «Действия» → Projects; opening a
     project replaces an existing editor entry; deleting the current
     project clears the session and its draft.
  9. `docs/roadmap.md` — U18-C workspace (metadata/versions/history/
     backups) added as the successor; A supplies the repository/session
     seams.
  10. Checklist U18/Ф6.14 `☑`; `docs/ui-screens.md` updated.

  **Known limitations (deferred to U18-C or a follow-up).**

  - The old single editor draft is not automatically migrated into a
    project; it still works for unbound editing.
  - Changing the adventure title does not rename the project file.
  - No publish-to-Library flow, version history, backup or trash.
  - The project manager is a list, not a richer workspace.

  **Verified.**

  - `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, 8 warnings.
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - USB smoke: Menu → Projects; create «Новый квест» opens a bound editor;
    editing + floppy saves back to that project; duplicate creates
    «Новый квест (копия)» and opens it; delete confirmation removes the
    copy and refreshes the list.

---

- **2026-11-28 · Closure pass before U13**

  - U18 restore hardened: if the editor ViewModel is empty and a
    persisted project session exists, the editor now loads that project's
    draft or file instead of leaving a blank editor bound to the project.
    Menu → Create clears the session; opening an unrelated file via SAF
    also clears the project binding.
  - U16 destructive smoke completed on disposable copies:
    «Начать заново» opened the reader at the start; «Удалить
    приключение» removed the copied JSON, its progress/slots, refreshed
    Library, and left the original `test_adventure.json` intact. Test
    copies were removed.
  - U18 process-death/draft smoke: added a scene to a bound project,
    force-stopped the app, reopened Editor via Library → kebab, and got
    the project draft back with «Сохранить файл» pending.
  - `:app:testDebugUnitTest` — **150/150 PASSED**.
  - `:app:lintDebug` — 0 errors, 8 warnings; the two baseline notices are
    still informational only.

---

- **2026-11-28 · U13 theming specification stable**

  **Decisions.**

  - 2-tier flat semantic tokens: app tokens + reader tokens; no
    component-token tier.
  - Quest theme is a `theme.json` sidecar in the quest folder; transitional
    locator also accepts `<quest-file>.theme.json` for the current flat
    storage.
  - Quest packaging is folder-first; ZIP is import/export transport, not
    live runtime.
  - User themes stay app-private (DataStore) with explicit JSON
    import/export; quest themes are read-only sidecars.
  - Reader modes: Author / Adaptive (default) / Override; user
    accessibility always wins.
  - App chrome is not author-themeable; author themes apply to the reader
    and its editor preview only.
  - JSON contract: `data/theme/QuestThemeConfig` with `schema_version`,
    `light`/`dark` reader colours, reader typography, shapes, spacing and
    relative assets.
  - `docs/theming.md` stable; linked from `docs/index.md`; U13/Ф6.3 `☑`.

  **What changed.**

  1. `docs/theming.md` — storage, token lists, may/may-not-change tables,
     priority matrix, modes, schema/example, fallback/accessibility rules,
     implementation split, out-of-scope.
  2. `data/theme/QuestThemeConfig.kt` — U13 contract types (reader
     colours, typography, local assets) using `HexColorSerializer`.
  3. `QuestThemeConfigTest` — parses the documented example and validates
     schema/version/colours/typography/assets.
  4. `docs/index.md` — theming link.
  5. Checklist U13/Ф6.3 `☑`.

  **Verified.**

  - `QuestThemeConfigTest` — PASSED.
  - Full app test suite: **151/151 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, 8 warnings.
  - U14 runtime is not implemented yet.

---

- **2026-11-28 · U20 folder-first quest storage**

  **Decision:** migrate now, no flat legacy; alpha has no users.

  **What changed.**

  1. `QuestStorage` — canonical folder helpers: `Adventures/<quest>/`
     and `NewAdventures/<project>/`, `quest.json`, `theme.json`, assets;
     stable quest id = folder document id; recursive delete/copy.
  2. `SetupViewModel` seeds `Adventures/test_adventure/quest.json`.
  3. `AdventureListViewModel` enumerates quest folders and reads
     `quest.json`; `AdventureListItem` carries questId/folderUri.
  4. `AdventureImportHelper` creates a quest folder instead of a flat
     JSON file.
  5. `ProgressManager` and `SlotManager` key progress/slots by quest
     folder id, not `quest.json`/file name. `SaveSlotPaths` remains a
     pure key helper.
  6. `AdventureDetailsScreen` deletes the quest folder recursively,
     including theme/assets, then cleans progress/slots.
  7. `SafProjectRepository`/Project Manager operate on project folders:
     list, create, duplicate (folder copy), delete; `ProjectRef.id` is
     the folder id.
  8. `EditorRouteSupport` creates a project folder when saving an
     unbound editor and writes into the bound `<project>/quest.json`.
  9. `docs/theming.md` now has only the canonical locator (no flat
     fallback); `docs/engine-saves.md` documents folder keys; checklist
     U20/Ф6.16 `☑`.

  **Verified.**

  - `:app:testDebugUnitTest` — **151/151 PASSED**.
  - `:app:lintDebug` — BUILD SUCCESSFUL, 0 errors, 8 warnings (4 stale
    baseline notices are informational).
  - `:app:assembleDebug` — BUILD SUCCESSFUL.
  - USB smoke: Library reads `Adventures/test_adventure/quest.json`;
    Reader opens it; project manager creates `NewAdventures/Новый
    квест/quest.json`; deleting a disposable `Adventures/test_delete2/`
    removed its `quest.json` and `assets/` recursively, leaving the
    original intact.

  **Notes.** Existing flat dev files are intentionally ignored; no
  migration code. `NewAdventures` flat files from alpha testing remain
  ignored until manually removed. U13's storage section was updated to
  the canonical folder path.
