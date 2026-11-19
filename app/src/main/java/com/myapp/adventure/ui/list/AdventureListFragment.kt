package com.myapp.adventure.ui.list

import android.net.Uri
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
import com.myapp.adventure.ui.reader.AdventureReaderFragment
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Thin wrapper: the screen itself lives in [AdventureListScreen]. The
 * fragment owns navigation only:
 *  - Menu button → pop back to Menu, or replace if back stack is empty;
 *  - Adventure row click → open AdventureReaderFragment with the file URI.
 *
 * The reader receives only `adventure_file_uri` — it reads the file itself,
 * which is the lossless path (see the comment that lived in the original
 * Fragment class KDoc).
 */
class AdventureListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdventureListScreen(
                        onNavigateToMenu = { navigateToMenu() },
                        onOpenAdventure = { uri -> openReader(uri) },
                    )
                }
            }
        }
    }

    /**
     * The original code did `replace(...).addToBackStack()`, which created
     * a tangled stack: Menu → Library → Menu → Back → Library → Back → Menu…
     * Popping the back stack (equivalent to the system Back button) is the
     * correct behaviour. Fallback (empty stack, e.g. Library opened directly)
     * replaces with Menu so the user is never stranded.
     */
    private fun navigateToMenu() {
        if (!parentFragmentManager.popBackStackImmediate()) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .commit()
        }
    }

    private fun openReader(uri: Uri) {
        val fragment = AdventureReaderFragment().apply {
            arguments = Bundle().apply {
                putString("adventure_file_uri", uri.toString())
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}