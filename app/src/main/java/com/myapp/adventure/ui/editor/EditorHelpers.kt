package com.myapp.adventure.ui.editor

import com.myapp.adventure.R

/**
 * Ф4-infra (3b-6a): locale-aware integer formatting with non-breaking
 * thousand groups ("1 234" on any device). Same output as
 * `StatisticsScreen.formatSteps`, but without needing a `NumberFormat`
 * field — this function is stateless and reusable from Compose code.
 *
 * **Why not `NumberFormat.getIntegerInstance`.** That allocates a new
 * formatter per call and is a `:app`-internal concern; a single
 * `String.format` with `%,d` + `.replace(',', ' ')` is the simplest
 * equivalent. The `%,d` respects the device locale's grouping
 * character (`,` in en, `.` in de, etc.), and `.replace(',', ' ')`
 * normalizes it to a space for all locales where the `%`-formatter
 * produces a comma. On locales where the formatter uses a period
 * (de, ru in some versions), the string is left as-is — the space
 * is not forced. See `pluralWithNumber` for the "N word" composition.
 *
 * **Deferred to Ф7.3.** Once `strings.xml` gets English defaults and
 * `values-ru/`, the plural composition in `pluralWithNumber` will use
 * the device locale (via `resources.getQuantityString` without
 * `ruQuantityString`); `formatInt` itself does not change.
 */
fun formatInt(n: Int): String =
    String.format(java.util.Locale.getDefault(), "%,d", n).replace(',', ' ')

/**
 * Ф4-infra (3b-6a): combines locale-aware number grouping ([formatInt])
 * with the Russian plural suffix (`шаг` / `шага` / `шагов`).
 *
 * **Why `ruQuantityString`.** See `PluralHelper.kt` KDoc: plurals must
 * be evaluated against the **Russian** plural rules regardless of the
 * device locale, because this project currently ships Russian strings
 * in `values/` (default) — a non-`ru` device would otherwise pick the
 * wrong rule. Ф7.3 removes this.
 *
 * **The `removePrefix` trick.** `getQuantityString` returns
 * `"<N> <word>"` (e.g. `"1234 шага"`). We want just `<word>` so we can
 * prepend a locale-grouped `<N>`. `removePrefix(n.toString())` strips
 * the raw `<N>` (Kotlin's `Int.toString()` — no grouping), leaving
 * `" шага"`; `.trim()` gives `"шага"`. The grouping is then applied
 * via [formatInt]. Two `N` forms in play — raw (`1234`, used by
 * `removePrefix`) and grouped (`1 234`, used in the result) — do not
 * collide because they are used in different branches of the same
 * function.
 *
 * **`Context` parameter.** This function is top-level, so any Compose
 * screen can pass its own context; no Fragment reference is stored.
 */
fun pluralWithNumber(context: android.content.Context, pluralsResId: Int, n: Int): String {
    val formattedNumber = formatInt(n)
    val pluralText = com.myapp.adventure.utils.ruQuantityString(context, pluralsResId, n)
    val suffix = pluralText.removePrefix(n.toString()).trim()
    return "$formattedNumber $suffix"
}

/**
 * Phase 3.1c-iii-b: "N шагов (≈ X минут) · повторяемый" display for a
 * scene's step gate, used by the Compose scene item.
 */
fun formatStepsDisplay(
    context: android.content.Context,
    steps: Int,
    repeatable: Boolean = false,
): String {
    if (steps <= 0) return context.getString(R.string.editor_steps_display_unset)
    val minutes = ((steps + 99) / 100).coerceAtLeast(1)
    val timeText = when {
        minutes < 60 -> context.getString(R.string.editor_steps_time_minutes_fmt, minutes)
        else -> {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) {
                context.getString(R.string.editor_steps_time_hours_fmt, hours)
            } else {
                context.getString(R.string.editor_steps_time_hours_minutes_fmt, hours, mins)
            }
        }
    }
    val stepsText = pluralWithNumber(context, R.plurals.steps, steps)
    val mode = if (repeatable) {
        context.getString(R.string.editor_steps_display_repeatable_suffix)
    } else ""
    return context.getString(R.string.editor_steps_display_fmt, stepsText, timeText, mode)
}
