package com.myapp.adventure.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentMenuBinding
import com.myapp.adventure.ui.editor.AdventureEditorFragment
import com.myapp.adventure.ui.list.AdventureListFragment
import com.myapp.adventure.utils.AdventureImportHelper
import com.myapp.adventure.utils.FontManager

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            AdventureImportHelper.importAdventure(requireContext(), uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        binding.tvLibrary.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdventureListFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.tvStatistics.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, StatisticsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.tvCreate.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdventureEditorFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.tvImport.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json"))
        }

        binding.tvLogs.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LogViewerFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.tvSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}