# Glossary

**Status:** evolving. Terms are added and refined as the app evolves.

This file is the single source of truth for:

- **Domain terminology** — how core concepts are named in code and docs.
- **UI strings** — Russian UI text mapped to English, so that adding an
  English locale later is a lookup, not a redesign.
- **Naming conventions** — spellings we commit to across the project.

When a new term is introduced anywhere in the project, add it here in the same
change. When in doubt about a spelling, follow this file — not your intuition.

## How to use

- **In docs**: use the English term from the "English" column. Do not invent
  synonyms.
- **In the app (Russian)**: the current UI uses Russian. When a string in the
  app matches a row here, treat the row as the canonical mapping.
- **For future i18n**: the Russian string is the source key. Keep the mapping
  up to date as the UI evolves.
- **In code**: keep the identifiers exactly as they appear in the
  "Identifier" columns. Renaming a data model or JSON field is a **breaking
  change** and requires a doc-update PR.

## 1. Domain terms

Concepts that appear across code, JSON and documentation.

| English            | Russian                   | Notes                                                                 |
|--------------------|---------------------------|-----------------------------------------------------------------------|
| Adventure          | Приключение               | Also the project's name. Never translate "Adventure" when it refers to the app or the project. |
| Chapter            | Глава                     | A group of scenes, separated by a step gate.                          |
| Scene              | Сцена                     | One screen of text and (optionally) choices.                          |
| Choice             | Выбор / вариант действия  | A single selectable action in a scene.                                |
| Next scene         | Следующая сцена           | The scene a choice leads to (`next_scene`).                           |
| Condition          | Условие                   | A rule that must hold for a choice to be visible.                     |
| Tag                | Метка                     | An effect or flag used by a scene or choice.                          |
| Flag               | Флаг                      | A boolean-ish marker (`set_flags` / `remove_flags`).                  |
| Stat               | Стат                      | A numeric value (`modify_stats`).                                     |
| Item               | Предмет                   | A tag with `category=ITEM`. Shown in the inventory.                   |
| Journal entry      | Запись журнала            | A tag with `category=JOURNAL` and `visible=true`.                     |
| Health             | Здоровье                  | A tag with `category=HEALTH` (or the literal name `health`).          |
| Initial state      | Начальное состояние       | The player's starting flags and stats (`initial_state`).              |
| Player state       | Состояние игрока          | Current flags + stats + inventory of the player.                      |
| Death scene        | Сцена смерти              | Special scene shown when the player dies (`death_scene`).             |
| Chapter end        | Конец главы               | A scene that closes a chapter (`isChapterEnd`).                       |
| Step gate          | Шаговый шлюз              | Number of steps required to open the next chapter.                    |
| Required steps     | Требуемые шаги            | The threshold of a step gate (`required_steps`).                      |
| Remaining steps    | Осталось шагов            | Steps left until the gate opens.                                      |
| Death (state)      | Смерть                    | The player's health has reached zero.                                 |
| Draft (editor)     | Черновик                  | Unsaved editor state, saved in `editor_draft_prefs`.                  |
| Validation issue   | Проблема валидации        | A warning or error reported by `StoryValidator`.                      |
| Story stats        | Статистика истории        | Scene/word/choice counts shown in the editor.                         |

## 2. JSON field names

These are the canonical field names of the adventure JSON. They are part of
the file format and must not be renamed without a migration plan.

