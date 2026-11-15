package com.myapp.adventure.data

import androidx.annotation.StringRes

enum class IssueSeverity { ERROR, WARNING }

/**
 * One issue in the editor's "Проверить" (Validate) dialog.
 *
 * **Ф4-infra (3b-6e-1):** [title] was a plain `String` — Russian titles
 * were produced by `StoryValidator.titleFor(...)` (in `:utils`, no
 * `Context`) and by `AdventureEditorFragment.showValidationDialog`
 * inline. Now [titleRes] is a `@StringRes Int`; both producers return
 * / assign resource IDs, and the dialog resolves them with
 * `getString(...)`. Same separation as `SearchResult.detailRes`.
 *
 * **`description` stays `String`.** It carries engine-level messages
 * (`typeError.message`, `compileError.message`, `lintIssue.message`)
 * which are **English** by contract — they come from `:engine` and are
 * diagnostic, not localization surface. The caller adds no prefix; the
 * message already names the scene / flag / error.
 */
data class ValidationIssue(
    val severity: IssueSeverity,
    val sceneUid: String,
    @StringRes val titleRes: Int,
    val description: String
)