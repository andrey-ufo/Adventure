- Начало в docs/progress/progress-log.md
- **2026-11-07 · Ф1.7.0** — Модель «явный конец игры» (проектное
  решение 2026-11-07: pre-alpha, отказ от обратной совместимости,
  читалка не угадывает auto-advance, тупик = ошибка). **Что
  сделано (3 шага, 6 правок):**
  **(1)** `Scene.isEnding: Boolean` (JSON `is_ending`). KDoc:
  несколько сцен могут быть помечены как концовки; `DeathScene` не
  участвует.
  **(2)** `CURRENT_VERSION = 4`; миграция v3 → v4 — identity
  (`is_ending` отсутствует → `false`).
  **(3)** `Severity.ERROR` — новый вариант. Семантика:
  «читалка должна отказаться загружать файл». `WARNING` — «играется,
  но автор скорее всего ошибся».
  **(4)** `LintKind.DeadEnd` изменён: сцена без `choices` **и** без
  `isEnding` = ERROR (не WARNING). Убрано исключение «последняя
  сцена последней главы» — конец должен быть явным.
  **(5)** `LintKind.EndingSceneWithChoices` — новый, ERROR: сцена с
  `isEnding = true` **и** непустым `choices` (игрок нажмёт мёртвые
  кнопки).
  **(6)** `test_adventure.json`: `version: 4`, `final.is_ending: true`.
  **(7)** `AnalyzerTest`: 2 теста обновлены (`isEnding = true` на
  терминальных сценах), 4 новых (`DeadEnd` = ERROR,
  `EndingSceneWithChoices` = ERROR, ending без choices ok,
  multiple endings ok), 3 positive-теста сужены до `single { it.kind
  is X }` — они про «X найден», а не «X — единственная проблема».
  `RealAdventureTest` +1 (`final scene is marked as ending`).
  `JsonToIrTest` +3 (`v4 без is_ending → false`, `v4 с is_ending
  → true`, `v3 без is_ending → false`).
  **(8)** `SchemaFingerprintTest.expectedFlatFields["Scene"] +=
  "is_ending"`.
  **(9)** golden пересоздан (`final.is_ending: true`).
  **Пользовательская выгода:** автор видит тупик **в редакторе** до
  публикации (ERROR), а не из жалобы игрока, застрявшего на пустом
  экране. Читалка (Ф1.7.1) откажется открывать файл с ERROR — игрок
  никогда не увидит «пустоту». Цена: любой v1/v2/v3-файл без
  `is_ending` теперь ломается до ручной правки автором — принято
  сознательно (pre-alpha).
  **Verified:** `./gradlew :engine:test --rerun-tasks` →
  **147/147 PASSED**. `lint is clean on test_adventure_json`
  подтверждает: reference sample остаётся чистым после всех
  изменений. `GoldenIrTest` пересоздан, `SchemaFingerprintTest`
  защищает поле.
  **Следующее действие — Ф1.7.1:** адаптер читалки на IR.