| Field                                 | Meaning                                                     |
|---------------------------------------|-------------------------------------------------------------|
| `title`                               | Adventure title.                                            |
| `author`                              | Adventure author.                                           |
| `description`                         | Short description.                                          |
| `initial_state`                       | Player's starting flags and stats.                          |
| `initial_state.flags`                 | Starting flags (list of strings).                           |
| `initial_state.stats`                 | Starting stats (map name → int).                            |
| `tags`                                | Registry of tag definitions for this adventure.             |
| `tags[].name`                         | Stable, machine-readable tag name.                          |
| `tags[].display_name`                 | Human-readable name shown in the UI.                        |
| `tags[].category`                     | `HEALTH` / `ITEM` / `JOURNAL`.                              |
| `tags[].visible`                      | Whether the tag is shown in the HUD.                        |
| `death_scene`                         | Optional. Death scene definition.                           |
| `death_scene.id`                      | Scene ID used for the death screen.                         |
| `death_scene.text`                    | Text shown when the player dies.                            |
| `death_scene.allow_step_back`         | Whether the "step back" button is shown.                    |
| `death_scene.allow_chapter_restart`   | Whether the "restart chapter" button is shown.              |
| `death_scene.allow_adventure_restart` | Whether the "restart adventure" button is shown.            |
| `chapters`                            | Ordered list of chapters.                                   |
| `chapters[].id`                       | Chapter ID.                                                 |
| `chapters[].title`                    | Chapter title.                                              |
| `chapters[].required_steps`           | Steps needed to open the next chapter (0 for the last one). |
| `chapters[].scenes`                   | Scenes in this chapter.                                     |
| `scenes[].id`                         | Scene ID (unique per adventure).                            |
| `scenes[].text`                       | Scene body text.                                            |
| `scenes[].set_flags`                  | Flags to add when the scene is entered.                     |
| `scenes[].remove_flags`               | Flags to remove when the scene is entered.                  |
| `scenes[].modify_stats`               | Stat changes when the scene is entered.                     |
| `scenes[].choices`                    | Available choices in this scene.                            |
| `choices[].text`                      | Choice label shown to the player.                           |
| `choices[].next_scene`                | ID of the scene this choice leads to.                       |
| `choices[].condition`                 | Optional condition gating this choice.                      |
| `choices[].set_flags`                 | Flags to add when this choice is taken.                     |
| `choices[].remove_flags`              | Flags to remove when this choice is taken.                  |
| `choices[].modify_stats`              | Stat changes when this choice is taken.                     |

## 3. Tag categories

| Value      | Russian  | Meaning                                                    |
|------------|----------|------------------------------------------------------------|
| `HEALTH`   | Здоровье | Numeric health-like value; shown in the HUD heart icon.    |
| `ITEM`     | Предмет  | Inventory item; shown in the HUD backpack.                 |
| `JOURNAL`  | Журнал   | Boolean-like flag or visible journal entry.                |

The literal tag name `health` is always treated as `HEALTH`, regardless of the
declared category.

## 4. Conditions (as strings in JSON)

| Syntax    | Meaning                                        |
|-----------|------------------------------------------------|
| `has_X`   | True if flag `X` is set or stat `X` > 0.       |
| `!has_X`  | True if flag `X` is not set and stat `X` ≤ 0.  |
| `X >= N`  | True if stat `X` is at least `N`.              |
| `X < N`   | True if stat `X` is less than `N`.             |
| *(empty)* | Always true.                                   |

## 5. UI strings (Russian → English)

Grouped by screen. This is not exhaustive yet — extend it when adding a new
screen or string. Use the Russian text as the source key.

### Menu

| Russian                    | English            |
|----------------------------|--------------------|
| Библиотека приключений     | Adventure library  |
| Статистика                 | Statistics         |
| Создать приключение        | Create adventure   |
| Импортировать приключение  | Import adventure   |
| Логи игры                  | Game logs          |
| Настройки                  | Settings           |

### Adventure list

| Russian                | English         |
|------------------------|-----------------|
| Мои приключения        | My adventures   |
| Меню                   | Menu            |
| Загрузить приключение  | Load adventure  |

### Editor — toolbar

| Russian       | English    |
|---------------|------------|
| Меню          | Menu       |
| Без названия  | Untitled   |
| + Сцена       | + Scene    |
| Сохранить     | Save       |
| Загрузить     | Load       |
| Название      | Title      |
| Проверить     | Validate   |
| Поиск         | Search     |
| Статистика    | Statistics |
| Карта         | Map        |

### Editor — scene

