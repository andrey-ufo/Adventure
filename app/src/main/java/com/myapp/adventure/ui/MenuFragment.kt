package com.myapp.adventure.ui

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentMenuBinding
import com.myapp.adventure.ui.editor.AdventureEditorFragment
import com.myapp.adventure.ui.list.AdventureListFragment
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importAdventure(uri)
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

        // Применяем выбранный шрифт ко всем элементам меню
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

    private fun importAdventure(sourceUri: Uri) {
        try {
            val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
            val folderUriString = prefs.getString("adventure_folder_uri", null)
            if (folderUriString == null) {
                Toast.makeText(requireContext(), "Папка не выбрана", Toast.LENGTH_SHORT).show()
                return
            }
            val folderUri = Uri.parse(folderUriString)

            val sourceName = requireContext().contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "imported_adventure.json"
                } else "imported_adventure.json"
            } ?: "imported_adventure.json"

            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/Adventures"
            )
            val destUri = DocumentsContract.createDocument(
                requireContext().contentResolver,
                adventuresDirUri,
                "application/json",
                sourceName
            )
            if (destUri != null) {
                requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                    requireContext().contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.log(requireContext(), "Приключение импортировано: $sourceName")
                Toast.makeText(requireContext(), "Приключение импортировано: $sourceName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Не удалось создать файл", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка импорта: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}