- **2026-11-07 · Ф1.7.0.4** — Модель гейтов расширена автором-выбором
  «один раз / каждый раз» (проектное решение 2026-11-07 по Q6).
  **Контекст:** механика гейта покрывает два принципиально разных
  сценария: (a) «пройди N шагов, чтобы разблокировать главу/книгу —
  один раз прошёл, дальше открыто» (дефолт), (b) «путь между
  локациями — вернулся, иди снова» (per-visit). Без выбора в модели
  автор не может выразить (b), а модель без него пришлось бы
  переделывать после Ф1.7.1. Поэтому — отдельный подшаг до читалки.
  **Что сделано (2 шага):**
  **(1)** `Gate` — `value class` → `data class` с двумя полями:
  `requiredSteps: Int` и `repeatable: Boolean = false`. KDoc
  переписан: два режима, зачем нужен каждый, где в JSON, почему
  IR-сериализация отличается от adventure-формата (data class →
  `{"required_steps": N, "repeatable": bool}` vs. flat JSON
  `"gate": N` + опциональный `"gate_repeatable": true`).
  `CURRENT_VERSION = 5`; миграция v4 → v5 — identity.
  **(2)** `SceneDto.gateRepeatable: Boolean? = null`;
  `JsonToIr.buildScene` маппит `?: false` (миграция identity).
  `SchemaFingerprintTest.expectedFlatFields["Gate"] = {"required_steps",
  "repeatable"}` + `assertFlat("Gate", …)`.
  **(3)** `test_adventure.json` упрощён под новое правило проекта
  «гейты в эталоне — 1 шаг, чтобы тестировщик ходил по 1 шагу»:
  `health_test.gate = 1` + `"gate_repeatable": true` (per-visit
  пример «путь»), `chapter_end_1.gate = 1`, `chapter_end_2.gate = 1`.
  Тексты четырёх сцен обновлены: «100 шагов» → «1 шаг», добавлены
  пояснения про `gate_repeatable` / одноразовый шлюз.
  **(4)** `RealAdventureTest > gates on specific scenes` — числа
  100/5/4000 → 1/1/1. Новый тест `health_test gate is repeatable,
  chapter ends are not` — фиксирует, что эталон демонстрирует **оба**
  режима.
  **(5)** `EngineStepTest > chapter_end_1 has explicit Continue choice`
  — `stepsInScene = 4/5` → `0/1` (тест проверяет логику «меньше —
  блок, больше или равно — проходит», а не конкретные числа).
  **(6)** golden пересоздан: `"gate": 100` → `{"required_steps": 100}`,
  `chapter_end_1` → `{"required_steps": 1}`, `health_test` →
  `{"required_steps": 1, "repeatable": true}`, тексты сцен
  обновлены. `version` в golden исчез (стало дефолтом 5,
  `encodeDefaults = false`).
  **Пользовательская выгода:** (a) автор выбирает режим
  гейта — «разблокировал и забыл» или «каждый раз идти заново»;
  (b) игрок не застревает на повторных входах в сцену с
  дефолтным `repeatable = false`; (c) когда авторы реально захотят
  «путь», они явно укажут `repeatable: true` — не раздражающий
  дефолт важнее. **Цена:** golden переделан, 3 старых теста
  обновлены (`RealAdventureTest`, `EngineStepTest`,
  `JsonToIrTest > minimal valid adventure`). Кода читалки Ф1.7.1a
  это не касается — `repeatable` пока читается в модель, но
  игнорируется (гейт в Ф1.7.1a работает «один раз за сессию» без
  сохранения), полная per-scene tracking `repeatable` — в Ф1.7.1b.
  **Verified:** `./gradlew :engine:updateGolden` → golden diff
  соответствует плану (4 новых текста сцен, форма `Gate`, `version`
  исчез); `./gradlew :engine:test --rerun-tasks` → **150/150
  PASSED** (+2 `JsonToIrTest`, +1 `RealAdventureTest`, −0 потерь,
  остальные 2 обновлены). `GoldenIrTest` и `SchemaFingerprintTest`
  защищают модель.
  **Следующее действие — Ф1.7.1a:** миграция
  `AdventureReaderFragment` на IR (20 секций).
- **2026-11-07 · Ф1.1 (fix)** — `app/build.gradle.kts` отсутствовала
  строка `implementation(project(":engine"))`. Это упущение
  приёмки Ф1.1: критерий «`:app` зависит от `:engine`» проверялся
  косвенно («`:engine:build` зелёный»), а неявная project-зависимость
  не была добавлена. Обнаружено в Ф1.7.1a, когда `:app`-код
  впервые импортировал `:engine`-классы: ~170 «Unresolved reference
  'engine'» каскадом. Фикс — одна строка в `dependencies {}`.
  **Урок для будущих приёмок:** критерий «A зависит от B»
  проверять явным «`./gradlew :A:assemble` после `import` из B»,
  а не «оба собираются по отдельности». **Verified:** после фикса
  `:app:assembleDebug` выдаёт ~15–20 реальных ошибок миграции
  (секции 3–19), не 170.