| Russian                              | English                                        |
|--------------------------------------|------------------------------------------------|
| Название сцены                       | Scene title                                    |
| Текст сцены                          | Scene text                                     |
| ID:                                  | ID:                                            |
| Пересобрать ID из названия           | Rebuild ID from title                          |
| Предпросмотр сцены в читалке         | Preview scene in reader                        |
| 🏁 Глава                             | 🏁 Chapter end                                 |
| 💀 Смерть                            | 💀 Death                                       |
| Добавить метку                       | Add tag                                        |
| Сделать концом главы                 | Mark as chapter end                            |
| 💀 Сцена смерти                      | 💀 Death scene                                 |
| Какие кнопки видит игрок после смерти: | Which buttons does the player see after death: |
| ↩ Один шаг назад                     | ↩ Step back                                    |
| 📖 В начало главы                    | 📖 To chapter start                            |
| 🏠 Начать приключение заново         | 🏠 Restart adventure                           |
| Добавить вариант действия            | Add choice                                     |

### Editor — choice

| Russian                 | English              |
|-------------------------|----------------------|
| Текст варианта          | Choice text          |
| След. сцена:            | Next scene:          |
| Условие (метка)         | Condition (tag)      |
| (без текста)            | (no text)            |
| ➕ Создать новую сцену… | ➕ Create new scene… |
| (выберите сцену)        | (choose a scene)     |

### Editor — tag

| Russian                       | English                 |
|-------------------------------|-------------------------|
| Имя метки (технич.)           | Tag name (technical)    |
| Знач.                         | Value                   |
| Видимое название              | Display name            |
| Показывать в статистике героя | Show in hero statistics |
| Здоровье                      | Health                  |
| Предмет                       | Item                    |
| Журнал                        | Journal                 |

### Editor — initial state

| Russian                | English          |
|------------------------|------------------|
| 🎬 Начальное состояние | 🎬 Initial state |
| + Метка                | + Tag            |

### Editor — steps dialog

| Russian                                                              | English                                                              |
|----------------------------------------------------------------------|----------------------------------------------------------------------|
| Шагов до следующей главы                                             | Steps to next chapter                                                |
| Сколько шагов нужно пройти, чтобы открыть следующую главу?           | How many steps to walk to open the next chapter?                     |
| Средний пользователь проходит ~6000 шагов в день (~60 минут ходьбы). | An average user walks ~6,000 steps per day (~60 minutes of walking). |
| 🚶 500 шагов  (≈ 5 минут)                                            | 🚶 500 steps (≈ 5 min)                                               |
| 🚶 1 000 шагов  (≈ 10 минут)                                         | 🚶 1,000 steps (≈ 10 min)                                            |
| 🚶 4 000 шагов  (≈ 40 минут)                                         | 🚶 4,000 steps (≈ 40 min)                                            |
| 🚶 8 000 шагов  (≈ 1 ч 20 мин)                                       | 🚶 8,000 steps (≈ 1 h 20 min)                                        |
| Своё значение:                                                       | Custom value:                                                        |
| Например, 2500                                                       | For example, 2500                                                    |
| Значение должно быть больше 0                                        | Value must be greater than 0                                         |
| Введите целое число больше 0                                         | Enter an integer greater than 0                                      |

### Common dialog buttons

| Russian      | English |
|--------------|---------|
| Применить    | Apply   |
| Готово       | Done    |
| Отмена       | Cancel  |
| Удалить      | Delete  |
| Закрыть      | Close   |
| Очистить     | Clear   |
| Да           | Yes     |
| Создать      | Create  |
| Восстановить | Restore |

### Reader

| Russian                      | English           |
|------------------------------|-------------------|
| Меню                         | Menu              |
| ❤️ 0                         | ❤️ 0              |
| 🎒 0                         | 🎒 0              |
| 📖 0                         | 📖 0              |
| 🐞                           | 🐞                |
| Продолжить                   | Continue          |
| Один шаг назад               | Step back         |
| В начало главы               | To chapter start  |
| Начать приключение заново    | Restart adventure |
| Начать заново                | Restart           |
| В главное меню               | Main menu         |
| Настройки                    | Settings          |
| Ожидание данных шагомера…    | Waiting for step data… |
| Здоровье                     | Health            |
| Рюкзак                       | Inventory         |
| Журнал                       | Journal           |
| (пусто)                      | (empty)           |

### Reader — debug panel

