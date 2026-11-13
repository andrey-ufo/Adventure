- Начало в docs/progress/progress-log.md

> **Legacy note:** Entries before Ф2.8 are written in Russian, following the
> historical record at the time. Since 2026-11-12 (Ф2.8) the progress log is
> in English — see `documentation-rules.md §1`. Do not translate this file
> retroactively: it is a historical record, and translation risks losing nuance.

## Progress log

- **Current** (English, from Ф2.8): `progress-log-3.md`
- **Archive** (Russian, before Ф2.8): `progress-log-1.md`, `progress-log-2.md`

Chronological, append-only. Each entry: date + task ID + short result +
verification. This is the **single source of truth** for "what is done".

Per-task statuses (`☐`/`☑`) in the phase tables above are **not maintained
per-step** — they reflect gate closure only, and may lag behind this log.
Same for Appendices A–D: update them only when a whole phase gate closes.

- **2026-11-05 · Ф0.1** — CI pipeline added: `../../.github/workflows/ci.yml`
  (checkout, JDK 21, Android SDK, `:app:lintDebug`,
  `:app:testDebugUnitTest`, artifact upload). Verified: pipeline runs,
  commit SHA is logged in the "Show commit info" step, `lint-reports` and
  `test-reports` artifacts are produced and downloadable.
- **2026-11-05 · Ф0.2** — `../lint-baseline.md` created: snapshot of
  known lint issues, per-file hardcoded-text counts, list of deferred
  warnings (UseKtx, NotifyDataSetChanged, UnusedResources, autofill,
  inputType), decision options documented. Saved in UTF-8.
- **2026-11-05 · Ф0.3** — Foreground Service error does not reproduce
  with lint 9.5.0 (`0 errors, 248 warnings`). Manifest is correct
  (`FOREGROUND_SERVICE_HEALTH` + `foregroundServiceType="health"`).
  `../../app/build.gradle.kts`: `lint {}` block moved inside `android {}`
  (was placed after the closing `}` — caused 110 Gradle parse errors;
  fixed by full rewrite of the file), `abortOnError = true`,
  `baseline = file("lint-baseline.xml")`. `app/lint-baseline.xml`
  generated (needs to be committed to git). Decision + rationale
  recorded in `docs/lint-baseline.md` → Decision log. **Ф0.7 (optional
  fix) — not required, skipped.**
- **2026-11-05 · Ф0.4** — Must/Nice reconciled with checklist 2.1.
  **C1:** P1.base («Редактор и визуализация не деградируют до 1–2 тыс.
  узлов…») и P4.base («Автотестирование не падает по памяти на
  реалистичных проектах») восстановлены в Приложении A с точными
  формулировками из 2.1; привязаны к Ф9.9 (P1.base) и Ф5.9 (P4.base).
  **C2:** гипотеза `.advanced`-расширений подтверждена — явных Nice 17,
  `.advanced` не отдельные Nice. **C3:** A4 (интерактивная отладка) →
  Nice, перенесено в Ф11. **C4:** E1.base (single-file HTML) → вариант B
  (roadmap). Итог: 42 исходных Must, 2 перенесены в Nice → **40 Must
  к приёмке**, Nice колонка = 29.
- **2026-11-05 · Ф0.4 (fin)** — Closing note. Приложение A «Итого»
  по-прежнему показывает 42 — **актуально 40 к приёмке** (см. запись
  выше). Приложение B содержит 17 Nice — **актуально 19** (17 + A4 +
  E1.base). «Следующее действие» уже переехало на Ф1.1. Таблицы
  сознательно не правятся по ходу работы; **при расхождении таблиц и
  Progress log — верить Progress log**. **Гейт Ф0: закрыт.** Следующее
  действие — Ф1.1 (создать модуль `:engine`).
- **2026-11-05 · Ф1.1** — Модуль `:engine` создан (Kotlin/JVM, без
  Android). Файлы: `../../engine/build.gradle.kts` (`jvmToolchain(21)`,
  `explicitApi()`, `kotlinx-serialization-json`, `kotlin("test")`),
  `settings.gradle.kts` содержит `include(":engine")`,
  `gradle/libs.versions.toml` расширен (`kotlin = "2.4.21"`,
  `kotlinxSerialization = "1.11.0"`, плагины `kotlin-jvm`,
  `kotlin-serialization`). Корневой `build.gradle.kts` объявляет оба
  Kotlin-плагина с `apply false`. Версии: KGP **2.4.21**,
  `kotlinx-serialization-json` **1.11.0** — согласованная пара.
  Попутно: в `pluginManagement.repositories` и
  `dependencyResolutionManagement.repositories` добавлено зеркало
  `https://maven-central.storage-download.googleapis.com/maven2/`
  первым в списке — решило зависание на скачивании
  `fus-statistics-gradle-plugin:2.4.21` (застревал на 27 KB с
  `repo.maven.apache.org`; зеркало Google отдало за секунды).
  **Verified:** `./gradlew :engine:build` → `BUILD SUCCESSFUL in 49s`.
  **Открытый риск (уйдёт в Ф1.2):** модуль всё ещё пустой (нет `.kt`
  файлов), поэтому `kotlinx-serialization-json` и `kotlin-test` в
  classpath не разрешались. При первом `.kt` файле они скачаются (или
  упадут с явной ошибкой — тогда чиним). Никаких блокеров для Ф1.2.
- **2026-11-05 · Ф1.2 (decisions)** — Архитектурные решения для IR
  зафиксированы до написания кода (обсуждены с владельцем проекта):
  **(1)** пакет `com.myapp.adventure.engine.ir` (отделён от `:app/data`);
  **(2)** `Condition` — sealed-иерархия с самого начала (не строка);
  **(3)** `Effect` — sealed-иерархия; **(4)** IR проектируется заново,
  не копия `Adventure.kt` из `:app/data`; **(5)** `@Serializable` прямо
  на IR (без отдельных DTO — один источник правды; `kotlinx.serialization`
  работает и в JS, ограничение на JS-таргет не нарушено);
  **(6)** ошибки парсинга — через `Result`/`Either`, не исключения
  (готовит Ф2.3); **(7)** `version: Int` в `Adventure` с самого начала
  (миграции IR в Ф5.3); **(8)** `Json { ignoreUnknownKeys = true }`
  при парсинге (forward compatibility, реализация — Ф1.3);
  **(9)** `SceneId` — `@JvmInline value class`, остальные Id — `String`
  (типобезопасность для самого частого Id; рефакторинг остальных —
  позже, если понадобится).
- **2026-11-06 · Ф1.2** — IR написан: 14 файлов в
  `../../engine/src/main/kotlin/com/myapp/adventure/engine/ir`
  (`TagCategory`, `TagDefinition`, `VarType`, `VarDecl`, `Condition`,
  `CompareOp`, `Effect`, `SceneId`, `Choice`, `Scene`, `Chapter`,
  `InitialState`, `DeathScene`, `Adventure`). Все `public` +
  `@Serializable`, `explicitApi()` включён. Особенности относительно
  `:app/data/Adventure.kt`: (a) `set_flags`/`remove_flags`/
  `modify_stats` схлопнуты в `List<Effect>` (порядок сохраняется
  парсером в Ф1.3); (b) `isChapterEnd` убран — границы глав по
  `Chapter.scenes`; (c) `InitialState.flags`/`stats` non-null с
  дефолтами; (d) `DeathScene` — отдельный класс, не подкласс `Scene`;
  (e) `DeathScene.id: SceneId`; (f) `SceneId` — value class,
  сериализуется прозрачно в строку; (g) `Adventure.version` с дефолтом
  `CURRENT_VERSION = 1`; (h) `Adventure.chapters` без дефолта — пустой
  файл должен падать на парсере. **Verified:** `./gradlew :engine:build`
  → `BUILD SUCCESSFUL` (после каждого из 7 подшагов). **Открытый
  вопрос — на входе в Ф1.3:** `Condition`/`Effect` в IR — это уже
  AST, но JSON хранит их строками (`"condition": "has_sword"`) и
  тремя параллельными полями. Парсер Ф1.3 должен (а) парсить строки
  условий в `Condition` (это AST-парсер, не только JSON-парсер),
  (б) собирать `Effect` из трёх полей с сохранением порядка. Также
  нужно определить правило маппинга `has_X` для `HEALTH`/`ITEM` тегов
  (сейчас в читалке `flags.contains(X) || stats[X] > 0` — это надо
  воспроизвести в AST или явно зафиксировать, что `Condition.Has`
  проверяет и флаг, и стат одновременно).
- **2026-11-06 · Ф1.3.1** — Фундамент парсера: 3 файла в
  `../../engine/src/main/kotlin/com/myapp/adventure/engine/parse` +
  1 тестовый в `engine/src/test/kotlin/com/myapp/adventure/engine/parse/`.
  **`ParseError`** — sealed: `Syntax`, `Structure`, `ConditionError`
  (все с `message: String` + контекст: `path`, `input`/`position`).
  **`ParseResult<T>`** — sealed `Success(value)` / `Failure(errors)`,
  `Failure.errors` гарантированно непустой. Собственный, не stdlib
  `Result` — потому что `Result.failure` несёт один `Throwable`, а нам
  нужен список структурированных ошибок для будущей панели ошибок
  в редакторе (Ф9.2). **`ConditionParser`** — object, `parse(input) →
  ParseResult<Condition>`. Синтаксический (без знания типов, это
  Ф2.2). Поддерживает: `has_X`, `!has_X`, `X >= N`, `X > N`, `X <= N`,
  `X < N`, `X == N`, `X != N`, отриц. числа, пробелы, составные
  имена переменных (`"my stat > 0"`). Двухсимвольные операторы
  (`>=`, `<=`, `==`, `!=`) проверяются раньше односимвольных, чтобы
  `>` не съело `=` из `>=`. **Семантика `has_X` пересмотрена** по
  решению владельца: **только для `Bool`-переменных**, для числовых —
  только `Compare` (`sword > 0`, не `has_sword`). KDoc у
  `Condition.Has` обновлён, старый `:app`-стиль
  (`flags.contains(X) || stats[X] > 0`) — наследие, будет
  переписано в `test_adventure.json` в Ф2.8. Парсер **не** отвергает
  `has_sword` для `ItemCount` — это семантика type checker (Ф2.2),
  разделение ответственности. Не поддержано пока (Ф3.2):
  `&&`, `||`, `!X` общего вида, скобки, `X in a..b` — падают с
  `ConditionError` (не silent pass). **Verified:** `./gradlew
  :engine:test --rerun-tasks` → `BUILD SUCCESSFUL`, в
  `engine/build/reports/tests/test/index.html` — **10 tests, 0
  failures, 0.469s**. В `engine/build.gradle.kts` добавлен
  `tasks.withType<Test> { testLogging { events("passed", "skipped",
  "failed") } }` — теперь каждый тест печатается в консоли
  (`PASSED`/`FAILED`), это постоянное улучшение для всех будущих
  шагов.
- **2026-11-06 · Ф1.3.2** — DTO-слой: 1 файл
  `engine/src/main/kotlin/com/myapp/adventure/engine/format/dto/Dto.kt`
  (7 `internal data class` в одном файле: `AdventureDto`, `ChapterDto`,
  `SceneDto`, `ChoiceDto`, `TagDefinitionDto`, `InitialStateDto`,
  `DeathSceneDto`). Все `@Serializable`, `internal`. `TagCategory`
  переиспользован из IR (`com.myapp.adventure.engine.ir.TagCategory`),
  дублирующий enum не создавался. `version: Int?` — единственное
  nullable-поле (нужно различать «нет версии» и «явная версия»);
  остальные опциональные поля — non-null с дефолтами. `variables`
  отложены до Ф2.8 (`test_adventure.json` пока без них). **Решение
  по пакету** (отдельно обсуждено с владельцем): DTO в
  `format/dto/`, а не `parse/` — потому что в Ф8 появятся
  `IrToJson` и JSON Schema рядом, и оба они про **формат**, а не про
  **парсинг**. `parse/` остаётся для мини-языка условий
  (`ConditionParser`, `ParseError`, `ParseResult`). **Verified:**
  `./gradlew :engine:test --rerun-tasks` → 16/16 PASSED (10
  `ConditionParserTest` + 6 `AdventureDtoTest`), 14s. Тесты
  подтверждают: `coerceInputValues = true` заменяет явный `null` на
  дефолт, `ignoreUnknownKeys = true` игнорирует `future_field`,
  `version` отсутствует → `null` в DTO, `version: 2` читается.
