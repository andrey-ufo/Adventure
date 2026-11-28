package com.myapp.adventure.ui.editor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.myapp.adventure.R
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Choice
import com.myapp.adventure.data.EditorChoice
import com.myapp.adventure.data.EditorDraft
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.EditorTag
import com.myapp.adventure.data.FileSaveStatus
import com.myapp.adventure.data.SaveStatus
import com.myapp.adventure.data.Scene
import com.myapp.adventure.data.TagCategory
import com.myapp.adventure.data.TagDefinition
import com.myapp.adventure.data.deepCopy
import com.myapp.adventure.projects.EditorProjectSession
import com.myapp.adventure.projects.ProjectDraftManager
import com.myapp.adventure.projects.ProjectRef
import com.myapp.adventure.utils.EditorDraftManager
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.QuestStorage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4-b: file/draft work extracted from the old editor Fragment.
 *
 * These functions are deliberately non-composable: they keep all
 * `Context.getString` calls out of the composition (the Compose lint check
 * `LocalContextGetResourceValueCall`) and make [EditorRoute] readable.
 */

internal data class DraftRestore(val draft: EditorDraft, val dateText: String)

/**
 * Loads a draft for the restore prompt, or `null` when the editor already
 * has content / no meaningful draft exists. Mirrors the old
 * `maybeRestoreDraft` guards exactly.
 */
internal fun readDraftForRestore(
    context: Context,
    viewModel: AdventureEditorViewModel,
): DraftRestore? {
    if (viewModel.scenes.isNotEmpty() ||
        viewModel.title.isNotEmpty() ||
        viewModel.author.isNotEmpty() ||
        viewModel.description.isNotEmpty() ||
        viewModel.initialStateTags.isNotEmpty()
    ) {
        return null
    }

    val project = EditorProjectSession.current(context)
    val draft = if (project != null) {
        ProjectDraftManager.load(context, project.id)
    } else {
        EditorDraftManager.loadDraft(context)
    } ?: return null
    if (draft.scenes.isEmpty() && draft.title.isEmpty() && draft.initialStateTags.isEmpty()) {
        if (project != null) {
            ProjectDraftManager.clear(context, project.id)
        } else {
            EditorDraftManager.clearDraft(context)
        }
        return null
    }

    val dateFormat = SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault())
    return DraftRestore(
        draft = draft,
        dateText = dateFormat.format(Date(draft.savedAt)),
    )
}

internal fun applyDraft(
    context: Context,
    viewModel: AdventureEditorViewModel,
    draft: EditorDraft,
) {
    viewModel.title = draft.title
    viewModel.author = draft.author
    viewModel.description = draft.description
    viewModel.variables.clear()
    viewModel.variables.addAll(draft.variables.map { it.copy() })
    viewModel.templates.clear()
    viewModel.templates.addAll(draft.templates.map { it.deepCopy() })
    viewModel.subroutines.clear()
    viewModel.subroutines.addAll(draft.subroutines.orEmpty().map { it.deepCopy() })
    viewModel.initialStateTags.clear()
    viewModel.initialStateTags.addAll(draft.initialStateTags.map { it.copy() })
    viewModel.scenes.clear()
    viewModel.scenes.addAll(draft.scenes.map { it.deepCopy() })
    viewModel.activeSceneUid = draft.activeSceneUid?.takeIf { uid ->
        viewModel.scenes.any { it.uid == uid }
    }

    viewModel.initHistory()
    viewModel.collapseAllScenes()
    viewModel.collapseAllChoices()
    viewModel.commitState()

    viewModel.setHasUnsavedChanges(false)
    viewModel.setSaveStatus(SaveStatus.SAVED)
    viewModel.setFileStatus(FileSaveStatus.DIRTY)
    Logger.log(context, "Черновик восстановлен, сцен: ${draft.scenes.size}")
}

private fun snapshotDraft(viewModel: AdventureEditorViewModel): EditorDraft = EditorDraft(
    title = viewModel.title,
    author = viewModel.author,
    description = viewModel.description,
    variables = viewModel.variables.map { it.copy() },
    templates = viewModel.templates.map { it.deepCopy() },
    subroutines = viewModel.subroutines.map { it.deepCopy() },
    initialStateTags = viewModel.initialStateTags.map { it.copy() },
    scenes = viewModel.scenes.map { it.deepCopy() },
    activeSceneUid = viewModel.activeSceneUid,
    savedAt = System.currentTimeMillis(),
)

private fun saveDraftForCurrentProject(
    context: Context,
    viewModel: AdventureEditorViewModel,
) {
    val project = EditorProjectSession.current(context)
    if (project != null) {
        ProjectDraftManager.save(context, project.id, snapshotDraft(viewModel))
    } else {
        EditorDraftManager.saveDraft(
            context,
            viewModel.title,
            viewModel.author,
            viewModel.description,
            viewModel.variables,
            viewModel.templates,
            viewModel.subroutines,
            viewModel.initialStateTags,
            viewModel.scenes,
            viewModel.activeSceneUid,
        )
    }
}

