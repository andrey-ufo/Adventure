package com.myapp.adventure.ui.editor

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.databinding.FragmentAdventureEditorBinding
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

class AdventureEditorFragment : Fragment() {

    private var _binding: FragmentAdventureEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var adapter: EditorSceneAdapter

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
        adapter = EditorSceneAdapter(viewModel)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvScenes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScenes.adapter = adapter

        binding.btnMenu.setOnClickListener {
            saveAdventure()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnLoad.setOnClickListener {
            openFileLauncher.launch(arrayOf("application/json"))
        }

        binding.btnAddScene.setOnClickListener {
            viewModel.addScene()
            adapter.submitList()
        }

        binding.btnSave.setOnClickListener { saveAdventure() }

        binding.etTitle.setText(viewModel.title)
        binding.etAuthor.setText(viewModel.author)
        binding.etDescription.setText(viewModel.description)

        adapter.submitList()
    }

    private fun loadFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()
            val adventure = Gson().fromJson(json, Adventure::class.java)

            viewModel.title = adventure.title
            viewModel.author = adventure.author
            viewModel.description = adventure.description
            viewModel.scenes.clear()

            for (chapter in adventure.chapters.orEmpty()) {
                for (scene in chapter.scenes.orEmpty()) {
                    val editorScene = EditorScene(
                        id = scene.id,
                        title = scene.id,
                        text = scene.text,
                        isChapterEnd = chapter.requiredSteps > 0,
                        requiredSteps = chapter.requiredSteps,
                        tags = extractTags(scene),
                        choices = scene.choices?.map { choice ->
                            EditorChoice(
                                text = choice.text,
                                nextSceneId = choice.nextScene,
                                conditionTag = choice.condition ?: ""
                            )
                        }?.toMutableList() ?: mutableListOf()
                    )
                    viewModel.scenes.add(editorScene)
                }
            }

            binding.etTitle.setText(viewModel.title)
            binding.etAuthor.setText(viewModel.author)
            binding.etDescription.setText(viewModel.description)
            adapter.submitList()
            Logger.log(requireContext(), "Приключение загружено из файла: ${uri.lastPathSegment}")
            Toast.makeText(requireContext(), "Приключение загружено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка загрузки приключения: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractTags(scene: com.myapp.adventure.data.Scene): MutableList<EditorTag> {
        val tags = mutableListOf<EditorTag>()
        for (flag in scene.setFlags.orEmpty()) {
            tags.add(EditorTag(name = flag, value = 0, isStatModifier = false))
        }
        for ((stat, value) in scene.modifyStats.orEmpty()) {
            tags.add(EditorTag(name = stat, value = value, isStatModifier = true))
        }
        return tags
    }

    private fun saveAdventure() {
        viewModel.title = binding.etTitle.text.toString()
        viewModel.author = binding.etAuthor.text.toString()
        viewModel.description = binding.etDescription.text.toString()

        if (viewModel.scenes.isEmpty()) {
            Toast.makeText(requireContext(), "Добавьте хотя бы одну сцену", Toast.LENGTH_SHORT).show()
            return
        }

        val adventure = viewModel.buildAdventure()
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventure)

        val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
        val folderUriString = prefs.getString("adventure_folder_uri", null)
        if (folderUriString == null) {
            Toast.makeText(requireContext(), "Папка не выбрана", Toast.LENGTH_SHORT).show()
            return
        }
        val folderUri = Uri.parse(folderUriString)

        try {
            val newAdventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/NewAdventures"
            )
            val fileName = viewModel.transliterate(adventure.title) + ".json"
            val existingFileUri = findFileInFolder(newAdventuresDirUri, fileName)

            if (existingFileUri != null) {
                requireContext().contentResolver.openOutputStream(existingFileUri, "wt")?.use {
                    it.write(json.toByteArray())
                }
                Logger.log(requireContext(), "Приключение перезаписано: $fileName")
                Toast.makeText(requireContext(), "Приключение обновлено: $fileName", Toast.LENGTH_LONG).show()
            } else {
                val newUri = DocumentsContract.createDocument(
                    requireContext().contentResolver,
                    newAdventuresDirUri,
                    "application/json",
                    fileName
                )
                if (newUri != null) {
                    requireContext().contentResolver.openOutputStream(newUri)?.use {
                        it.write(json.toByteArray())
                    }
                    Logger.log(requireContext(), "Приключение сохранено: $fileName")
                    Toast.makeText(requireContext(), "Приключение сохранено: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Не удалось создать файл", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка сохранения: ${e.message}")
            Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findFileInFolder(folderUri: Uri, fileName: String): Uri? {
        val contentResolver = requireContext().contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getDocumentId(folderUri)
        )
        contentResolver.query(childrenUri, null, null, null, null)?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                if (displayName == fileName) {
                    val docId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                }
            }
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}