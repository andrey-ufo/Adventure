package com.myapp.adventure.ui.statistics

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
import com.myapp.adventure.ui.menu.MenuFragment
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Thin wrapper: the screen itself lives in [StatisticsScreen]. The fragment
 * owns navigation only.
 */
class StatisticsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatisticsScreen(
                        onNavigateToMenu = { navigateToMenu() },
                    )
                }
            }
        }
    }

    /**
     * Behaviour change vs the original code: the "Menu" button now pops the
     * back stack (i.e. Back) instead of pushing another MenuFragment onto it.
     * The original created a tangled back stack (Menu → Statistics → Menu),
     * so Back from Statistics went forward to the second Menu, then back
     * again, etc. Popping is the standard "return to previous screen" action.
     *
     * Fallback (empty back stack, e.g. Statistics opened directly): replace
     * with MenuFragment so the user is never stranded.
     */
    private fun navigateToMenu() {
        if (!parentFragmentManager.popBackStackImmediate()) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .commit()
        }
    }
}