internal fun saveDraftImmediate(
    context: Context,
    viewModel: AdventureEditorViewModel,
) {
    viewModel.setSaveStatus(SaveStatus.SAVING)
    viewModel.setFileStatus(FileSaveStatus.DIRTY)
    viewModel.commitSnapshot()
    saveDraftForCurrentProject(context, viewModel)
    viewModel.setHasUnsavedChanges(false)
    viewModel.setSaveStatus(SaveStatus.SAVED)
    Logger.log(context, "Черновик автосохранён, сцен: ${viewModel.scenes.size}")
}

/**
 * Best-effort save on route disposal (old `onDestroyView`). Unlike
 * [saveDraftImmediate], it does not touch save status or snapshots.
 */
internal fun saveDraftSilently(
    context: Context,
    viewModel: AdventureEditorViewModel,
) {
    if (!viewModel.uiState.value.hasUnsavedChanges || viewModel.scenes.isEmpty()) return
    saveDraftForCurrentProject(context, viewModel)
}

/**
 * Loads an adventure JSON into the editor. Returns `true` when the file has
 * no declared variables, so the caller can show the "declare types" prompt
 * (the old `loadFromUri` AlertDialog).
 */
internal fun loadAdventureFromUri(
    context: Context,
    viewModel: AdventureEditorViewModel,
    uri: Uri,
    clearDraft: Boolean = true,
): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val json = reader.readText()
        reader.close()
        val adventure = Gson().fromJson(json, Adventure::class.java)

        if (clearDraft) {
            val project = EditorProjectSession.current(context)
            if (project != null) {
                ProjectDraftManager.clear(context, project.id)
            } else {
                EditorDraftManager.clearDraft(context)
            }
        }
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

        viewModel.subroutines.clear()
        adventure.subroutines?.forEach { dto ->
            viewModel.subroutines.add(viewModel.editorSubroutineFromDto(dto))
        }
        val typesMissing = viewModel.variables.isEmpty()

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
                        visible = def?.visible ?: false,
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
                        visible = def?.visible ?: false,
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
                            subroutineCall = choice.call?.let { call ->
                                com.myapp.adventure.data.EditorSubroutineCall(
                                    subroutineId = call.subroutineId,
                                    args = call.args ?: emptyMap(),
                                    outputs = call.outputs ?: emptyMap(),
                                )
                            },
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
                    title = context.getString(R.string.item_editor_scene_death_scene),
                    text = deathDef.text,
                    isDeathScene = true,
                    deathAllowStepBack = deathDef.allowStepBack,
                    deathAllowChapterRestart = deathDef.allowChapterRestart,
                    deathAllowAdventureRestart = deathDef.allowAdventureRestart,
                )
                viewModel.scenes.add(deathSceneEditor)
            }
        }

        viewModel.activeSceneUid = adventure.startScene
            ?.let { id -> viewModel.scenes.firstOrNull { it.id == id }?.uid }
            ?: viewModel.scenes.firstOrNull { !it.isDeathScene }?.uid
            ?: viewModel.scenes.firstOrNull()?.uid
        viewModel.initHistory()
        viewModel.collapseAllScenes()
        viewModel.collapseAllChoices()
        viewModel.commitState()

        viewModel.setSaveStatus(SaveStatus.LOADED)
        viewModel.setFileStatus(FileSaveStatus.CLEAN)

        Logger.log(context, "Приключение загружено из файла: ${uri.lastPathSegment}")
        Toast.makeText(context, R.string.editor_toast_loaded, Toast.LENGTH_SHORT).show()
        typesMissing
    } catch (e: Exception) {
        Logger.log(context, "Ошибка загрузки приключения: ${e.message}")
        Toast.makeText(
            context,
            context.getString(R.string.editor_toast_load_error, e.message),
            Toast.LENGTH_LONG,
        ).show()
        false
    }
}

private fun extractTags(
    scene: Scene,
    tagDefs: Map<String, TagDefinition>,
): MutableList<EditorTag> =
    buildTagList(scene.setFlags, scene.removeFlags, scene.modifyStats, tagDefs)

private fun extractChoiceTags(
    choice: Choice,
    tagDefs: Map<String, TagDefinition>,
): MutableList<EditorTag> =
    buildTagList(choice.setFlags, choice.removeFlags, choice.modifyStats, tagDefs)

