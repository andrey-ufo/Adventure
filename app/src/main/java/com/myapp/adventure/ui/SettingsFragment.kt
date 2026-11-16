package com.myapp.adventure.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.myapp.adventure.ui.settings.SettingsScreen
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Thin wrapper: the screen itself lives in [SettingsScreen]. The fragment
 * only handles `recreate()` — which must be done at the Activity level
 * when the theme or font changes.
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onRecreateActivity = { requireActivity().recreate() },
                    )
                }
            }
        }
    }
}