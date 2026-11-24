package com.myapp.adventure.data

import androidx.annotation.StringRes

enum class SearchFieldType(val icon: String) {
    SCENE_TITLE("📄"),
    SCENE_TEXT("📝"),
    CHOICE_TEXT("💬"),
    TAG_NAME("🏷")
}

/**
 * One search hit.
 *
 * **Ф4-infra (3b-6e-1):** [detail] was a plain `String` built inside
 * `SearchService` in Russian. That made `SearchService` (an `object`
 * without `Context`) a hidden source of user-facing strings and a hidden
 * blocker for `values-en/`. Now [detailRes] is a `@StringRes Int` and
 * the caller (`SearchRoute`) resolves it
 * with `getString(...)`.
 *
 * **Parametrized details.** One detail line is parametrized:
 * "Выбор #%d" (choice index). [detailArg] carries the `Int` argument;
 * for non-parametrized details it is `null`. The caller does:
 * ```
 * val text = if (result.detailArg != null)
 *     context.getString(result.detailRes, result.detailArg)
 * else
 *     context.getString(result.detailRes)
 * ```
 * If a future detail needs two or more args, upgrade [detailArg] to
 * `Array<Any>` — one call site, one field.
 */
data class SearchResult(
    val sceneUid: String,
    val sceneTitle: String,
    val fieldType: SearchFieldType,
    @StringRes val detailRes: Int,
    val detailArg: Int? = null,
    val contextBefore: String,
    val match: String,
    val contextAfter: String,
    val totalMatchesInField: Int,
    val charOffset: Int
)