- **2026-11-06 · Ф1.3.3.1** — Copy task для `test_adventure.json`:
  в `../../engine/build.gradle.kts` добавлено
  `tasks.named<Copy>("processTestResources") { from(rootProject.file("app/src/main/res/raw/test_adventure.json")) }`.
  **Развилка (3 варианта, обсуждено с владельцем):** (A) ручная копия
  в `engine/src/test/resources/` — может разойтись с оригиналом;
  (B) относительный путь `../app/...` из тестов — нарушает изоляцию
  `:engine` от `:app`; (C) Gradle copy task — всегда синхронизировано.
  **Выбран C.** **Пользовательская выгода:** новый пользователь при
  первом запуске получает `test_adventure.json` скопированным в его
  папку `Adventures/` (SetupFragment). Без C тесты `:engine`
  проверяли бы ручную копию (риск расхождения) или inline-упрощение
  (не тестирует реальный файл). С C **CI ловит поломку реального
  файла до релиза** — «первое впечатление» нового пользователя
  защищено. **Цена:** +4 строки в Gradle, риск configuration cache
  (проверено — работает). **Verified:** `./gradlew :engine:test
  --rerun-tasks` → 16/16 PASSED, `5 actionable tasks: 5 executed`
  (было 4 — `processTestResources` теперь работает), файл
  `engine/build/resources/test/test_adventure.json` создан,
  содержимое идентично `app/src/main/res/raw/test_adventure.json`.
- - **2026-11-06 · Ф1.3.3.2** — `JsonToIr` (маппер DTO → IR) +
    `JsonToIrTest` (12 тестов) + правка `ParseError.ConditionError`
    (добавлено поле `path: String? = null`) + правка `ConditionParser`
    (закрыт silent pass). **Решения Ф1.3.3 (обсуждены):** (1) `object
  JsonToIr { fun parse(text: String): ParseResult<Adventure> }` —
    stateless, `Json { }` один раз внутри; (2) `path` с индексами + id
    (`"chapters[1:forest].scenes[0:start].choices[2].condition"`), чтобы
    автор в редакторе видел имя сцены/главы, а не только индекс;
    (3) плоский `List<ParseError>` в парсере — группировка по path
    остаётся в `Analyzer` (Ф1.6) / UI (Ф9.2). **Пользовательская
    выгода:** (a) все семантические ошибки за один проход (12 тестов
    это подтверждают: `multiple errors accumulated` PASSED); (b)
    сообщение об ошибке содержит путь с именем сцены — автор находит
    место за секунды, а не «посчитай сам главу 2, сцену 4»; (c) в
    случае битого JSON — один `ParseError.Syntax` вместо крэша.
    **Найден и исправлен баг до Ф1.3.4:** `ConditionParser` принимал
    `has_sword && health` как `Condition.Has("sword && health")` — флаг
    с таким именем никогда не существует, условие молча всегда `false`,
    выбор молча скрывался. Это **ровно тот silent pass**, который
    N8/Ф2.5 призван убрать. Исправление: в `ConditionParser` добавлен
    `checkNoReservedChars` (набор `& | ! < > = ( )`) для имён флагов
    и переменных; при обнаружении — `ConditionError` с подсказкой
    «Logical operators (&&, ||, !) ... are not supported yet (planned
    for Ф3.2 / N7.base)». 3 новых теста на регресс. **Verified:**
    `./gradlew :engine:test --rerun-tasks` → **31/31 PASSED** (10
    `ConditionParserTest` + 3 новых + 6 `AdventureDtoTest` + 12
    `JsonToIrTest`), 3s.
- **2026-11-06 · Ф1.3.4** — Интеграционный тест на реальном файле:
  `../../engine/src/test/kotlin/com/myapp/adventure/engine/format/RealAdventureTest.kt`
  (8 тестов). Загружает `/test_adventure.json` через copy task
  (Ф1.3.3.1), парсит через `JsonToIr.parse`, проверяет метаданные,
  главы, death_scene, tags, initial_state, AST `has_sword`.
  **Пользовательская выгода:** `test_adventure.json` — **тот же файл**,
  который SetupFragment копирует в папку нового пользователя при
  первом запуске. Если парсер сломается, CI падает **до релиза**,
  а не новый пользователь видит битое «первое приключение». Это
  защита «первого впечатления». **Verified:** `./gradlew :engine:test
  --rerun-tasks` → **39/39 PASSED** (10 `ConditionParserTest` +
  3 reserved-char + 6 `AdventureDtoTest` + 12 `JsonToIrTest` +
  8 `RealAdventureTest`), 3s. **Ф1.3 закрыт целиком** (4 подшага:
  1.3.1 фундамент парсера → 1.3.2 DTO → 1.3.3.1 copy task →
  1.3.3.2 `JsonToIr` → 1.3.4 реальный файл). Открытые пункты
  (уйдут в Ф1.4/Ф1.6/Ф2): `version < CURRENT_VERSION` → миграции
  (Ф5.3); `next_scene` reachability и дубли `scene.id` → `Analyzer`
  (Ф1.6); `has_sword` на `ItemCount` → type checker (Ф2.2), файл
  переписать в Ф2.8 на `sword > 0`.
- **2026-11-06 · Ф1.3.4.1** — Зафиксирована граница «парсер vs
  анализатор». **(1)** KDoc в `JsonToIr` расширен: явно указано, что
  `JsonToIr.parse` — **единственная** точка входа для JSON → IR
  (используется читалкой Ф1.7, редактором Ф9.2, анализатором Ф1.6,
  тестами Ф1.3.4/Ф1.4, и — в будущем — валидацией авторских файлов
  из `NewAdventures/`). Запрещено писать второй парсер рядом; при
  необходимости нового формата — `parse`-overload или новый
  `<Format>ToIr` с тем же `ParseResult<Adventure>` контрактом (это
  ровно то, что N2/Ф3 существует предотвратить). **(2)** Два
  boundary-теста в `JsonToIrTest`: `typo in flag name parses as Has -
  type check is F2` (`has_swrod` проходит парсер, но не type checker
  Ф2.2) и `unknown next_scene parses as SceneId - reachability is F2`
  (`next_scene: "nonexistent"` проходит парсер, но не analyzer Ф1.6).
  **Пользовательская выгода:** (a) через 3 месяца при добавлении
  новой проверки будет ясно, в какой слой её класть — не будет
  случайного «полу-анализатора» внутри парсера; (b) автор получит
  ошибки «unknown variable 'swrod'» (Ф2.2) и «scene 'nonexistent'
  does not exist» (Ф1.6) — **разные, понятные сообщения из разных
  слоёв**, а не размытое «parse error»; (c) подготовлена почва для
  валидации `NewAdventures/`: тот же `JsonToIr.parse` + analyzer даст
  автору полную картину. **Цена:** два теста, которые **должны**
  падать при смешении слоёв — это фича, не баг. **Verified:**
  `./gradlew :engine:test --rerun-tasks` → **40/40 PASSED**
  (14 `JsonToIrTest` + 8 `RealAdventureTest` + 6 `AdventureDtoTest` +
  12 `ConditionParserTest`), 2s. **Ф1.3 закрыт.**
- **2026-11-06 · Ф1.4** — Golden-тест IR закрыт. **(1)** `../../engine/src/test/kotlin/com/myapp/adventure/engine/format/GoldenWriter.kt` — test-source утилита (`main()`), не входит в jar. Читает `/test_adventure.json` с classpath, парсит через `JsonToIr.parse`, сериализует через `Adventure.serializer()` с `prettyPrint = true, encodeDefaults = false`, пишет в `engine/src/test/resources/golden_ir.json` (12778 chars, 3 chapters). **(2)** В `engine/build.gradle.kts` добавлена задача `:engine:updateGolden` (`JavaExec`, classpath = `sourceSets.test.runtimeClasspath`, **`workingDir = rootProject.projectDir`** — без этого путь `engine/src/test/resources/...` резолвился в `engine/engine/src/...`; поймано при первом запуске, исправлено). **(3)** `engine/src/test/kotlin/com/myapp/adventure/engine/format/GoldenIrTest.kt` — 3 теста: `golden matches current IR` (семантическое сравнение через `Json.parseToJsonElement`, не текстовое — не падает от форматирования и переупорядочивания), `missing golden is a clear failure` (сообщение с командой `updateGolden`), `golden round-trips through IR serializer` (decode → encode = исходный `JsonElement`; защита от encode-only golden, который нельзя прочитать обратно). **Пользовательская выгода:** любое **случайное** изменение IR (переименовали поле, поменяли тип, забыли про старые файлы) падает в CI **до** релиза. Для pre-alpha, где формат будет меняться, это обязательная страховка — иначе первый же PR «улучшил `Choice`» тихо сломает все авторские приключения, а автор узнает об этом, открыв свой файл. **Цена:** PR с **преднамеренным** изменением IR требует `./gradlew :engine:updateGolden` + commit golden в том же PR — две строки, но явные. **Verified:** `./gradlew :engine:test --rerun-tasks` → **43/43 PASSED** (14 `JsonToIrTest` + 8 `RealAdventureTest` + 6 `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest`), 2s. **Заметка для Ф8.2 (экспорт):** golden — это **IR-сериализация** (`{"type":"has","name":"sword"}`), а не adventure-формат (`"has_sword"`). `IrToJson` в Ф8.2 должен идти через DTO (обратное к `JsonToIr`), а не через `Adventure.serializer()` — иначе экспортированный файл не будет читаться `JsonToIr`.
- **2026-11-06 · Ф1.5 (decisions)** — 9 развилок Ф1.5 обсуждены с
  владельцем проекта, решения зафиксированы до кода.
  **(1) `ChoiceId`:** `@JvmInline value class ChoiceId(val value: String)`,
  `Choice.id: ChoiceId`. `JsonToIr` генерирует id, если в JSON нет:
  `"<scene_id>_choice_<index>"` (детерминированно, стабильно при
  вставке). **Пользовательская выгода:** стабильные сохранения —
  редактирование приключения (вставили выбор) не сдвигает выбор у
  старого сохранения, игрок не попадает не туда после возврата.
  **Цена:** golden перегенерировать, IR меняется (небольшой diff).
  **(2) `PlayerState`:** immutable `data class` в
  `engine/ir/PlayerState.kt` (`flags: Set<String>`, `stats:
  Map<String, Int>`), изменения через `copy()`. **Пользовательская
  выгода:** откат «шаг назад» после смерти тривиален (сохранили
  старый snapshot), undo/redo (Ф5/S3) ляжет сверху без переделки,
  меньше багов «изменил состояние не там».
  **(3) `Engine.step` возврат:** sealed `StepResult`:
  `Success(state, currentScene, isDead)` / `Rejected(reason)` /
  `Invalid(message)`. **Пользовательская выгода:** каждый исход
  обрабатывается явно — недоступный выбор → «недоступно» (N4),
  смерть → экран смерти, ошибка → понятное сообщение. Никаких
  молчаливых «ок, но ничего не произошло».
  **(4) `Engine` структура:** object с публичным `step` и
  приватными `applyEffects` / `evaluate` / `findScene` / `isDead`.
  **Пользовательская выгода:** почти нулевая для пользователя —
  архитектура для тестируемости (меньше багов в долгую, эффект
  отложен).
  **(5) Clamp-политика:** `ModifyStat` clamp `>= 0` для **всех**
  переменных на Ф1.5 (временно, до Ф2.8, когда появятся типы
  переменных). **Пользовательская выгода:** игрок видит «❤️ 0»
  после смертельного удара, не «❤️ -5» — аккуратнее визуально.
  **Цена:** `mana -3` тоже уходит в 0, что может не соответствовать
  замыслу автора; после Ф2.8 переделаем на clamp по типу (HEALTH,
  ITEM → `>= 0`, `Integer`/`IntRange` — по границам).
  **(6) Индекс сцен:** `Engine.compile(adventure): CompiledAdventure`
  — `Map<SceneId, Scene>` строится один раз при загрузке. Golden
  **не** меняется (`Adventure` остаётся чистым IR).
  **Пользовательская выгода:** переход между сценами O (1), не O (n).
  При 2000 сцен (P1.base, Must) игрок не видит разницы, но при
  10 000 (P1.advanced, Nice) O (n²) был бы фризом; архитектура
  заложена **сейчас**, тестируется стресс-тестом на генераторе.
  **(7) `StressAdventureGenerator` + `PerformanceTest` в Ф1.5:**
  генератор в `../../engine/src/test/kotlin`, не файл. Прогон через
  `Engine.step` на 100 / 1000 / 5000 / 10000 сцен. **Пользовательская
  выгода:** архитектура O (1) проверена **до** того, как автор с
  2000 сцен откроет приключение; P1.base (1–2 тыс.) и
  P1.advanced (10k) застрахованы от «забыли про индекс» при
  рефакторинге Ф5.7.
  **(8) Persistent collections:** на Ф1.5 **не берём** — обычные
  `Set`/`Map`. Если профилирование покажет (Ф5.8) — заменим через
  `@Serializable(with=...)` (обратно совместимо). **Пользовательская
  выгода:** не тянем зависимость и не усложняем API ради
  оптимизации, которая пока не подтверждена замером.
  **(9) `test_adventure.json` остаётся компактным (20–30 сцен):**
  правило закреплено в `docs/test-adventure-policy.md` → Rule 6.
  **Пользовательская выгода:** golden читаем в PR-diff; CI не
  тормозит; при первом запуске пользователь видит компактный
  пример, а не «мегабайтную свалку». 10 000 сцен = ~3 000 000 слов
  = 5× «Война и мир» — это не «как большие книги», это «в разы
  больше», нереалистично для одного автора. Реалистичный максимум
  автора — 500–2000 сцен, это P1.base (Must). 10k+ — P1.advanced
  (Nice), проверяется генератором, не файлом.
  **Побочные мини-задачи (не блокеры Ф1.5, отдельно):**
  (a) `test_adventure.json` копируется в `Adventures/` — временная
  мера; правильное место — `TestAdventures/` и только в debug;
  зафиксировано в `docs/test-adventure-policy.md` → Rule 6;
  реализация — мини-задача в `:app` после Ф1.5.
  (b) Копирование test_adventure.json пользователю — UX-минус
  «чужое приключение в моей библиотеке»; устранение — в той же
  мини-задаче.
