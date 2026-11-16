package com.myapp.adventure.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.myapp.adventure.R
import com.myapp.adventure.ui.LogViewerFragment
import com.myapp.adventure.ui.SettingsFragment
import com.myapp.adventure.ui.statistics.StatisticsFragment
import com.myapp.adventure.ui.editor.AdventureEditorFragment
import com.myapp.adventure.ui.list.AdventureListFragment
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Thin wrapper: the screen itself lives in [MenuScreen]. The fragment owns
 * navigation only: it maps [MenuDestination] to concrete fragments. In
 * Phase 4 the same `onNavigate` callback will be backed by
 * `NavController.navigate(route)` instead of FragmentTransaction.
 */
class MenuFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MenuScreen(
                        onNavigate = { destination -> navigateTo(destination) },
                    )
                }
            }
        }
    }

    private fun navigateTo(destination: MenuDestination) {
        val fragment = when (destination) {
            MenuDestination.Library -> AdventureListFragment()
            MenuDestination.Create -> AdventureEditorFragment()
            MenuDestination.Statistics -> StatisticsFragment()
            MenuDestination.Logs -> LogViewerFragment()
            MenuDestination.Settings -> SettingsFragment()
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}