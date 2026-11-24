package com.myapp.adventure.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Ф4-infra (3b-5-fix): Russian-aware plurals.
 *
 * **Why.** All user-facing strings live in `values/strings.xml` and are
 * currently in Russian. On a device with a non-`ru` locale (e.g. `en`),
 * Android applies the **device locale's** plural rules to Russian
 * strings:
 *
 * ```
 * getQuantityString(R.plurals.steps, 4882, 4882)  // en device
 *   → "4882 шагов"  (rule `other`)
 * // expected on ru:
 *   → "4882 шага"   (rule `few`)
 * ```
 *
 * This is documented Android behaviour: plural rules come from the
 * **configuration**, not from the source `values/` directory. As long as
 * this project ships Russian strings in the default `values/` (i.e.
 * before Ф7.3 adds English defaults + `values-ru/`), any non-`ru` device
 * will get wrong plural forms for RU strings.
 *
 * **What this does.** Builds a `Context` whose locale is forced to `ru`
 * and returns its `Resources`. Callers use `getQuantityString` on this
 * `Resources` — that is, [ruQuantityString] — and get correct Russian
 * plural forms on **any** device.
 *
 * **Temporary.** Ф7.3 will add English defaults to `values/` and move
 * Russian to `values-ru/`. At that point:
 * - this file is **deleted**;
 * - all `ruQuantityString(context, id, n)` calls become
 *   `context.resources.getQuantityString(id, n, n)` (which then picks
 *   the right rules for the device locale automatically);
 * - `docs/i18n-policy.md` §Migration status is updated.
 *
 * Tracked as a follow-up in `docs/i18n-policy.md`.
 */
fun ruResources(context: Context): Resources {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale.forLanguageTag("ru"))
    return context.createConfigurationContext(config).resources
}

/**
 * Ф4-infra (3b-5-fix): [Resources.getQuantityString] with the Russian
 * locale forced via [ruResources].
 *
 * Same convention as [Resources.getQuantityString]: the number is passed
 * once as [quantity] (for the rule decision) and once as the format
 * argument (substituted into `%d`). On any device — `ru` or not — the
 * RU plural rules apply.
 *
 * **Does NOT format the number itself** — callers that want a
 * locale-grouped number ("4 882" / "4,882") format the number separately
 * (see `formatInt` in `EditorHelpers` and `formatSteps` in
 * `StatisticsScreen`) and prepend it to the plural suffix.
 * This separation lets each caller use its own number formatter while
 * sharing the plural logic.
 */
fun ruQuantityString(context: Context, pluralsResId: Int, quantity: Int): String =
    ruResources(context).getQuantityString(pluralsResId, quantity, quantity)