| Russian                                                                                        | English                                                                                |
|------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| 🐞 Отладка                                                                                     | 🐞 Debug                                                                               |
| Текущая сцена: —                                                                               | Current scene: —                                                                       |
| Статы                                                                                          | Stats                                                                                  |
| + Стат                                                                                         | + Stat                                                                                 |
| Флаги                                                                                          | Flags                                                                                  |
| + Флаг                                                                                         | + Flag                                                                                 |
| Шаги до следующей главы                                                                        | Steps to next chapter                                                                  |
| Симуляция прогресса шагомера. При достижении порога глава откроется.                           | Simulation of step progress. When the threshold is reached, the chapter will open.     |
| Пройдено шагов                                                                                 | Steps taken                                                                            |
| Применить                                                                                      | Apply                                                                                  |
| Открыть главу сейчас                                                                           | Open chapter now                                                                       |
| Сбросить к начальному состоянию                                                                | Reset to initial state                                                                 |
| Изменить значение                                                                              | Change value                                                                           |
| Удалить стат                                                                                   | Delete stat                                                                            |
| Удалить флаг                                                                                   | Delete flag                                                                            |
| (нет статов)                                                                                   | (no stats)                                                                             |
| (нет флагов)                                                                                   | (no flags)                                                                             |

### Statistics

| Russian                                                                                                           | English                                                                                                            |
|-------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Статистика шагов                                                                                                  | Step statistics                                                                                                    |
| Меню                                                                                                              | Menu                                                                                                               |
| Всего пройдено                                                                                                    | Total walked                                                                                                       |
| Сегодня                                                                                                           | Today                                                                                                              |
| За 7 дней                                                                                                         | Last 7 days                                                                                                        |
| За 30 дней                                                                                                        | Last 30 days                                                                                                       |
| Последние 14 дней                                                                                                 | Last 14 days                                                                                                       |
| Звание                                                                                                            | Rank                                                                                                               |
| Начинающий путешественник                                                                                         | Beginner traveller                                                                                                 |
| %1$d%% до звания «%2$s»                                                                                            | %1$d%% to rank "%2$s"                                                                                              |
| Звание «%1$s» получено                                                                                             | Rank "%1$s" achieved                                                                                               |
| Данных пока нет.\n\nПройдитесь с включённым шагомером — статистика появится здесь.                                  | No data yet.\n\nWalk with the step counter on — statistics will appear here.                                        |
| Нет разрешения на физическую активность.\nШаги не будут считаться, пока разрешение не выдано.                      | No physical activity permission.\nSteps will not be counted until the permission is granted.                       |
| Загрузка…                                                                                                         | Loading…                                                                                                           |

### Settings

| Russian                                                                                            | English                                                                                  |
|----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Настройки                                                                                          | Settings                                                                                 |
| Включить запись лога в файл                                                                        | Enable file logging                                                                      |
| Синхронизация с Health Connect                                                                     | Sync with Health Connect                                                                 |
| Размер шрифта интерфейса:                                                                          | Interface font size:                                                                     |
| Шрифт интерфейса:                                                                                  | Interface font:                                                                          |
| Добавить                                                                                           | Add                                                                                      |
| Образец текста                                                                                     | Text sample                                                                              |
| Выключено — используется датчик телефона (шаги в реальном времени)                                 | Disabled — using the phone sensor (real-time steps)                                     |
| Включено — шаги обновляются с задержкой (Health Connect)                                           | Enabled — steps update with a delay (Health Connect)                                    |
| Health Connect недоступен — используется датчик телефона                                           | Health Connect unavailable — using the phone sensor                                     |
| Health Connect недоступен на этом устройстве                                                       | Health Connect is not available on this device                                          |
| Health Connect включён                                                                             | Health Connect enabled                                                                   |
| Разрешение не выдано — используется датчик телефона                                                | Permission not granted — using the phone sensor                                         |

### Setup

| Russian                                                                     | English                                                             |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------|
| Добро пожаловать в Adventure!                                               | Welcome to Adventure!                                               |
| Для начала выберите или создайте папку Adventure на устройстве.             | To start, choose or create an Adventure folder on the device.       |
| Выбрать папку Adventure                                                     | Choose Adventure folder                                             |
| Папка выбрана!                                                              | Folder selected!                                                    |
| Нужно выбрать папку для продолжения                                         | You must choose a folder to continue                                |

### Logs

