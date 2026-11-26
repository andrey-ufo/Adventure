# Progress log — Ф5 onward

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Continuation from [`progress-log-4.md`](./progress-log-4.md), which was
sealed when the Ф4 gate closed on 2026-11-25. File structure follows the
**one-phase-per-file** rule in
[`../documentation-rules.md` §2](../documentation-rules.md#2-file-format-and-naming):
this file covers **Ф5 (runtime and saves)** and only Ф5.

**Phases in this file:**

- **Ф5 — Runtime and saves** (S1 manual slots, S2 autosave isolation,
  S5 exact state/delta, S6 versioning/migrations, P2 precompiled IR and
  bounded hot path). S3/S4/S7 remain Nice.

## File boundary

Read this file **from the bottom** to see what is currently in progress.
Entry format is the same as in
[`progress-log.md`](./progress-log.md#entry-format).

---

- **2026-11-25 · Ф5.0 — phase opened after Ф4 gate**

  **Context.** Gate Ф4 closed; see the sealing entry in
  [`progress-log-4.md`](./progress-log-4.md). Engine tests **380/380**,
  app tests **51/51**, `:app:assembleDebug` green at the gate.

  **Scope from `checklist-tracker.md`.**

    - **S1 / Ф5.5–Ф5.6:** manual save slots; autosave writes only
      `progress.json` and never overwrites a manual slot.
    - **S5 / Ф5.2:** explicit `StateDelta` model (`Set/Add/Sub/Flag`),
      `apply(base, delta)`; replace implicit `previousStepSnapshot` /
      `chapterStartSnapshot`.
    - **S6 / Ф5.3–Ф5.4:** `ProgressData.version`, `MigrationRegistry`,
      legacy `progress.json` migration with `lastAbsoluteAtSave` preserved.
    - **P2 / Ф5.7–Ф5.8:** precompiled IR cache (no JSON parse per open)
      and bounded allocations in the hot path.
    - **Ф5.9–Ф5.10:** integration tests + `docs/engine-saves.md` stable.

  **Out of scope (Nice):** S3 undo, S4 backlog, S7 compact history. The
  delta model must still be designable so undo can be layered on without
  a rewrite.

  **First task — Ф5.1: design save model** (`SaveState { version,
  baseSnapshot, delta, manualSlots }`), record the decision
  **operation log vs snapshot diff** (checklist recommends operation log
  for S3/N5), and write `docs/engine-saves.md` (draft). No format changes
  are made before that decision is recorded.

  **Next — Ф5.1.** Fork to present before coding: operation log vs
  snapshot diff vs hybrid; migration order (version first); slot file
  layout.

---

- **2026-11-25 · Ф5.1 — save model design (`docs/engine-saves.md`,
  draft)**

  **DoD** *(translated from `checklist-tracker.md` §Ф5.1)*. "Design the
  save model: `SaveState { version, baseSnapshot, delta:
  List<StateDelta>, manualSlots: List<SlotRef> }`; record it in
  `docs/engine-saves.md` (draft). The schema is described; the
  operation-log vs snapshot-diff decision is fixed (operation log is
  recommended — needed for S3/undo and N5/merge); the `version` field is
  mandatory."

  **Owner decisions.**

    - **D1 — operation log.** `SaveState = baseSnapshot + ordered
      StateDelta`. Not a saved diff. Needed for undo/backlog (S3), merge
      analysis (N5) and readable debugging.
    - **D2 — version first.** `version` exists before the delta shape
      changes; v0 → v1 migration is explicit.
    - **D3/D4 — slots:** separate file per slot, inside a per-adventure
      subfolder `saves/<adventureKey>/slot-<uuid>.json`, not a flat
      `saves/` with ~250 files. `adventureKey` = sanitized display name +
      short stable hash; UUID filename avoids collisions/renames.
      Rationale and alternatives are recorded in the document.
    - **D5 — autosave stays `progress.json`** in the root adventure
      folder; slot files live under `saves/`. S2 is guaranteed by
      separate paths.
    - **D6 — slot id is UUID**, human name is metadata inside the file.
    - **D7 — `manualSlots` is not duplicated in every SaveState.** The
      slot directory is the catalog/source of truth; the checklist's
      list is adapted to avoid drift. Documented explicitly.
    - **D8 — `StateDelta` also carries cursor/flow ops** (`EnterScene`,
      applied scene effects, gate, item order, warnings, passed gates),
      not only Set/Add/Sub/Flag. Exact restore needs them.

  **Что в `docs/engine-saves.md` (draft).**

    - Target `SaveState` / `ProgressSnapshot` / `StateDelta` shapes.
    - `apply(base, delta)` semantics: pure fold; Set/Flag idempotent,
      Add/Sub are operations (not resulting diffs); replay once.
    - Deltas come from `Engine.step` (choice/subroutine operations) plus
      reader-recorded scene/gate/cursor ops; no diff reconstruction.
    - v1 `ProgressData`: outer map unchanged, per-entry `version`,
      `base_snapshot`, `delta`. v0 (legacy, missing `version`) migrates
      by wrapping current state, preserving `lastAbsoluteAtSave` catch-up,
      gates, warnings and passed gates. Unknown version = explicit error.
    - Slot folder layout, SAF creation, cap (default 20, no silent
      auto-delete), orphan handling, slot file JSON.
    - Compatibility notes for `PlayerStateSnapshot`, implicit
      `previousStepSnapshot`/`chapterStartSnapshot`, preview mode.
    - Open questions deferred: compaction/undo tail, optional slot
      catalog for very large counts, orphan cleanup, preview text.

  **No code/format changes yet.** This is the design gate before Ф5.2;
  tests and APK are unchanged from the Ф4 gate:
  `:engine:test` **380/380**, `:app:testDebugUnitTest` **51/51**,
  `:app:assembleDebug` green.

  **Docs links:** `docs/index.md` and `docs/engine-architecture.md` now
  point to `engine-saves.md`.

  **Next — Ф5.2:** add `StateDelta` + `apply`, expose applied operations
  from `Engine.step`, and replace implicit snapshots in the reader.
  Version/migration (Ф5.3–Ф5.4) follows before any v1 file is written.

---

- **2026-11-25 · Ф5.2a — engine `StateDelta`, `apply`, `StepResult.appliedDeltas`**

  **DoD (partial).** Ф5.2 requires `StateDelta` (Set/Add/Sub/Flag),
  `apply(base, delta) → State`, and replacing implicit snapshots in the
  reader. This batch ships the **engine model + step log**; persistence
  replacement remains Ф5.2b after version/migration.

  **What changed.**

    - `engine/StateDelta.kt` (new):
      - sealed `StateDelta`: `SetFlag`, `RemoveFlag`, `SetStat`,
        `AddStat`, `SubStat` (`@Serializable`, snake-case names for a
        future v1 JSON).
      - `applyDelta(PlayerState, delta)` — pure, clamps numeric results
        at 0 exactly like `EffectsApplier`.
      - `applyDeltas(base, deltas)` — fold.
      - `effectsToDeltas(effects)` — IR effects to operations:
        `ModifyStat(+) → AddStat`, `ModifyStat(-) → SubStat`,
        `Set → SetStat`, flags direct.
    - `StepResult.Success.appliedDeltas: List<StateDelta>` (empty
      default): choice effects first, then top-level subroutine output
      writes.
    - `Engine.step` records choice-effect deltas and appends
      `SubroutineExecutor` global output deltas.
    - `SubroutineExecutor.Result.Success.globalDeltas`: only **top-level
      output writes** (Bool → Set/RemoveFlag, numeric → SetStat). Internal
      frame changes stay atomic/invisible.
    - `docs/engine-saves.md` D8 corrected: `StateDelta` is player-state
      only; cursor/flow state stays in `ProgressSnapshot` (a future
      cursor/step log can add S3 without changing the delta model).

  **Tests.**

    - `StateDeltaTest` (3): base + 3 deltas; clamp at zero for
      Sub/Add/Set; Set/RemoveFlag idempotence.
    - `EngineStepTest` +2: `appliedDeltas` for choice effects replay to
      exactly the returned state (including clamp); subroutine output
      writes appear in the log.
    - `:engine:test` — **385/385 PASSED** (380 + 5).

  **What was NOT done (next).**

    - `ProgressData` still has the legacy flags/stats + implicit
      snapshots. Ф5.2b will switch it to `baseSnapshot + delta` and make
      the reader record scene-effect deltas.
    - Version/`MigrationRegistry` (Ф5.3–Ф5.4) must land before any v1
      file is written; v0 continues to be the only on-disk shape.
    - Cursor/step log (S3) is still Nice.

  **Verified.** `:app:testDebugUnitTest` — **51/51 PASSED**;
  `:app:assembleDebug` — BUILD SUCCESSFUL.

  **Next — Ф5.3/Ф5.4 (version first):** add `ProgressData.version`,
  `MigrationRegistry`, and legacy v0 → v1 conversion preserving
  `lastAbsoluteAtSave`/gates/warnings. Then Ф5.2b can persist
  `baseSnapshot + delta` safely.

---

- **2026-11-25 · Ф5.3/Ф5.4 — versioned progress + legacy migration**

  **DoD.** S6: `ProgressData.version`; `MigrationRegistry.migrate`
  works; v0 (legacy without version) → v1; unknown version is an
  explicit error, not a silent pass. Legacy `progress.json` keeps
  `lastAbsoluteAtSave` catch-up, gates and warnings.

  **What changed.**

    - `data/ProgressData.kt` — `version: Int = 0`. `0` explicitly means
      "legacy v0 / field absent"; new writer sets
      `ProgressMigration.CURRENT_VERSION` (1).
    - `utils/ProgressMigration.kt` (new):
      - `CURRENT_VERSION = 1`;
      - `ProgressMigrationException`;
      - v0 → v1 and v1 idempotence;
      - unknown/future/negative versions throw an explicit message;
      - **Gson normalization**: legacy JSON may omit fields added later,
        so `flags/stats/itemOrder/appliedSceneEffects/passedGates`,
        strings and nested `PlayerStateSnapshot`s are filled with safe
        empty values before copying. This fixes the `flags = null` crash
        explained to the owner: same Gson reader, but the new full-copy
        migration made missing fields visible.
    - `utils/ProgressManager.kt`:
      - per-entry migration in `readFromDisk`; valid entries are cached,
        invalid entries are stored with their reason (other adventures
        still load);
      - `loadProgress` throws `ProgressMigrationException` for that
        adventure;
      - `saveProgress` normalizes/migrates before writing, so new files
        are v1; invalid/future data is never overwritten.
    - `AdventureReaderViewModel`:
      - catches migration errors during `initNormalState` and returns
        `ReaderInitResult.ProgressError`;
      - `saveProgress` writes `version = CURRENT_VERSION`.
    - `ReaderRoute` — blocking “Не удалось открыть сохранение” dialog for
      ProgressError; file is not overwritten. This reuses the scrollable
      error dialog from Ф4.8a.
    - `docs/engine-saves.md` — v1 clarified: versioned legacy shape now;
      `base_snapshot` / `delta` are optional additive fields in the same
      v1 envelope for Ф5.2b; Gson normalization documented.

  **Tests.**

    - `ProgressMigrationTest` (4): v0 preserves all fields/snapshots;
      v1 identity; future version throws; legacy JSON without version
      parses as 0, migrates to 1 and keeps `remainingSteps`,
      `isGateActive`, `lastAbsoluteAtSave`.
    - `:app:testDebugUnitTest` — **55/55 PASSED** (51 + 4).
    - `:app:assembleDebug` — BUILD SUCCESSFUL.
    - Engine unchanged — **385/385**.

  **Manual smoke (needed for Ф5.4).**

    1. Use an old `progress.json` (no `version`) with a played adventure:
       open it — the player must continue at the same scene; the next
       save writes `version: 1`; `lastAbsoluteAtSave`, gates and warning
       fingerprint are unchanged.
    2. Hand-edit one entry to `"version": 999` — opening that adventure
       must show the blocking “Не удалось открыть сохранение” dialog; the
       file must not be overwritten. Other adventures still open.
    3. Ensure step catch-up still works after migration (close app, take
       steps, reopen).

  **Not done yet.** `base_snapshot`/`delta` are not persisted; the
  reader still uses legacy fields. **Ф5.2b** switches it to the operation
  log after this versioning foundation.

  **Next — Ф5.2b:** persist `baseSnapshot + delta` in `ProgressData`
  under v1 (additive fields), record scene-effect deltas in the reader,
  and keep reading v0/v1 legacy fields for backward compatibility.

---

- **2026-11-25 · Ф5.3/Ф5.4 — manual smoke confirmed**

  Owner verification:

    - Old `progress.json` without `version` opened and continued at the
      same scene; migration v0 → v1 preserved the playthrough.
    - Hand-edited unknown future version (`999`) produced the blocking
      “Не удалось открыть сохранение” dialog after a full app stop; the
      file was not silently overwritten.
    - Step-gate catch-up across a closed app works: steps taken while the
      app was stopped reduce `remainingSteps` on reopen.

  Checklist: S6, Ф5.3, Ф5.4 marked `☑`. App tests **55/55**, engine
  **385/385**, APK green.

  **Next — Ф5.2b.**

---

- **2026-11-25 · Ф5.2b — persist `baseSnapshot + delta` in the reader**

  **DoD (S5, core).** `ProgressData` contains `baseSnapshot + delta`;
  `apply` reconstructs the current player state; old v0/v1 entries still
  load. (Full removal of the explicit undo/chapter snapshots is S3 future
  work; they remain for the existing step-back/chapter-restart features.)

  **What changed.**

    - `data/ProgressSnapshot.kt` (new): full operation-log base
      (`chapter`, scene, flags, stats, itemOrder, applied effects, gate,
      warnings, passed gates) + Gson-friendly `StateDeltaDto` with
      `toDto` / `toDomainOrNull` for the engine `StateDelta`.
    - `ProgressData` — additive `baseSnapshot: ProgressSnapshot?` and
      `delta: List<StateDeltaDto>?`; legacy current-state fields remain
      the compatibility/cursor source.
    - `ProgressMigration.normalize` — normalizes the new base snapshot
      (including Gson `null` fields) and carries the delta; v0 entries
      without these fields normalize to `null`.
    - `ReaderUiState` — `saveBaseSnapshot` + `operationLog`.
    - `AdventureReaderViewModel`:
      - new adventure / restart: base = initial snapshot, log empty;
      - choice step: append `StepResult.appliedDeltas`
        (choice effects + subroutine outputs);
      - scene effects: append `effectsToDeltas(scene.effects)`;
      - load: replay `base + delta` for flags/stats when both exist,
        otherwise fall back to legacy fields and start a fresh log;
      - save: write `base_snapshot` + `delta` plus the legacy current
        fields (old readers keep working);
      - undo/step-back/chapter restart: compact to the restored snapshot
        (base = snapshot, log empty), so the persisted pair stays exact.
    - `effectsToDeltas` in `:engine` is now public for the reader.
    - `docs/engine-saves.md` compatibility/implementation notes updated.

  **Tests.**

    - `ProgressMigrationTest` +1 (Gson round-trip of
      `ProgressSnapshot` + `StateDeltaDto`; replay produces expected
      flags/stats).
    - `:app:testDebugUnitTest` — **56/56 PASSED**.
    - `:engine:test` — **385/385 PASSED**.
    - `:app:assembleDebug` — BUILD SUCCESSFUL.

  **Manual smoke (confirmed 2026-11-26).** Owner reported all checks
  passed; no regressions found.

    1. Make several steps, including a scene with scene effects and the
       `drink_potion` subroutine; close the app fully; reopen — scene,
       flags, stats, item order and gates must match.
    2. Inspect `progress.json`: the entry should contain
       `base_snapshot` and `delta` in addition to the legacy fields; the
       replay of base+delta must equal the current flags/stats.
    3. Use “↩ Шаг назад” / chapter restart; continue and save — the
       new base/log must start from the restored snapshot, not from the
       old history.
    4. Open a legacy v0 entry — it must still load (base starts from the
       loaded state, log empty) and write v1 base/delta on the next save.

  **Not done yet.**

    - Manual slots (Ф5.5) and autosave isolation tests (Ф5.6).
    - P2 precompiled IR / bounded allocations (Ф5.7–Ф5.8).
    - S3 undo/backlog derived from the log (Nice); the existing explicit
      step/chapter snapshots remain in the file.

## Ф5.5 — ручные слоты (S1), 2026-11-26

**Статус:** реализовано; ручной smoke нужен.

**Что сделано.**

- `data/SaveSlot.kt` (new):
  - `SaveSlot` (id, name, createdAt, updatedAt, exact `ProgressData` state);
  - `SaveSlotPaths` — `adventureKey` (читаемое имя + короткий SHA-256),
    имена файлов `slot-<uuid>.json`, лимит 20;
  - `SaveSlotCodec` — `format_version = 1`, encode/decode, явные ошибки
    для пустого/повреждённого файла, неизвестной версии и отсутствующего
    состояния; встроенное состояние проходит тот же
    `ProgressMigration.migrate`/`normalize`, что и `progress.json`.
- `utils/SlotManager.kt` (new): SAF-хранилище
  `saves/<adventureKey>/slot-<uuid>.json`; list/create/load/delete;
  создание отказывается на лимите 20 с понятным сообщением; битый или
  более новый слот — явная ошибка с именем файла, а не тихое скрытие.
  Автосейв (`progress.json`) здесь не читается и не пишется.
- `ProgressMigration.normalize` стал публичным: слоты используют ту же
  Gson-нормализацию, что и автосейв.
- `ReaderUiState`: `manualSlots`, `slotsLoading`, `slotsError`,
  одноразовый `slotLoaded`.
- `AdventureReaderViewModel`:
  - `refreshSlots` / `createSlot` / `loadSlot` / `deleteSlot`;
  - автосейв и слоты собираются одним `buildProgressData`;
  - загрузка состояния из `progress.json` и слота идёт через общий
    `applySavedProgress` (replay `base + delta`, legacy fallback);
  - загрузка слота заменяет только автосейв, файл слота не меняется
    (S2 гарантирован путями);
  - слот сохраняется с `lastAbsoluteAtSave = -1`, чтобы при загрузке не
    вычитать шаги, пройденные в реальной жизни после создания слота.
- UI: пункт меню «Сохранения» → полноэкранный `ReaderSlotsScreen`:
  список (новые сверху), создание с именем, подтверждения загрузки и
  удаления, состояния «пусто / загрузка / ошибка / лимит 20», back.
- Строки добавлены в `strings.xml`.

**Тесты.**

- `SaveSlotCodecTest` +7: round-trip слота, неизвестная версия, пустое
  состояние, миграция legacy state, стабильность/санитизация
  `adventureKey`, имя файла ↔ id, лимит 20.
- `:app:testDebugUnitTest` — **63/63 PASSED**.
- `:engine:test` — **385/385 PASSED**.
- `:app:assembleDebug` — BUILD SUCCESSFUL.

**Ручной smoke (нужен).**

1. Открыть приключение, пройти несколько шагов, меню → «Сохранения» →
   «Создать сохранение», задать имя. В `saves/<adventureKey>/` должен
   появиться `slot-<uuid>.json` с `format_version`, `slot`,
   `adventure_key` и `state` (`baseSnapshot` + `delta`).
2. Продолжить игру (изменить флаги/статы/сцену), затем загрузить слот:
   сцена, флаги, статы, порядок предметов, гейт и пройденные гейты должны
   совпасть с моментом сохранения.
3. После загрузки `progress.json` обновляется, а файл слота остаётся
   неизменным (имя, содержимое, `updated_at`).
4. Удалить слот: файл исчезает, список обновляется; «Начать заново»
   удаляет автосейв, но не ручные слоты.
5. Изменить `format_version` слота на `2` и открыть список: должно быть
   явное сообщение об ошибке, слот не должен молча исчезнуть.
6. Создать 20 слотов: 21-й отклоняется сообщением о лимите, старые не
   удаляются автоматически.

**Не сделано.**

- Ф5.6: integration-тест «автосейв после ручного сейва не меняет слот»
  (разделение путей уже реализовано, тест — следующий шаг).
- Ф5.7–Ф5.10: P2-кэш IR, bounded-аллокации, CI-интеграция, stable-док.

## Ф5.6 — S2: автосейв и ручные слоты независимы, 2026-11-26

**Статус:** реализовано; автоматический integration-тест в CI.

**Что сделано.**

- `utils/SaveStorage.kt` (new): минимальный файловый интерфейс
  (`readDocument` / `writeDocument` / `listDocuments` / `deleteDocument`)
  и `SafSaveStorage` над выбранным деревом SAF. Один слой для автосейва
  и слотов, без Android-типов в интерфейсе.
- `utils/SlotStore.kt` (new): чистые list/create/load/delete над
  `SaveStorage`; все пути — `saves/<adventureKey>/slot-<uuid>.json`,
  лимит 20, обёртка ошибок с именем файла. `SlotManager` теперь только
  резолвит URI/контекст и готовит состояние слота.
- `utils/ProgressStore.kt` (new): кэш, миграция, `invalidEntries` и
  flush `progress.json` над `SaveStorage`. `ProgressManager` оставлен
  тонким: Android-контекст, debounce и имя файла.
- Исправлена гонка flush: если сохранение приходит во время записи,
  `dirty` больше не сбрасывается — версия мутаций сохраняет его до
  следующего flush.
- S2 теперь структурный и проверяемый: автосейв вызывает только путь
  `progress.json`, слоты — только `saves/...`; общего файла нет.

**Тесты.**

- `AutosaveSlotIsolationTest` +4:
  - `ON_PAUSE` (`save`) + `ON_STOP` (`flush`) после ручного слота:
    содержимое слота не меняется, записывается только `progress.json`;
  - создание слота не пишет автосейв;
  - автосейв сохраняет оба ручных слота и их содержимое;
  - пути автосейва и слотов не пересекаются.
- `:app:testDebugUnitTest` — **67/67 PASSED**.
- `:engine:test` — **385/385 PASSED**.
- `:app:lintDebug` — BUILD SUCCESSFUL, 9 старых `UnusedResources`-warnings,
  новых нет.
- `:app:assembleDebug` — BUILD SUCCESSFUL.

**Не сделано.**

- Ф5.7–Ф5.10: P2-кэш IR, bounded-аллокации, CI-интеграция, stable-док.

## Ф5.7 — in-memory IR cache (P2), 2026-11-26

**Статус:** реализовано; нужен ручной замер повторного открытия.

**Что сделано.**

- `data/AdventureLoader.kt` (new):
  - `LoadedAdventure` — `Adventure` + `CompiledAdventure` + лениво
    вычисляемый и кэшируемый `Analyzer.lint`;
  - `AdventureCache` — access-order LRU на 3 приключения;
  - `AdventureLoader` — fingerprint по SHA-256 JSON, cache hit/miss,
    тайминги parse/compile/total;
  - `AdventureLoads` — process-wide экземпляр, общий для читалки и
    preview.
- Ключ кэша — **содержимое** JSON, а не URI: редактирование файла даёт
  новый fingerprint и новый cache entry; старая запись вытесняется LRU.
- Ошибки parse/type/compile не кэшируются: следующий open снова пытается
  загрузить файл.
- `AdventureReaderViewModel.init` больше не вызывает `JsonToIr.parse` /
  `Engine.compile` / `Analyzer.lint` напрямую: он берёт результат из
  `AdventureLoads` и маппит ошибки в `ReaderInitResult`.
- В лог добавлена строка `Reader IR cache hit/miss: total=…ms parse=…ms
  compile=…ms fingerprint=…` — по ней видно, что повторное открытие не
  парсит JSON.
- Кэш ограничен 3 записями: память не растёт с числом открытых
  приключений.

**Тесты.**

- `AdventureLoaderTest` +6:
  - второй load того же JSON не вызывает parser/compiler;
  - изменённый JSON — новый cache entry;
  - LRU вытесняет least-recently-used;
  - parse failure не кэшируется;
  - compile failure не кэшируется;
  - реальный `JsonToIr.parse` + `Engine.compile` на минимальном
    приключении: повторный open — hit, lint-результат переиспользуется.
- `:app:testDebugUnitTest` — **73/73 PASSED**.
- `:engine:test` — **385/385 PASSED**.
- `:app:lintDebug` — BUILD SUCCESSFUL, 9 старых `UnusedResources`-warnings,
  новых нет.
- `:app:assembleDebug` — BUILD SUCCESSFUL.

**Ручной замер (нужен).**

1. Открыть приключение A: в логе `Reader IR cache miss` с ненулевыми
   `parse=…ms compile=…ms`.
2. Выйти в список и открыть A снова: `Reader IR cache hit`,
   `parse=0ms compile=0ms`, `total` близко к 0.
3. Открыть A → B → A → C → B: после четырёх разных приключений
   первое вытесняется, повторное открытие снова miss.
4. Изменить A в редакторе и открыть: fingerprint другой, снова miss
   (старый кэш не отдаёт устаревший IR).
5. Открыть preview A: тот же JSON → hit, lint для preview не считается.

**Не сделано.**

- Ф5.8: bounded-аллокации в горячем пути (`step`/render).
- Ф5.9–Ф5.10: CI-интеграция и stable-документ.

**Next — Ф5.8:** убрать необязательные аллокации в горячем пути
перехода/рендера и проверить отсутствие роста памяти на 1000 переходах.