- **2026-11-06 · Ф1.5.2** — Базовые классы рантайма, **не подключённые**
  к IR (компиляция не сломалась, golden не изменился):
  **(1)** `engine/ir/ChoiceId.kt` — `@Serializable @JvmInline value
  class ChoiceId(val value: String)`. KDoc объясняет: стабильный id
  выбора → сохранения выживают при редактировании (вставка выбора не
  сдвигает «3-й» у старого сохранения); генерация
  `"<scene_id>_choice_<index>"` в `JsonToIr`; уникальность — за
  `Analyzer` (Ф1.6). **(2)** `engine/ir/PlayerState.kt` — `data class
  PlayerState(flags: Set<String>, stats: Map<String, Int>)`, immutable,
  с дефолтами `emptySet()`/`emptyMap()`. KDoc: отличается от
  `InitialState` (это runtime, не файл); immutable для
  undo/step-back/save; **никакого clamping** в самом классе —
  политика в `Engine.applyEffects` (Ф1.5), потому что зависит от
  `VarType` (Ф2.8). **Verified:** `./gradlew :engine:test
  --rerun-tasks` → **43/43 PASSED**, golden не изменился
  (новые классы ещё ни к чему не подключены).
- **2026-11-06 · Ф1.5.3** — `Choice.id: ChoiceId` интегрирован в IR.
  **(1)** `engine/ir/Choice.kt` — добавлено поле `id: ChoiceId`
  (обязательное, без default — «нет id» невалидный IR; парсер всегда
  генерирует). KDoc: id делает сохранения устойчивыми к
  редактированию; генерация `"<scene_id>_choice_<index>"` при
  отсутствии в JSON. **(2)** `ChoiceDto.id: String? = null` —
  nullable, потому что в JSON может не быть. **(3)**
  `JsonToIr.buildChoice` — генерирует `ChoiceId("${sceneId}_choice_$index")`,
  если `dto.id` пустой; явный id из JSON сохраняется. **(4)**
  `JsonToIrTest` — 2 новых теста: `choice id generated when absent in
  JSON` (проверяет `start_choice_0`, `start_choice_1` для двух choices
  в сцене `start`) и `choice id from JSON preserved when present`
  (проверяет `my_custom_id`). **Пользовательская выгода:** при
  редактировании приключения (вставили новый выбор в середину)
  старые сохранения игрока указывают на **тот же** выбор, не
  сдвигаются. Игрок возвращается через день, нажимает «продолжить»
  — попадает туда, куда шёл. Без `ChoiceId` (индекс) — попадал бы
  на соседний выбор, «почему я тут?» и потеря прогресса.
  **Демонстрация golden-workflow:** после правки IR
  `GoldenIrTest > golden matches current IR FAILED` и
  `golden round-trips through IR serializer FAILED` (MissingFieldException —
  старый golden без `id` не читается) — **это и есть доказательство
  работы защиты**. `./gradlew :engine:updateGolden` → 14097 chars
  (было 12778, +1319 байт на 5 choices × `"id": "..."`). После
  update — снова зелёные. Ровно workflow из Ф1.4. **Verified:**
  `./gradlew :engine:test --rerun-tasks` → **45/45 PASSED** (14
  `JsonToIrTest` + 2 новых + 8 `RealAdventureTest` + 6
  `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest`),
  2s. Golden обновлён.
- **2026-11-06 · Ф1.5.4.1–2** — Рефакторинг шлюзов (Gate на сцене,
  не на главе). **(1)** `engine/ir/Gate.kt` — `@Serializable
  @JvmInline value class Gate(requiredSteps: Int)`. KDoc: гейт
  принадлежит **сцене**, а не главе; автор может ставить гейт где
  угодно (первая/средняя/последняя сцена, несколько гейтов в главе,
  ноль гейтов); положителен по построению (`Gate(0)` бессмыслен —
  парсер отвергает `gate <= 0`); **не проверяется** `Engine.step` —
  шаги считает читалка (`:app`), engine не знает про шагомер.
  **(2)** `engine/ir/Scene.kt` — добавлено `gate: Gate? = null`.
  **(3)** `SceneDto.gate: Int? = null`, `JsonToIr.buildScene` маппит
  `gate` с валидацией (`<= 0` → `ParseError.Structure` с path).
  **(4)** `test_adventure.json` переписан на v2 с демонстрацией:
  гейт **100 шагов в середине главы** (`health_test`), гейт **5** на
  последней сцене главы 1 (`chapter_end_1` — эквивалент старого
  `required_steps: 5`), гейт **4000** на последней сцене главы 2
  (`chapter_end_2`), **межглавный `next_scene`**
  (`conditions_test` глава 2 → `final` глава 3, обходит гейт 4000).
  `required_steps` на главах убран из JSON. **(5)**
  `RealAdventureTest` — переименован
  `three chapters with expected step gates` →
  `three chapters with gates on specific scenes`; новые тесты
  `gates on specific scenes` (проверяет 100/5/4000 на конкретных
  сценах и null на остальных) и `cross-chapter next_scene is allowed`.
  **(6)** `JsonToIrTest` — 4 новых: `gate absent → null`,
  `gate positive → parsed`, `gate zero → Structure error`,
  `gate negative → Structure error`. **Пользовательская выгода:**
  автор теперь может ставить гейт **где угодно** — в середине главы,
  на нескольких сценах сразу, ноль гейтов (чистая группировка);
  межглавные переходы разрешены (автор осознанно делает «прыжок»,
  `Analyzer` в Ф1.6 предупредит, но не запретит). Раньше автор был
  заперт в «гейт = конец главы» — новая модель развязывает руки.
  **Цена:** 0 для пользователя; для нас — golden перегенерирован
  (было 14097 → 14723 chars, +626 на 3 `"gate": N` и структурные
  изменения). **Verified:** golden упал до `updateGolden` (как
  планировали), после — **51/51 PASSED** (18 `JsonToIrTest` +
  10 `RealAdventureTest` + 6 `AdventureDtoTest` + 12
  `ConditionParserTest` + 3 `GoldenIrTest`), 2s.
- **2026-11-06 · Ф1.5.4.3** — Убран `Chapter.requiredSteps` из IR
  (финал рефакторинга шлюзов). **(1)** `engine/ir/Chapter.kt` — поле
  удалено; KDoc переписан: «глава — чисто нарративная группировка»
  (title, id, scenes; ничего механического); явно описано, что гейты
  живут на `Scene.gate` (Ф1.5.4.1), что даёт автору свободу (гейт в
  середине главы, ноль гейтов, несколько гейтов в главе, межглавные
  `nextScene`); задокументирована миграция v1 → v2 для тех, кто
  сделал fork репозитория. **(2)** `ChapterDto.requiredSteps` —
  оставлен, но помечен как **legacy (v1)**; комментарий объясняет,
  что `JsonToIr` мигрирует ненулевое значение на последнюю сцену
  **предыдущей** главы. **(3)** `Adventure.CURRENT_VERSION` = 2;
  version history расширен. **(4)** `JsonToIr`: миграция v1 → v2 —
  `effectiveVersion = dto.version ?: 1` (файлы без version = v1);
  для главы `index` берётся `dto.chapters[index + 1].requiredSteps`
  (не текущей!) → `Gate(N)` на последней сцене главы `index`. Логика:
  в v1 `chapter[i].required_steps = N` означает «пройти N, чтобы войти
  в главу i», т.е. гейт стоит на выходе из главы `i-1` — это и есть
  «последняя сцена предыдущей главы». **(5)** `JsonToIrTest` — 2
  новых: `v1 file migrates chapter required_steps to gate on previous
  chapter last scene` (проверяет миграцию на конкретной сцене) и
  `v1 file explicit version 1 migrates the same way` (то же, но с
  явным `version: 1`). **(6)**
  `JsonToIrTest > minimal valid adventure` обновлён: `version == 2`
  (было жёстко 1). **Пользовательская выгода:** старые v1-файлы
  (если кто-то уже создал приключение в pre-alpha до рефакторинга)
  читаются, гейты автоматически переезжают на правильную сцену —
  автор не потеряет шлюз молча. **Пойманный баг в процессе:** первая
  версия миграции брала `dto.chapters[index].requiredSteps` вместо
  `dto.chapters[index + 1]` — гейт переезжал на последнюю сцену
  **той же** главы вместо предыдущей. Тесты на миграцию упали сразу
  (2 из 2), ловля до коммита. **Наблюдение о golden:** удаление
  `Chapter.requiredSteps` **не изменило** `golden_ir.json` — потому
  что `encodeDefaults = false` и поле имело default `0`, а в
  `test_adventure.json` (уже v2 после батча 2) `required_steps` на
  главах не было → `requiredSteps = 0` → сериализатор его не писал.
  Удаление поля защищено **компилятором** (код, обращающийся к
  `chapter.requiredSteps`, не соберётся), это сильнее golden-теста.
  **Известное ограничение golden (на будущее):** `encodeDefaults =
  false` → golden не отслеживает **добавление** полей с default
  (поле с дефолтом не появляется в golden → `golden matches`
  остаётся PASSED). Это OK для обратной совместимости, но не
  сигнал о смене формата. Если понадобится ловить schema-изменения
  (а не только значения) — отдельный «schema fingerprint» тест
  (список имён полей, не значений). Пока не нужно.
  **Verified:** `./gradlew :engine:test --rerun-tasks` → **53/53
  PASSED** (22 `JsonToIrTest` + 10 `RealAdventureTest` + 6
  `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest`),
  2s. `updateGolden` записал тот же 14723 chars (golden не изменился,
  как и объяснено выше).
