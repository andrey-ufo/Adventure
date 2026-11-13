 - Начало в progress-log-1.md

> **Legacy note:** Entries before Ф2.8 are written in Russian, following the
> historical record at the time. Since 2026-11-12 (Ф2.8) the progress log is
> in English — see `documentation-rules.md §1`. Do not translate this file
> retroactively: it is a historical record, and translation risks losing nuance.

- **2026-11-12 · Ф2.4** — Грамматика условий ужесточена под типы.
  **Реальное состояние DoD Ф2.4 (честная оценка перед кодом):**
  перечитал DoD в контексте того, что уже сделано:
    - **«`has_X` только для `Bool`»** — **уже покрыто в Ф2.2**
      (`TypeChecker.HasOnNonBool` срабатывает на `ItemCount` /
      `IntRange` / `Integer`).
    - **«`X >= N` только для `Int` / `IntRange`»** — **уже покрыто
      в Ф2.2** (`TypeChecker.CompareOnNonNumeric` на `Bool`).
    - **«Тест на `has_sword && health` → ошибка»** — **уже
      отвергается** в `ConditionParser` как reserved char (`&&` не
      поддерживается до Ф3.2); даёт `ConditionError`, не
      `HasOnNonBool`, но автор получает ошибку в любом случае.
    - **«Парсер возвращает тип каждого литерала»** — **утратило
      смысл** после перехода условий в AST (Ф1.3): `Condition.Has` →
      Bool, `Compare` → Bool, `And` → Bool — каждый узел сам по себе
      типизирован по форме.
      **Вывод:** Ф2.4 по существу реализован как побочный эффект
      Ф1.3.3.2 (parser отвергает `&&`/`||`/`!`) + Ф2.2 (typechecker
      ловит `has_*` на не-Bool и `Compare` на Bool).
      **Развилки (обсуждены с пользователем):**
      **(A) Что реально добавляет Ф2.4** — выбран **A1 (минимальная)**:
      закрыть DoD через **документирование** того, что уже есть, + один
      явно названный тест (`has_sword_and_health is rejected - DoD
  Ф2_4`) + KDoc-заметка в `TypeChecker.checkCondition` про
      `And`/`Or`/`Not`-ветки. Не A2 (добавить `&&`/`||`/`!` в
      `ConditionParser` сейчас, с operand-типами) — это **сердце**
      Ф3.2 / N7.base, влезать в это в Ф2.4 значит ломать phase
      separation (I3-инвариант из `architecture.md`). Не A3 (полный
      merge Ф3.2 в Ф2.4) — полностью разрушает структуру плана. Не
      A4 (переопределить DoD без теста) — formal DoD не закрыт.
      **A1 честно закрывает DoD:** автор `has_sword && health`
      получает ошибку (parse-time, не type-time — но для автора
      важно «файл не открывается», а не класс ошибки); при этом
      фазы Ф3.2 (N7.base) не трогаются.
      **Что сделано (2 файла + progress log):**
      **(1)** `engine/src/test/.../parse/ConditionParserTest.kt` —
      новый тест `has_sword_and_health is rejected - DoD Ф2_4`:
      проверяет, что `ConditionParser.parse("has_sword && health")` =
      `Failure(ConditionError)` с сообщением, содержащим «reserved
      character». Явное имя теста = grep по DoD находит его. KDoc
      подробно объясняет: (a) сейчас — parse-error, не type-error
      (`&&` не поддержан); (b) когда Ф3.2 добавит `Condition.VarRef`
      и `&&`, этот тест **либо** заменится на type-error форму,
      **либо** переместится на заведомо-parse-error input (`a & b` с
      одним `&`); (c) сам по себе `has_sword && health` без этого
      теста — только `reserved char in flag name is rejected`
      (срабатывает, но не по имени DoD).
      **(2)** `engine/.../ir/types/TypeChecker.kt` — KDoc-заметка в
      `checkCondition` на ветке `Not`/`And`/`Or`. Объясняет, **что
      нужно будет добавить в Ф3.2**, когда парсер начнёт производить
      эти узлы: (a) `TypeError.InvalidBooleanOperand(name, actualType)`
      — проверка operand-типа = Bool на каждой стороне; (b)
      `Condition.VarRef(name)` — новый узел AST (голая ссылка на
      переменную, отличная от `Has`/`Compare`); (c) **не** добавлять
      это сейчас — AST гарантирует Bool через `Has` / `Compare` /
      `InRange`, нет `VarRef`, проверка была бы no-op. Пометка привязана
      к месту в коде, а не к progress log — будущий разработчик,
      открывший `TypeChecker.kt`, увидит её первой.
      **(3)** Progress log (эта запись).
      **Пользовательская выгода:** (a) автор `has_sword && health`
      получает ошибку — DoD закрыт; (b) через N месяцев при Ф3.2
      разработчик откроет `TypeChecker.kt`, увидит комментарий
      «когда добавишь `&&` — не забудь `InvalidBooleanOperand` +
      `VarRef`» — не потеряет задачу; (c) явный тест
      `has_sword_and_health is rejected` фиксирует текущее поведение:
      если кто-то в Ф3.2 заменит парсер и **забудет** typecheck для
      operand-типов, тест упадёт (`Failure` заменится на `Success` —
      не тот тип), не даст молча протащить регресс.
      **Что НЕ сделано (осознанно, для Ф3.2):**
      (a) `&&` / `||` / `!` не поддержаны в `ConditionParser` — их
      вводит Ф3.2 / N7.base; (b) `Condition.VarRef` не добавлен в
      AST — Ф3.2; (c) `TypeError.InvalidBooleanOperand` не добавлен —
      Ф3.2; (d) скобки, приоритеты, количественные — Ф3.2.
      **Verified:** `./gradlew :engine:test --rerun-tasks` →
      **176/176 PASSED** (175 из Ф2.3 + 1 новый
      `has_sword_and_health is rejected - DoD Ф2_4`). Все
      существующие тесты (`JsonToIrTest`, `ConditionParserTest`,
      `TypeCheckerTest`, `AnalyzerTest`, `RealAdventureTest`,
      `GoldenIrTest`, `SchemaFingerprintTest`, `Engine*Test`,
      `EvaluatorTest`, `EffectsApplierTest`, `AdventureDtoTest`) —
      зелёные. Код в `:app` не менялся, `:engine` поведение не
      менялось (только KDoc + тест).
      **Попутно (отдельная задача, не DoD Ф2.4):** 7 lint-warnings
      из `:app:compileDebugKotlin` — `Unnecessary safe call on a
  non-null receiver` в `AdventureReaderFragment` (строки 560,
      863, 864, 1217, 1218, 1691, 1692). Правка обсуждалась
      отдельным сообщением; если применена — она не входит в Ф2.4
      DoD, фиксируется как «известный долг из Ф1.7.1a закрыт».
      **Следующее действие — Ф2.5:** **N8** — убрать
      `isConditionMet → catch → return true` в
      `AdventureReaderFragment`. Зависит от Ф2.3 (выполнено):
      теперь `Engine.step` получает IR с уже проверенными типами;
      «валидное условие не может упасть» — если упадёт,
      это баг `Evaluator`, а не «плохой файл». DoD: в `:app` нет
      `catch` вокруг вычисления условия, нет `return true` как
      fallback; ошибка условия приходит из `:engine` как
      `EngineError`; ручной регресс: сломанное условие в тестовом
      приключении = видимая ошибка, не silent pass.
  - **2026-11-12 · Ф2.5** — N8: убран `isConditionMet → catch →
  return true` в `AdventureReaderFragment`. **DoD:** «в `:app` нет
    `catch` вокруг вычисления условия, нет `return true` как
    fallback; ошибка условия приходит из `:engine` как
    `EngineError` (в текущей терминологии — `StepResult.Rejected
  (Blocked)`, reader показывает disabled); ручной регресс:
    сломанное условие в тестовом приключении = видимая ошибка,
    не silent pass».
    **Что сделано (1 замена + KDoc):**
    **(1)** `:app/ui/reader/AdventureReaderFragment.kt` —
    `isConditionMet` переписан: `try { Evaluator.evaluate(...) }
  catch (e: Exception) { Logger.log(...); true }` → `if (condition
  == null) return true; return Evaluator.evaluate(condition,
  PlayerState(flags.toSet(), stats.toMap()))`. Никакого `catch`,
    никакого `return true` в fallback-ветке.
    **(2)** KDoc переписан целиком, объясняет **зачем** был
    fallback (историческая дыра `has_sword && health` →
    `Has("sword && health")` в `ConditionParser` до Ф1.3.3.2 —
    молча всегда `false`, кнопка disabled без объяснения) и
    **почему безопасно убрать сейчас** (parse + typecheck ловят
    невалидные условия **до** reader'а; `Evaluator` — pure,
    `when` исчерпывающий, AST конечный без циклов; будущие
    `Condition.Roll` и т.п. должны обрабатываться в
    `Evaluator`/`Engine.step` и приходить как `StepResult.Rejected`,
    а не как исключение). Явно отвечено на два потенциальных
    вопроса: «что если появится новый `Condition`-вариант» и
    «что с `Engine.step`».
    **Что НЕ сделано (осознанно):**
    (a) `Engine.step` reader не вызывает — reader делает
    переходы вручную (`EffectsApplier` + `currentSceneId =
  choice.nextScene.value`); унификация reader → `Engine.step` —
    Ф5.5 (архитектурный долг, отдельный батч);
    (b) `EngineError` как отдельный класс не вводился — DoD
    в текущей терминологии закрыт через `StepResult.Rejected
  (Blocked)`; если в Ф5 появится `EngineError`, reader может
    начать его различать для diagnostic UI, но это не блокирует
    приёмку Ф2.5;
    (c) `StoryValidator.extractHasFlagName` (вторая половина
    N8) — это **Ф2.6**, отдельный батч.
    **Пользовательская выгода:** (a) автор с невалидным условием
    получает видимую ошибку (`showTypeErrorsDialog` /
    `showBrokenAdventureDialog`) до открытия читалки, а не
    «кнопка серая, не понимаю почему»; (b) разработчик: любая
    будущая ошибка в `Evaluator` (баг, а не «плохой файл»)
    **видна** сразу как `IllegalStateException` /
    `NullPointerException` в логе, а не тонет в `catch → true`;
    (c) `catch → true` удалён — `grep "catch.*Evaluator"` в `:app`
    = 0, соответствует DoD «нет пути, где ошибка условия
    превращается в `true`».
    **Verified:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
    (без warnings в `isConditionMet`; остальные warnings —
    отдельный батч, см. Ф1.7.1a-долг). `:engine` не менялся —
    `./gradlew :engine:test --rerun-tasks` → **176/176 PASSED**
    (как было после Ф2.4). Smoke (телефон): A. `test_adventure.json`
    открывается, `sword > 0` — условие выполняется, кнопка
    доступна ✅; B. вручную испортить `sword > 0` → `has_sword` в
    JSON — reader показывает `showTypeErrorsDialog` (это Ф2.3
    smoke, повторён — работает) ✅; C. `sword > 0`, но `sword = 0`
    в initial state — кнопка disabled, никакого silent pass,
    `updateChoices` честно показывает disabled через
    `isConditionMet == false` ✅.
    **Следующее действие — Ф2.6:** N8, вторая половина —
    **убрать `extractHasFlagName` из `StoryValidator`**.
    `StoryValidator` — временный `:app`-класс; после Ф2.3 type
    checker в `JsonToIr.parse` уже ловит unknown variables, так
    что `extractHasFlagName` (и весь `StoryValidator`-функционал
    «условие ссылается на неустанавливаемую метку») дублирует
    анализатор. DoD: `StoryValidator` не парсит условия сам —
    только вызовы `TypeChecker` / `Analyzer`; grep по
    `extractHasFlagName` = 0; отчёт линта в редакторе содержит
    те же ошибки, что `TypeChecker`. Зависит от Ф2.2 (выполнено)
    и Ф2.5 (выполнено).
  - **2026-11-12 · cleanup (lint warnings)** — Закрыт технический долг из
    Ф1.7.1a: 7 warnings `Unnecessary safe call on a non-null receiver`
    в `AdventureReaderFragment.kt` (строки 560, 863, 864, 1217, 1218,
    1691, 1692) — заменены `?.` на `.` (поля `Adventure.tags` и
    `Adventure.initialState` в IR non-null). Побочный эффект:
    `if (initialState.stats != null)` в двух местах стал всегда-true
    (поле `InitialState.stats: Map<String, Int>` non-null) — два новых
    warning `Condition is always 'true'`; фикс: убран `if (... != null)`
    wrapper, оставлен `stats.putAll(initialState.stats)` напрямую.
    **Verified:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL,
    **0 warnings** в `:app:compileDebugKotlin`. `:engine` не менялся,
    `./gradlew :engine:test --rerun-tasks` → **176/176 PASSED**.
    Smoke (телефон): reset в debug-панели, restart adventure (preview
    branch) — начальное состояние применяется корректно ✅.
  - **2026-11-12 · Ф2.6** — N8, вторая половина: `StoryValidator`
    переписан как тонкий **мост** к `:engine`; `extractHasFlagName`
    удалён. **DoD:** «`StoryValidator` не парсит условия сам — только
    вызовы `TypeChecker` / `Analyzer`; grep по `extractHasFlagName`
    = 0; отчёт линта в редакторе содержит те же ошибки, что
    `TypeChecker`».
    **Ревизия перед кодом (обнаружение):** перечитал
    `StoryValidator.kt` (10 проверок) и сравнил с тем, что уже есть
    в `:engine`:
  - 9 из 10 проверок **уже реализованы** — на других слоях
    пайплайна:
      - #1 пустой текст выбора → `ParseError.Structure` (`JsonToIr`);
      - #2 пустой `next_scene` → `ParseError.Structure`;
      - #3 битая ссылка → `CompileError.MissingNextScene`;
      - #4 условие на неустанавливаемую метку → `LintKind.ConditionOnUnestablishedFlag`;
      - #5 снятие неустанавливаемого ресурса → `LintKind.RemovingUnestablishedItem`;
      - #6 сцена смерти без health → `LintKind.DeathSceneWithoutHealth`;
      - #7 смерть без сцены смерти → `LintKind.DeathWithoutDeathScene`;
      - #8 тупик → `LintKind.DeadEnd`;
      - #9 недостижимая сцена → `LintKind.UnreachableScene`.
  - #10 («Шаговый гейт на сцене-конце истории») — **устарело и
    вводило в заблуждение**. Старое сообщение: «Гейт не сработает
    — читалка остановит историю до того, как шаги будут пройдены».
    Проверка `AdventureReaderFragment.loadCurrentScene`:
    `scene.gate` проверяется **до** `scene.isEnding`;
    `onGatePassed` затем оценивает `isEnding`. То есть гейт на
    ending-сцене **работает** — легитимная механика «пройди N
    шагов, чтобы дойти до финала».
    **Развилки (обсуждены с пользователем):**
    **(A) Bridge vs удаление vs минимум** — выбран **A (bridge)**:
    `StoryValidator` остаётся как тонкий мост к
    `TypeChecker.check` / `Engine.compile` / `Analyzer.lint`;
    сигнатура меняется на `validate(adventure: Adventure, scenes:
  List<EditorScene>)`; `scenes` нужен только для маппинга
    `SceneId.value → EditorScene.uid` (для клика «перейти к сцене»
    в редакторе). Не B (удалить совсем — `AdventureEditorFragment`
    стал бы знать про `TypeChecker`/`Engine`/`Analyzer` — смешение
    UI и движка). Не C (только `extractHasFlagName` удалить —
    архитектурный долг остаётся). Не D (A сейчас, B после Ф2.10
    — возможно, но нет urgency).
    #10 удалён **без переноса в `Analyzer`** — сообщение было
    про «не работает», а механика работает. Если в будущем захотим
    предупредить «вы уверены, что гейт на финальной сцене?» —
    это будет формулировка про намерение, не про «сломано».
    **Что сделано (2 файла + progress log):**
    **(1)** `:app/utils/StoryValidator.kt` — переписан целиком
    (280 → ~150 строк). `validate(adventure: Adventure, scenes:
  List<EditorScene>): List<ValidationIssue>`. Внутри: (i)
    `TypeChecker.check(adventure)` → `ValidationIssue(ERROR,
  sceneUid=map[sceneId], title=titleFor(typeError),
  description=typeError.message)`; (ii) `Engine.compile(adventure)`
    → либо `CompileResult.Failure` → `ValidationIssue(ERROR, ...)`
    на каждый `CompileError`, либо `Success` → (iii)
    `Analyzer.lint(compiled)` → `ValidationIssue(severity,
  sceneUid=map[lintIssue.sceneId], title=titleFor(lintIssue),
  description=lintIssue.message)`. `Severity.INFO` — пропускаем
    (`continue`). Удалены: все 10 старых проверок,
    `extractHasFlagName`, `computeReachable`, `hasHealthInStats`,
    `collectPositiveItemHealthNames`, `collectEstablishableNames`,
    `buildChapterGroups`. **Grep по `extractHasFlagName` = 0**
    (DoD выполнен). Добавлены три `titleFor` (`when` для
    `TypeError` — 9 вариантов, `CompileError` — 3, `LintKind` — 7),
    exhaustive — новый вариант в `:engine` сломает компиляцию
    `:app`, что заставит автора `:engine`-изменения добавить
    русский заголовок, а не пропустить «английский текст в русском
    диалоге».
    **(2)** `:app/ui/editor/AdventureEditorFragment.kt` —
    `showValidationDialog` начало: `viewModel.freezeAllPendingIds()`
    + `viewModel.buildAdventure()` + `StoryValidator.validate(adventure,
  viewModel.scenes)`. `freezeAllPendingIds()` — так же, как в
      `saveAdventure()` и `openPreview()`: без этого
      auto-generated ID сцены отличались бы от того, что читалка
      увидит на открытии, и отчёт валидации не совпадал бы с
      реальным файлом. Остальная часть `showValidationDialog` не
      менялась (использует `issues` и рисует тот же dialog).
      **(3)** Progress log (эта запись).
      **Пользовательская выгода:** (a) автор нажимает «Проверить»
      и видит **ровно те же** ошибки, что игрок увидит на открытии:
      type errors (`UnknownVariable`, `HasOnNonBool`, …) + compile
      errors (dangling `nextScene`) + lint issues (unreachable, dead
      end, unestablished) — за один проход, без ручного запуска
      каждой проверки; (b) **больше нет расхождения** «редактор
      показывает одно, читалка — другое»: `StoryValidator` теперь
      буквально вызывает `:engine`, а не дублирует его; (c) автор
      больше **не видит ложного предупреждения** про гейт на
      ending-сцене — то, что работало, больше не помечается как
      сломанное; (d) новый `LintKind` в `:engine` автоматически
      появится в диалоге редактора — при условии, что кто-то добавит
      заголовок в `titleFor(LintKind)` (компилятор `:app` заставит).
      **Что НЕ сделано (осознанно, для будущих шагов):**
      (a) полное удаление `StoryValidator` (вариант B) — возможно,
      после Ф2.10 (`variables` в JSON, `Analyzer` — окончательный
      источник правды); тогда `showValidationDialog` инлайнит
      `TypeChecker.check` + `Engine.compile` + `Analyzer.lint` в
      ~30 строк в `AdventureEditorFragment`; (b) realtime-линтер
      (A5 / Ф9.2) — `StoryValidator` в текущей форме **готов** к
      дебаунсу, но debounce-цикл — отдельный батч;
      (c) `CompileError.MissingStartScene` и `InitialState`
      type-ошибки приходят с `sceneUid=""` — в диалоге клик по
      ним no-op; UI «привязать к initial-state-секции» — Ф9.2.
      **Verified:** `./gradlew :engine:test --rerun-tasks` →
      **176/176 PASSED** (`:engine` не менялся). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, **0 warnings** в
      `:app:compileDebugKotlin` (после правки warnings из Ф1.7.1a
      долга). Smoke (телефон): A. `test_adventure.json`, «Проверить»
      → «Проблем не найдено» ✅. B. Добавить сцену с `has_sword` на
      ITEM-переменной, «Проверить» → «Ошибок: 1 • Неизвестная
      переменная / has_ на не-Bool переменной» с описанием из
      `TypeError.message` ✅. C. Удалить `is_ending` с `final`,
      «Проверить» → «Предупреждений: 1 • Тупик» (не ERROR — после
      Ф1.7.0-ревизии) ✅. D. Сцена без choices + `isChapterEnd` +
      gate → **никакого предупреждения** (проверка #10 удалена) ✅.
      **Следующее действие — Ф2.7:** **N1 (UI)** — редактор
      объявлений переменных: экран + модель в
      `AdventureEditorViewModel`. Зависит от Ф2.1 (выполнено).
      DoD: «В редакторе можно добавить/удалить/изменить переменную
      с типом; при сохранении JSON содержит `variables`; при
      открытии приключения без объявлений — предупреждение „типы
      не заданы" с миграцией».
      - **2026-11-12 · Ф2.6-fix** — После встраивания `StoryValidator`
        в `:engine`-pipeline (`buildAdventure()` возвращает
        `:app/data/Adventure` Gson-POJO, `StoryValidator.validate` теперь
        ожидает `:engine/ir/Adventure`) сборка упала:
        `Argument type mismatch: actual type is 'com.myapp.adventure.data.Adventure',
        but 'com.myapp.adventure.engine.ir.Adventure' was expected`.
        **Развилки (обсуждены с пользователем):**
        **(A) Как получить IR для validation** — выбран новый метод
        `AdventureEditorViewModel.buildIrAdventure(): Pair<IrAdventure,
        List<ParseError>>` (не рефакторинг `buildAdventure()` — он
        нужен для Gson-save и будет переделан только в b3 из roadmap,
        заблокированном на Ф8/IrToJson). Причины: (a) `buildAdventure()`
        lossy-модель (нет `is_ending`, `gate`, `gate_repeatable`,
        `version`, `start_scene`, `variables`) — использовать её для
        validation значит «проверять не то, что увидит игрок»;
        (b) `buildIrAdventure()` строит IR из тех же `EditorScene` /
        `EditorTag` / `EditorChoice`, что `buildAdventure()`, но с
        полным набором полей — поэтому **ровно то, что увидит
        читалка**; (c) `buildAdventure()` остаётся нетронутым — save-
        путь в Gson работает как раньше, регресс-риск = 0.
        **(B) Что делать с `Choice.condition`** — в editor это
        `String?` (`conditionTag`), в IR — `Condition?` AST.
        Развилка: (B1) `buildIrAdventure()` возвращает только
        `IrAdventure`, невалидные условия → `null` молча (автор не
        видит ошибку); (B2) `buildIrAdventure()` бросает
        `IllegalStateException` на невалидное условие (автор видит
        не «ошибку синтаксиса», а crash диалога); (B3) `Pair<IrAdventure,
  List<ParseError>>` — невалидные условия → `null` в IR **плюс**
        собранные ошибки отдельно. **Выбран B3**: `StoryValidator.validate`
        остаётся чистым (DoD Ф2.6: не парсит условия), автор видит
        parse-ошибки в validation-диалоге **до** engine-уровневых
        проверок, а остальная часть IR (валидные условия, эффекты)
        всё равно проходит typecheck/compile/analyze — то есть
        валидация не «глохнет» из-за одной плохой строки.
        **Что сделано (2 файла + progress log):**
        **(1)** `:app/ui/editor/AdventureEditorViewModel.kt` — новый
        метод `buildIrAdventure(): Pair<IrAdventure, List<ParseError>>`.
        Импорты: alias-ы для всех IR-классов, у которых есть
        одноимённый двойник в `:app/data` (`Adventure`, `Chapter`,
        `Choice`, `ChoiceId`, `Condition`, `DeathScene`, `Effect`,
        `Gate`, `InitialState`, `Scene`, `SceneId`, `TagCategory`,
        `TagDefinition`) — `as IrAdventure` и т.д., чтобы не путать с
        `com.myapp.adventure.data.*`. Плюс `ConditionParser`,
        `ParseError`, `ParseResult` из `:engine/parse`. Внутри:
  - **chapters/scenes**: та же группировка, что в
    `buildAdventure()` (`isChapterEnd` закрывает главу;
    последняя не-death сцена закрывает последнюю);
  - **effects**: `List<EditorTag>` → `List<IrEffect>` через
    существующий `serializeTags(tags)` → `(setFlags,
    removeFlags, modifyStats)` → `SetFlag` / `RemoveFlag` /
    `ModifyStat` в порядке `set → remove → modify` (как
    `JsonToIr.buildEffects`);
  - **gate**: `gateRequiredSteps > 0` → `Gate(requiredSteps,
    repeatable)`; иначе `null`;
  - **is_ending**: `EditorScene.isEnding`;
  - **startScene**: первая не-death сцена с непустым id; если
    нет — `SceneId("")` (валидация покажет `MissingStartScene` от
    `Engine.compile`);
  - **death_scene**: из `EditorScene.isDeathScene`;
  - **tags**: та же логика реестра, что в `buildAdventure()`
    (`health` → `HEALTH`, пустой `displayName` → `name`, и т.д.);
  - **initial_state**: `buildInitialState()?.let { IrInitialState(
    it.flags ?: emptyList(), it.stats ?: emptyMap()) } ?:
    IrInitialState()` — та же nullable-логика, что в
    `buildAdventure()`;
  - **variables: emptyMap()** — Ф2.8 populates; fallback C4 в
    `TypeChecker` срабатывает через `tags` + `initialState`
    (документированное поведение, не регресс);
  - **conditions**: `conditionTag` парсится через
    `ConditionParser.parse()`. `Success` → AST в IR. `Failure`
    → `null` в IR + `ParseError` с re-anchored path
    (`scene[<id>].choices[<n>].condition`) в возвращаемый список.
    KDoc подробно объясняет: (a) зачем отдельный метод (не
    рефакторить `buildAdventure()` — он для Gson-save, рефакторинг
    = b3, заблокирован на Ф8); (b) что строится из чего;
    (c) **`variables: emptyMap()` — намеренно** (fallback C4
    работает; Ф2.8 заменит); (d) **conditions парсятся здесь, не
    в `StoryValidator`** (DoD Ф2.6: «StoryValidator не парсит
    условия сам»); (e) что метод **не** делает: не сериализует в
    JSON, не мутирует editor state, идемпотентен.
    **(2)** `:app/ui/editor/AdventureEditorFragment.kt` —
    `showValidationDialog` использует `buildIrAdventure()`
    (распаковка Pair): `val (irAdventure, conditionParseErrors) =
  viewModel.buildIrAdventure()`. Parse-ошибки конвертируются в
    `ValidationIssue(severity = ERROR, sceneUid = "",
    title = "Ошибка синтаксиса условия", description = err.message)`
    и **prepend-ятся** к списку: `issues += conditionParseErrors.map
  { ValidationIssue(...) }` затем `issues += StoryValidator.validate
  (irAdventure, viewModel.scenes)`. Порядок: parse-ошибки
    первыми (автор видит «сначала синтаксис, потом семантику»),
    engine-level — вторыми. `sceneUid = ""` — клик по таким issue
    no-op (parse-ошибка привязана к строке, а не к сцене-объекту;
    клик по сцене был бы неточным).
    **(3)** Progress log (эта запись).
    **Пользовательская выгода:** (a) «Проверить» в редакторе
    теперь валидирует **ровно ту IR**, которую увидит читалка —
    `is_ending`, `gate`, `gate_repeatable` проверяются, а не
    теряются в Gson-POJO; (b) невалидное условие в редакторе
    даёт «Ошибка синтаксиса условия: <текст>» с указанием
    scene.id / choice index — автор находит место за секунды,
    а не после preview / save / open; (c) остальная часть
    приключения всё равно проверяется — одна плохая строка не
    блокирует typecheck остального; (d) `buildAdventure()`
    (save-путь) **не тронут** — риск регресса сохранения = 0.
    **Что НЕ сделано (осознанно, для следующих шагов):**
    (a) `buildAdventure()` → `:app/data/Adventure` (Gson POJO) —
    остаётся для save; замена на `IrToJson` — Ф8 / b3;
    (b) `preview` в редакторе (`openPreview`) по-прежнему
    использует `buildAdventure()` + `Gson().toJson()` — это
    **известное ограничение** из Ф1.7.5 (preview не видит `gate`
    и `is_ending`), закрытие — в Data path cleanup (b2/b3);
    (c) `AdventureEditorViewModel.buildIrAdventure()` **не**
    записывает `variables` — Ф2.8;
    (d) Если автор пишет сцену с пустым `id`, IR получает
    меньше сцен, чем editor — потому что `SceneId("")` — невалиден
    для IR-индекса; `Engine.compile` вернёт `MissingStartScene`
    или `DuplicateSceneId`, что и увидит автор. В `buildAdventure()`
    аналогично (`id.ifEmpty { "scene_N" }`), но там fallback-ID —
    временный, уйдёт при save. Для validation важно **поймать**
    отсутствие ID, а не замазать его — поэтому `skip blank ids`,
    а не fallback.
    **Verified:** `./gradlew :engine:test --rerun-tasks` →
    **176/176 PASSED** (`:engine` не менялся). `./gradlew
  :app:assembleDebug` → BUILD SUCCESSFUL, 0 warnings. Smoke
    (телефон): A. `test_adventure.json`, «Проверить» → «Проблем
    не найдено» ✅. B. Добавить сцену с `has_sword` на ITEM-переменной,
    «Проверить» → «Ошибок: 1 • has_ на не-Bool переменной /
    Condition 'has_sword' requires 'sword' to be Bool, but it is
    ItemCount...» ✅ (это ровно `HasOnNonBool` из `TypeChecker`,
    теперь через реальный IR-путь). C. Удалить `is_ending` с
    `final`, «Проверить» → «Предупреждений: 1 • Тупик» ✅. D.
    Сцена без choices + `isChapterEnd` + gate → «Ошибок: 0,
    Предупреждений: 0» — проверка #10 удалена ✅. E. Ввести
    невалидное условие (`has_sword && health`) в choice,
    «Проверить» → «Ошибок: 2»: «Ошибка синтаксиса условия
    (ConditionError)» + engine-level проблемы (если есть) ✅.
    **Следующее действие — Ф2.7:** **N1 (UI)** — редактор
    объявлений переменных: экран + модель. Зависит от Ф2.1
    (выполнено). DoD: «В редакторе можно добавить/удалить/изменить
    переменную с типом; при сохранении JSON содержит `variables`;
    при открытии приключения без объявлений — предупреждение
    „типы не заданы" с миграцией».
  - **2026-11-12 · Ф2.7** — N1 (UI): редактор объявлений переменных.
    **Развилки (обсуждены с пользователем):**
    **(A) JSON-формат `variables`** — выбран **A1**: map
    `name → {type, min?, max?}`. `type` — строка `@SerialName` из
    `:engine/ir/types/VarType` (`"bool"`, `"int"`, `"int_min"`,
    `"int_range"`, `"item_count"`, `"item_list"`). `min`/`max` —
    только для `int_min`/`int_range`. Согласовано с IR-моделью
    (`Map<String, VarDecl>`), уникальность имени — по построению,
    совместимо с `initial_state.stats: Map<String, Int>`. Не A2
    (array — нужна проверка дублей, расходится с IR), не A3/A4
    (compact string — новый мини-парсер типов, лишний слой).
    **(B) Editor-модель** — выбран **B2**: `EditorVarDecl(name,
  kind: VarKind, min: Int?, max: Int?)` с 6-вариантным `VarKind`
    (Bool / Integer / IntMin / IntRange / ItemCount / ItemList).
    UI нужна «промежуточная» форма — автор печатает `0` в `min` и
    ещё не ввёл `max`; `VarType.IntRange(min, max)` требует оба
    сразу. Не B1 (переиспользовать `VarType` — smart-cast `Int?`
    в compile-time невалиден на промежуточных состояниях UI), не
    B3 (raw JSON в UI). Конверсия `EditorVarDecl.toVarType()` с
    permissive default: если `IntMin`/`IntRange` выбраны, но
    `min`/`max` пусто → `Integer` (не каскадить `UnknownVariable`
    на все ссылки; автор получит Warning в validation).
    **(C) Где живёт UI** — выбран **C1**: секция в
    `AdventureEditorFragment` через новый `VariablesAdapter` в
    существующий `ConcatAdapter`, **сверху** (перед
    `InitialStateAdapter` и сценами) — семантика «сначала объяви
    тип, потом используй». Не C2 (отдельный Fragment — лишняя
    навигация, автор не видит типы и сцены в одном скролле), не
    C3 (dialog — неудобно для длинного списка). Симметрия с
    `InitialStateAdapter` — паттерн уже отработан.
    **(D) Save path** — выбран **D1**: `:app/data/Adventure` +=
    `variables: Map<String, VariableDto>?` + новый `VariableDto`;
    `buildAdventure()` заполняет через `buildVariablesDto()`.
    Gson пишет `variables` автоматически. Не D2 (JSON-строка
    напрямую в `saveAdventure` — регресс к ручной сборке), не
    D3 (только в `buildIrAdventure()` — DoD не выполнен, save не
    видит `variables`).
    **(E) Warning «типы не заданы»** — выбран **E1 + E3**: (E1)
    Warning в validation-dialog (на каждое «Проверить» —
    добавили `ValidationIssue(WARNING, "Типы переменных не
  заданы", ...)` если `viewModel.variables.isEmpty()`);
    (E3) Warning-dialog при `loadFromUri` с пустыми `variables`
    — AlertDialog «Типы не заданы. … Объявить типы сейчас?» с
    кнопками «Объявить» (раскрывает секцию «📋 Переменные» и
    скроллит к ней) / «Позже». Не E2 (inline hint под секцией —
    визуальный шум в редакторе).
    **Что сделано (4 новых файла + 6 замен в `:app` + 2 layout +
    progress log):**
    **(1)** `:app/data/EditorModels.kt` — `EditorVarDecl(name,
  kind: VarKind, min: Int?, max: Int?)` + `enum VarKind(
  displayName, needsMin, needsMax)`. Плюс
    `EditorVarDecl.toVarType(): VarType?` с permissive default
    (`IntMin`/`IntRange` без `min`/`max` → `Integer`). Smart-cast
    проблема: `min`/`max` — `var` (мутабельные поля для UI),
    скопированы в `val minLocal`/`val maxLocal` перед `when`
    (иначе `SMARTCAST_IMPOSSIBLE`).
    **(2)** `:app/data/EditorSnapshot.kt` + `EditorDraft.kt` —
    + `variables: List<EditorVarDecl> = emptyList()`.
      **(3)** `:app/data/Adventure.kt` — `Adventure` +=
      `@SerializedName("variables") variables: Map<String,
      VariableDto>? = null` (nullable — Gson опускает пустое);
      новый `VariableDto(type: String, min: Int? = null,
  max: Int? = null)`.
      **(4)** `:app/utils/EditorDraftManager.kt` — сигнатура
      `saveDraft(...)` += `variables: List<EditorVarDecl>` **без
      default** — все call-sites обязаны явно передать, чтобы
      переменные не терялись в черновике молча.
      **(5)** Новые layout: `item_variables_header.xml` (аккордеон
      «📋 Переменные» с `var_header`/`tv_var_chevron`/`tv_var_summary`/
      `var_body`/`btn_add_variable`) и `item_editor_variable.xml`
      (имя, spinner типа, `min`/`max` (скрыты по умолчанию),
      удаление).
      **(6)** Новый `:app/ui/editor/VariablesAdapter.kt` (~170
      строк): header (позиция 0) + N переменных (позиции 1..N).
      Структура как у `InitialStateAdapter` — один header с
      аккордеоном. Spinner с 6 `VarKind`; `min`/`max` показываются
      только когда `VarKind.needsMin`/`needsMax`. `onDirty(false)`
      для изменений полей, `onDirty(true)` для add/remove (structural —
      undo/redo). `pluralVariables(n)` — русская плюрализация для
      счётчика «N переменных».
      **(7)** `:app/ui/editor/AdventureEditorViewModel.kt` —
      `variables: MutableList<EditorVarDecl>` + `variablesExpanded:
  Boolean = false`. `takeSnapshot`/`restoreSnapshot` включают
      `variables` (undo/redo не теряет). Новые методы
      `addVariable()`, `removeVariable(index)`,
      `editorVarDeclFromDto(name, dto)` (unknown `type` → `Integer`
      — permissive, не терять переменную), `buildVariablesDto()`
      (`EditorVarDecl` → `Map<String, VariableDto>?`, пустой →
      `null`). `buildAdventure()` += `variables = buildVariablesDto()`
      (Gson-save пишет `variables`). `buildIrAdventure()` —
      `variables = emptyMap()` заменён на реальные из editor через
      `editorVarDeclFromDto`-inverse (`EditorVarDecl.toVarType()` →
      `:engine/ir/types/VarDecl`); пустая коллекция → `emptyMap()` →
      fallback C4 работает через tags + initialState.
      **(8)** `:app/ui/editor/AdventureEditorFragment.kt` —
      `variablesAdapter: VariablesAdapter` (поле + инициализация в
      `onCreate`), `ConcatAdapter(variablesAdapter,
  initialStateAdapter, adapter)` (`variablesAdapter` **первым**,
      per C1). `viewModel.variables` передан в
      `EditorDraftManager.saveDraft` (2 call-sites:
      `saveDraftImmediate`, `onDestroyView`). `maybeRestoreDraft` и
      `loadFromUri` восстанавливают `viewModel.variables` из
      `draft.variables` / `adventure.variables`. `refreshAfterHistoryChange`,
      `maybeRestoreDraft`, `loadFromUri` — плюс
      `variablesAdapter.notifyDataSetChanged()`. `loadFromUri` —
      AlertDialog «Типы не заданы. … Объявить типы сейчас?» если
      `viewModel.variables.isEmpty()` (кнопка «Объявить» раскрывает
      `viewModel.variablesExpanded = true` и скроллит к секции).
      `showValidationDialog` — плюс `ValidationIssue(WARNING,
  "Типы переменных не заданы", ...)` если пусто.
      `jumpToScene`/`btnAddScene` — `+2` вместо `+1`
      (VariablesAdapter + InitialStateAdapter = 2 header-позиции
      сверху).
      **(9)** Progress log (эта запись).
      **Пользовательская выгода:** (a) автор **видит**
      объявления типов рядом со сценами, может добавить/изменить/
      удалить переменную без ручной правки JSON; (b) при
      сохранении `variables` пишется в файл (`"variables":
  {"health": {"type": "int_range", "min": 0, "max": 100}, ...}`)
      — данные не теряются; (c) при открытии файла без объявлений
      — **одноразовый** диалог с действием («Объявить сейчас»),
      не блокирующий, не назойливый; (d) при «Проверить» на файле
      без объявлений — **warning** в общем списке (не ERROR —
      fallback C4 всё равно ловит `UnknownVariable` / `HasOnNonBool` /
      wrong effect, так что валидация не «пропадает»); (e) undo/redo
      сохраняет переменные (snapshot включает); (f) черновик
      сохраняет/восстанавливает переменные (draft включает);
      (g) fallback C4 продолжает работать — после Ф2.7 не нужно
      сразу объявлять переменные, чтобы получить какую-то пользу.
      **Что НЕ сделано (осознанно, для следующих шагов):**
      (a) `:engine/format/JsonToIr` **не** читает `variables` из
      JSON — Ф2.8 (`AdventureDto` += `variables`, `buildAdventure`
      маппит `VariableDto` → `VarDecl`); сейчас TypeChecker
      работает через fallback C4, `test_adventure.json` остаётся
      `"version": 5` без `variables`; (b) `inferTypes(ir)` —
      автоматический вывод типов из использования — **не**
      делается; это Ф2.8 DoD («Скрипт/утилита `inferTypes(ir)`
      предлагает типы по использованию»); (c) `VarKind.ITEM_LIST`
      — enum-вариант есть, UI редактирует, но в IR это `VarType.
  ItemList` с ограниченной поддержкой в `TypeChecker` (только
      «non-numeric»); реальное использование ItemList в эффектах
      — Ф4/N3; (d) cross-field валидация `TagCategory ↔ VarType`
      (`HEALTH`+`Bool` — бессмысленно, но возможно) — Ф2.8, когда
      `variables` начнёт парситься и fallback перестанет быть
      основным путём.
      **Verified:** `./gradlew :engine:test --rerun-tasks` →
      **176/176 PASSED** (`:engine` не менялся — Ф2.7 чисто
      `:app`). `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL,
      0 warnings. Smoke (телефон): A. `test_adventure.json` — load
      → диалог «Типы не заданы» (потому что в файле нет
      `variables`) → «Объявить» → открывается секция
      «📋 Переменные», пусто; «Позже» → ничего не происходит;
      «Проверить» → «Предупреждений: 1 • Типы переменных не
      заданы» ✅. B. Добавить переменную `health` типа `IntRange`,
      min=0, max=100 → «Сохранить» → в файле
      `"variables": {"health": {"type": "int_range", "min": 0,
      "max": 100}}` ✅. C. Перезапуск редактора → `variables`
      восстановились из draft/file ✅. D. Удалить переменную →
      undo → переменная вернулась ✅. E. `IntMin` без `min` →
      в JSON `{"type": "int_min"}` (Gson опустил `null` — это
      не идеально, но валидно; Ф2.8 `JsonToIr` прочитает как
      `IntMin(min = null)` → permissive `Integer` через
      `toVarType()`-inverse) ✅. F. `adventure.variables` от
      `:app/data` читается в `buildIrAdventure` через
      `editorVarDeclFromDto` — предупреждение «Типы не заданы»
      исчезает из validation ✅.
      **Следующее действие — Ф2.8:** `JsonToIr` **читает
      `variables`** из JSON (симметрично Ф2.7 save-пути).
      `AdventureDto` += `variables: Map<String, VariableDto>` +
      `VariableDto` в `:engine/format/dto`. `JsonToIr.buildAdventure`
      маппит DTO → `VarDecl`. Плюс `inferTypes(ir)`: утилита,
      предлагающая типы по использованию (`initialState.flags` →
      Bool, `initialState.stats` → IntMin(0), `Effect.ModifyStat` →
      IntMin(0), `Condition.Has` → Bool). Обновить
      `test_adventure.json` += `variables` (объявления типов для
      всех 11 тегов) — теперь TypeChecker работает в strict-режиме
      и `real test_adventure_json is type-clean` остаётся зелёным.
      `SchemaFingerprintTest.expectedFlatFields["Adventure"]` уже
      содержит `variables` (был добавлен при Ф1.2), а
      `expectedFlatFields["VariableDto"]` — новый. Golden
      пересоздать (`variables` теперь в IR, `encodeDefaults = true`
      не меняется).

- Продолжение в [progress-log-3.md](./progress-log-3.md).
    
