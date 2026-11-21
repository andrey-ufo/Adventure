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
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.EditorDraftManager

/**
 * Phase 3.1c-iv-a: host for [EditMetaScreen]. Activity-scoped
 * `AdventureEditorViewModel` carries the state across the navigation.
 *
 * Save path (on `Применить`):
 *  1. write the three fields to the ViewModel;
 *  2. `commitSnapshot()` — undo returns to the pre-edit state;
 *  3. `commitState()` — pushes to `uiState` so the editor's toolbar
 *     title / state refresh;
 *  4. `EditorDraftManager.saveDraft(...)` — immediate persistence (no
 *     debounce: the user is leaving the screen);
 *  5. `popBackStack()`.
 *
 * Back path (on `Назад` or system back): no writes, just `popBackStack()`.
 */
class EditMetaFragment : Fragment() {

    private lateinit var viewModel: AdventureEditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
                            requireContext(),
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
                        parentFragmentManager.popBackStack()
                    },
                        onBack = { parentFragmentManager.popBackStack() },
                    )
                }
            }
        }
    }
}