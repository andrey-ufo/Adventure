# Adventure 

> Adventure - приложение созданное для людей, которым нравятся текстовые приключения (такие как Fighting Fantasy и подобные) и пешие прогулки, а также для авторов, которые хотят писать текстовые квесты.

## 📚 Документация

Для получения подробной информации обратитесь к соответствующим разделам:


## 🤝 Участие в разработке

Мы рады вашей помощи! Пожалуйста, ознакомьтесь с [Руководством по контрибьютингу](CONTRIBUTING.md) перед созданием Pull Request.

## 📄 Лицензия

Проект распространяется под лицензией MIT. Подробнее см. в файле [LICENSE](LICENSE).

test_adventure.json - это тестовое приключение. Его нужно переписывать по мере развития приложения, чтобы оно соответствовало новым функциям приложения и позволяло тестировать их.

Комментарии в коде должны быть написаны на английском языке. Все комментарии на русском нужно заменять на английские.

Приложение имеет папки «NewAdventures» и «Adventures» чтобы разделять черновики и готовые книги. «Adventures» - библиотека законченных приключений. «NewAdventures» - черновики.


## Step counter architecture

Three layers, from lowest to highest:

1. **Source** (`StepCounterService`, optional `HealthConnectStepSource`) — a stream of
   step *deltas*. Sources never touch `progress.json` or game state.
2. **History** (`StepHistoryManager`) — the single owner of step history:
    - 30 daily buckets (`date → steps`) + monotonic `totalStepsAllTime`
    - in-memory cache, batched disk writes, `Mutex`-protected
    - public API: `getTotalSteps`, `getTodaySteps`, `getLastNDaysSum`,
      `getDailyBuckets`, `recordDelta`, `flush`.
3. **Game** (`AdventureReaderFragment`) — reads only aggregates from
   `StepHistoryManager`; owns `remainingSteps` and `isGateActive` for chapter gates.

## API for future features

The following are deliberately exposed and stable; future features should
build on them and not open `progress.json` / `step_history.json` directly:

- **Achievements** (`StepAchievementEngine`, not yet implemented) —
  read via `StepHistoryManager.getLastNDaysSum(ctx, 7)` and `(ctx, 30)`.
  Planned examples: "≥50 000 steps in the last 7 days → +10 stamina".
  The `TODO(feature:achievements)` marker in `StepHistoryManager.kt` shows
  where the engine will hook in.
- **Step-gated choices** (5000 / 10000 steps in a scene) — implement via a
  per-choice `remainingSteps` identical to chapter gates; the model already
  lives in `ProgressData.remainingSteps`.
- **Adventure unlocks** — read `StepHistoryManager.getTotalSteps(ctx)` and
  compare against per-adventure thresholds. No new storage is required.
- **Ranks** — pure function of `getTotalSteps()`; the statistics screen
  already shows progress to the first rank as a template.

Everything above is a pure read from `StepHistoryManager`; sources
(`StepCounterService`, HC) can be swapped without touching game logic.