| Russian                        | English                    |
|--------------------------------|----------------------------|
| Логи                           | Logs                       |
| Копировать                     | Copy                       |
| Очистить                       | Clear                      |
| Лог пуст                       | Log is empty               |
| Лог скопирован в буфер обмена  | Log copied to clipboard    |
| Лог очищен                     | Log cleared                |
| Очистить лог?                  | Clear log?                 |

### Search

| Russian                            | English                             |
|------------------------------------|-------------------------------------|
| Поиск по истории                   | Search the story                    |
| Введите запрос (минимум 2 символа) | Enter a query (at least 2 characters) |
| Заголовки                          | Titles                              |
| Тексты                             | Texts                               |
| Выборы                             | Choices                             |
| Метки                              | Tags                                |
| Введите минимум 2 символа          | Enter at least 2 characters         |
| Ничего не найдено                  | Nothing found                       |
| Найдено …                          | Found …                             |

### Validation

| Russian            | English      |
|--------------------|--------------|
| Здоровье истории   | Story health |
| Ошибок: N          | Errors: N    |
| Предупреждений: N  | Warnings: N  |
| Проблем не найдено. История выглядит здоровой! ✅ | No problems found. The story looks healthy! ✅ |

### Editor — dialogs

| Russian                                                                                            | English                                                                                  |
|----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Восстановить черновик?                                                                             | Restore draft?                                                                           |
| Найден несохранённый черновик от …                                                                 | An unsaved draft was found from …                                                        |
| Редактировать приключение                                                                          | Edit adventure                                                                           |
| Название приключения                                                                               | Adventure title                                                                          |
| Автор                                                                                              | Author                                                                                   |
| Описание                                                                                           | Description                                                                              |
| Новая сцена                                                                                        | New scene                                                                                |
| Название новой сцены                                                                               | New scene title                                                                          |
| ID будет сгенерирован из названия автоматически.                                                   | The ID will be generated from the title automatically.                                   |
| Удалить сцену?                                                                                     | Delete scene?                                                                            |
| Удалить вариант действия?                                                                          | Delete choice?                                                                           |
| Заменить сцену смерти?                                                                             | Replace death scene?                                                                     |
| Пересобрать ID?                                                                                    | Rebuild ID?                                                                              |
| Смена шрифта                                                                                       | Change font                                                                              |
| Применить шрифт «…» ко всем экранам? Приложение будет перезапущено.                                 | Apply font "…" to all screens? The app will restart.                                     |
| Черновик автосохранён                                                                              | Draft auto-saved                                                                         |
| Изменения…                                                                                         | Changes…                                                                                 |
| Сохранение…                                                                                        | Saving…                                                                                  |
| Сохранено                                                                                          | Saved                                                                                    |
| Сохранено в файл                                                                                   | Saved to file                                                                            |
| Загружено из файла                                                                                 | Loaded from file                                                                         |

## 6. Naming conventions

- **"Adventure" as the project and the app** — never translated. The word is
  the brand.
- **Domain terms in code** — use the spellings in section 1 (e.g.
  `Adventure`, `Chapter`, `Scene`, `Choice`, `DeathScene`, `PlayerState`).
- **JSON fields** — always `snake_case`, exactly as listed in section 2.
- **Kotlin identifiers** — as in the codebase (`EditorScene`, `EditorChoice`,
  `EditorTag`, `StepHistoryManager`, `ProgressManager`, `StepCounterService`,
  `HealthConnectStepSource`, `SensorStepSource`).
- **Paths and folder names** — `Adventures/` and `NewAdventures/` are the
  only user-visible folders created by the app; their names are fixed.
- **Russian UI text** — use «guillemets» for quoted user-facing text inside
  English docs, e.g. «Меню».

## 7. Not covered (yet)

Deliberately excluded, to be added as the relevant feature stabilizes:

- Locale codes and Android resource qualifiers (`values-ru`, `values-en`,
  etc.) — to be documented when the English locale is added.
- Format specifications for `progress.json`, `step_history.json`,
  `debug_log.txt` — internal persistence formats that may still change.
- Debug-only strings beyond the debug panel (internal simulations) — these
  are for developers and live only in code.

Add new sections as the app gains new user-facing surfaces.