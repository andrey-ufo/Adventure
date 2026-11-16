package com.myapp.adventure.ui.setup

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
 * Thin wrapper: the screen itself lives in [SetupScreen]. The fragment owns
 * navigation only (FragmentTransaction until Phase 4 migrates to Nav Compose).
 */
class SetupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        onNavigateToList = {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, MenuFragment())
                                .commit()
                        },
                    )
                }
            }
        }
    }
}