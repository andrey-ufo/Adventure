package com.myapp.adventure.ui.list

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.databinding.FragmentAdventureListBinding
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.ui.reader.AdventureReaderFragment
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

class AdventureListFragment : Fragment() {

    private var _binding: FragmentAdventureListBinding? = null
    private val binding get() = _binding!!

    private val gson = Gson()
    private val adventures = mutableListOf<Adventure>()
    private val adventureUris = mutableListOf<Uri>()
    private lateinit var adapter: AdventureAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем выбранный шрифт ко всем элементам фрагмента
        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        adapter = AdventureAdapter(adventures) { adventure, position ->
            val fileUri = adventureUris[position]
            val fragment = AdventureReaderFragment().apply {
                arguments = Bundle().apply {
                    putString("adventure_json", gson.toJson(adventure))
                    putString("adventure_file_uri", fileUri.toString())
                }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.rvAdventures.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdventures.adapter = adapter

        binding.btnMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        }

        loadAdventures()
    }

    private fun loadAdventures() {
        adventures.clear()
        adventureUris.clear()

        try {
            val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
            val folderUriString = prefs.getString("adventure_folder_uri", null) ?: run {
                Toast.makeText(requireContext(), "Папка не выбрана", Toast.LENGTH_SHORT).show()
                return
            }
            val folderUri = Uri.parse(folderUriString)

            val adventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/Adventures"
            )

            val contentResolver = requireContext().contentResolver
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                adventuresDirUri,
                DocumentsContract.getDocumentId(adventuresDirUri)
            )

            val cursor = contentResolver.query(childrenUri, null, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val documentId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    val mimeType = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )
                    if (mimeType == "application/json") {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(
                            adventuresDirUri,
                            documentId
                        )
                        val adventure = parseAdventureFromUri(fileUri)
                        if (adventure != null) {
                            adventures.add(adventure)
                            adventureUris.add(fileUri)
                        }
                    }
                }
            }
            adapter.notifyDataSetChanged()
            Logger.log(requireContext(), "Загружено приключений: ${adventures.size}")
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка загрузки списка: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка доступа к папке: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseAdventureFromUri(uri: Uri): Adventure? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()
            gson.fromJson(json, Adventure::class.java)
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка чтения файла: ${e.message}")
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}