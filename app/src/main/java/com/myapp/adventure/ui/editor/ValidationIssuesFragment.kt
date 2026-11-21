package com.myapp.adventure.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.myapp.adventure.R
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.ValidationIssue
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.StoryValidator

/**
 * Phase 3.1c-iv-b: host for [ValidationIssuesScreen].
 *
 * Validation is computed **once** in [onCreate] — the same pipeline the
 * old `showValidationDialog` ran:
 *   1. `freezeAllPendingIds()` (pending auto-ids become concrete before IR);
 *   2. `buildIrAdventure()` → IR + condition parse errors;
 *   3. `StoryValidator.validate(ir, scenes)`;
 *   4. WARNING when `variables` is empty (fallback type checking).
 *
 * Errors are ordered before warnings. Tap on an issue with a non-empty
 * `sceneUid` → `requestJumpToScene` + `popBackStack`; the editor's
 * `EditorScenesList` picks up `pendingJumpToSceneUid` and scrolls.
 *
 * Recomputed on every `onCreate` (rotation, return from child). Cost is
 * proportional to scene count; acceptable for pre-alpha (validate is
 * cheap relative to IO).
 */
class ValidationIssuesFragment : Fragment() {

    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var issues: List<ValidationIssue>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
        issues = buildIssues()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ValidationIssuesScreen(
                        issues = issues,
                        onBack = { parentFragmentManager.popBackStack() },
                        onIssueClick = { uid ->
                            if (uid.isNotEmpty()) {
                                viewModel.requestJumpToScene(uid)
                            }
                            parentFragmentManager.popBackStack()
                        },
                    )
                }
            }
        }
    }

    private fun buildIssues(): List<ValidationIssue> {
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
                description = getString(R.string.validation_desc_types_not_declared),
            )
        }
        return all.filter { it.severity == IssueSeverity.ERROR } +
                all.filter { it.severity == IssueSeverity.WARNING }
    }
}