- **2026-11-06 · Ф1.5.4.4** — `SchemaFingerprintTest` — фиксирует
  **имена** полей и вариантов в IR, независимо от значений. Закрывает
  дыру `GoldenIrTest` + `encodeDefaults = false`: golden **не ловит**
  добавление/удаление полей с default (такое поле не попадает в
  сериализованный JSON → golden не меняется). `SchemaFingerprintTest`
  ловит: 4 теста — `flat class fields` (`Adventure`, `Chapter`,
  `Scene`, `Choice`, `TagDefinition`, `InitialState`, `DeathScene`,
  `VarDecl` — захардкоженный Map `имя → Set<сериализованных имён
  полей>`), `Condition sealed variants`, `Effect sealed variants`,
  `VarType sealed variants`. **Захардкоженный** список, не
  рекурсивный обход `descriptor`: цель — **ревью** (в PR-diff видно
  «добавил `age` в `Adventure` + в `expectedFlatFields`»), а не
  «автоматически поймать». Дополняет golden: golden — «значения в
  файле сейчас», fingerprint — «из каких полей состоит схема»; оба
  должны быть зелёными. **Поймал сам себя при разработке:**
  первоначальный `variantsOf` брал варианты sealed с первого уровня
  descriptor, но `kotlinx.serialization` для sealed возвращает
  **обёртку** `{type, value}` — варианты лежат на втором уровне
  (`value.getElementDescriptor(i)`). `exceptionFormat = FULL` в
  `../../engine/build.gradle.kts` (постоянное улучшение) сразу показал
  `expected:/actual:` — фикс `polymorphicVariants` с
  `require(elementsCount == 2)` и именами `type`/`value`. KDoc
  теста объясняет «зачем», «что не проверяет» (типы, nullability,
  значения `@SerialName`, value classes — `SceneId`/`ChoiceId`/`Gate`
  сериализуются прозрачно, полей нет) и «maintenance» (добавил
  flat-класс → добавь в `expectedFlatFields` + в тест). **Цена
  (пользователь):** 0. **Выгода (автор):** невозможно молча удалить
  `Adventure.version` (миграции сломались бы) или добавить поле с
  default без явного решения — CI стоп, ревьюер видит оба изменения.
  **Verified:** `./gradlew :engine:test --rerun-tasks` → **57/57
  PASSED** (22 `JsonToIrTest` + 10 `RealAdventureTest` + 6
  `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest`
  + 4 `SchemaFingerprintTest`), 2s.
  - **2026-11-06 · Ф1.5.4.5** — Документация рефакторинга шлюзов.
    **(1)** `../glossary.md` — `Step gate / Gate` и `Required steps`
    переформулированы: гейт — на сцене, не на главе; `Required steps` —
    числовой порог `Gate`, в v1 жил на `chapters[].required_steps`,
    мигрируется на последнюю сцену предыдущей главы. В JSON-полях:
    `chapters[].required_steps` помечено как **legacy (v1 only)**,
    добавлено `scenes[].gate` (v2+). **(2)** `docs/test-adventure-policy.md`
    — добавлен **Rule 7**: «главы — чисто нарративная группировка,
    гейты — на сценах»; перечислено, что автор теперь может (гейт в
    середине главы, без гейтов, несколько гейтов в главе, межглавные
    `nextScene`); `test_adventure.json` демонстрирует все четыре
    (100 на `health_test`, 5 на `chapter_end_1`, 4000 на
    `chapter_end_2`, `conditions_test → final`); backward compat:
    v1-файлы мигрируются автоматически, `required_steps` в новых
    главах — **не добавлять** (legacy, игнорируется); упомянуты две
    страховки — `GoldenIrTest` (значения) и `SchemaFingerprintTest`
    (имена полей и варианты) — чтобы автор знал, какой тест обновлять
    при добавлении поля. **Пользовательская выгода:** через 3 месяца
    при «добавлю новую фичу» разработчик/автор видит **где** ставить
    гейт (сцена, не глава) и **какой** тест обновить (fingerprint +
    golden) — это не требует вспоминать Progress log или спрашивать.
    **Verified:** код не менялся, `./gradlew :engine:test --rerun-tasks`
    → **57/57 PASSED**. Ф1.5.4 **закрыт полностью** (батчи 1–3 + docs).
  - **2026-11-06 · Ф1.5.5.1** — Фундамент рантайма, **не подключённый**
    к `:app` и не ломающий golden. Пять файлов в
    `../../engine/src/main/kotlin/com/myapp/adventure/engine`.
    **(1)** `CompiledAdventure.kt` — `data class CompiledAdventure(
  adventure, scenesById: Map<SceneId, Scene>, chapterIndexBySceneId:
  Map<SceneId, Int>)`. Индексы строятся **один раз** при compile;
    `Engine.step` — O (1) на переход. KDoc: индексы **не** живут в
    `Adventure` (иначе дублирование в golden и риск рассинхронизации);
    валидация — только «что мешает построить индекс» (дубли `SceneId`),
    всё остальное — `Analyzer` (Ф1.6), тот же boundary, что в Ф1.3.4.1.
    **(2)** `CompileResult.kt` — `sealed CompileResult { Success(compiled)
  / Failure(errors: List<CompileError>) }`; `sealed CompileError {
  DuplicateSceneId(sceneId, chapterIndices) }`. Один вариант, потому
    что только дубли `SceneId` делают индекс невозможным; семантика —
    в `Analyzer`. **(3)** `Engine.kt` — `object Engine { fun
  compile(adventure): CompileResult }`. Stateless, O (n) один раз,
    `putIfAbsent` — «первый wins», ошибка всё равно вернётся. **(4)**
    `Evaluator.kt` — `object Evaluator { fun evaluate(condition, state):
  Boolean; fun isDead(state): Boolean }`. **Единственная точка правды
    о `Condition`** — `Engine.step`, `:app` UI (N4, disabled с
    причиной), `Analyzer` (Ф1.6), debug-панель — все через неё. `Has` —
    **только** `flags` (не `stats`, семантика Ф1.5: `has_sword` для
    `Bool`, числовые — через `Compare`); неизвестная переменная →
    `0`/`false` (type checker в Ф2.2). `isDead` — `health <= 0`, если
    `health` нет в `stats` — жив (автор не использует смерть).
    **(5)** `EffectsApplier.kt` — `object EffectsApplier { fun apply(
  state, effects): PlayerState }`. **Единственная точка правды о
    `Effect`**; порядок — как в списке (parser: `set → remove →
  modify`); **clamp `>= 0` временно** для всех `ModifyStat` (Ф2.8
    заменит на clamp по типу: `HEALTH`/`ITEM` → `>= 0`, `Integer`/
    `IntRange` — по границам); state не мутируется.
    **(6)** `StepResult.kt` — `sealed StepResult { Success(state,
  currentScene, isDead) / Rejected(reason) }`; `sealed RejectReason {
  ConditionNotMet / GateNotPassed / ChoiceNotFound / SceneNotFound }`.
    `isDead = true` — **не ошибка**, reader показывает экран смерти для
    `Success` с `isDead = true`. **Пользовательская выгода:** (a) O (1)
    на переход — нет фризов при 2000+ сцен; (b) **одна точка правды**
    для условий и эффектов — UI, движок и анализатор не расходятся
    (это и есть N2, которое Ф3 закрепляет); (c) UI различает «мягкий
    отказ» (disabled с причиной — N4) от «бага» (ошибка/лог);
    (d) при загрузке сохранения UI может спросить `Evaluator.isDead`
    сразу — не «живая сцена, потом смерть». **Verified:**
    `./gradlew :engine:test --rerun-tasks` → **81/81 PASSED**
    (11 `EvaluatorTest` + 10 `EffectsApplierTest` + 3 `EngineCompileTest`
  + 22 `JsonToIrTest` + 10 `RealAdventureTest` + 6 `AdventureDtoTest`
  + 12 `ConditionParserTest` + 3 `GoldenIrTest` + 4
    `SchemaFingerprintTest`). Golden не изменился (новые классы — не
    IR).
  - **2026-11-06 · Ф1.5.5.2** — `Engine.step` — рантайм работает
    end-to-end. **(1)** `Engine.step(compiled, state, sceneId, choiceId,
  stepsInScene: Int? = null): StepResult`. **Не** применяет
    `scene.effects` (только `choice.effects`) — идемпотентность входа в
    сцену (`appliedSceneEffects` в `ProgressData`) остаётся у reader'а,
    иначе повторный вход в сцену drain'ил бы health. KDoc
    `Success.isDead` явно: «смерть от choice.effects; reader должен
    повторно проверить `Evaluator.isDead` после применения
    `scene.effects` — они тоже могут убить». **(2)** Порядок проверок:
    SceneNotFound (current) → ChoiceNotFound → собрать **все** блокеры
    (ConditionNotMet, потом GateNotPassed — «постоянный раньше
    временного») → SceneNotFound (next) **до применения эффектов** →
    Success. Это ловит баг «choice ведёт на несуществующую сцену» до
    того, как эффекты уйдут в void. **(3)** `stepsInScene: Int? = null`:
    `null` = «не знаю» → трактуется как **0 пройдено** (безопасный
    default: caller, забывший передать шаги, не обходит гейт). Ignored
    для сцен без gate. **(4)** `RejectReason.Blocked(blockers:
  List<Blocker>)` + `sealed Blocker { ConditionNotMet / GateNotPassed }`
    — **переделка развилки 8** по решению владельца: UI N4 показывает
    **все** блокеры на disabled-кнопке («нужен меч; пройдите 100 шагов»),
    не только первый. `ChoiceNotFound`/`SceneNotFound` вынесены из
    `Blocked` — это структурные ошибки (баг), UI логирует, не
    показывает как «disabled». **(5)** `InitialState.toPlayerState():
  PlayerState` — extension для reader'а (старт, restart); копирует
    `toSet()`/`toMap()` чтобы не алиасить мутабельный backing.
    **(6)** 19 тестов в `EngineStepTest`: happy path, effects order,
    state not mutated, SceneNotFound/ChoiceNotFound, condition pass/fail,
    gate pass/fail/null-safe-default, оба блокера сразу, death by
    choice.effects, no health, cross-chapter, `chapter_end_1` без
    choices (фиксация до Ф1.5.5.0). **Пользовательская выгода:** (a) UI
    показывает **все** причины недоступности за один раз — игрок не
    дёргается «нажал, пройди N, нажал, ещё блокер»; (b) баг
    «choice → несуществующая сцена» не применяет эффекты в void
    (иначе health drain'ится, а игрок даже не перешёл — потеря данных);
    (c) `null` steps не обходит гейт (safety); (d) reader может
    fast-path: `isDead = true` после choice — сразу экран смерти, не
    входя в сцену. **Verified:** `./gradlew :engine:test --rerun-tasks`
    → **100/100 PASSED** (19 `EngineStepTest` + 11 `EvaluatorTest` + 10
    `EffectsApplierTest` + 3 `EngineCompileTest` + 22 `JsonToIrTest` +
    10 `RealAdventureTest` + 6 `AdventureDtoTest` + 12
    `ConditionParserTest` + 3 `GoldenIrTest` + 4
    `SchemaFingerprintTest`), 2s. Golden не изменился.
  - **2026-11-06 · Ф1.5.5.0** — `test_adventure.json` v2, финал: в
    `chapter_end_1` и `chapter_end_2` добавлены **явные** Continue
    choices (`"Продолжить"` → `conditions_test` / `final`). Решение
    развилки 2C: «сцена без choices = конец приключения» остаётся у
    reader'а, но «конец главы» автор выражает **явным** choice, а не
    «reader угадает по последней сцене главы» — главы больше не несут
    никакого механического смысла (Ф1.5.4). **Пользовательская
    выгода:** (a) игрок видит привычную кнопку «Продолжить» после
    gates — не «нажал и ничего не произошло»; (b) автор сам решает,
    показать ли Continue или закончить приключение (например,
    «здесь конец книги — идите отдыхать»); (c) архитектурно: reader
    не знает про главы, engine не угадывает «куда идти», `step` всегда
    принимает реальный `choiceId`. **Регресс-фиксация:** тест
    `chapter_end_1 has no choices yet...` (из Ф1.5.5.2) переписан на
    `chapter_end_1 has explicit Continue choice - player can proceed
    after gate` — паттерн «сначала падающий тест, потом фикс»
    сработал ровно как задумано: тест зафиксировал «no choices», фикс
    добавил Continue, тест обновлён, diff в PR — читаемый. Новый
    `chapter_end_2 has explicit Continue choice going to final`.
    **Verified:** `./gradlew :engine:updateGolden` → 15269 chars (было
    14723, +546 на 2 Continue с сгенерированными id
    `chapter_end_1_choice_0` / `chapter_end_2_choice_0`).
    `./gradlew :engine:test --rerun-tasks` → **101/101 PASSED** (19
    `EngineStepTest` — включая переписанный и новый — + 11
    `EvaluatorTest` + 10 `EffectsApplierTest` + 3 `EngineCompileTest`
  + 22 `JsonToIrTest` + 10 `RealAdventureTest` + 6
    `AdventureDtoTest` + 12 `ConditionParserTest` + 3
    `GoldenIrTest` + 4 `SchemaFingerprintTest`), 5s.
  - **2026-11-06 · Ф1.5 (closing)** — **Ф1.5 «Рантайм `Engine.step`»
    закрыт целиком.** Весь путь от JSON до перехода работает на
    реальном `test_adventure.json`: `start → … → chapter_end_1 → (gate
  5) → conditions_test → … → chapter_end_2 → (gate 4000) → final`,
  плюс демо межглавного перехода `conditions_test → final` в обход
  гейта 4000. Что сделано за Ф1.5 (подшаги 1–5.0):
  **(1)** `ChoiceId` value class + `Choice.id` (стабильные
  сохранения при редактировании).
  **(2)** `PlayerState` immutable + `InitialState.toPlayerState()`.
  **(3)** `Chapter.requiredSteps` убран; `Gate` на `Scene`
  (гейт где угодно в главе, межглавные переходы разрешены) +
  миграция v1 → v2 (`CURRENT_VERSION = 2`).
  **(4)** `SchemaFingerprintTest` — страховка от добавления/удаления
  полей с default (golden этого не ловит из-за
  `encodeDefaults = false`).
  **(5)** `CompiledAdventure` с индексами `scenesById` /
  `chapterIndexBySceneId` (O(1) на переход, готово к P1.advanced) +
  `Engine.compile` + `CompileResult` / `CompileError.DuplicateSceneId`
  (только то, без чего нельзя построить индексы; остальное —
  `Analyzer`).
  **(6)** `Evaluator` (единственная точка правды о `Condition`,
  включает `isDead`) + `EffectsApplier` (единственная точка правды
  о `Effect`, clamp `>= 0` до Ф2.8) — закрывают N2-риск
  «UI и engine считают по-разному» с самого начала.
  **(7)** `StepResult` / `RejectReason` / `Blocker` —
  **все** блокеры за один вызов (переделка развилки 8): UI N4
  показывает «нужен меч; пройдите 100 шагов», не по одному.
  Порядок: SceneNotFound → ChoiceNotFound → `Blocked(все)` →
  SceneNotFound (next) **до** применения эффектов → Success
  (ловит баг «choice → несуществующая сцена» до drain'а health).
  **(8)** `Engine.step` с `stepsInScene: Int? = null` (`null` = 0
  walked, safety).
  **(9)** 101 тест в `:engine`, все зелёные; golden
  перегенерирован (15269 chars); `Engine.step` работает end-to-end
  на `test_adventure.json`.
  **Что осталось за Ф1.5 (уходит в Ф1.6+):** `Analyzer` —
  reachability, дубли `scene.id` (уже `CompileError`, но не в
  отчёте редактора), `has_X` на числовой переменной (типизация —
  Ф2.2); `appliedSceneEffects` (идемпотентность `scene.effects`) —
  это `:app` / Ф5.5 (сохранения); clamp по типу — Ф2.8;
  `N4` UI (disabled с причиной) — Ф4.
  **Следующее действие — Ф1.6:** перенос `StoryValidator` +
  `computeReachable` в `:engine`, `Analyzer.reachable(ir)` /
  `Analyzer.lint(ir)`, unit-тест на недостижимую сцену; **парсер
     валидатора переносится как есть** (унификация — Ф3/N2).
  - **2026-11-06 · Ф1.7.gamebook-3a** — `docs/gamebook-mechanics-coverage.md`
    создан. Статусная карта 37 механик из
    `docs/research/gamebook-mechanics.md` (классы M1–M5). **Что в файле:**
    summary (13 Supported / 3 Partial / 21 Not supported из 37), 5 detail-таблиц
    (M1–M5) с колонками «Code / Name / Status / Blocked by / Planned», 6
    архитектурных пробелов (RNG, PROC, EXT, CROSS, MULTI, APP) с перечнем
    механик, которые каждый закрывает, обоснование «почему нет новых Must»
    (N6 subroutines + sealed `Condition`/`Effect` + versioning Ф5.3 +
    `CompiledAdventure` уже образуют фундамент; всё остальное — Ф12–Ф18 в
    `docs/roadmap.md`), процедура обновления (когда механика реализована —
    поменять статус, добавить запись в progress log, обновить
    `authoring-guide.md` если есть UI). **Пользовательская выгода:** автор
    или разработчик открывает файл и видит **что уже работает без единой
    правки кода** (13 механик), **что частично** (3, с указанием конкретного
    ограничения), **что в roadmap и в какой фазе** (21, с конкретными
    архитектурными пробелами). Никакого «надо ли менять чеклист» — явно
    написано «no». **Verified:** файл создан, GFM-таблицы отрисованы,
    ссылки на `research/gamebook-mechanics.md`, `roadmap.md`,
    `progress/progress-log.md`, `test-adventure-policy.md`,
    `authoring-guide.md` — часть пока «битая» (roadmap, authoring-guide
    создаются в шагах 3b/3c и Ф10.7, что ожидаемо).
  - **2026-11-06 · Ф1.7.gamebook-3b** — `docs/roadmap.md` создан: полный
    post-Ф10 план, три части. **Part 1 — Gamebook mechanics (Ф12–Ф18):**
    Ф12 RNG foundation (7 задач, ~13 дней: `RandomSource`, `Effect.Roll`/
    `Condition.Roll` HIGH/LOW/DVD, hybrid, boost, random link, random event);
    Ф13 Procedures + open-world (6 задач, ~16.5 дней: `Procedure` IR node
    с локальным state и bounded loop, combat round/ratio, atomic crafting,
    hub navigation); Ф14 Character initialization (3 задачи, ~6.5 дней:
    `startBuild` в IR, UI-редактор, миграция v3→v4); Ф15 External outcomes
  + realtime (3 задачи, ~5.5 дней: `Condition.ExternalOutcome`, wall-clock
    pacing в `:app`); Ф16 Cross-adventure state (4 задачи, ~10 дней:
    `LibraryState`, transfer, persistent/legacy); Ф17 Meta-mechanics
    (4 задачи, ~11.5 дней: turn counter, modules, multiplayer «только если
    спрос и после Ф17.1/17.2, иначе паркуется»); Ф18 Authoring UI для новых
    механик (6 задач, ~13.5 дней: визуальный редактор `Procedure`, редактор
    rolls, transfer, external outcomes, end-to-end audit). **Итого
    Ф12–Ф18: ~76.5 дней** при одном разработчике. Каждая фаза имеет секцию
    «Gate» (что должно работать end-to-end) и «Scope guard» (что НЕ делать
    — например, «combat — single-player, turn-based; никакого real-time,
    сетки, spell-crafting»). **Part 2 — Nice-to-have:** 17 explicit Nice
  + 10 `.advanced` extensions из Приложения B с триггерами входа (после
    какой фазы). **Part 3 — Distribution/community:** E3 mobile distribution,
    E4 Steam (только если E1.base вернётся), X3.advanced. Явно: «No dates.
    No promises. Every item triggered by a concrete question, not by a
    calendar». **Процедура обновления:** как добавить механику, как закрыть
    фазу, как промотировать Nice-to-have в фазу. **Пользовательская
    выгода:** roadmap не оставляет «висящих» идей; каждая механика имеет
    фазу, каждое «not supported» в coverage map — конкретный пробел и фазу.
    Фундамент (Ф1.5 + Ф5 + N6) уже есть, roadmap — это что строить **поверх**,
    без переделки. **Verified:** файл создан, GFM-таблицы отрисованы,
    ссылки на coverage, checklist-tracker, progress-log, research — рабочие;
    ссылки на будущие `docs/engine-rng.md`, `docs/engine-procedures.md`,
    `docs/engine-navigation.md`, `docs/engine-external.md`,
    `docs/engine-library-state.md`, `docs/engine-meta.md`,
    `docs/authoring-guide.md` — создадутся в соответствующих фазах,
    ожидаемо. **Ключевая связь:** `docs/roadmap.md` (этот файл) отвечает
    на «когда», `gamebook-mechanics-coverage.md` — на «что сейчас».
    Правило: при закрытии фазы обновить **оба** (плюс progress log).
  - **2026-11-06 · Ф1.7.gamebook-3d** — Корневой `ROADMAP.md` обновлён:
    добавлена секция **Post-Ф10** со ссылкой на
    `docs/roadmap.md` и кратким обзором трёх групп (gamebook-механики
    Ф12–Ф18, Nice-to-have, distribution/community). Явно зафиксировано
    «No dates. No promises. Every item triggered by a concrete question,
    not by a calendar» — чтобы newcomers не воспринимали roadmap как
    обязательство. Разделение: корневой `ROADMAP.md` — one-page summary
    для «куда идёт проект», `docs/roadmap.md` — полный post-Ф10 план
    (Part 1/2/3 с фазами Ф12–Ф18, задачами, днями, gates, scope guards).
    **Пользовательская выгода:** (a) newcomer открывает `ROADMAP.md`
    и видит, что за Ф10 есть **план** (не «мы закончили и разошлись»);
    (b) не раздувается one-page «current focus / next / later»;
    (c) contributor, желающий взяться за combat/RNG, сразу видит,
    куда идти (`docs/roadmap.md` → Part 1 → Ф12/Ф13);
    (d) отсутствие дат в корневом — снижает expectation mismatch.
    **Verified:** секция Post-Ф10 добавлена; ссылки на `docs/roadmap.md`
    и `docs/gamebook-mechanics-coverage.md` — рабочие (файлы созданы
    в шагах 3a/3b).
  - **2026-11-06 · Ф1.7.gamebook (closing)** — **Фундамент для gamebook-механик
    зафиксирован.** Что сделано внепланово (7 шагов, ~2 часа):
    **(1)** `docs/research/` создана, `gamebooks-classification.md` и
    `gamebook-mechanics.md` перемещены туда; в `docs/documentation-rules.md`
    добавлена §11 «Research docs» с исключениями (русский язык, необязательный
    Status, не нужен glossary-entry) и подсекцией 11.1 «When to promote».
    **(2)** `docs/index.md` — секция «Research» со ссылками на оба документа;
    секция «For everyone» дополнена ссылкой на `docs/roadmap.md`.
    **(3)** `docs/glossary.md` — три термина с пометкой Planned:
    `RandomSource` (Ф12+), `Procedure` (Ф13+), `Combat` (Ф13+).
    **(4)** `docs/gamebook-mechanics-coverage.md` — 37 механик, статус
    (13 Supported / 3 Partial / 21 Not supported), 6 архитектурных пробелов,
    «почему нет новых Must».
    **(5)** `docs/roadmap.md` — полный post-Ф10 план: Part 1 Gamebook
    mechanics (Ф12–Ф18, ~76.5 дней), Part 2 Nice-to-have (17+10 из
    Приложения B с триггерами), Part 3 Distribution/community; процедура
    обновления.
    **(6)** `docs/checklist-tracker.md` — Приложение E (краткая
    summary-таблица + таблица 6 пробелов + ссылки).
    **(7)** Корневой `ROADMAP.md` — секция «Post-Ф10» со ссылкой на
    `docs/roadmap.md`.
    **Пользовательская выгода (автор):** открывает `checklist-tracker.md`
    → Приложение E, видит «13 из 37 механик работают сегодня, 3 частично,
    21 в roadmap». Хочет combat — открывает `docs/roadmap.md` → Ф13.
    Хочет RNG — Ф12. Не нужно гадать «поддерживает ли движок броски» —
    есть точная карта. **Выгода (разработчик):** при добавлении механики
    есть чёткая процедура — обновить `gamebook-mechanics-coverage.md` +
    progress log + `authoring-guide.md` (если есть UI). Новый contributor
    видит фундамент (`sealed Condition/Effect`, versioning, N6 subroutines,
    `CompiledAdventure` O(1)) и понимает, **куда** встраивать новую
    механику. **Выгода (проект):** чеклист 2.1 не раздут — Must остаётся 40,
    Nice — 19. Gamebook-расширения — в `docs/roadmap.md`, не блокируют
    приёмку Ф10. **Что показал анализ:** 4 архитектурных решения,
    принятых ранее, «бесплатно» дают фундамент — (a) sealed
    `Condition`/`Effect` (расширяемость без ломания), (b) versioned IR +
    migrations (каждая механика = v3, v4…), (c) N6 subroutines
    (scaffolding для `Procedure`), (d) `CompiledAdventure` O(1)
    (не меняется при добавлении механик). **Verified:** файлы созданы,
    GFM-таблицы отрисованы, внутренние ссылки в новых файлах — рабочие;
    известные «будущие» ссылки (`docs/engine-rng.md`, `docs/authoring-guide.md`
    и т.п.) — создадутся в соответствующих фазах, не ошибка.
    **Возврат к плану:** следующее действие — **Ф1.6**
    (`Analyzer.reachable` + `Analyzer.lint`).
  - **2026-11-06 · Ф1.6.0** — `Adventure.startScene: SceneId` явный,
    миграция v2 → v3. **(1)** `engine/ir/Adventure.kt` —
    `@SerialName("start_scene") startScene: SceneId` (required, без
    default); `CURRENT_VERSION = 3`; version history расширен.
    **(2)** `AdventureDto.startScene: String? = null` — nullable, потому
    что в v1/v2 файлах поля нет. **(3)** `JsonToIr.buildAdventure` —
    миграция: явный `start_scene` побеждает (v3+); для
    `effectiveVersion < 3` — `chapters.firstOrNull()?.scenes?.firstOrNull()?.id`
    (identity для всех существующих файлов); для v3+ без `start_scene`
    — `ParseError.Structure` (поле обязательно). **(4)**
    `test_adventure.json` — `"start_scene": "start"` после `_comment`.
    **(5)** `SchemaFingerprintTest.expectedFlatFields["Adventure"]` —
    добавлен `start_scene` (если забыть — тест упал бы с явным
    «field missing in golden / present in actual»). **(6)** 4 новых теста:
    `JsonToIrTest > v2 file without start_scene migrates from chapters`,
    `JsonToIrTest > v2 file with explicit start_scene preserves it`,
    `RealAdventureTest > start scene is start`,
    `JsonToIrTest > minimal valid adventure` (обновлён: `version == 3`
    вместо 2). **(7)** `EngineCompileTest` и `EngineStepTest` helper
    `adventure(...)` — `startScene = chapters.firstOrNull()?.scenes?.firstOrNull()?.id`.
    **Пользовательская выгода:** (a) автор может задумать «старт с
    середины» (интригующая сцена, откат в пролог через `nextScene`) —
    без `startScene` `Analyzer.reachable` в Ф1.6.1 ложно объявил бы
    все «стартовые» сцены недостижимыми → автор не доверяет линтеру;
    (b) явный старт — фундамент для будущих фич (например, нескольких
    стартов — расширится до `List<SceneId>` без ломания). **Verified:**
    golden упал до `updateGolden` (оба golden-теста — `round-trips` с
    `MissingFieldException: Field 'start_scene' is required`, `matches`
    с `Hint: field 'start_scene' present in actual, missing in golden`);
    `./gradlew :engine:updateGolden` → 15297 chars (было 14723, +574);
    `./gradlew :engine:test --rerun-tasks` → **104/104 PASSED**
    (22+2 `JsonToIrTest` + 10+1 `RealAdventureTest` + 6
    `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest` +
    4 `SchemaFingerprintTest` + 19 `EngineStepTest` + 11 `EvaluatorTest`
  + 10 `EffectsApplierTest` + 3 `EngineCompileTest`), 4s.
  - **2026-11-06 · Ф1.6.1** — `Analyzer.reachable` + `LintIssue`. **(1)**
    `engine/analyze/LintIssue.kt` — `data class LintIssue(kind: LintKind,
  message: String, sceneId: SceneId?, severity: Severity = WARNING)` +
    `enum Severity { WARNING, INFO }` + `sealed LintKind { UnreachableScene }`.
    Решение развилки A: типизированный `kind` (sealed) вместо плоского
    `message`, потому что в Ф2.2/Ф3.6/Ф4.6 появятся `TypeMismatch`,
    `DeadCondition`, `MergeConflict` — UI сможет `when`-ить по `kind` и
    форматировать контекст (например, «Сцена „forest" недостижима»
    с кликабельной ссылкой). **(2)** `engine/analyze/ReachabilityReport.kt`
    — `data class ReachabilityReport(reachable: Set<SceneId>,
  unreachable: List<SceneId>, deadEnds: List<SceneId>)`. Решение
    развилки B: `reachable` — множество (O(1) проверка «достижима ли»),
    `unreachable` и `deadEnds` — сортированные списки для стабильного
    UI/golden. `deadEnds` — будущая проверка (WARNING «тупик»), сейчас
    используется тестами. **(3)** `engine/analyze/Analyzer.kt` —
    `object Analyzer { fun reachable(compiled): ReachabilityReport;
    fun lint(compiled): List<LintIssue> }`. BFS от `startScene` по
    `nextScene`; **missing reference ≠ reachability**: если `startScene`
    не существует — `reachable` пусто, все существующие сцены →
    `unreachable`; если `nextScene` ведёт на несуществующую сцену —
    она **не** попадает в `reachable` (это отдельный `MissingNextScene`
    в Ф1.6.2). Это **пойманный баг** при первом прогоне: код добавлял
    `start` в `reachable` безусловно, тест `missing start scene -
  everything unreachable` упал с `expected:<[]> but was:<[SceneId(ghost)]>`;
    фикс — `if (start in compiled.scenesById)` перед seed'ом BFS +
    `if (choice.nextScene in compiled.scenesById)` перед добавлением ребра.
    **(4)** `AnalyzerTest` — 10 тестов (linear chain, orphan, cross-chapter,
    self-loop, two-way cycle, missing start, missing nextScene, lint
    empty/reports/sorted) + 2 новых после фикса (missing-ref-семантика).
    **Пользовательская выгода:** (a) `ReachabilityReport` даёт автору
    обзорную картину «18 из 20 сцен достижимо, 2 недостижимо, 5 концовок»
    в UI редактора (Ф9.2) — не только список ошибок; (b)
    **корректность missing-ref ≠ reachability**: автор видит
    «сцена X недостижима» и «choice ведёт на несуществующую сцену Z»
    как **два разных** сообщения — не путает причину; (c) тесты ловят
    расхождение KDoc↔код (как сейчас — третий раз в проекте): обещали
    одну семантику, код делал другую, тест остановил.
    **Verified:** `./gradlew :engine:test --rerun-tasks` → **116/116
    PASSED** (22+2 `JsonToIrTest` + 10+1 `RealAdventureTest` + 6
    `AdventureDtoTest` + 12 `ConditionParserTest` + 3 `GoldenIrTest` +
    4 `SchemaFingerprintTest` + 19 `EngineStepTest` + 11 `EvaluatorTest`
  + 10 `EffectsApplierTest` + 3 `EngineCompileTest` + 10+2
    `AnalyzerTest`), 3s.
  - **2026-11-07 · Check-list expansion (U11–U14, U15, T1–T4)** —
    Расширение чеклиста 2.1 по решению владельца проекта. **Контекст:**
    при обсуждении авторских тем и перевода приключений выяснилось, что
    (а) U4.base («проверка контраста в **редакторе тем**») невыполним —
    редактора тем нет; (б) workflow перевода приключений (одно приключение
    × N языков, обновление при изменении источника, валидация placeholder)
    не покрыт ни L1–L4, ни roadmap; (в) базовая работа над UI/UX
    (карта экранов, единое меню, что такое тема и как она работает)
    отсутствует как системный пункт — темы и доступность размазаны по
    U2–U9 без общего фундамента. **Что сделано:**
    **(1) 4 новых Must** в категорию U (Ф6):
  - **U11** — карта экранов и навигация (`docs/ui-screens.md`, back
    stack, отсутствие тупиков);
  - **U12** — единое меню приложения (главное/читалка/редактор, без
    дублирования, ≤ 2 клика до настроек);
  - **U13** — спецификация темы (`docs/theming.md`, JSON-схема, уровни
    пользователь/автор/дефолт, приоритеты, «вместе или отдельно»,
    взаимодействие с доступностью) — это **решение**, не код;
  - **U14** — базовая реализация темы (`ThemeManager`, чтение
    `theme.json`, fallback при ошибке, выбор темы в `SettingsFragment`).
    **(2) U4.base переформулирован:** «в редакторе тем» → «в UI
    выбора/настройки темы»; зависит от U13/U14 (Ф6.3/Ф6.4) и U9.base
    (Ф6.10). **(3) 4 новые задачи Ф6.1–Ф6.4** (U11–U14); старые
    Ф6.1–Ф6.9 сдвинуты на Ф6.5–Ф6.13. Итог Ф6: **15 → 24.5 дней**.
    **(4) Гейт Ф6 расширен** 4 проверками (карта экранов, единое меню,
    спецификация темы, реализация темы); границы обновлены (U15 — не
    U11–U14; U13 — документ, не код; U14 — не редактор тем; U11/U12 — не
    редизайн). **(5) Приложение A:** секция U 7 → 11 строк;
    `Итого: 42 строки` → `46 строк (44 к приёмке)`; снята помета
    TBD/развилки. **(6) Приложение D (DoD):** «44/44 Must», «19 явных
    Nice (17 + A4 + E1.base)», в артефактах добавлены `ui-screens.md` и
    `theming.md`, секция «Расхождения» — C1–C4 закрыты, U11–U14 отмечены
    как сознательное расширение. **(7) `docs/roadmap.md` Part 2:**
    добавлены две подсекции — **Author themes (U15)** (автор задаёт
    визуальный стиль, наследование с пользовательскими настройками,
    приоритеты, UI редактора, загрузка вместе с приключением; зависит
    от U13/U14) и **Translation workflow (T1–T4)** (multi-language
    packaging, update flow через diff `StringId`, placeholder-валидация,
    translator UI; зависит от L1–L4 и E7-ZIP). Оба — Nice, **не**
    блокируют приёмку, **вне** Приложения B (B — замороженный контракт
    2.1; новые Nice-идеи живут в roadmap).
    **Пользовательская выгода (автор):** (a) U11/U12 дают
    предсказуемую навигацию и единое меню — автор и читатель находят
    настройки/логи/импорт за ≤ 2 клика из любого экрана; (b) U13/U14
    дают механизм темы — автор выбирает стиль из набора, читатель
    получает визуально разные приключения; (c) U4.base теперь проверяет
    **реальную** тему (а не гипотетический «редактор»); (d) U15 (post-
    Ф10) даст автору UI для собственных тем, но **не раньше**, чем
    механизм (U13/U14) стабилизируется; (e) T1–T4 (post-Ф10) дадут
    workflow перевода, но **не раньше**, чем L1–L4 дадут фундамент.
    **Пользовательская выгода (читатель):** (a) единое меню — не
    теряется «где настройки»; (b) авторские темы — атмосфера
    приключения; (c) доступность (reduced motion, контраст) **не**
    переопределяется авторской темой — приоритет зафиксирован в U13;
    (d) переводы приключений — читатель выбирает язык книги независимо
    от UI-локали (T1–T4). **Цена:** Ф6 15 → 24.5 дней (+9.5); DoD
    42 → 44 Must к приёмке. Согласовано с владельцем проекта:
    «увеличит срок выполнения чеклиста — это приемлемо».
    **Известные будущие риски:** (i) U13 может выявить, что тема
    требует новых полей в IR — это будет **отдельная** задача в
    roadmap, не расширение U13; (ii) U14 — минимальный `ThemeManager`,
    не редактор тем (редактор — U15); (iii) T1 формат (ZIP + strings
    vs parallel JSON) не решён — решается в T1, документируется в
    `docs/localization.md`.
    **Verified:** правки в `docs/checklist-tracker.md` (шапка, Ф6
    таблица-превью, Ф6 задачи, гейт/границы, Приложение A секция U,
    Приложение A итог, Приложение D) и `docs/roadmap.md` (Part 2).
    Сводка трекера сходится: 44 Must к приёмке, 19 явных Nice, 29
    упоминаний Nice. Возврат к плану: **Ф1.6.2.2**
    (`LintKind.RemovingUnestablishedItem` + `LintKind.DeathSceneWithoutHealth`
    в `Analyzer.lint`).
  - **2026-11-07 · Ф1.6.2.3** — Перенесены оставшиеся проверки
    `:app/utils/StoryValidator.kt` в `Analyzer.lint` (3 новых `LintKind`):
    **(1)** `ConditionOnUnestablishedFlag(flagName)` — `Condition.Has` /
    `Not(Has)` ссылается на имя, которого нет ни в `initialState.flags`,
    ни в `initialState.stats.keys`, ни в одном `Effect.SetFlag` ни в
    одной сцене/выборе. Обход AST рекурсивный (`collectHasFlags`).
    **Shim (Ф1.6 → Ф2.2):** `initial_state.stats.keys` **включены** в
    «establishable», потому что `test_adventure.json` использует
    `has_sword` при `sword: ItemCount`. Строгая семантика («has_X только
    для Bool») — отдельный `HasOnNonBoolVariable` в Ф2.2; сейчас наша
    цель уже: ловить опечатки (`has_swrod` при существующем `sword`),
    не мешая переходному `test_adventure.json`. `Compare`/`InRange`
    намеренно пропускаются — числовые переменные, type checker Ф2.2.
    **(2)** `DeathWithoutDeathScene` — `health` используется (в
    `initial_state.stats` или через `Effect.ModifyStat`), но
    `deathScene == null`. Обратная к `DeathSceneWithoutHealth`:
    та — «экран есть, health нет», эта — «health есть, экрана нет».
    **(3)** `DeadEnd` — сцена без `choices`, не последняя сцена
    последней главы, **и достижима**. Адаптация старого
    `scene.choices.isEmpty() && !isChapterEnd && idx < last` к IR v3:
    глав больше не несут механического смысла (Ф1.5.4), «конец главы»
    выражается явным Continue-choice (Ф1.5.5.0), поэтому
    «нет choices, но не последняя» = «игрок увидит „конец“ раньше
    времени». Фильтр `scene.id in reachable` — чтобы не дублировать
    `UnreachableScene` для недостижимых терминалов.
    **Verified:** `./gradlew :engine:test --rerun-tasks` → **138/138
    PASSED** (+13 тестов в `AnalyzerTest`: 5 `ConditionOnUnestablishedFlag`
    включая shim-тест, 4 `DeathWithoutDeathScene`, 3 `DeadEnd`,
    1 `lint is clean on test_adventure_json`), 4s. Новый интеграционный
    тест гарантирует: `test_adventure.json` остаётся lint-clean —
    ни одна из новых проверок не ломает reference sample. `GoldenIrTest`
    и `SchemaFingerprintTest` не менялись — IR не затронут.
    **Пойманные и починенные тесты:**
    (a) `lint does not report death scene when health modified in scene`
    в Ф1.6.2.2 падал из-за `RemovingUnestablishedItem("health")` на
    сетапе с единственным негативным `ModifyStat("health")` — фикс:
    добавлен положительный `ModifyStat("health", 10)`, health становится
    established, тест проверяет ровно то, что заявлено;
    (b) `lint reports unreachable scene` и `lint issues are sorted by
  scene id` в Ф1.6.2.3 падали из-за `DeadEnd("a")` на терминальной
    сцене `a` не в конце главы — фикс: self-loop `a → a` делает `a`
    non-terminal, тесты снова проверяют только то, что заявлено.
    **Что НЕ перенесено (осознанно):** старая проверка #10 «Шаги на
    финальной сцене» (в IR v3 бессмысленна). Проверки #1–3, #9 покрыты
    парсером/компилятором; #5, #7, #11 — в Ф1.6.2.1/Ф1.6.2.2.
    **Ф1.6 закрыт целиком** (5 батчей: 1.6.0 → 1.6.2.3).
    **Следующее действие — Ф1.7:** адаптер `:app` → IR.
  - Продолжение в docs/progress/progress-log-1.md

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
  - **2026-11-12 · Ф2.1** — Система типов спроектирована и зафиксирована.
    **Развилки (обсуждены с пользователем):**
    **(A) Числовые границы** — выбран вариант **A2'**: `VarType.IntMin(min)` —
    новый вариант, «≥ min, без верха». Зачем: закрывает кейсы
    `M-STATE-BUILD` (Ф14, броски при создании персонажа) и
    `M-META-TIME` (Ф17, turn counter 0..30), которые без `IntMin`
    требовали бы магии `IntRange(min, Int.MAX_VALUE)`. Не путать с
    `VarType.Integer` (совсем без границ) и `VarType.IntRange` (обе
    границы).
    **(B) ItemList** — оставлен как был (задел под будущее, не
    используется в `test_adventure.json`; YAGNI-проверка — в Ф4/N3,
    если понадобится).
    **(C) VarType ↔ TagCategory** — оставлены обе оси.
    `TagCategory` — про UI-отображение (HUD heart/backpack/book),
    `VarType` — про type checker и clamp. Не дублируют друг друга:
    `health` может быть `TagCategory.HEALTH` + `VarType.IntRange(0,
  100)`; `gold` — `TagCategory.ITEM` + `VarType.IntMin(0)`; флаг
    `knows_secret` — `TagCategory.JOURNAL` + `VarType.Bool`. Убрать
    `TagCategory` нельзя (потеряем подсказку HUD); убрать `VarType`
    нельзя (потеряем type checker).
    **Что сделано (6 файлов):**
    **(1)** `engine/.../ir/types/VarType.kt` — новый подпакет
    `com.myapp.adventure.engine.ir.types` (per DoD
    `:engine/.../ir/types/`). Добавлен `VarType.IntMin(min: Int)`.
    KDoc: три числовых варианта (Integer / IntMin / IntRange), зачем
    каждый, правило выбора, почему не `IntRange(min, max: Int?)`.
    Дефолты по типам (`Bool→false`, `Integer→0`, `IntMin→min`,
    `IntRange→min`, `ItemCount→0`, `ItemList→emptyList()`).
    **(2)** `engine/.../ir/types/VarDecl.kt` — переехал в новый
    пакет. KDoc: ссылки на `Adventure.variables`, `Condition`,
    `Effect`, `InitialState` (все через импорты для KDoc-резолва).
    **(3)** `engine/.../ir/Adventure.kt` — импорты `VarDecl`,
    `VarType`; KDoc `variables` дополнен: «The type of each variable
    determines its default value (see [VarType] KDoc)».
    **(4)** `engine/.../ir/Condition.kt`, `InitialState.kt`,
    `PlayerState.kt` — добавлены импорты `VarType` (для KDoc-резолва
    существующих ссылок `[VarType.Bool]`, `[VarDecl]` и т.п.).
    **(5)** `engine/src/test/.../format/SchemaFingerprintTest.kt` —
    `expectedVarTypeVariants` += `"int_min" to setOf("min")`; импорты
    `VarDecl` / `VarType` → из `types`-пакета. Новый тест
    `VarType IntMin round-trips through serializer` — сериализует
    `IntMin(min = 5)`, проверяет `"int_min"` в JSON, десериализует,
    `assertEquals`.
    **(6)** **`CURRENT_VERSION` не бампался** — намеренно. Изменение
    чисто IR-уровневое (`Adventure.variables` в JSON ещё не
    парсится — это Ф2.8); JSON-формат не меняется, миграции не
    нужны. `test_adventure.json` остаётся `"version": 5`; golden
    не пересоздаётся. Следующий bump будет в Ф2.8 — когда `variables`
    появится в DTO и `JsonToIr` начнёт писать в `Adventure.variables`
    непустой Map.
    **Пользовательская выгода:** (a) type checker (Ф2.2) получит
    полную информацию о границах — сможет ловить «бросок 2d6+skill
    при skill ≤ 12 может дать 14» и «+1 stamina выведет за верх»;
    (b) `M-STATE-BUILD` (Ф14) и `M-META-TIME` (Ф17) разблокированы —
    без `IntMin` их нельзя выразить; (c) authoring: автор пишет
    `{"type": "int_min", "min": 0}` вместо `{"type": "int_range",
  "min": 0, "max": 2147483647}`; (d) forward-compatible: `IntMin`
    в старом файле не появится, `IntRange`/`Integer` остались как
    были.
    **Что НЕ сделано (осознанно, для следующих шагов):**
    (a) `EffectsApplier` пока клэмпит всё в `>= 0` — type-aware
    clamp в Ф2.8 (когда `Adventure.variables` начнёт заполняться);
    (b) `JsonToIr` всё ещё пишет `variables = emptyMap()` — реальный
    парсинг `int_min` из JSON в Ф2.8 вместе с `VarDecl`;
    (c) `AdventureDto` не имеет `variables` поля — Ф2.8;
    (d) `test_adventure.json` пока не использует `variables` —
    Ф2.8 (когда type checker потребует).
    **Verified:** `./gradlew :engine:test --rerun-tasks` → все тесты
    зелёные; `SchemaFingerprintTest > VarType sealed variants match
  expected fingerprint` — PASSED с новым `int_min`; новый
    `VarType IntMin round-trips through serializer` — PASSED;
    `GoldenIrTest > golden matches current IR` — PASSED (golden не
    изменился: `variables = emptyMap()`, encodeDefaults = false →
    поле отсутствует в golden); `GoldenIrTest > golden round-trips` —
    PASSED. `:engine:compileKotlin` — BUILD SUCCESSFUL.
    **Следующее действие — Ф2.2:** Type checker
    (`TypeChecker.check(ir) → List<TypeError>`) — проверка всех
    ссылок в условиях и эффектах против `Adventure.variables`;
    unit-тесты: неизвестная переменная, несовместимый тип
    (`health` как Bool), `health >= "abc"`, опечатка `has_swrod`
    → ошибка. Зависит от Ф2.1 (выполнено) и Ф1.2.
  - **2026-11-12 · Ф2.2** — Type checker (`TypeChecker.check(ir) →
  List<TypeError>`) + unit-тесты. **Развилки (обсуждены с
    пользователем):**
    **(A) Где живёт TypeChecker** — выбран **A1**:
    `engine/.../ir/types/TypeChecker.kt`, рядом с `VarDecl`/`VarType`.
    Не `analyze/` (там про reachability + lint; `TypeError` ближе к
    `CompileError` — «файл не должен открываться»), не `parse/` (там
    про синтаксис, не про смысл — I3-инвариант).
    **(B) Набор `TypeError`** — выбран **B2**: DoD-минимум (3 варианта:
    `UnknownVariable`, `HasOnNonBool`, `InitialState mismatch`) + 3
    проверки эффектов (`SetFlagOnNonBool`, `RemoveFlagOnNonBool`,
    `ModifyStatOnNonNumeric`) + 2 симметричных проверки условий
    (`CompareOnNonNumeric`, `InRangeOnNonNumeric`) = 9 вариантов.
    B3/B4 (диапазоны `IntRange(min>max)`, cross-field проверки) —
    отложены в Ф3.6 / Ф2.8, где появятся реальные данные.
    **(C) Fallback при пустом `variables`** — выбран **C4**: если
    `Adventure.variables` пуст (текущее состояние всех файлов до
    Ф2.8), `TypeChecker` строит прокси-таблицу из `Adventure.tags` +
    `Adventure.initialState`:
    `TagCategory.HEALTH → IntMin(0)`, `ITEM → ItemCount`,
    `JOURNAL + в initial.flags → Bool`, `JOURNAL + в initial.stats →
  IntMin(0)`, `JOURNAL + нигде → Bool`. Плюс все имена из
    `initialState.flags` (→ Bool) и `initialState.stats` (→ IntMin(0)),
    если их нет в tags. Это **немедленно** закрывает DoD «опечатка
    `has_swrod` → ошибка» — `swrod` нет ни в tags, ни в
    initialState, значит `UnknownVariable`. Если и tags, и
    initialState пусты — возвращаем `emptyList()` (нечего проверять);
    «ты не объявил переменные» — сигнал для UI редактора (Ф9.2),
    не для `TypeError`. Не C1 (строгий — ломает `test_adventure.json`
    до Ф2.8), не C2 (вообще выключить — DoD не выполнен), не C3
    (вывод типов из использования — это Ф2.8 `inferTypes`).
    **(D) Встраивание в пайплайн** — выбран **D1**: только класс +
    тесты, `JsonToIr.parse` / `Engine.compile` / `Analyzer.lint` не
    трогаем. Встраивание — Ф2.3 (`parse → typecheck → typed IR` или
    `ParseResult.Errors`).
    **Что сделано (2 новых файла + 1 тестовый):**
    **(1)** `engine/.../ir/types/TypeError.kt` — `sealed class
  TypeError` с 9 вариантами. Каждый — `data class` с `message:
  String` и `sceneId: SceneId?` (null для InitialState-ошибок).
    Сообщения — English, actionable, с конкретным fix-hint
    («use '$name > 0' instead» для `HasOnNonBool`; «use
    'modify_stat' for numeric variables» для `SetFlagOnNonBool`;
    и т.д.). Вспомогательный `internal fun VarType.displayName():
  String` — Kotlin-`toString()` (`IntRange(min=0, max=100)`) плохо
    читается в сообщении; `displayName()` даёт
    `IntRange(0..100)`.
    **(2)** `engine/.../ir/types/TypeChecker.kt` — `object
  TypeChecker { fun check(adventure): List<TypeError> }`. Две
    функции построения таблицы: `buildTypeTable` (strict: из
    `variables`; fallback: из `tags` + `initialState`), и две
    рекурсивные проверки: `checkCondition` (по AST, обходит
    `Has`/`Not`/`And`/`Or`/`Compare`/`InRange`), `checkEffects`
    (`SetFlag`/`RemoveFlag`/`ModifyStat`). Порядок проверок: сначала
    `initialState.flags` и `initialState.stats`, потом сцены (сначала
    `scene.effects`, потом `choice.condition` + `choice.effects`).
    Финальный `sortedWith` — по `(sceneId == null, sceneId.value,
  message)` — тот же паттерн, что в `Analyzer.lint`, стабильно для
    снапшот-тестов.
    **(3)** `engine/src/test/.../TypeCheckerTest.kt` — 20 тестов:
    empty adventure; strict-mode unknown variable (condition +
    effect); strict-mode HasOnNonBool (ItemCount, IntRange);
    strict-mode B2-эффекты (SetFlagOnNonBool, RemoveFlagOnNonBool,
    ModifyStatOnNonNumeric); strict-mode B2-условия
    (CompareOnNonNumeric, InRangeOnNonNumeric); strict-mode
    InitialFlagOnNonBool + InitialStatOnNonNumeric; strict-mode clean
    adventure (валидный: Bool + ItemCount + IntRange + все три
    эффекта); strict-mode «variables wins over tags»
    (`UnknownVariable`, хотя имя есть в tags); fallback-mode: typo
    `swrod` → `UnknownVariable` (DoD), has_sword на ItemCount →
    `HasOnNonBool`, journal в initial.flags → Bool (clean), health
    tag → IntMin (clean), potion tag → ItemCount (clean); сортировка
    (initial-state errors первыми, по sceneId, по message); multiple
    errors в одной сцене. Все — по DoD Ф2.2 + B2-расширение.
    **Пользовательская выгода:** (a) автор видит опечатку
    (`has_swrod` при существующем `sword`) **сразу при редактировании**
    (Ф9.2 выведет `TypeError` inline), а не в момент, когда игрок
    нажмёт кнопку и она окажется disabled; (b) `HasOnNonBool` с
    fix-hint «use '$name > 0' instead» учит автора правильному
    синтаксису — на `test_adventure.json` это ровно тот класс
    ошибки, который smoke поймал в Ф1.7.1a-fix3 (`has_sword` вместо
    `sword > 0`); (c) `SetFlagOnNonBool` / `ModifyStatOnNonBool` /
    `ModifyStatOnNonNumeric` ловят перепутанные эффекты — автор не
    тратит время на debug «почему у меня health не уменьшается»,
    когда он случайно написал `set_flag` вместо `modify_stat`;
    (d) fallback C4 даёт **немедленную** пользу на существующих
    файлах без миграции до Ф2.8 — `test_adventure.json` (уже
    использующий `sword > 0`) проходит, но новый файл с
    `has_sword` на ITEM-теге получает `HasOnNonBool` сразу.
    **Что НЕ сделано (осознанно, для следующих шагов):**
    (a) `JsonToIr.parse` не вызывает `TypeChecker.check` — Ф2.3;
    (b) `Engine.compile` не отклоняет IR с `TypeError` — Ф2.3
    (компилятор уже отклоняет `CompileError`s, но type-ошибки
    добавляются в тот же слой);
    (c) `Analyzer.lint` не показывает `TypeError` — Ф9.2 (UI
    редактора); `TypeError` — не `LintIssue`, разные слои;
    (d) `Adventure.variables` в JSON не парсится — Ф2.8; fallback
    C4 работает только на in-memory данных.
    **Verified:** `./gradlew :engine:test --rerun-tasks` → **172/172
    PASSED** (151 из Ф2.1 + 20 новых `TypeCheckerTest` + 1 тест
    `initial state errors come before scene errors`, который сначала
    падал из-за обратного порядка компаратора — `compareBy { it.sceneId
  == null }` давал scene errors первыми; фикс — `!= null`; см.
    «BUG, найденный на smoke» ниже). `VarType IntMin round-trips`
    из Ф2.1 остаётся в 151. Golden не пересоздавался (`TypeChecker`
    не влияет на IR). `SchemaFingerprintTest` не менялся (новых
    IR-классов нет). Все существующие тесты (`RealAdventureTest`,
    `AnalyzerTest`, `JsonToIrTest`, `Engine*Test`, `EvaluatorTest`,
    `EffectsApplierTest`, `ConditionParserTest`, `GoldenIrTest`,
    `AdventureDtoTest`) — зелёные.
    **BUG, найденный на smoke (Ф2.2-fix):** тест `initial state
  errors come before scene errors` сначала падал с сообщением
    `Expected value to be of type TypeError.InitialStatOnNonNumeric,
    actual TypeError.UnknownVariable`. Причина — в `TypeChecker.check`
    финальная сортировка использовала `compareBy<TypeError> { it.sceneId
  == null }`: `false < true` в Boolean-сравнении, поэтому
    scene-errors (`sceneId != null` → `false` → 0) шли первыми, а
    initial-state errors (`sceneId == null` → `true` → 1) — вторыми.
    Замысел был обратный: initial-state errors (не привязаны к сцене)
    должны идти первыми, как самое «верхнее» сообщение в редакторе
    (Ф9.2). Фикс — одна строка: `!= null` вместо `== null`.
    **Урок:** компараторы на `Boolean` коварны — `compareBy { x == null
  }` читается как «null-элементы первыми», но реально это
    «не-null первыми». Правильнее писать `{ x != null }` и явно
    проговаривать в комментарии, что значит `false` в этом компараторе.
    **Следующее действие — Ф2.3:** встроить `TypeChecker.check` в
    пайплайн — `JsonToIr.parse() → ParseResult<Adventure>` должно
    возвращать `ParseResult.Failure` при type-ошибках (либо новый
    `ParseResult.TypedFailure` с `List<TypeError>`, либо встроить
    в `Engine.compile` как `CompileError.TypeMismatch`); частичный
    IR не должен исполняться. Зависит от Ф2.2 (выполнено) и Ф1.3.
  2026-11-12 · Ф2.3 — Встраивание type checker в пайплайн.
Развилки (обсуждены с пользователем):
(A) Форма «parse возвращает type-ошибки» — выбран A2:
ParseResult.Failure(errors: List<ParseError>, typeErrors: List<TypeError> = emptyList()) — два раздельных поля, не один
список с вложенным ParseError.TypeErrors. A1 (обёртка в
ParseError.TypeErrors) — плохо масштабируется: ParseError
перестаёт быть про парсинг, в Ф8/Ф10 туда же придётся
складывать analyzerErrors, границы слоёв размываются. A3
(ParseResult.TypedFailure) — то же, что A2 по UX, но ломает
when во всех call-sites. A2 с дефолтным параметром — обратная
совместимость: все существующие Failure(listOf(...))
компилируются без правок; единственное место чтения — when (result) { is Failure -> result.errors } — тоже без правок
(для parse-ошибок), а result.typeErrors — новый path для
type-ошибок. Для конечного продукта: (a) автор видит две группы
ошибок в редакторе (Ф9.2) — «синтаксис/структура» vs «типы
переменных» — меньше когнитивной нагрузки; (b) читатель получает
специализированный диалог для type-ошибок (fix-hints полезнее
«файл повреждён»); (c) в Ф10 Error hierarchy честно отражает
четыре слоя (parse / typecheck / compile / analyzer), а не три с
размытой границей.
(B) Порядок проверок — выбран B1: typecheck только при
чистом парсинге. Если errors.isNotEmpty() — возвращаем Failure
и не запускаем typecheck. Причина: частичный IR (у choice
condition = null после сбоя парсинга условия) даст false
negatives в лучшем случае и false positives в худшем. Это ровно
DoD «частичный IR не исполняется» — если парсинг не завершён,
typecheck не имеет смысла. Автор исправляет парсинг → видит
типы.
(C) Защита в Engine.compile — выбран C1: Engine.compile
не трогаем. DoD формально закрыт: parse — единственный публичный
путь JSON → Adventure, Engine.compile — публичный API для уже
готового IR. Если кто-то строит IR руками (тест, будущая фича) —
это его ответственность, как и с CompileError.DuplicateSceneId,
который тоже проверяет не всё. Defence in depth — YAGNI; если
когда-то понадобится — точечно добавим C2.
Что сделано (5 файлов в :engine, 2 в :app, 3 в docs):
(1) engine/.../parse/ParseResult.kt — переписан целиком.
Failure(errors: List<ParseError>, typeErrors: List<TypeError> = emptyList()). init { require(...) } — инвариант: ровно один
из двух списков непустой (оба пусты → Success; оба непусты →
нарушение B1-последовательности). KDoc — двухступенчатый пайплайн:
parse → (только при чистом) typecheck; почему два поля, а не один
список с вариантом; I3-инвариант.
(2) engine/.../format/JsonToIr.kt — в parse после
buildAdventure: если errors.isNotEmpty() → Failure(errors)
без typecheck; иначе TypeChecker.check(adventure); если
typeErrors.isNotEmpty() → Failure(emptyList(), typeErrors);
иначе Success(adventure). Импорт TypeChecker. В KDoc parse
добавлено пояснение про двухступенчатость.
(3) engine/src/test/.../format/JsonToIrTest.kt — старый тест
typo in flag name parses as Has - type check is F2 переименован
в typo in flag name produces TypeError now that F2_3 wires the checker и переписан: теперь Failure, errors пуст,
typeErrors = [UnknownVariable("swrod")]. Важно: в JSON
добавлен initial_state.flags = ["marker"] — без непустой type
table fallback C4 возвращает emptyList() (нечего проверять), и
swrod молча проходит как Success. Это не баг, а
документированное поведение fallback: пустая таблица = «автор
ещё не объявил переменные, проверять не по чему». Тест
фиксирует, что как только таблица непуста — typo ловится.
Два новых теста: type errors are not checked when parse errors are present (B1: JSON с blank choice.text + has_swrod →
Failure(errors=non-empty, typeErrors=empty)); type errors produced when parse is clean - HasOnNonBool path (ITEM-тег
sword, has_sword → Failure(typeErrors=[HasOnNonBool]), демонстрация
другого варианта TypeError через реальный пайплайн).
(4) :app/ui/reader/AdventureReaderFragment.kt — обработка
ParseResult.Failure разделена: typeErrors.isNotEmpty() →
showTypeErrorsDialog(typeErrors) + return; errors.isNotEmpty()
→ существующий Toast + pop. Новый метод showTypeErrorsDialog:
AlertDialog (не Toast), заголовок «Не удалось открыть», сообщение
со списком до 5 ошибок и счётчиком «…и ещё N», setCancelable(false),
ОК → pop. Почему диалог: type-ошибки содержат actionable
fix-hints («use '$name > 0' instead»), которые игрок может
передать автору.
(5) :app/ui/list/AdventureListFragment.kt — в
parseAdventureFromUri логирование причины Skip:
result.errors.firstOrNull()?.message ?: result.typeErrors.firstOrNull()?.message ?: "unknown error". Без правки type-error-only файл логировался
как Skip '...': null (бесполезно).
(6) docs/engine-architecture.md — §«Single entry point»
дополнен подразделом «Two-stage pipeline (Ф2.3)» с примером
when (ParseResult); §«Error hierarchy» — теперь четыре слоя
(ParseError + TypeError в ParseResult.Failure / CompileError /
LintIssue), с обоснованием «два поля, не один список» и
инвариантом «ровно один непуст».
(7) docs/architecture.md — I3 переписан:
«Parser vs type checker vs compiler vs analyzer», четыре
вопроса вместо трёх.
(8) Progress log (эта запись).
Пользовательская выгода: (a) автор, открыв сломанный файл
в редакторе (Ф9.2), увидит раздельные группы «синтаксис»
и «типы»; (b) читатель при type-ошибке получает
специализированный диалог с fix-hints вместо размытого
«файл повреждён»; (c) в логах AdventureListFragment теперь
есть причина пропуска — можно диагностировать удалённо;
(d) в architecture.md / engine-architecture.md граница
«parse / typecheck / compile / analyzer» зафиксирована явно
— новый contributor не положит type-check внутрь парсера
по ошибке.
Что НЕ сделано (осознанно, для следующих шагов):
(a) Engine.compile не отклоняет IR с TypeError — C1
(defence in depth — YAGNI); (b) Analyzer.lint не показывает
TypeError — это не LintIssue, разные слои; в редакторе
(Ф9.2) покажем отдельной группой; (c) Adventure.variables в
JSON не парсится — Ф2.8 (fallback C4 работает до неё);
(d) test_adventure.json в fallback-режиме проходит — sanity-тест
real test_adventure_json is type-clean (Ф2.2-шаг-1) остаётся
зелёным.
Verified: ./gradlew :engine:test --rerun-tasks →
175/175 PASSED (172 из Ф2.2 + 1 sanity real test_adventure_json is type-clean из шага 1 + 2 новых
JsonToIrTest + старый переименован без прироста). Из них:
real test_adventure_json is type-clean — PASSED,
typo in flag name produces TypeError now that F2_3 wires the checker — PASSED, type errors are not checked when parse errors are present — PASSED, type errors produced when parse is clean - HasOnNonBool path — PASSED. Все остальные
JsonToIrTest / AnalyzerTest / RealAdventureTest /
GoldenIrTest / SchemaFingerprintTest — зелёные. Golden
не пересоздавался (IR-поля не менялись; ParseResult — не
IR). :app:assembleDebug — BUILD SUCCESSFUL.
Smoke (телефон): A. test_adventure.json открывается
(reader: Success, typecheck чистый); B. вручную испортить
has_sword → sword > 0 откатить не нужно — теперь наоборот,
sword > 0 рабочий вариант, а has_sword даёт
HasOnNonBool → AlertDialog «Файл содержит 1 ошибок в типах
переменных: • Condition 'has_sword' requires 'sword' to be
Bool, but it is ItemCount. For numeric variables use
'sword > 0' instead.» ✅; C. AdventureListFragment:
type-error-only файл → Skip '<uri>': Condition 'has_sword' requires 'sword' to be Bool, ... в логах ✅.
Следующее действие — Ф2.4: ужесточить грамматику условий
под типы — has_X только для Bool/ItemCount, X >= N
только для Int/IntRange; парсер должен возвращать тип
каждого литерала; смешение типов (Bool && Int) — ошибка.
Зависит от Ф2.1/Ф2.2/Ф2.3 (выполнено).
  - - **2026-11-12 · Ф2.4** — Грамматика условий ужесточена под типы.
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
  - Продолжение в progress-log-2.md
  