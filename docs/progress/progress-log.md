## Progress log

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