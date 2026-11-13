package com.myapp.adventure.ui.list

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentAdventureListBinding
import com.myapp.adventure.engine.format.JsonToIr
import com.myapp.adventure.engine.ir.Adventure
import com.myapp.adventure.engine.parse.ParseResult
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.ui.reader.AdventureReaderFragment
import com.myapp.adventure.utils.AdventureImportHelper
import com.myapp.adventure.utils.FontManager
import com.myapp.adventure.utils.Logger

/**
 * Library list of adventures. Reads each adventure file via
 * [JsonToIr.parse] and holds `:engine/ir/Adventure` (not the legacy
 * `:app/data/Adventure` POJO). See roadmap "Data path cleanup" b1.
 *
 * **Why the reader is passed only `adventure_file_uri`, never
 * `adventure_json`.** The reader (`AdventureReaderFragment`) already
 * prefers `adventure_file_uri` when present (Ф1.7.1a-fix) and reads
 * the file itself — that path guarantees full IR: `is_ending`,
 * `gate`, `gate_repeatable`, `version`, `start_scene` all survive.
 * Passing `adventure_json` from this fragment would mean serialising
 * `:engine/ir/Adventure` either via Gson (which does not honour
 * `@SerialName`, so the resulting JSON would not be an adventure
 * file) or via `Adventure.serializer()` (which emits the IR shape,
 * not the adventure shape). Both are dead ends. Only the URI path
 * works, and it is already what the reader uses.
 */
class AdventureListFragment : Fragment() {

    private var _binding: FragmentAdventureListBinding? = null
    private val binding get() = _binding!!

    private val adventures = mutableListOf<Adventure>()
    private val adventureUris = mutableListOf<Uri>()
    private lateinit var adapter: AdventureAdapter

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            AdventureImportHelper.importAdventure(requireContext(), uri)
            loadAdventures()
        }
    }

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

        val fontName = FontManager.getSelectedFont(requireContext())
        FontManager.applyFontToViewTree(view, fontName, requireContext())

        adapter = AdventureAdapter(
            adventures = adventures,
            onItemClick = { _, position ->
                val fileUri = adventureUris[position]
                val fragment = AdventureReaderFragment().apply {
                    arguments = Bundle().apply {
                        // Only the URI: the reader reads the file itself,
                        // which is the lossless path. See class KDoc.
                        putString("adventure_file_uri", fileUri.toString())
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            },
            onLoadClick = {
                importFileLauncher.launch(arrayOf("application/json"))
            }
        )

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
                adapter.notifyDataSetChanged()
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

    /**
     * Parses one adventure file via [JsonToIr] and returns the IR.
     *
     * Files that fail to parse are **silently skipped** (logged, not
     * shown in the list). This matches the previous behaviour: an
     * unparseable file was just not added to the list. The reader
     * still offers a proper error dialog when the user opens a file
     * that parses but fails the analyzer's ERROR check (Ф1.7.1a).
     * Here we just do not clutter the list with files we cannot even
     * display (title / author / description are read from the parsed
     * `Adventure`).
     */
    private fun parseAdventureFromUri(uri: Uri): Adventure? {
        return try {
            val json = requireContext().contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return null
            when (val result = JsonToIr.parse(json)) {
                is ParseResult.Success -> result.value
                is ParseResult.Failure -> {
                    // Ф2.3: ParseResult.Failure may carry parse errors OR
                    // type errors (exactly one of the two — see
                    // ParseResult.Failure KDoc). Pick whichever is
                    // non-empty; without this, a type-error-only file would
                    // log "Skip '...': null", which is useless.
                    val reason = result.errors.firstOrNull()?.message
                        ?: result.typeErrors.firstOrNull()?.message
                        ?: "unknown error"
                    Logger.log(
                        requireContext(),
                        "Skip '${uri.lastPathSegment}': $reason",
                    )
                    null
                }
            }
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