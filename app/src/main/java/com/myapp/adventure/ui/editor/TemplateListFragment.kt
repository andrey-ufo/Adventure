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
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-iii-b-3-ii-b-1: thin wrapper. The screen itself lives in
 * [TemplateListScreen]. The Fragment owns navigation only (back button);
 * everything else — list, edit dialog, delete confirmation — is Compose.
 *
 * `AdventureEditorViewModel` is Activity-scoped so the working copy of
 * the open edit dialog (in `viewModel.editingTemplate`) survives the
 * Fragment's own lifecycle.
 */
class TemplateListFragment : Fragment() {

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
                    TemplateListScreen(
                        viewModel = viewModel,
                        onBack = { parentFragmentManager.popBackStack() },
                    )
                }
            }
        }
    }
}