private fun buildTagList(
    setFlags: List<String>?,
    removeFlags: List<String>?,
    modifyStats: Map<String, Int>?,
    tagDefs: Map<String, TagDefinition>,
): MutableList<EditorTag> {
    val tags = mutableListOf<EditorTag>()
    for (flag in setFlags.orEmpty()) {
        val def = tagDefs[flag]
        val category = def?.category ?: TagCategory.JOURNAL
        val value = if (category == TagCategory.ITEM) 1 else 0
        val isStat = category == TagCategory.ITEM
        tags.add(
            EditorTag(
                flag,
                value,
                isStat,
                category,
                def?.displayName ?: flag,
                def?.visible ?: false,
            )
        )
    }
    for (flag in removeFlags.orEmpty()) {
        val def = tagDefs[flag]
        tags.add(
            EditorTag(
                "!$flag",
                0,
                false,
                def?.category ?: TagCategory.JOURNAL,
                def?.displayName ?: flag,
                def?.visible ?: false,
            )
        )
    }
    for ((stat, value) in modifyStats.orEmpty()) {
        val def = tagDefs[stat]
        tags.add(
            EditorTag(
                stat,
                value,
                true,
                def?.category ?: TagCategory.JOURNAL,
                def?.displayName ?: stat,
                def?.visible ?: false,
            )
        )
    }
    return tags
}

internal fun saveAdventureToFile(
    context: Context,
    viewModel: AdventureEditorViewModel,
): Boolean {
    if (viewModel.scenes.isEmpty()) {
        Toast.makeText(
            context,
            R.string.editor_toast_add_scene_first,
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    viewModel.freezeAllPendingIds()

    val adventure = viewModel.buildAdventure()
    val gson = GsonBuilder().setPrettyPrinting().create()
    val json = gson.toJson(adventure)

    val prefs = context.getSharedPreferences("adventure_prefs", 0)
    if (prefs.getString("adventure_folder_uri", null) == null) {
        Toast.makeText(
            context,
            R.string.editor_toast_folder_not_selected,
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }
    viewModel.setFileStatus(FileSaveStatus.DIRTY)

    try {
        val currentProject = EditorProjectSession.current(context)
        var savedUri: Uri? = null
        var savedProjectId = currentProject?.id
        var savedFileName = currentProject?.fileName.orEmpty()
        var updated = false

        if (currentProject != null) {
            val target = currentProject.fileUri.toUri()
            context.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(json.toByteArray())
                savedUri = target
                savedFileName = currentProject.fileName
                updated = true
            }
        }

        if (savedUri == null) {
            val folder = QuestStorage.createQuestFolder(
                context = context,
                baseName = QuestStorage.NEW_ADVENTURES_DIR,
                desiredName = adventure.title.ifBlank { "Новый квест" },
                questJson = json,
            )
            if (folder != null) {
                savedUri = folder.questFileUri
                savedProjectId = folder.id
                savedFileName = folder.name
            }
        }

        val target = savedUri
        val projectId = savedProjectId
        if (target != null && projectId != null) {
            EditorProjectSession.setCurrent(
                context,
                ProjectRef(
                    id = projectId,
                    fileUri = target.toString(),
                    fileName = savedFileName,
                    title = adventure.title,
                    author = adventure.author,
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
            ProjectDraftManager.clear(context, projectId)
            EditorDraftManager.clearDraft(context)
            viewModel.setHasUnsavedChanges(false)
            viewModel.setSaveStatus(SaveStatus.SAVED_TO_FILE)
            viewModel.setFileStatus(FileSaveStatus.CLEAN)
            Toast.makeText(
                context,
                context.getString(
                    if (updated) R.string.editor_toast_updated else R.string.editor_toast_saved,
                    savedFileName,
                ),
                Toast.LENGTH_LONG,
            ).show()
            return true
        }
        return false
    } catch (e: Exception) {
        Logger.log(context, "Ошибка сохранения: ${e.message}")
        Toast.makeText(
            context,
            context.getString(R.string.editor_toast_save_error, e.message),
            Toast.LENGTH_LONG,
        ).show()
        viewModel.setFileStatus(FileSaveStatus.DIRTY)
        return false
    }
}

/**
 * Builds the preview payload (adventure JSON + start scene + chapter index)
 * and shows the same validation toasts as the old `openPreview`.
 */
internal fun buildPreviewRequest(
    context: Context,
    viewModel: AdventureEditorViewModel,
    sceneUid: String,
): EditorPreviewRequest? {
    val scene = viewModel.scenes.find { it.uid == sceneUid } ?: return null
    if (scene.title.isBlank()) {
        Toast.makeText(
            context,
            R.string.editor_toast_title_first,
            Toast.LENGTH_SHORT,
        ).show()
        return null
    }

    viewModel.freezeAllPendingIds()
    if (scene.id.isBlank()) {
        Toast.makeText(
            context,
            R.string.editor_toast_id_generate_failed,
            Toast.LENGTH_SHORT,
        ).show()
        return null
    }

    val adventure = viewModel.buildAdventure()
    val chapterIndex = adventure.chapters?.indexOfFirst { ch ->
        ch.scenes?.any { it.id == scene.id } == true
    } ?: -1
    if (chapterIndex < 0) {
        Toast.makeText(
            context,
            R.string.editor_toast_scene_not_found,
            Toast.LENGTH_SHORT,
        ).show()
        return null
    }

    return EditorPreviewRequest(
        adventureJson = Gson().toJson(adventure),
        sceneId = scene.id,
        chapterIndex = chapterIndex,
    )
}
