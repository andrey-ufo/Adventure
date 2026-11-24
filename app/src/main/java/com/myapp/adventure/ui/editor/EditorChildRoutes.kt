package com.myapp.adventure.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.myapp.adventure.R
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.data.ValidationIssue
import com.myapp.adventure.utils.EditorDraftManager
import com.myapp.adventure.utils.StoryStatsService
import com.myapp.adventure.utils.StoryValidator

/**
 * Phase 4-c: Compose destinations that replace the old thin editor child
 * screens (EditMeta, Validation, Search, Stats). They share the
 * Activity-scoped [AdventureEditorViewModel] injected by `AppNavHost`.
 */

@Composable
fun EditMetaRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    EditMetaScreen(
        initialTitle = viewModel.title,
        initialAuthor = viewModel.author,
        initialDescription = viewModel.description,
        onSave = { title, author, description ->
            viewModel.title = title
            viewModel.author = author
            viewModel.description = description
            viewModel.commitSnapshot()
            viewModel.commitState()
            EditorDraftManager.saveDraft(
                context,
                viewModel.title,
                viewModel.author,
                viewModel.description,
                viewModel.variables,
                viewModel.templates,
                viewModel.initialStateTags,
                viewModel.scenes,
            )
            viewModel.setHasUnsavedChanges(false)
            viewModel.setSaveStatus(SaveStatus.SAVED)
            onBack()
        },
        onBack = onBack,
    )
}

@Composable
fun ValidationIssuesRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val typesNotDeclaredDescription =
        stringResource(R.string.validation_desc_types_not_declared)
    val issues = remember(viewModel, typesNotDeclaredDescription) {
        buildValidationIssues(viewModel, typesNotDeclaredDescription)
    }

    ValidationIssuesScreen(
        issues = issues,
        onBack = onBack,
        onIssueClick = { uid ->
            if (uid.isNotEmpty()) {
                viewModel.requestJumpToScene(uid)
            }
            onBack()
        },
    )
}

@Composable
fun SearchRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    // Snapshot once: the editor is not mutated while this screen is on top.
    val scenes = remember(viewModel) { viewModel.scenes.toList() }
    SearchScreen(
        scenes = scenes,
        onBack = onBack,
        onResultClick = { uid ->
            viewModel.requestJumpToScene(uid)
            onBack()
        },
    )
}

@Composable
fun StoryStatsRoute(
    viewModel: AdventureEditorViewModel,
    onBack: () -> Unit,
) {
    val titleFallback = stringResource(R.string.label_without_title)
    val stats = remember(viewModel, titleFallback) {
        StoryStatsService.calculate(viewModel.scenes, titleFallback = titleFallback)
    }
    StoryStatsScreen(stats = stats, onBack = onBack)
}

/**
 * Same pipeline as the old validation screen:
 *  1. `freezeAllPendingIds()` — pending auto-ids become concrete before IR;
 *  2. `buildIrAdventure()` — IR + condition parse errors;
 *  3. `StoryValidator.validate(ir, scenes)`;
 *  4. WARNING when `variables` is empty (fallback type checking).
 *
 * Errors are ordered before warnings. The localized description for the
 * "types not declared" warning is passed in from the composable
 * (`stringResource`), keeping `Context.getString` out of composition.
 */
private fun buildValidationIssues(
    viewModel: AdventureEditorViewModel,
    typesNotDeclaredDescription: String,
): List<ValidationIssue> {
    viewModel.freezeAllPendingIds()
    val (irAdventure, conditionParseErrors) = viewModel.buildIrAdventure()

    val all = mutableListOf<ValidationIssue>()
    for (err in conditionParseErrors) {
        all += ValidationIssue(
            severity = IssueSeverity.ERROR,
            sceneUid = "",
            titleRes = R.string.validation_title_condition_parse_error,
            description = err.message,
        )
    }
    all += StoryValidator.validate(irAdventure, viewModel.scenes)
    if (viewModel.variables.isEmpty()) {
        all += ValidationIssue(
            severity = IssueSeverity.WARNING,
            sceneUid = "",
            titleRes = R.string.validation_title_types_not_declared,
            description = typesNotDeclaredDescription,
        )
    }
    return all.filter { it.severity == IssueSeverity.ERROR } +
            all.filter { it.severity == IssueSeverity.WARNING }
}
