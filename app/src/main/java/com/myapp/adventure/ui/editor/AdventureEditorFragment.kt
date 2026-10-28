package com.myapp.adventure.ui.editor

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.databinding.FragmentAdventureEditorBinding
import com.myapp.adventure.databinding.ItemEditorSceneBinding
import com.myapp.adventure.ui.MenuFragment
import com.myapp.adventure.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

class AdventureEditorFragment : Fragment() {

    private var _binding: FragmentAdventureEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AdventureEditorViewModel

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loadFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            refreshScenesContainer()
        }

        binding.btnSave.setOnClickListener {
            saveAdventure()
        }

        binding.etTitle.setText(viewModel.title)
        binding.etAuthor.setText(viewModel.author)
        binding.etDescription.setText(viewModel.description)

        refreshScenesContainer()
    }

    private fun refreshScenesContainer() {
        val container = binding.scenesContainer
        container.removeAllViews()

        for ((index, scene) in viewModel.scenes.withIndex()) {
            val sceneBinding = ItemEditorSceneBinding.inflate(
                LayoutInflater.from(requireContext()), container, false
            )

            // Удаление сцены
            sceneBinding.btnDeleteScene.setOnClickListener {
                viewModel.removeScene(index)
                refreshScenesContainer()
            }

            // Название сцены
            sceneBinding.etSceneTitle.setText(scene.title)
            sceneBinding.etSceneTitle.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val newTitle = s.toString()
                    scene.title = newTitle
                    if (scene.id.isEmpty() || scene.id.startsWith("scene_")) {
                        scene.id = viewModel.generateSceneId(newTitle)
                        sceneBinding.tvSceneId.text = "ID: ${scene.id}"
                    }
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })

            sceneBinding.tvSceneId.text = "ID: ${scene.id.ifEmpty { "(будет сгенерирован)" }}"

            // Текст сцены
            sceneBinding.etSceneText.setText(scene.text)
            sceneBinding.etSceneText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    scene.text = s.toString()
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })

            // Метки
            val tagsContainer = sceneBinding.containerTags
            tagsContainer.removeAllViews()
            for ((tagIdx, tag) in scene.tags.withIndex()) {
                val tagView = layoutInflater.inflate(R.layout.item_editor_tag, tagsContainer, false)
                val etTagName = tagView.findViewById<EditText>(R.id.et_tag_name)
                val etTagValue = tagView.findViewById<EditText>(R.id.et_tag_value)
                val btnDeleteTag = tagView.findViewById<View>(R.id.btn_delete_tag)

                etTagName.setText(tag.name)
                etTagValue.setText(tag.value.toString())

                btnDeleteTag.setOnClickListener {
                    viewModel.removeTag(index, tagIdx)
                    refreshScenesContainer()
                }

                etTagName.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { tag.name = s.toString() }
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                })
                etTagValue.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        tag.value = s?.toString()?.toIntOrNull() ?: 0
                    }
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                })
                tagsContainer.addView(tagView)
            }

            sceneBinding.btnAddTag.setOnClickListener {
                viewModel.addTag(index)
                refreshScenesContainer()
            }

            // Чекбокс конца главы
            sceneBinding.cbChapterEnd.isChecked = scene.isChapterEnd
            sceneBinding.cbChapterEnd.setOnCheckedChangeListener { _, isChecked ->
                scene.isChapterEnd = isChecked
                sceneBinding.layoutSteps.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            sceneBinding.layoutSteps.visibility = if (scene.isChapterEnd) View.VISIBLE else View.GONE
            sceneBinding.etSteps.setText(scene.requiredSteps.toString())
            sceneBinding.etSteps.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    scene.requiredSteps = s?.toString()?.toIntOrNull() ?: 0
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })

            // Варианты действий
            val choicesContainer = sceneBinding.containerChoices
            choicesContainer.removeAllViews()
            for ((choiceIdx, choice) in scene.choices.withIndex()) {
                val choiceView = layoutInflater.inflate(R.layout.item_editor_choice, choicesContainer, false)
                val etChoiceText = choiceView.findViewById<EditText>(R.id.et_choice_text)
                val spinnerScene = choiceView.findViewById<Spinner>(R.id.spinner_next_scene)
                val etCondition = choiceView.findViewById<EditText>(R.id.et_choice_condition)
                val btnDeleteChoice = choiceView.findViewById<View>(R.id.btn_delete_choice)

                etChoiceText.setText(choice.text)
                etCondition.setText(choice.conditionTag)

                btnDeleteChoice.setOnClickListener {
                    viewModel.removeChoice(index, choiceIdx)
                    refreshScenesContainer()
                }

                val options = viewModel.getSceneOptions(index)
                val displayList = mutableListOf("(выберите сцену)")
                displayList.addAll(options.map { it.first })
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerScene.adapter = adapter

                val currentId = choice.nextSceneId
                val selectedPos = options.indexOfFirst { it.second == currentId } + 1
                if (selectedPos in 1..options.size) {
                    spinnerScene.setSelection(selectedPos)
                }

                spinnerScene.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position == 0) choice.nextSceneId = ""
                        else choice.nextSceneId = options.getOrNull(position - 1)?.second ?: ""
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

                etChoiceText.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { choice.text = s.toString() }
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                })
                etCondition.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { choice.conditionTag = s.toString() }
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                })

                choicesContainer.addView(choiceView)
            }

            sceneBinding.btnAddChoice.setOnClickListener {
                viewModel.addChoice(index)
                refreshScenesContainer()
            }

            container.addView(sceneBinding.root)
        }
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
            refreshScenesContainer()
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
                requireContext().contentResolver.openOutputStream(existingFileUri, "wt")?.use { outputStream ->
                    outputStream.write(json.toByteArray())
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
                    requireContext().contentResolver.openOutputStream(newUri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
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
        val cursor = contentResolver.query(childrenUri, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (displayName == fileName) {
                    val docId = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
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