- **2026-11-07 · Ф1.7.1a** — `AdventureReaderFragment` мигрирован на
  `:engine`-IR. Читалка больше не парсит JSON через Gson-модель
  `:app/data/Adventure` — получает `CompiledAdventure` от
  `JsonToIr.parse` + `Engine.compile`, проверяет `Analyzer.lint`,
  работает с типами `engine.ir.*`. **Это был большой батч (~22
  секции правок, ~3 раунда отладки), затронувший три файла
  `:app` и два `:engine`.** Итог:
  **(1) Парсинг.** `onCreate` → `JsonToIr.parse` (единственный
  парсер) → `Engine.compile` (индексы O(1)) → `Analyzer.lint`
  (ERROR блокирует, WARNING предупреждает). Preview-mode
  (`Ф1.7.5` TODO) пока skip'ает lint — редактор ещё не умеет
  ставить `is_ending`.
  **(2) Типы.** `adventure: Adventure` (IR, non-null `tags`,
  `initialState`, `startScene: SceneId`), `compiled: CompiledAdventure`,
  `currentChapterIndex` **удалён** (главы — нарративная группировка,
  Ф1.5.4; индекс на лету из `compiled.chapterIndexBySceneId`).
  `currentSceneId` оставлен `String` для минимизации дифа — `.value`
  на границе с IR. Полный переход на `SceneId` — Ф5.5.
  **(3) Единые evaluator/applier.** `isConditionMet(condition: Condition?)`
  → `Evaluator.evaluate(condition, PlayerState)`; `applyEffects(effects: List<Effect>)`
  → `EffectsApplier.apply(...)`. `when (category)` не exhaustive —
  эффекты применяются к `PlayerState`, `TagCategory` смотрится
  отдельно для `registerItem`. `isConditionMet` **сохраняет `catch
  → true`** (TODO Ф2.5/N8) — это осознанное решение, чтобы при
  регрессе `Evaluator` читалка не падала; уйдёт вместе с N8.
  **(4) Гейты scene-scoped (Ф1.5.4 + Ф1.7.0.4).** `stepReceiver`
  смотрит `compiled.scenesById[currentSceneId].gate`; `startSceneGate`
  / `startSceneGateInPreview` / `showGateWaiting` / `updateStepsProgressUi`
  / `onGatePassed` — на `scene.gate`, не на `chapter.requiredSteps`.
  `advanceToNextChapter` **удалён** — авто-переходов нет,
  автор выражает переход явным choice (Ф1.5.5.0).
  **(5) Новое поле `passedGates: MutableSet<String>`** — in-memory
  set сцен, чьи non-repeatable гейты пройдены в текущей сессии.
  `onGatePassed` добавляет **только** для `repeatable = false`;
  `loadCurrentScene` проверяет `scene.id.value !in passedGates`
  перед активацией гейта; `onRestartAdventure` **очищает**
  (баг: без этого one-shot гейт «протекал» через restart; поймано
  в ревью, не на smoke). `repeatable = true` гейты **никогда** не
  попадают в `passedGates` — активируются при каждом входе.
  **Персистентность (Ф1.7.1b) — отложена.**
  **(6) КРИТИЧЕСКИЙ баг, пойманный smoke-тестом:**
  `AdventureListFragment` передавал JSON в читалку через
  `Gson().toJson(:app.data.Adventure)`. У этой модели нет полей
  `is_ending`, `version`, `gate_repeatable` → reader видел
  `isEnding = false` на всех сценах → `DeadEnd` ERROR на `final`
  → блокировка открытия. **Файл на диске был правильный**, тесты
  `:engine` зелёные. Фикс: `readAdventureJsonFromFile()` —
  если есть `adventure_file_uri`, читалка **читает файл сама**,
  не доверяя `adventure_json` из аргументов. Аргумент оставлен
  как fallback для preview (где URI нет). **Урок:** при миграции
  любого транзитного узла (list → reader, editor → preview,
  import → reader) нужно проверять, что данные не проходят через
  **устаревшую** модель. Впредь буду явно смотреть на такие
  пути до smoke.
  **(7) UX-фиксы по итогам smoke:**
    - `SetupFragment.createSubFolders` — проверка существования
      папок (`createFolderIfMissing`), без этого SAF-провайдеры
      молча создают `Adventures(1)`, `NewAdventures(1)`.
    - `SetupFragment.copyTestAdventure` — **перезаписывает**
      существующий образец (`findFileUriInFolder` + openOutputStream
      `"wt"`), без этого старая копия без `is_ending` не обновлялась
      после Ф1.7.0, и sample становился ERROR. **Более широкое
      решение** (`SampleAdventureMigrator` на старте приложения) —
      отдельный батч.
    - `updateChoices` — недоступные по condition и гейту теперь
      **видны, но disabled** (`forceDisabled: Boolean = false`,
      `alpha = 0.5f`). Раньше скрывались.
    - `showGateWaiting` — `updateChoices(scene.choices, forceDisabled = true)`,
      не `removeAllViews`. Игрок видит, что будет доступно.
      **(8) `has_sword` → `sword > 0` в `test_adventure.json`.**
      Smoke показал: кнопка «Проверить условие (нужен меч)» disabled
      при `sword = 1`. Причина — семантика Ф1.5: `Condition.Has`
      проверяет **только** `flags`, не `stats` (для чисел — `Compare`).
      Старое поведение `:app` (`flags.contains || stats > 0`) при
      миграции осознанно убрано, но sample использовал legacy
      синтаксис. Фикс: `has_sword` → `sword > 0` (два choices),
      `RealAdventureTest > has_sword condition parsed as Has AST` →
      `sword_gt_zero condition parsed as Compare AST`. **Это
      подготовка к Ф2.2:** type checker отвергнет `has_sword` на
      `ItemCount` с подсказкой «используйте `sword > 0`».
      **(9)** `DeadEnd` severity: ERROR → WARNING (ревизия 2026-11-07).
      Пользователь: «отсутствие конца в приключении не должно
      выбрасывать из читалки». Пересмотр утреннего решения:
      `DeadEnd` = WARNING, читалка показывает
      `showWarningDialog` — «Продолжить игру» / «Выйти из игры»;
      игрок решает. `EndingSceneWithChoices` **остаётся ERROR** —
      мёртвые кнопки, однозначная поломка. Запись в `ProgressData`:
      `warningsFingerprint: String = ""` — fingerprint набора
      warnings (sceneId + message, sorted), диалог показывается
      только если fingerprint ≠ сохранённый. При правке файла
      (один warning убран, другой добавлен) fingerprint меняется
      → диалог возвращается. Меню читалки: пункт «Предупреждения (N)»
      (только при наличии) → read-only `showWarningInfoDialog`.
      «Продолжить игру» → acknowledge (`saveProgress`);
      «Выйти из игры» / системный back → **не** acknowledge.
      `onRestartAdventure` → сброс fingerprint (свежий старт —
      показать снова). **Это первое поле `ProgressData`, добавленное
      после Ф1.5; старые `progress.json` дефолтят в `""` — safe.**
      **Verified:** `./gradlew :engine:test --rerun-tasks` → **150/150
      PASSED** (после DeadEnd→WARNING и `sword > 0` golden пересоздан).
      `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
      `./gradlew :app:lintDebug` → **0 errors**, 1 warning (version
      bump `kotlinx-serialization-json`, отложен).
      **Smoke-тест (телефон):** сценарий 1 (`isEnding` на `final`) —
      ✅; сценарий 2 (per-visit гейт на `health_test`) — ✅; сценарий
      3 (disabled choice по condition + `has_sword` fix) — ✅;
      сценарий 4 (ERROR-блок при `final` без `is_ending`) — ✅;
      warning-диалог с fingerprint (A/B из последнего батча) — ✅.
      **Остаточные 7 lint-warnings** (`?.` на non-null `tags` /
      `initialState` в `AdventureReaderFragment`) — косметика,
      отдельный батч.
      **Открытые задачи, возникшие в этом батче:**
      (a) `Ф1.7.1b` — persistence `passedGates` в `ProgressData`
      (сейчас one-shot гейты не работают между сессиями);
      (b) `SampleAdventureMigrator` — обновление `test_adventure.json`
      на старте приложения (не только в setup); нужно для сценария
      «обновили приложение, sample устарел»;
      (c) `Ф1.7.5` — `is_ending` checkbox в редакторе (`AdventureEditorFragment`
    + `buildAdventure()` пишет `is_ending`); без этого preview
      читалки не видит флаг и показывает «Конец предпросмотра»
      вместо «🎉 Конец истории»;
      (d) `test_adventure.json` — гейты в отдельную ветку (сейчас
      мешают smoke остальных параметров, «трясти телефон»);
      (e) error codes (`E-001`, `W-042`) — отдельный батч после Ф1.7.5;
      (f) N4/Ф4 — reason на disabled-choice (сейчас серая кнопка).
      **Следующее действие — Ф1.7.1b:** персистентность `passedGates`.
- **2026-11-07 · Ф1.7.1b** — Persistence `passedGates` + прогресс
  гейта между сессиями. **Дизайн-решения (обсуждены с
  пользователем):**
  **(Q1)** `loadCurrentScene` не сбрасывает `remainingSteps`, если
  восстановлено из save — 4 случая (`!isGateActive` → fresh;
  `0 < remainingSteps <= requiredSteps` → in-progress, restore;
  `remainingSteps <= 0` → catch-up завершил, `onGatePassed`;
  `remainingSteps > requiredSteps` → stale, reset).
  **(Q2)** Прогресс гейта **никогда** не сбрасывается при закрытии
  приложения. Сбрасывается только: (a) при прохождении, (b) при
  `restoreSnapshot` (step back / chapter restart — уточнение
  2026-11-07: `resetGate()` в `restoreSnapshot` — это **план на
  будущее** step back из любой сцены, а не текущее поведение;
  сейчас он no-op, но не удалять). Для `repeatable = true` —
  тот же принцип: пока гейт не пройден, прогресс живёт.
  **(Q3)** Поле `version` в `ProgressData` **не** добавляем —
  альфа, backward compat не нужна; старые `progress.json`
  дефолтят (`passedGates` → `emptyList()`, `warningsFingerprint`
  → `""`); реальные миграции — Ф5.3.
  **Что сделано (6 замен):**
  **(1)** `ProgressData.passedGates: List<String> = emptyList()` —
  список сцен, чьи non-repeatable гейты пройдены в текущем
  прохождении. KDoc: зачем persisting (без него one-shot гейт
  «протекает» через перезапуск); для `repeatable = true` **не**
  пополняется; очищается `restart`. In-progress state — в
  существующих `isGateActive` + `remainingSteps`.
  **(2)** `initNormalState` (ветка `saved != null`):
  `passedGates.clear(); passedGates.addAll(saved.passedGates)`.
  **(3)** `saveProgress`: `passedGates = passedGates.toList()`.
  **(4)** `tryCatchUpGate`: **убрано** `onGatePassed(scene)` при
  `remainingSteps <= 0` — оставляем `remainingSteps = 0`, решение
  передаётся `loadCurrentScene`. Причина: для `repeatable = true`
  `onGatePassed` **не** пополняет `passedGates`, и если бы
  `tryCatchUpGate` вызывал `onGatePassed` (который ещё и
  `updateChoices`), а потом `loadCurrentScene` проверял
  `!in passedGates` — гейт re-engages сразу, хотя игрок только что
  его прошёл. Централизация в `loadCurrentScene` — один источник
  правды «гейт пройден?».
  **(5)** `loadCurrentScene` — 4-случайный `when`:
  `!isGateActive` → `startSceneGate`; `remainingSteps <= 0` →
  `onGatePassed`; `remainingSteps > gate.requiredSteps` → reset
  (автор правил файл, пока прогресс был сохранён); else →
  `showGateWaiting` (in-progress, restore). Для `repeatable = true`
  после прохождения — `passedGates` не пополняется, следующий
  вход = fresh (per-visit).
  **(6)** `restoreSnapshot`: `resetGate()` в начале + KDoc
  переписан: «сейчас no-op (step back только с death screen,
  где гейт уже пройден); **план на будущее** step back из любой
  сцены (roadmap S3a) — ушёл из гейтованной сцены → прогресс
  гейта потерян → возврат = fresh (repeatable) / passed
  (non-repeatable). Не удалять.»
  **BUG, найденный на smoke (Ф1.7.1b-fix):** `tvStepsRequired`
  («Пройдено X из Y») **никогда не скрывался** при переходе из
  гейтованной сцены в негейтованную. Сценарий: `health_test`
  (гейт 50) → пройдено 30 → «Начать приключение заново» →
  `start` → «Проверить журнал» → `journal_test` показывает
  «Пройдено 30 из 50» (stale text из `health_test`). Фикс:
  `updateChoices` при `!forceDisabled` (т.е. когда гейта нет или
  он пройден) делает `binding.tvStepsRequired.visibility =
  View.GONE`. `showGateWaiting` вызывает с `forceDisabled = true`
  → не скрывает, сам ставит `VISIBLE`. Баг был до Ф1.7.1b, но
  вылез только сейчас — до persistence не тестировался restart
  с активным гейтом.
  **Verified:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
  Smoke-сценарии A (прогресс не сбрасывается при перезапуске),
  B (one-shot не сбрасывается между сессиями, restart очищает),
  C (`repeatable = true` прогресс живёт, после прохождения
  fresh) — ✅ все. Новый сценарий (уход из гейтованной сцены в
  негейтованную) — ✅ после Ф1.7.1b-fix.
  **Открытые задачи, обновление:** (a) `S3a` (step back из любой
  сцены) — переоценено с «отдельная эпопея» на «~30 строк»:
  `previousStepSnapshot` **уже** одноуровневый (сохраняется перед
  каждым choice в `updateChoices`), книжная-игровая норма
  «1 шаг назад, в начало главы, начать заново» — три снапшота
  **уже есть**. Нужно: пункт меню «↩ Шаг назад» (если
  `previousStepSnapshot != null`) + `previousStepSnapshot = null`
  в `restoreSnapshot` (запрет цепочки). `resetGate()` в
  `restoreSnapshot` наконец становится функциональным.
  (b) `Ф1.7.5` — `is_ending` checkbox в редакторе.
  (c) `test_adventure.json` — гейты в отдельную ветку (упростить
  smoke).
  (d) `SampleAdventureMigrator` — обновление sample на старте
  приложения.
  **Следующее действие — S3a:** step back из любой сцены.
- **2026-11-08 · S3a** — Step back из любой сцены (Nice, не в
  чеклисте 2.1; реализовано внепланово между Ф1.7.1b и Ф1.7.5).
  **Контекст:** до S3a шаг назад был доступен только на death
  screen (`previousStepSnapshot` для «↩ Один шаг назад»). Игрок,
  зашедший в сцену с активным гейтом и не желающий идти N шагов,
  не имел выхода, кроме «Начать приключение заново». S3a закрывает
  этот UX-разрыв.
  **Дизайн-решения (обсуждены с пользователем, 2026-11-07):**
  **(1) Одноуровневый undo, не stack.** В книгах-играх не позволяют
  бесконечные шаги назад — только 1 шаг, в начало главы, в начало
  книги. Все три снапшота **уже есть** (`previousStepSnapshot`,
  `chapterStartSnapshot`, `onRestartAdventure`). Мой первый
  вариант `ArrayDeque<PlayerStateSnapshot>` был ошибкой — я
  применил рефлексию «undo = stack», не сверившись с жанром.
  Пользователь поправил; принято.
  **(2) Запрет цепочки.** После шага назад `previousStepSnapshot
  = null` — второй подряд step back невозможен. Способы
  продолжить: новый choice (создаёт свежий snapshot), restart
  chapter, restart adventure. Это ровно «1 шаг назад», а не
  «отмена отмены».
  **(3) UI — пункт «↩ Шаг назад» в меню читалки.** Виден только
  когда `!previewMode && !isDead && previousStepSnapshot !=
  null`. Не показывается в preview (undo — player affordance,
  не author tool) и на death screen (там уже есть отдельная
  кнопка «↩ Один шаг назад», дублирование = шум).
  **(4) Сброс прогресса гейта.** `resetGate()` в
  `restoreSnapshot` до S3a был **no-op** (step back был доступен
  только после смерти, а на гейтованной сцене нельзя кликнуть
  choice, чтобы умереть). С S3a становится функциональным:
  ушёл из гейтованной сцены → прогресс гейта потерян; вернулся
  → гейт fresh (repeatable) / passed (non-repeatable).
  **Что сделано (3 замены в `:app` + roadmap):**
  **(1)** `restoreSnapshot`: в начале `previousStepSnapshot = null`
  (single source of truth — любой caller: step back, chapter
  restart); KDoc `resetGate()` переписан: «больше не no-op,
  функциональность для S3a».
  **(2)** `onChapterRestart`: убран явный `previousStepSnapshot =
  null` — теперь это делает `restoreSnapshot`. Один источник
  правды, меньше мест для баги.
  **(3)** `showReaderMenuDialog`: динамический список пунктов
  (`items: MutableList<Pair<String, () -> Unit>>`), добавлен
  пункт «↩ Шаг назад» с фильтрами из дизайна.
  **(4)** `docs/roadmap.md` Part 2: подраздел S3a с историей,
  дизайном, entry trigger (shipped alongside Ф1.7.1b).
  **Пользовательская выгода:** игрок, случайно зашедший в
  гейтованную сцену и не желающий идти N шагов, откатывается
  одним кликом. Без S3a оставался только «Начать приключение
  заново» — потеря всего прогресса. С S3a — потеря только
  шагов текущего гейта (что логично: «ушёл из сцены, прогресс
  гейта не сохраняется»).
  **Verified:** `./gradlew :app:assembleDebug` → BUILD
  SUCCESSFUL. Smoke-сценарии (телефон):
  **A** step back из обычной сцены — ✅; после step back пункт
  меню исчез (запрет цепочки).
  **B** step back из гейтованной сцены (`health_test` gate 20,
  пройдено 5/20) → step back → `start` → возврат в `health_test`
  → «0 из 20» (прогресс гейта потерян, как задумано).
  **C** step back не предлагается на death screen — ✅
  (`isDead` фильтр сработал, отдельная кнопка «↩ Один шаг
  назад» на death screen работает).
  **D** step back после step back невозможен — ✅ (пункт меню
  исчез после первого).
  **Замечание про тесты:** `:engine:test` не менялся (S3a — только
  `:app`-код). `:app:assembleDebug` — единственная верификация
  компиляции; поведение — smoke на телефоне.
  **Следующее действие — отступление от плана перед Ф1.7.5:**
  уточняется у пользователя (см. следующее сообщение).
- **2026-11-09 · Ф1.7.5** — `is_ending` checkbox в редакторе.
  **Контекст:** после Ф1.7.0 флаг `is_ending` был обязателен для
  финальных сцен (`DeadEnd` = WARNING, читалка показывает
  предупреждение), но автор не мог поставить его из UI — только
  ручной правкой JSON. Preview в редакторе тоже не видел флаг:
  `AdventureEditorViewModel.buildAdventure()` строил
  `:app/data/Adventure` (Gson POJO), у которого не было поля
  `is_ending` — при `Gson().toJson(:app/data/Adventure)` флаг
  терялся, `JsonToIr.parse` в preview видел `isEnding = false`
  на всех сценах, и `final` показывал «▶ Конец предпросмотра»
  вместо «🎉 Конец истории».
  **Дизайн (обсуждён с пользователем):**
  **(a) минимальный** — добавить `is_ending` в data-модели
  редактора и в мост `buildAdventure()`/`loadFromUri()`. Не
  трогать `:app/data/Adventure.kt` (write-модель), не делать
  полный переход editor на `:engine/ir/Adventure`. Полный
  переход — отдельный батч **(b) Data path cleanup** после
  Ф1.7.5 (см. roadmap).
  **Что сделано (3 секции, 5 замен):**
  **(1) Data-модели** — `EditorScene.isEnding: Boolean = false`
  (`:app/data/EditorModels.kt`, KDoc: отличается от
  `isChapterEnd` — это legacy «конец главы = шаговый шлюз на
  следующую»; `isEnding` пишет `"is_ending"` на сцену и
  останавливает историю); `:app/data/Scene.isEnding: Boolean?
  = null` с `@SerializedName("is_ending")` (nullable, чтобы
  различать «поле отсутствует» от «явно false»; Gson не уважает
  Kotlin-дефолты).
  **(2) UI** — `item_editor_scene.xml`: checkbox
  `@+id/cb_ending` «🎉 Конец игры» между `cb_chapter_end`
  («🏁 Сделать концом главы») и `cb_death_scene`
  («💀 Сцена смерти»). `EditorSceneAdapter`: обработчик
  `cbEnding.setOnCheckedChangeListener` → `s.isEnding = isChecked;
  onDirty(false)` (не structural — флаг-тумблер не пушит
  историю, паттерн как у `cbDeathAllowStepBack` и соседей).
  KDoc у обработчика: `isEnding` ортогонален `isChapterEnd` и
  `isDeathScene`; взаимные исключения — на анализаторе
  (`EndingSceneWithChoices` ERROR, death-over-end приоритет
  читалки), не дублировать в UI.
  **(3) Мост** — `AdventureEditorViewModel.buildAdventure()`:
  `isEnding = if (scene.isEnding) true else null` в построении
  `Scene`. `null` → Gson **опускает** поле (не `"is_ending":
  false` на каждой сцене, как было бы с `false` по умолчанию);
  на загрузке `JsonToIr` дефолтит в `false` (identity
  migration). Паттерн совпадает с `setFlags`/`choices` в
  `buildAdventure()` (пустой → опускается).
  `AdventureEditorFragment.loadFromUri()`: `isEnding = scene.isEnding
  ?: false` в `EditorScene(...)` — `null` из nullable Gson-модели
  → `false` в редакторе (тот же identity migration).
  **Пользовательская выгода:** (a) автор ставит «🎉 Конец игры»
  в редакторе без ручной правки JSON; (b) preview видит флаг и
  показывает «🎉 Конец истории» вместо «▶ Конец предпросмотра»;
  (c) сохранённый JSON содержит `"is_ending": true` только там,
  где автор явно поставил галочку, а не на всех сценах.
  **Что НЕ сделано (осознанно, отдельный батч):**
  (a) полный переход editor на `:engine/ir/Adventure` — это **(b)
  Data path cleanup** (см. roadmap «Data path cleanup (after
  Ф1.7.5)» → `:app/data/Adventure` → `:engine/ir/Adventure`
  unification);
  (b) гейты в preview (`gate`, `gate_repeatable`) по-прежнему
  не работают — `:app/data/Scene` не имеет этих полей, они
  теряются при `buildAdventure()` → Gson → preview. Это
  **известное ограничение**, лечится в (b). Smoke это
  подтвердил (сценарий D: гейт на `health_test` в preview
  неактивен — так было и до Ф1.7.1a, не регресс).
  **Verified:** `./gradlew :app:assembleDebug` → BUILD
  SUCCESSFUL. Smoke (телефон):
  **A** load `test_adventure.json` → `final.cb_ending` отмечен
  (`"is_ending": true` из файла), другие сцены — не отмечены ✅;
  **B** preview: `start` → `chapter_end_1` (гейт 1) → `conditions_test`
  → `chapter_end_2` (гейт 1) → `final` показывает **«🎉 Конец
  истории»** (не «▶ Конец предпросмотра») ✅;
  **C** новая сцена + `cb_ending` → «Сохранить» → в файле
  `"is_ending": true` на этой сцене, на остальных поля нет ✅;
  **D** (известное ограничение) preview `health_test` гейт
  неактивен — ожидаемо, лечится в (b) ✅.
  **Следующее действие — (b) Data path cleanup:** уточнение
  плана (см. следующее сообщение в Progress log, если оно
  изменит scope; пока — roadmap «Data path cleanup (after
  Ф1.7.5)»).
- **2026-11-09 · b1 (Data path cleanup)** — `AdventureListFragment`
  читает IR. `parseAdventureFromUri` → `JsonToIr.parse` →
  `:engine/ir/Adventure`; `AdventureAdapter` принимает
  `List<:engine/ir/Adventure>`. `adventure_json` больше **не**
  передаётся в reader — только `adventure_file_uri` (reader
  читает файл сам, Ф1.7.1a-fix). Это устраняет последний
  **latent** путь, где list мог бы отдать lossy JSON в reader
  (потому что `:app/data/Adventure` всё ещё используется в
  editor write path — это b2/b3). **Verified:** `:app:assembleDebug`
  зелёный; smoke (library list, open, play) — OK; в `:app/data`
  больше нет **read**-потребителя.
  **Следующее — b2:** `:app/data` += `gate`/`gate_repeatable`/
  `version`/`start_scene`; UI per-scene gate; preview перестанет
  терять гейты.
- **2026-11-09 · Ф1.8** — Закрыт **неявно во время Ф1.7.1a** (reader
  миграция на `:engine`-IR). Проверка по DoD:
  (a) `AdventureReaderFragment.isConditionMet` принимает `Condition?`
  (IR AST), не `String?` — **парсинга условий в `:app` больше нет**;
  (b) единственный вызов — `Evaluator.evaluate(condition,
  PlayerState(flags.toSet(), stats.toMap()))` из `:engine`;
  (c) `catch → return true` сохранён (намеренно — N8/Ф2.5 уберёт
  это вместе с type checker); (d) grep `isConditionMet` в `:app` —
  определение + один вызов в `updateChoices`, других нет.
  **Отдельного PR не потребовалось**: миграция reader на IR в
  Ф1.7.1a включала и замену `isConditionMet` на `Evaluator` (нельзя
  было мигрировать reader и оставить парсинг — компилятор бы не
  пропустил `Condition?` в `isConditionMet(String?)`). **Побочный
  эффект:** старый `catch → return true` теперь ловит не
  `NumberFormatException` из `parts[1].toInt()` (парсинга нет), а
  гипотетические RuntimeException из `Evaluator.evaluate` —
  которые на валидном IR не возникают. Т.е. `catch` стал
  защитным, не функциональным. Уйдёт в Ф2.5/N8 вместе с
  `ConditionParser` → `TypeChecker`.
  **Verified:** `:app:assembleDebug` зелёный (в Ф1.7.1a и во всех
  последующих батчах). `RealAdventureTest > sword_gt_zero condition
  parsed as Compare AST` подтверждает: condition в IR — AST, не
  строка.
  **Следующее — Ф1.9:** CI `:engine:test`.
- **2026-11-09 · Ф1.9** — CI `:engine:test` уже присутствует в
  `../../.github/workflows/ci.yml` (добавлен при настройке модуля
  `:engine` в Ф1.1, но формально не был залогирован как Ф1.9).
  **Проверка по DoD:** (a) шаг `Run engine tests` с
  `./gradlew :engine:test` — есть; (b) артефакты
  `engine-test-reports` с `engine/build/test-results/test/` и
  `engine/build/reports/tests/test/`, `if: always()` — есть.
  **Дополнительных правок не требуется.** CI гоняет все 150 тестов
  `:engine` при каждом push/PR.
  **Verified:** `.github/workflows/ci.yml` содержит
  `run: ./gradlew :engine:test` и `Upload engine test reports`;
  последний прогон `:engine:test --rerun-tasks` (Ф1.7.5/b1) —
  150/150 PASSED.
  **Следующее — Ф1.10:** `docs/engine-architecture.md` (draft).
- **2026-11-09 · Ф1.10** — `docs/engine-architecture.md` (draft) +
  `docs/architecture.md` (draft). **DoD чеклиста:** «Схема слоёв, IR,
  точки входа парсера/рантайма/анализатора; ссылка из
  `docs/architecture.md`». Что создано:
  **(1)** `docs/architecture.md` (draft) — высокоуровневая схема:
  два модуля (`:app` / `:engine`), data flow (adventure.json →
  JsonToIr → Engine.compile → Analyzer.lint → reader; sensor →
  StepCounterService → StepHistoryManager → reader → Engine.step),
  три инварианта step-пайплайна (source / history / game), 6
  инвариантов (I1: `:engine` не зависит от Android; I2: единый
  Evaluator/EffectsApplier; I3: parser vs compiler vs analyzer
  boundary; I4: severity отражает player impact; I5: immutable
  PlayerState; I6: стабильные имена JSON-полей). Ссылка на
  `engine-architecture.md`. Стиль high-level, per
  `documentation-rules.md §8`.
  **(2)** `docs/engine-architecture.md` (draft) — deep dive:
  module boundary, packages, IR (классы + value classes +
  CURRENT_VERSION), parser (single entry point + two-stage parsing
    + condition grammar), runtime (`Engine.compile` / `Engine.step` /
      `StepResult` / `RejectReason` / `Blocker`), Evaluator /
      EffectsApplier (single source of truth), analyzer (reachable,
      lint, таблица всех 7 `LintKind` с severity), error hierarchy
      (три слоя), testing strategy (все 9 тестовых классов `:engine`),
      что отложено в Ф2/Ф3/Ф4.
      **(3)** `docs/index.md` — секция «For developers» дополнена
      ссылкой на `engine-architecture.md`; заодно закрыта старая
      битая ссылка на `architecture.md` (файл теперь существует).
      **Пользовательская выгода:** (a) новый contributor за одно
      чтение `architecture.md` понимает, где что лежит, и зачем
      `:engine` отделён; (b) разработчик, добавляющий фичу, видит
      инварианты (I1–I6) и понимает, куда класть код — в `:engine`
      или в `:app`; (c) deep dive в `engine-architecture.md` экономит
      часы на чтении исходников `JsonToIr` / `Engine` / `Analyzer`;
      (d) `docs/index.md` больше не содержит битых ссылок.
      **Что НЕ сделано (осознанно, для X3.base в Ф10):**
      (a) `docs/getting-started.md` — ссылка из index.md остаётся
      битой (файл создаётся в Ф10.6);
      (b) `docs/authoring-guide.md` — Ф10.7;
      (c) глубокие per-class заметки, sequence diagrams — отложены до
      стабилизации фич (Ф10+);
      (d) `docs/architecture.md` не описывает `:app`-внутренности
      (ProgressManager, StepHistoryManager, FontManager, …) — только
      их место в data flow; детальный `:app`-архитектурный обзор
      появится в X3.base.
      **Verified:** оба файла созданы, GFM-таблицы отрисованы,
      внутренние ссылки (`./engine-architecture.md`,
      `./architecture.md`, `./documentation-rules.md`,
      `../README.md`, `./checklist-tracker.md`, `./roadmap.md`) —
      рабочие. `docs/index.md` обновлён, битая ссылка `architecture.md`
      теперь ведёт на существующий файл. Код не менялся,
      `:engine:test` перезапускать не требуется.
      **Ф1 закрыт целиком** (10 из 10 задач: Ф1.1–Ф1.10).
      **Следующее действие — Ф2.1:** спроектировать систему типов
      (`sealed class VarType` — Bool/Int/IntRange/ItemCount/ItemList,
      `VarDecl`, таблица объявлений на уровне `Adventure`).
  