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
 * Phase 3.1c-iv-d: host for [SearchScreen]. Snapshots `viewModel.scenes`
 * once in [onCreate] — the editor is not mutated while the search screen
 * is on top, so a snapshot is equivalent to live reads for this screen's
 * lifetime.
 *
 * On result click: `requestJumpToScene` + `popBackStack`. The editor's
 * `EditorScenesList` picks up `pendingJumpToSceneUid` and scrolls/highlights.
 */
class SearchFragment : Fragment() {

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
                    SearchScreen(
                        scenes = viewModel.scenes.toList(),
                        onBack = { parentFragmentManager.popBackStack() },
                        onResultClick = { uid ->
                            viewModel.requestJumpToScene(uid)
                            parentFragmentManager.popBackStack()
                        },
                    )
                }
            }
        }
    }
}