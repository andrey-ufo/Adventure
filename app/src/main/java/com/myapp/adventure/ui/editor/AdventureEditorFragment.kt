package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.databinding.FragmentAdventureEditorBinding
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.ui.menu.MenuFragment
import com.myapp.adventure.ui.reader.AdventureReaderFragment
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.EditorDraftManager
import com.myapp.adventure.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdventureEditorFragment : Fragment() {

    private var _binding: FragmentAdventureEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdventureEditorViewModel

    private val debounceHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveDraftImmediate() }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdventureEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWindowInsetsToRoot(view)

        binding.composeToolbar.setContent {
            AdventureTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                EditorToolbar(
                    state = state,
                    onMenuClick = {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, MenuFragment())
                            .addToBackStack(null)
                            .commit()
                    },
                    onToggleActions = { viewModel.toggleMenuExpanded() },
                    onUndo = { performUndo() },
                    onRedo = { performRedo() },
                    onAddScene = {
                        viewModel.addScene()
                        viewModel.commitSnapshot()
                        markDirty()
                    },
                    onSave = { saveAdventure() },
                    onLoad = { openFileLauncher.launch(arrayOf("application/json")) },
                    onEditMeta = { openEditMetaScreen() },
                    onValidate = { openValidationIssuesScreen() },
                    onSearch = { openSearchScreen() },
                    onStats = { openStoryStatsScreen() },
                    onTemplates = {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, TemplateListFragment())
                            .addToBackStack(null)
                            .commit()
                    },
                )
            }
        }

        val dirtyCallback: (Boolean) -> Unit = { structural ->
            if (structural) viewModel.commitSnapshot()
            markDirty()
        }

        binding.composeBody.setContent {
            AdventureTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EditorScenesList(
                        viewModel = viewModel,
                        onDirty = dirtyCallback,
                        onPreviewScene = { uid -> openPreview(uid) },
                    )
                }
            }
        }

        viewModel.commitState()

        maybeRestoreDraft()

        if (!viewModel.isHistoryInitialized()) {
            viewModel.initHistory()
        }
    }

    private fun applyWindowInsetsToRoot(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val i = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
    }

    // ---------- Редактирование названия (отдельный экран) ----------

    private fun openEditMetaScreen() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditMetaFragment())
            .addToBackStack(null)
            .commit()
    }

    // ---------- Undo / Redo ----------

    private fun performUndo() {
        debounceHandler.removeCallbacks(saveRunnable)
        if (viewModel.uiState.value.hasUnsavedChanges) viewModel.commitSnapshot()
        if (!viewModel.undo()) return
        saveDraftImmediate()
        viewModel.commitState()
    }

    private fun performRedo() {
        debounceHandler.removeCallbacks(saveRunnable)
        if (!viewModel.redo()) return
        saveDraftImmediate()
        viewModel.commitState()
    }

    // ---------- Debounce-автосохранение ----------

    private fun markDirty() {
        if (_binding == null) return
        viewModel.setHasUnsavedChanges(true)
        viewModel.setSaveStatus(SaveStatus.EDITING)
        viewModel.commitState()
        debounceHandler.removeCallbacks(saveRunnable)
        debounceHandler.postDelayed(saveRunnable, DEBOUNCE_MS)
    }

    private fun saveDraftImmediate() {
        if (_binding == null) return
        debounceHandler.removeCallbacks(saveRunnable)
        viewModel.setSaveStatus(SaveStatus.SAVING)

        viewModel.commitSnapshot()
        EditorDraftManager.saveDraft(
            requireContext(),
            viewModel.title,
            viewModel.author,
            viewModel.description,
            viewModel.variables,
            viewModel.templates,
            viewModel.initialStateTags,
            viewModel.scenes
        )
        viewModel.setHasUnsavedChanges(false)
        viewModel.setSaveStatus(SaveStatus.SAVED)
        Logger.log(requireContext(), "Черновик автосохранён, сцен: ${viewModel.scenes.size}")
    }

    // ---------- Восстановление черновика ----------

    private fun maybeRestoreDraft() {
        if (viewModel.scenes.isNotEmpty() ||
            viewModel.title.isNotEmpty() ||
            viewModel.author.isNotEmpty() ||
            viewModel.description.isNotEmpty() ||
            viewModel.initialStateTags.isNotEmpty()
        ) return

        val draft = EditorDraftManager.loadDraft(requireContext()) ?: return
        if (draft.scenes.isEmpty() && draft.title.isEmpty() && draft.initialStateTags.isEmpty()) {
            EditorDraftManager.clearDraft(requireContext())
            return
        }

        val dateFormat = SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault())
        val draftDateStr = dateFormat.format(Date(draft.savedAt))

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_dialog_restore_draft_title)
            .setMessage(
                getString(
                    R.string.editor_draft_restore_message_fmt,
                    draftDateStr,
                    draft.scenes.size
                )
            )
            .setPositiveButton(R.string.dialog_restore) { _, _ ->
                viewModel.title = draft.title
                viewModel.author = draft.author
                viewModel.description = draft.description
                viewModel.variables.clear()
                viewModel.variables.addAll(draft.variables.map { it.copy() })
                viewModel.templates.clear()
                viewModel.templates.addAll(draft.templates.map { it.deepCopy() })
                viewModel.initialStateTags.clear()
                viewModel.initialStateTags.addAll(draft.initialStateTags.map { it.copy() })
                viewModel.scenes.clear()
                viewModel.scenes.addAll(draft.scenes.map { it.deepCopy() })

                viewModel.initHistory()
                viewModel.collapseAllScenes()
                viewModel.collapseAllChoices()
                viewModel.commitState()

                viewModel.setHasUnsavedChanges(false)
                viewModel.setSaveStatus(SaveStatus.SAVED)
                Logger.log(requireContext(), "Черновик восстановлен, сцен: ${draft.scenes.size}")
            }
            .setNegativeButton(R.string.dialog_delete) { _, _ ->
                EditorDraftManager.clearDraft(requireContext())
            }
            .show()
    }

    // ---------- Загрузка из файла ----------

    private fun loadFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()
            val adventure = Gson().fromJson(json, Adventure::class.java)

            debounceHandler.removeCallbacks(saveRunnable)
            EditorDraftManager.clearDraft(requireContext())
            viewModel.setHasUnsavedChanges(false)

            viewModel.title = adventure.title
            viewModel.author = adventure.author
            viewModel.description = adventure.description
            viewModel.scenes.clear()

            viewModel.variables.clear()
            adventure.variables?.forEach { (name, dto) ->
                viewModel.variables.add(viewModel.editorVarDeclFromDto(name, dto))
            }

            viewModel.templates.clear()
            adventure.choiceTemplates?.forEach { dto ->
                viewModel.templates.add(viewModel.editorTemplateFromDto(dto))
            }
            if (viewModel.variables.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.editor_dialog_types_missing_title)
                    .setMessage(R.string.editor_dialog_types_missing_message)
                    .setPositiveButton(R.string.dialog_declare) { _, _ ->
                        viewModel.updateVariablesExpanded(true)
                    }
                    .setNegativeButton(R.string.dialog_later, null)
                    .show()
            }

            val tagDefs = adventure.tags.orEmpty().associateBy { it.name }

            viewModel.initialStateTags.clear()
            val init = adventure.initialState
            if (init != null) {
                init.flags?.forEach { flag ->
                    val def = tagDefs[flag]
                    viewModel.initialStateTags.add(
                        EditorTag(
                            name = flag,
                            value = 0,
                            isStatModifier = false,
                            category = def?.category ?: TagCategory.JOURNAL,
                            displayName = def?.displayName ?: flag,
                            visible = def?.visible ?: false
                        )
                    )
                }
                init.stats?.forEach { (name, value) ->
                    val def = tagDefs[name]
                    val cat = def?.category ?: TagCategory.JOURNAL
                    viewModel.initialStateTags.add(
                        EditorTag(
                            name = name,
                            value = value,
                            isStatModifier = true,
                            category = cat,
                            displayName = def?.displayName ?: name,
                            visible = def?.visible ?: false
                        )
                    )
                }
            }

            val allChapters = adventure.chapters.orEmpty()
            for ((chapterIndex, chapter) in allChapters.withIndex()) {
                val scenes = chapter.scenes.orEmpty()
                val v1GateForLastScene =
                    allChapters.getOrNull(chapterIndex + 1)?.requiredSteps ?: 0
                for ((sceneIndex, scene) in scenes.withIndex()) {
                    val isLastInChapter = sceneIndex == scenes.lastIndex
                    val v5Gate = scene.gate ?: 0
                    val effectiveGate =
                        if (v5Gate > 0) v5Gate
                        else if (isLastInChapter) v1GateForLastScene
                        else 0
                    val editorScene = EditorScene(
                        id = scene.id,
                        idIsAuto = false,
                        title = scene.id,
                        text = scene.text,
                        isChapterEnd = isLastInChapter,
                        gateRequiredSteps = effectiveGate,
                        gateRepeatable = scene.gateRepeatable ?: false,
                        isEnding = scene.isEnding ?: false,
                        isMerge = scene.merge ?: false,
                        mergeSources = scene.mergeSources ?: emptyList(),
                        tags = extractTags(scene, tagDefs),
                        choices = scene.choices?.map { choice ->
                            EditorChoice(
                                text = choice.text,
                                nextSceneId = choice.nextScene,
                                conditionTag = choice.condition ?: "",
                                visibilityConditionTag = choice.visibilityCondition ?: "",
                                tags = extractChoiceTags(choice, tagDefs),
                                templateUid = choice.templateRef?.templateId,
                                templateArgs = choice.templateRef?.args ?: emptyMap(),
                            )
                        }?.toMutableList() ?: mutableListOf()
                    )
                    viewModel.scenes.add(editorScene)
                }
            }

            val deathDef = adventure.deathScene
            if (deathDef != null) {
                val existingScene = viewModel.scenes.find { it.id == deathDef.id }
                if (existingScene != null) {
                    existingScene.isDeathScene = true
                    existingScene.deathAllowStepBack = deathDef.allowStepBack
                    existingScene.deathAllowChapterRestart = deathDef.allowChapterRestart
                    existingScene.deathAllowAdventureRestart = deathDef.allowAdventureRestart
                } else {
                    val deathSceneEditor = EditorScene(
                        id = deathDef.id,
                        idIsAuto = false,
                        title = getString(R.string.item_editor_scene_death_scene),
                        text = deathDef.text,
                        isDeathScene = true,
                        deathAllowStepBack = deathDef.allowStepBack,
                        deathAllowChapterRestart = deathDef.allowChapterRestart,
                        deathAllowAdventureRestart = deathDef.allowAdventureRestart
                    )
                    viewModel.scenes.add(deathSceneEditor)
                }
            }

            viewModel.initHistory()
            viewModel.collapseAllScenes()
            viewModel.collapseAllChoices()
            viewModel.commitState()

            viewModel.setSaveStatus(SaveStatus.LOADED)

            Logger.log(requireContext(), "Приключение загружено из файла: ${uri.lastPathSegment}")
            Toast.makeText(requireContext(), R.string.editor_toast_loaded, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка загрузки приключения: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.editor_toast_load_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun extractTags(
        scene: com.myapp.adventure.data.Scene,
        tagDefs: Map<String, TagDefinition>
    ): MutableList<EditorTag> = buildTagList(scene.setFlags, scene.removeFlags, scene.modifyStats, tagDefs)

    private fun extractChoiceTags(
        choice: com.myapp.adventure.data.Choice,
        tagDefs: Map<String, TagDefinition>
    ): MutableList<EditorTag> = buildTagList(choice.setFlags, choice.removeFlags, choice.modifyStats, tagDefs)

    private fun buildTagList(
        setFlags: List<String>?,
        removeFlags: List<String>?,
        modifyStats: Map<String, Int>?,
        tagDefs: Map<String, TagDefinition>
    ): MutableList<EditorTag> {
        val tags = mutableListOf<EditorTag>()
        for (flag in setFlags.orEmpty()) {
            val def = tagDefs[flag]
            val category = def?.category ?: TagCategory.JOURNAL
            val value = if (category == TagCategory.ITEM) 1 else 0
            val isStat = category == TagCategory.ITEM
            tags.add(EditorTag(flag, value, isStat, category, def?.displayName ?: flag, def?.visible ?: false))
        }
        for (flag in removeFlags.orEmpty()) {
            val def = tagDefs[flag]
            tags.add(EditorTag("!$flag", 0, false, def?.category ?: TagCategory.JOURNAL, def?.displayName ?: flag, def?.visible ?: false))
        }
        for ((stat, value) in modifyStats.orEmpty()) {
            val def = tagDefs[stat]
            tags.add(EditorTag(stat, value, true, def?.category ?: TagCategory.JOURNAL, def?.displayName ?: stat, def?.visible ?: false))
        }
        return tags
    }

    // ---------- Сохранение в файл ----------

    private fun saveAdventure() {
        if (viewModel.scenes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.editor_toast_add_scene_first, Toast.LENGTH_SHORT).show()
            return
        }
        debounceHandler.removeCallbacks(saveRunnable)
        viewModel.freezeAllPendingIds()

        val adventure = viewModel.buildAdventure()
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventure)

        val prefs = requireContext().getSharedPreferences("adventure_prefs", 0)
        val folderUriString = prefs.getString("adventure_folder_uri", null)
        if (folderUriString == null) {
            Toast.makeText(requireContext(), R.string.editor_toast_folder_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val folderUri = Uri.parse(folderUriString)

        try {
            val newAdventuresDirUri = DocumentsContract.buildDocumentUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri) + "/NewAdventures"
            )
            val fileName = viewModel.transliterate(adventure.title.ifBlank { "adventure" }) + ".json"
            val existingFileUri = findFileInFolder(newAdventuresDirUri, fileName)

            var saved = false
            if (existingFileUri != null) {
                requireContext().contentResolver.openOutputStream(existingFileUri, "wt")?.use {
                    it.write(json.toByteArray())
                }
                Toast.makeText(requireContext(), getString(R.string.editor_toast_updated, fileName), Toast.LENGTH_LONG).show()
                saved = true
            } else {
                val newUri = DocumentsContract.createDocument(
                    requireContext().contentResolver, newAdventuresDirUri, "application/json", fileName
                )
                if (newUri != null) {
                    requireContext().contentResolver.openOutputStream(newUri)?.use {
                        it.write(json.toByteArray())
                    }
                    Toast.makeText(requireContext(), getString(R.string.editor_toast_saved, fileName), Toast.LENGTH_LONG).show()
                    saved = true
                }
            }

            if (saved) {
                EditorDraftManager.clearDraft(requireContext())
                viewModel.setHasUnsavedChanges(false)
                viewModel.setSaveStatus(SaveStatus.SAVED_TO_FILE)
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка сохранения: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.editor_toast_save_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun findFileInFolder(folderUri: Uri, fileName: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri, DocumentsContract.getDocumentId(folderUri)
        )
        requireContext().contentResolver.query(childrenUri, null, null, null, null)?.use {
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

    // ---------- Валидатор (отдельный экран) ----------

    private fun openValidationIssuesScreen() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ValidationIssuesFragment())
            .addToBackStack(null)
            .commit()
    }

    // ---------- Поиск (отдельный экран) ----------

    private fun openSearchScreen() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SearchFragment())
            .addToBackStack(null)
            .commit()
    }

    // ---------- Статистика (отдельный экран) ----------

    private fun openStoryStatsScreen() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, StoryStatsFragment())
            .addToBackStack(null)
            .commit()
    }

    // ---------- Прыжок к сцене (упрощённо) ----------

    private fun jumpToScene(uid: String) {
        if (_binding == null) return
        viewModel.requestJumpToScene(uid)
    }

    private fun openPreview(sceneUid: String) {
        val scene = viewModel.scenes.find { it.uid == sceneUid } ?: return
        if (scene.title.isBlank()) {
            Toast.makeText(requireContext(), R.string.editor_toast_title_first, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.freezeAllPendingIds()
        if (scene.id.isBlank()) {
            Toast.makeText(requireContext(), R.string.editor_toast_id_generate_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val adventure = viewModel.buildAdventure()
        val gson = Gson()
        val chapterIndex = adventure.chapters?.indexOfFirst { ch ->
            ch.scenes?.any { it.id == scene.id } == true
        } ?: -1
        if (chapterIndex < 0) {
            Toast.makeText(requireContext(), R.string.editor_toast_scene_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val fragment = AdventureReaderFragment().apply {
            arguments = Bundle().apply {
                putString("adventure_json", gson.toJson(adventure))
                putString("preview_scene_id", scene.id)
                putInt("preview_chapter_index", chapterIndex)
                putBoolean("preview_mode", true)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        debounceHandler.removeCallbacks(saveRunnable)

        if (_binding != null && viewModel.uiState.value.hasUnsavedChanges && viewModel.scenes.isNotEmpty()) {
            EditorDraftManager.saveDraft(
                requireContext(),
                viewModel.title,
                viewModel.author,
                viewModel.description,
                viewModel.variables,
                viewModel.templates,
                viewModel.initialStateTags,
                viewModel.scenes
            )
        }
        _binding = null
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
    }
}