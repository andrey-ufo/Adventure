package com.myapp.adventure.ui.editor

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.ChapterStats
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.data.ValidationIssue
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.databinding.FragmentAdventureEditorBinding
import com.myapp.adventure.ui.menu.MenuFragment
import com.myapp.adventure.ui.reader.AdventureReaderFragment
import com.myapp.adventure.utils.EditorDraftManager
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.SearchService
import com.myapp.adventure.utils.StoryStatsService
import com.myapp.adventure.utils.StoryValidator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdventureEditorFragment : Fragment() {

    private var _binding: FragmentAdventureEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdventureEditorViewModel
    private lateinit var adapter: EditorSceneAdapter
    private lateinit var initialStateAdapter: InitialStateAdapter
    private lateinit var variablesAdapter: VariablesAdapter

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var hasUnsavedChanges = false
    private var menuExpanded = false

    private val saveRunnable = Runnable { saveDraftImmediate() }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(AdventureEditorViewModel::class.java)

        val dirtyCallback: (Boolean) -> Unit = { structural ->
            if (structural) viewModel.commitSnapshot()
            markDirty()
        }

        adapter = EditorSceneAdapter(
            viewModel = viewModel,
            onDirty = dirtyCallback,
            onPreview = { uid -> openPreview(uid) }
        )
        initialStateAdapter = InitialStateAdapter(viewModel, dirtyCallback)
        variablesAdapter = VariablesAdapter(viewModel, dirtyCallback)
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

        binding.rvScenes.layoutManager = LinearLayoutManager(requireContext())

        // Ф2.7: VariablesAdapter идёт **первым** — «сначала объяви тип,
        // потом используй». InitialStateAdapter — вторым, сцены — третьими.
        // Сдвиг для scroll-операций (`jumpToScene`, `btnAddScene`) теперь
        // `+2` вместо `+1` (две header-секции сверху).
        val concatAdapter = ConcatAdapter(variablesAdapter, initialStateAdapter, adapter)
        binding.rvScenes.adapter = concatAdapter

        binding.rvScenes.setItemViewCacheSize(15)
        binding.rvScenes.itemAnimator = null

        binding.btnMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MenuFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.btnToggleMenu.setOnClickListener { toggleMenu() }

        binding.btnUndo.setOnClickListener { performUndo() }
        binding.btnRedo.setOnClickListener { performRedo() }
        binding.btnAddScene.setOnClickListener {
            viewModel.addScene()
            viewModel.commitSnapshot()
            markDirty()
            adapter.submitList()
            binding.rvScenes.post {
                if (_binding != null) {
                    // Ф2.7: +2 — VariablesAdapter + InitialStateAdapter
                    // занимают две header-позиции сверху; позиция новой
                    // сцены в ConcatAdapter = viewModel.scenes.size + 2 - 1?
                    // Нет: ConcatAdapter позиции: 0 = VariablesAdapter,
                    // 1 = InitialStateAdapter, 2..N+2 = scenes.
                    // Последняя сцена (index viewModel.scenes.size - 1 в
                    // EditorSceneAdapter) → 2 + (size - 1) = size + 1.
                    binding.rvScenes.scrollToPosition(viewModel.scenes.size + 1)
                }
            }
        }
        binding.btnSave.setOnClickListener { saveAdventure() }
        binding.btnLoad.setOnClickListener { openFileLauncher.launch(arrayOf("application/json")) }
        binding.btnEditMeta.setOnClickListener { openEditMetaDialog() }
        binding.btnValidate.setOnClickListener { showValidationDialog() }
        binding.btnSearch.setOnClickListener { showSearchDialog() }
        binding.btnStats.setOnClickListener { showStatsDialog() }
        binding.btnMap.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdventureMapFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.btnTemplates.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TemplateListFragment())
                .addToBackStack(null)
                .commit()
        }

        (binding.rvScenes.layoutManager as? LinearLayoutManager)
            ?.initialPrefetchItemCount = 4

        adapter.submitList()
        updateTitleField()
        updateUndoRedoButtons()

        maybeRestoreDraft()

        if (!viewModel.isHistoryInitialized()) {
            viewModel.initHistory()
            updateUndoRedoButtons()
        }

        // Если редактор открывается после возврата из карты
        val pending = viewModel.pendingJumpToSceneUid
        if (pending != null) {
            viewModel.pendingJumpToSceneUid = null
            binding.rvScenes.post { jumpToScene(pending) }
        }
    }

    override fun onResume() {
        super.onResume()
        val pending = viewModel.pendingJumpToSceneUid
        if (pending != null && _binding != null) {
            viewModel.pendingJumpToSceneUid = null
            jumpToScene(pending)
        }
    }

    private fun applyWindowInsetsToRoot(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, topInset, 0, 0)
            insets
        }
    }

    // ---------- Toolbar ----------

    private fun toggleMenu() {
        menuExpanded = !menuExpanded
        binding.menuActions.visibility = if (menuExpanded) View.VISIBLE else View.GONE
    }

    private fun updateTitleField() {
        if (_binding == null) return
        binding.tvAdventureTitle.text = viewModel.title.ifBlank { getString(R.string.editor_title_untitled) }
    }

    private fun updateUndoRedoButtons() {
        if (_binding == null) return
        val undoEnabled = viewModel.canUndo() || (hasUnsavedChanges && viewModel.isHistoryInitialized())
        val redoEnabled = viewModel.canRedo()
        binding.btnUndo.isEnabled = undoEnabled
        binding.btnUndo.alpha = if (undoEnabled) 1f else 0.4f
        binding.btnRedo.isEnabled = redoEnabled
        binding.btnRedo.alpha = if (redoEnabled) 1f else 0.4f
    }

    // ---------- Редактирование названия ----------

    private fun openEditMetaDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_meta, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_meta_title)
        val etAuthor = dialogView.findViewById<EditText>(R.id.et_meta_author)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_meta_description)

        etTitle.setText(viewModel.title)
        etAuthor.setText(viewModel.author)
        etDesc.setText(viewModel.description)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_dialog_edit_meta_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_apply) { _, _ ->
                viewModel.title = etTitle.text.toString().trim()
                viewModel.author = etAuthor.text.toString().trim()
                viewModel.description = etDesc.text.toString().trim()
                updateTitleField()
                markDirty()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------- Undo / Redo ----------

    private fun performUndo() {
        debounceHandler.removeCallbacks(saveRunnable)
        if (hasUnsavedChanges) viewModel.commitSnapshot()
        if (!viewModel.undo()) return
        saveDraftImmediate()
        refreshAfterHistoryChange()
    }

    private fun performRedo() {
        debounceHandler.removeCallbacks(saveRunnable)
        if (!viewModel.redo()) return
        saveDraftImmediate()
        refreshAfterHistoryChange()
    }

    private fun refreshAfterHistoryChange() {
        if (_binding == null) return
        adapter.submitList()
        initialStateAdapter.notifyDataSetChanged()
        variablesAdapter.notifyDataSetChanged()
        updateTitleField()
        updateUndoRedoButtons()
    }

    // ---------- Debounce-автосохранение ----------

    private fun markDirty() {
        if (_binding == null) return
        hasUnsavedChanges = true
        binding.tvSaveStatus.text = getString(R.string.editor_save_status_editing)
        binding.tvSaveStatus.setTextColor(Color.parseColor("#FFC107"))
        updateUndoRedoButtons()
        debounceHandler.removeCallbacks(saveRunnable)
        debounceHandler.postDelayed(saveRunnable, DEBOUNCE_MS)
    }

    private fun saveDraftImmediate() {
        if (_binding == null) return
        debounceHandler.removeCallbacks(saveRunnable)
        binding.tvSaveStatus.text = getString(R.string.editor_save_status_saving)
        binding.tvSaveStatus.setTextColor(Color.parseColor("#9E9E9E"))

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
        hasUnsavedChanges = false
        binding.tvSaveStatus.text = getString(R.string.editor_save_status_saved)
        binding.tvSaveStatus.setTextColor(Color.parseColor("#4CAF50"))
        updateUndoRedoButtons()
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

                adapter.submitList()
                initialStateAdapter.notifyDataSetChanged()
                variablesAdapter.notifyDataSetChanged()
                updateTitleField()
                updateUndoRedoButtons()

                hasUnsavedChanges = false
                binding.tvSaveStatus.text = getString(R.string.editor_save_status_saved)
                binding.tvSaveStatus.setTextColor(Color.parseColor("#4CAF50"))
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
            hasUnsavedChanges = false

            viewModel.title = adventure.title
            viewModel.author = adventure.author
            viewModel.description = adventure.description
            viewModel.scenes.clear()

            // Ф2.7: restore variable declarations from JSON.
            // `adventure.variables` is `Map<String, VariableDto>?`
            // (nullable; Gson omits the field when nothing is declared).
            viewModel.variables.clear()
            adventure.variables?.forEach { (name, dto) ->
                viewModel.variables.add(viewModel.editorVarDeclFromDto(name, dto))
            }

            // Ф4.3.1: restore choice templates from JSON.
            // `adventure.choiceTemplates` is `List<ChoiceTemplateDto>?`
            // (nullable; Gson omits the field when nothing is declared).
            // Round-trip: templates stored in JSON survive a re-save
            // (this fixes the data-loss bug).
            viewModel.templates.clear()
            adventure.choiceTemplates?.forEach { dto ->
                viewModel.templates.add(viewModel.editorTemplateFromDto(dto))
            }
            // Ф2.7 DoD: «при открытии приключения без объявлений —
            // предупреждение». Показываем один раз при загрузке, с
            // действием «Объявить сейчас» — переключает фокус на
            // секцию «📋 Переменные».
            if (viewModel.variables.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.editor_dialog_types_missing_title)
                    .setMessage(R.string.editor_dialog_types_missing_message)
                    .setPositiveButton(R.string.dialog_declare) { _, _ ->
                        viewModel.variablesExpanded = true
                        variablesAdapter.notifyDataSetChanged()
                        binding.rvScenes.post {
                            if (_binding != null) {
                                binding.rvScenes.scrollToPosition(0)
                            }
                        }
                    }
                    .setNegativeButton(R.string.dialog_later, null)
                    .show()
            }

            val tagDefs = adventure.tags.orEmpty().associateBy { it.name }

            // Восстанавливаем initialStateTags из adventure.initialState
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

            // b2: per-scene gate + chapter-boundary as two independent
            // concepts.
            //
            // Reading order: scenes are grouped into chapters exactly as
            // before (`chapter.scenes`). Within a chapter, the **last**
            // scene is marked as the narrative chapter boundary
            // (`isChapterEnd = true`) — this is what the map / stats /
            // validator use for grouping and the "🏁 Конец главы" badge.
            //
            // The gate is now a **scene property** (`scene.gate` in v5
            // JSON), not a chapter property. For files that predate v5
            // (no `scene.gate`, but `chapter.required_steps` present on
            // the *next* chapter), we migrate: the legacy v1 gate on
            // `chapter[i+1]` becomes `gateRequiredSteps` on the last
            // scene of chapter `i`. Same migration `JsonToIr.parse`
            // does — kept here so the editor sees the same data before
            // and after the "load → save" round-trip.
            val allChapters = adventure.chapters.orEmpty()
            for ((chapterIndex, chapter) in allChapters.withIndex()) {
                val scenes = chapter.scenes.orEmpty()
                // v1 migration: `chapter[i+1].required_steps` → gate on
                // the last scene of chapter `i`. `null` (field absent)
                // and `0` both mean "no gate".
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
                        // Ф1.7.5: read `"is_ending"` from the JSON.
                        // `:app/data/Scene.isEnding` is nullable
                        // (absent on pre-v4 files, or on files the
                        // editor itself writes without the flag);
                        // map `null` to `false` — the same identity
                        // migration that `JsonToIr` applies.
                        isEnding = scene.isEnding ?: false,
                        // Ф4.5: round-trip `merge` / `merge_sources`.
                        // `null` / absent → `false` / `emptyList()` —
                        // same identity migration as `isEnding`.
                        isMerge = scene.merge ?: false,
                        mergeSources = scene.mergeSources ?: emptyList(),
                        tags = extractTags(scene, tagDefs),
                        choices = scene.choices?.map { choice ->
                            EditorChoice(
                                text = choice.text,
                                nextSceneId = choice.nextScene,
                                conditionTag = choice.condition ?: "",
                                // Ф4.3.2b: read `"visibility_condition"` back
                                // from JSON. Absent / null → empty string, same
                                // convention as `conditionTag`.
                                visibilityConditionTag = choice.visibilityCondition ?: "",
                                tags = extractChoiceTags(choice, tagDefs),
                                // Ф4.3.2b: round-trip of `template_ref`.
                                // Absent / null (hand-written choice) → both
                                // `templateUid = null` and `templateArgs =
                                // emptyMap`, matching the pre-4.3.2b state.
                                templateUid = choice.templateRef?.templateId,
                                templateArgs = choice.templateRef?.args ?: emptyMap(),
                            )
                        }?.toMutableList() ?: mutableListOf()
                    )
                    viewModel.scenes.add(editorScene)
                }
            }

            // Восстанавливаем сцену смерти из adventure.deathScene
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

            adapter.submitList()
            initialStateAdapter.notifyDataSetChanged()
            variablesAdapter.notifyDataSetChanged()
            updateTitleField()
            updateUndoRedoButtons()

            binding.tvSaveStatus.text = getString(R.string.editor_save_status_loaded)
            binding.tvSaveStatus.setTextColor(Color.parseColor("#4CAF50"))

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
    ): MutableList<EditorTag> {
        return buildTagList(
            setFlags = scene.setFlags,
            removeFlags = scene.removeFlags,
            modifyStats = scene.modifyStats,
            tagDefs = tagDefs
        )
    }

    private fun extractChoiceTags(
        choice: com.myapp.adventure.data.Choice,
        tagDefs: Map<String, TagDefinition>
    ): MutableList<EditorTag> {
        return buildTagList(
            setFlags = choice.setFlags,
            removeFlags = choice.removeFlags,
            modifyStats = choice.modifyStats,
            tagDefs = tagDefs
        )
    }

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
            tags.add(
                EditorTag(
                    name = flag,
                    value = value,
                    isStatModifier = isStat,
                    category = category,
                    displayName = def?.displayName ?: flag,
                    visible = def?.visible ?: false
                )
            )
        }

        for (flag in removeFlags.orEmpty()) {
            val def = tagDefs[flag]
            tags.add(
                EditorTag(
                    name = "!$flag",
                    value = 0,
                    isStatModifier = false,
                    category = def?.category ?: TagCategory.JOURNAL,
                    displayName = def?.displayName ?: flag,
                    visible = def?.visible ?: false
                )
            )
        }

        for ((stat, value) in modifyStats.orEmpty()) {
            val def = tagDefs[stat]
            tags.add(
                EditorTag(
                    name = stat,
                    value = value,
                    isStatModifier = true,
                    category = def?.category ?: TagCategory.JOURNAL,
                    displayName = def?.displayName ?: stat,
                    visible = def?.visible ?: false
                )
            )
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
                Logger.log(requireContext(), "Приключение перезаписано: $fileName")
                Toast.makeText(requireContext(), getString(R.string.editor_toast_updated, fileName), Toast.LENGTH_LONG).show()
                saved = true
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
                    Toast.makeText(requireContext(), getString(R.string.editor_toast_saved, fileName), Toast.LENGTH_LONG).show()
                    saved = true
                } else {
                    Toast.makeText(requireContext(), R.string.editor_toast_create_failed, Toast.LENGTH_SHORT).show()
                }
            }

            if (saved) {
                EditorDraftManager.clearDraft(requireContext())
                hasUnsavedChanges = false
                binding.tvSaveStatus.text = getString(R.string.editor_save_status_saved_to_file)
                binding.tvSaveStatus.setTextColor(Color.parseColor("#4CAF50"))
                updateUndoRedoButtons()
            }
        } catch (e: Exception) {
            Logger.log(requireContext(), "Ошибка сохранения: ${e.message}")
            Toast.makeText(requireContext(), getString(R.string.editor_toast_save_error, e.message), Toast.LENGTH_LONG).show()
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

    // ---------- Валидатор ----------

    private fun showValidationDialog() {
        // Ф2.6: "Проверить" now runs the reader's exact pipeline
        // (type check → compile → analyze) via `StoryValidator`'s
        // bridge to `:engine`, so the author sees the same errors
        // the player would hit on open.
        //
        // Freeze pending IDs first — same as `saveAdventure()` and
        // `openPreview()`. A scene whose ID is still auto-generated
        // would produce a different `Adventure` than the one the
        // reader gets, so validation would not match the actual file.
        viewModel.freezeAllPendingIds()
        // Ф2.6-fix: build the **IR** adventure (not the :app/data/Adventure
        // Gson POJO). StoryValidator.validate expects :engine/ir/Adventure;
        // the IR is also the richer model — it carries is_ending, gate,
        // gate_repeatable. The second element of the pair carries
        // condition-parse errors that couldn't be represented in the IR
        // (a bad condition string becomes `null` in the IR; the author
        // still needs to see why). We prepend them to the issue list so
        // they appear before the engine-level errors.
        val (irAdventure, conditionParseErrors) = viewModel.buildIrAdventure()
        val issues = mutableListOf<ValidationIssue>()
        for (err in conditionParseErrors) {
            issues += ValidationIssue(
                severity = IssueSeverity.ERROR,
                sceneUid = "",
                titleRes = R.string.validation_title_condition_parse_error,
                description = err.message,
            )
        }
        issues += StoryValidator.validate(irAdventure, viewModel.scenes)

        // Ф2.7 DoD: «если пусто — предупреждение, type checking работает
        // в fallback-режиме». Добавляем как WARNING (не блокирует):
        // fallback C4 всё ещё ловит unknown variables / HasOnNonBool /
        // wrong effect type — просто не видит `IntRange`-границ и
        // не проверяет `Integer` vs `ItemCount` (оба → числовые).
        // Автор, заполнив объявления, получит более строгий отчёт.
        if (viewModel.variables.isEmpty()) {
            issues += ValidationIssue(
                severity = IssueSeverity.WARNING,
                sceneUid = "",
                titleRes = R.string.validation_title_types_not_declared,
                description = getString(R.string.validation_desc_types_not_declared),
            )
        }

        if (issues.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.editor_dialog_health_title)
                .setMessage(R.string.editor_dialog_health_no_issues_message)
                .setPositiveButton(R.string.dialog_close, null)
                .show()
            return
        }

        val errorCount = issues.count { it.severity == IssueSeverity.ERROR }
        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_validation_issues, null)
        val tvSummary = dialogView.findViewById<TextView>(R.id.tv_summary)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_issues)

            val summaryParts = mutableListOf<String>()
            if (errorCount > 0) {
                summaryParts.add(getString(R.string.validation_summary_errors_fmt, errorCount))
            }
            if (warningCount > 0) {
                summaryParts.add(getString(R.string.validation_summary_warnings_fmt, warningCount))
            }
        tvSummary.text = summaryParts.joinToString(" • ")

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_dialog_health_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .create()

        val ordered = issues.filter { it.severity == IssueSeverity.ERROR } +
                issues.filter { it.severity == IssueSeverity.WARNING }

        for (issue in ordered) {
            val rowView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_validation_issue, container, false)
            val icon = rowView.findViewById<TextView>(R.id.tv_severity_icon)
            val title = rowView.findViewById<TextView>(R.id.tv_title)
            val desc = rowView.findViewById<TextView>(R.id.tv_description)

            icon.text = if (issue.severity == IssueSeverity.ERROR) "❌" else "⚠️"
            title.text = getString(issue.titleRes)
            desc.text = issue.description

            rowView.setOnClickListener {
                dialog.dismiss()
                jumpToScene(issue.sceneUid)
            }
            container.addView(rowView)
        }

        dialog.show()
    }

    // ---------- Поиск ----------

    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search, null)

        val etQuery = dialogView.findViewById<EditText>(R.id.et_search_query)
        val cbTitles = dialogView.findViewById<CheckBox>(R.id.cb_search_titles)
        val cbTexts = dialogView.findViewById<CheckBox>(R.id.cb_search_texts)
        val cbChoices = dialogView.findViewById<CheckBox>(R.id.cb_search_choices)
        val cbTags = dialogView.findViewById<CheckBox>(R.id.cb_search_tags)
        val tvSummary = dialogView.findViewById<TextView>(R.id.tv_search_summary)
        val container = dialogView.findViewById<LinearLayout>(R.id.container_results)

        cbTitles.isChecked = true
        cbTexts.isChecked = true
        cbChoices.isChecked = true
        cbTags.isChecked = true

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_dialog_search_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .create()

        val searchHandler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        fun runSearch() {
            val query = etQuery.text.toString()
            if (query.trim().length < 2) {
                tvSummary.text = getString(R.string.editor_search_enter_query)
                container.removeAllViews()
                return
            }

            val results = SearchService.search(
                scenes = viewModel.scenes,
                query = query,
                searchInTitles = cbTitles.isChecked,
                searchInTexts = cbTexts.isChecked,
                searchInChoices = cbChoices.isChecked,
                searchInTags = cbTags.isChecked,
                titleFallback = getString(R.string.label_without_title),
            )

            container.removeAllViews()

            if (results.isEmpty()) {
                tvSummary.text = getString(R.string.editor_search_nothing_found)
                return
            }

            val scenesCount = results.map { it.sceneUid }.distinct().size
            val totalMatches = results.sumOf { it.totalMatchesInField }

            val matchesText = formatMatchesCount(totalMatches)
            val scenesText = if (scenesCount > 1) {
                " " + formatScenesPrepositional(scenesCount)
            } else {
                ""
            }
            tvSummary.text = getString(R.string.editor_search_matches_fmt, matchesText + scenesText)

            for (result in results) {
                val rowView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_search_result, container, false)
                val tvIcon = rowView.findViewById<TextView>(R.id.tv_search_icon)
                val tvTitleDetail = rowView.findViewById<TextView>(R.id.tv_search_title_detail)
                val tvContext = rowView.findViewById<TextView>(R.id.tv_search_context)

                tvIcon.text = result.fieldType.icon

                val detailText = if (result.detailArg != null) {
                    getString(result.detailRes, result.detailArg)
                } else {
                    getString(result.detailRes)
                }
                val titleDetailText = buildString {
                    append(result.sceneTitle)
                    append("  ·  ")
                    append(detailText)
                    if (result.totalMatchesInField > 1) {
                        append(getString(R.string.editor_search_more_fmt, result.totalMatchesInField - 1))
                    }
                }
                val spannableTitle = SpannableString(titleDetailText)
                spannableTitle.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, result.sceneTitle.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableTitle.setSpan(
                    ForegroundColorSpan(Color.parseColor("#AAAAAA")),
                    result.sceneTitle.length, titleDetailText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvTitleDetail.text = spannableTitle

                val contextText = "«…${result.contextBefore}${result.match}${result.contextAfter}…»"
                val spannableCtx = SpannableString(contextText)
                val matchStart = 2 + result.contextBefore.length
                val matchEnd = matchStart + result.match.length
                spannableCtx.setSpan(
                    BackgroundColorSpan(Color.parseColor("#FFC107")),
                    matchStart, matchEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableCtx.setSpan(
                    ForegroundColorSpan(Color.parseColor("#000000")),
                    matchStart, matchEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvContext.text = spannableCtx

                rowView.setOnClickListener {
                    dialog.dismiss()
                    searchHandler.removeCallbacksAndMessages(null)
                    jumpToScene(result.sceneUid)
                }
                container.addView(rowView)
            }
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val r = Runnable { runSearch() }
                searchRunnable = r
                searchHandler.postDelayed(r, 300L)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        etQuery.addTextChangedListener(watcher)

        val filterListener = CompoundButton.OnCheckedChangeListener { _, _ -> runSearch() }
        cbTitles.setOnCheckedChangeListener(filterListener)
        cbTexts.setOnCheckedChangeListener(filterListener)
        cbChoices.setOnCheckedChangeListener(filterListener)
        cbTags.setOnCheckedChangeListener(filterListener)

        dialog.setOnDismissListener {
            searchHandler.removeCallbacksAndMessages(null)
        }

        dialog.show()
        etQuery.requestFocus()
        tvSummary.text = getString(R.string.editor_search_enter_query)
    }

    private fun formatMatchesCount(n: Int): String =
        resources.getQuantityString(R.plurals.word_matches, n, n)

    private fun formatScenesPrepositional(n: Int): String =
        resources.getQuantityString(R.plurals.word_scenes_in, n, n)

    // ---------- Статистика ----------

    private fun showStatsDialog() {
        val stats = StoryStatsService.calculate(
            viewModel.scenes,
            titleFallback = getString(R.string.label_without_title),
        )

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_story_stats, null)

        val tvScenes = dialogView.findViewById<TextView>(R.id.tv_stat_scenes)
        val tvWords = dialogView.findViewById<TextView>(R.id.tv_stat_words)
        val tvChoices = dialogView.findViewById<TextView>(R.id.tv_stat_choices)
        val tvChapters = dialogView.findViewById<TextView>(R.id.tv_stat_chapters)
        val tvReading = dialogView.findViewById<TextView>(R.id.tv_stat_reading_time)
        val tvAvg = dialogView.findViewById<TextView>(R.id.tv_stat_avg)
        val tvLongest = dialogView.findViewById<TextView>(R.id.tv_stat_longest)
        val tvShortest = dialogView.findViewById<TextView>(R.id.tv_stat_shortest)
        val containerChapters = dialogView.findViewById<LinearLayout>(R.id.container_chapters)

        tvScenes.text = getString(
            R.string.editor_stats_scenes_fmt,
            formatInt(stats.totalScenes),
            formatScenes(stats.totalScenes)
        )
        tvWords.text = getString(R.string.editor_stats_words_fmt, formatInt(stats.totalWords))
        tvChoices.text = getString(R.string.editor_stats_choices_fmt, formatInt(stats.totalChoices))
        tvChapters.text = getString(R.string.editor_stats_chapters_fmt, formatInt(stats.totalChapters))

        tvReading.text = getString(
            R.string.editor_stats_reading_fmt,
            formatReadingTime(stats.estimatedReadingMinutes)
        )

        if (stats.totalScenes > 0) {
            tvAvg.text = getString(
                R.string.editor_stats_avg_fmt,
                formatWords(stats.avgWordsPerScene)
            )
            tvLongest.text = getString(
                R.string.editor_stats_longest_fmt,
                stats.longestSceneTitle,
                formatWords(stats.longestSceneWords)
            )
            tvShortest.text = getString(
                R.string.editor_stats_shortest_fmt,
                stats.shortestSceneTitle,
                formatWords(stats.shortestSceneWords)
            )
        } else {
            tvAvg.text = ""
            tvLongest.text = ""
            tvShortest.text = ""
        }

        containerChapters.removeAllViews()
        for (chapter in stats.chapters) {
            addChapterStatsRow(containerChapters, chapter)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_dialog_stats_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun addChapterStatsRow(
        container: LinearLayout,
        chapter: ChapterStats
    ) {
        val rowView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_chapter_stats, container, false)
        val tvTitle = rowView.findViewById<TextView>(R.id.tv_chapter_title)
        val tvDetails = rowView.findViewById<TextView>(R.id.tv_chapter_details)
        val tvSteps = rowView.findViewById<TextView>(R.id.tv_chapter_steps)

        tvTitle.text = getString(R.string.editor_chapter_title_fmt, chapter.index)

        tvDetails.text = getString(
            R.string.editor_chapter_details_fmt,
            formatScenes(chapter.sceneCount),
            formatWords(chapter.wordCount),
            formatChoices(chapter.choiceCount)
        )

        if (chapter.isLast) {
            tvSteps.text = getString(R.string.editor_chapter_last)
            tvSteps.setTextColor(Color.parseColor("#4CAF50"))
        } else if (chapter.requiredSteps > 0) {
            tvSteps.text = getString(
                R.string.editor_chapter_next_steps_fmt,
                formatSteps(chapter.requiredSteps)
            )
            tvSteps.setTextColor(Color.parseColor("#FFC107"))
        } else {
            tvSteps.text = getString(R.string.editor_chapter_no_gate)
            tvSteps.setTextColor(Color.parseColor("#FF9800"))
        }

        container.addView(rowView)
    }

    // ---------- Форматирование чисел и склонений ----------

    // Ф4-infra (3b-6a): `formatInt` and `pluralWithNumber` are now
    // top-level in `EditorHelpers.kt` (same package). Delete the local
    // copies to keep one source of truth. The per-`format` wrappers
    // below stay — they bind each `@PluralsRes` id to its callers.
    private fun formatScenes(n: Int): String =
        pluralWithNumber(requireContext(), R.plurals.word_scenes, n)

    private fun formatWords(n: Int): String =
        pluralWithNumber(requireContext(), R.plurals.word_words, n)

    private fun formatChoices(n: Int): String =
        pluralWithNumber(requireContext(), R.plurals.word_choices, n)

    private fun formatSteps(n: Int): String =
        pluralWithNumber(requireContext(), R.plurals.steps, n)

    private fun formatReadingTime(minutes: Int): String {
        if (minutes < 1) return getString(R.string.editor_stats_reading_less_than_minute)
        return pluralWithNumber(requireContext(), R.plurals.word_minutes, minutes)
    }

    // ---------- Прыжок к сцене ----------

    private fun jumpToScene(uid: String) {
        if (_binding == null) return
        val pos = viewModel.scenes.indexOfFirst { it.uid == uid }
        if (pos < 0) return

        viewModel.expandScene(uid)
        adapter.submitList()

        binding.rvScenes.post {
            if (_binding == null) return@post
            (binding.rvScenes.layoutManager as? LinearLayoutManager)
                // +2 — VariablesAdapter (позиция 0) + InitialStateAdapter (позиция 1)
                ?.scrollToPositionWithOffset(pos + 2, 50)

            adapter.setHighlighted(uid)
            debounceHandler.postDelayed({
                if (_binding != null) adapter.setHighlighted(null)
            }, 2500L)
        }
    }

    // ---------- Предпросмотр сцены ----------

    private fun openPreview(sceneUid: String) {
        val scene = viewModel.scenes.find { it.uid == sceneUid } ?: return

        if (scene.title.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.editor_toast_title_first,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewModel.freezeAllPendingIds()

        if (scene.id.isBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.editor_toast_id_generate_failed,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val adventure = viewModel.buildAdventure()
        val gson = Gson()

        val chapterIndex = adventure.chapters?.indexOfFirst { chapter ->
            chapter.scenes?.any { it.id == scene.id } == true
        } ?: -1

        if (chapterIndex < 0) {
            Toast.makeText(
                requireContext(),
                R.string.editor_toast_scene_not_found,
                Toast.LENGTH_SHORT
            ).show()
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

        Logger.log(requireContext(), "Предпросмотр сцены '${scene.id}' (глава ${chapterIndex + 1})")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        debounceHandler.removeCallbacks(saveRunnable)

        if (_binding != null && hasUnsavedChanges && viewModel.scenes.isNotEmpty()) {
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
            Logger.log(requireContext(), "Черновик сохранён при выходе из редактора")
        }
        _binding = null
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
    }
}