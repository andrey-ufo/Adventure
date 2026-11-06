# Lint baseline

**Status:** evolving. Captured for Ф0.2 of the roadmap.

This file records the known lint issues at a point in time, so that CI can
be green while a decision on each issue is pending (see Ф0.3). It is not a
replacement for `lint-baseline.xml` — the actual baseline mechanism is
decided in Ф0.3.

## Snapshot

- **Date:** 2026-11-05
- **Commit:** `<hash>` (filled in on first CI run)
- **AGP:** 9.5.0
- **Lint:** 9.5.0 (bundled with AGP)

## Summary

**Current state (after AGP 9.5.0 / lint 9.5.0):**
`./gradlew :app:lintDebug` → `0 errors, 248 warnings`.

**Original state (from `index.html`, older lint):**
- 1 error
- 291 warnings
- 39 weak warnings
- 1 grammar error
- 17 typos

The error (Foreground Service type not declared) is **gone** on the newer
lint — see the "Error" section below for details.

## Error (resolved in Ф0.3)

### Foreground Service type not declared for Android 14+ — NOT PRESENT ANYMORE

- **Original file:** `app/src/main/AndroidManifest.xml` (line 4)
- **Original message:** "Android 14+ requires Foreground Service types
  declaration and justification."
- **Original inspection ID:** 1798711127
- **Current state:** the error does not reproduce with lint 9.5.0. The
  manifest already declares `android:foregroundServiceType="health"` on
  `<service>` and `FOREGROUND_SERVICE_HEALTH` as a permission. Most likely
  the check has been re-evaluated by the newer lint (Play Policy insights
  apply to Play Console submission, which is out of scope per `ROADMAP.md`).
- **Decision (Ф0.3):** no fix and no baseline suppression needed. The
  error simply does not reproduce; `abortOnError = true` works.
- **Ref:** `index.html`, ID 1798711127 (historical).

## Notable warnings (not blocking; deferred to later phases)

### Hardcoded text — 125 occurrences (L6 / Ф7.3)

| File | Count |
|---|---|
| `item_editor_scene.xml` | 17 |
| `fragment_adventure_editor.xml` | 13 |
| `dialog_debug_panel.xml` | 11 |
| `dialog_chapter_steps.xml` | 8 |
| `item_editor_choice.xml` | 7 |
| `fragment_adventure_read.xml` | 6 |
| `fragment_menu.xml` | 6 |
| `fragment_settings.xml` | 6 |
| `item_editor_tag.xml` | 6 |
| `dialog_search.xml` | 5 |
| `item_initial_state.xml` | 4 |
| `dialog_edit_meta.xml` | 3 |
| `fragment_adventure_map.xml` | 3 |
| `fragment_setup.xml` | 3 |
| `dialog_debug_add.xml` | 2 |
| `dialog_new_scene.xml` | 2 |
| `dialog_story_stats.xml` | 2 |
| `fragment_adventure_list.xml` | 2 |
| `fragment_log_viewer.xml` | 2 |
| `fragment_statistics.xml` | 2 |
| `item_debug_stat.xml` | 2 |
| `item_load_adventure.xml` | 2 |
| `item_map_chapter_header.xml` | 2 |
| others (1 each) | 10 |

**Total: 125.** Fix in Ф7.3.

### Constant Locale — `Logger`

- **File:** `app/src/main/java/com/myapp/adventure/utils/Logger.kt`
- **Message:** "Assigning `Locale.getDefault()` to a final static field is suspicious."
- **Current state:** not present in the current lint 9.5.0 run. Either
  already fixed (Logger uses `Locale.ROOT`) or the report was stale.
- **Planned fix (if it comes back):** U7 / Ф6.5.

### Obsolete SDK_INT version check — 3 warnings

- `mipmap-anydpi-v26` folder is unnecessary (`minSdk` is 26).
- `SetupFragment.kt:190` — `SDK_INT` always ≥ 26.
- `StepCounterService.kt:124` — `SDK_INT` always ≥ 26.
- **Not blocking**; clean up in a later pass.

### Other (deferred)

- "Use KTX extension function" — 38 (`String.toColorInt`, `String.toUri`,
  `SharedPreferences.edit`, `ViewGroup.isNotEmpty`).
- "Invalidating all RecyclerView data" — 9 (`notifyDataSetChanged`).
- "Unused resources" — 3 (`colors.xml: black, white`, `backup_rules.xml`).
- "Missing autofillHints" / "Missing inputType" — deferred to editor
  cleanup pass.

See `index.html` for the historical full list; current runs produce the
same warnings minus the error.

## How to reproduce

```bash
./gradlew :app:lintDebug
```

Reports:
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`
- `app/build/reports/lint-results-debug.txt`

## Decision log

| Date | Decision | By |
|---|---|---|
| 2026-11-05 | Baseline captured; temporary `abortOnError = false` to keep CI green | this PR |
| 2026-11-05 | **Ф0.3: closed.** `./gradlew :app:lintDebug` → `0 errors, 248 warnings`. The Foreground Service error from the original `index.html` does not reproduce with lint 9.5.0; the manifest is correct (`FOREGROUND_SERVICE_HEALTH` + `foregroundServiceType="health"`). `abortOnError = true` is in place. Baseline file `app/lint-baseline.xml` was generated and records the current 248 warnings as known. Ф0.7 (fix) is not required. | this PR |