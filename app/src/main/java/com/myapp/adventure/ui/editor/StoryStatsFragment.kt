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
import com.myapp.adventure.data.StoryStats
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.StoryStatsService

/**
 * Phase 3.1c-iv-c: host for [StoryStatsScreen]. Stats are computed once
 * in [onCreate] — same source as the old `showStatsDialog`:
 * `StoryStatsService.calculate(viewModel.scenes, titleFallback = ...)`.
 *
 * Read-only; no writes to the ViewModel. Recomputed on rotation / return
 * (cheap relative to the scene count).
 */
class StoryStatsFragment : Fragment() {

    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var stats: StoryStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
        stats = StoryStatsService.calculate(
            viewModel.scenes,
            titleFallback = getString(R.string.label_without_title),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StoryStatsScreen(
                        stats = stats,
                        onBack = { parentFragmentManager.popBackStack() },
                    )
                }
            }
        }
    }
}