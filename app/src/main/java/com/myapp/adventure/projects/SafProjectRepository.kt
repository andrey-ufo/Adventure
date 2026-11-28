package com.myapp.adventure.projects

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.myapp.adventure.data.Adventure
import com.myapp.adventure.data.Chapter
import com.myapp.adventure.data.Scene
import com.myapp.adventure.utils.Logger
import com.myapp.adventure.utils.QuestStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A-phase implementation over `<selected folder>/NewAdventures/<project>/`.
 * Each project is a folder with `quest.json` (later also `theme.json` and
 * assets). The quest folder document id is the stable [ProjectRef.id].
 */
class SafProjectRepository(
    private val context: Context,
) : ProjectRepository {

    private val gson = Gson()

    override suspend fun list(): List<ProjectRef> = withContext(Dispatchers.IO) {
        QuestStorage.listQuestFolders(context, QuestStorage.NEW_ADVENTURES_DIR)
            .mapNotNull { folder -> folder.toProjectRef() }
            .sortedByDescending { it.modifiedAt }
    }

    override suspend fun create(title: String): ProjectRef? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Новый квест" }
        val template = Adventure(
            version = 5,
            title = cleanTitle,
            startScene = "scene_1",
            chapters = listOf(
                Chapter(
                    id = "chapter_1",
                    title = "Глава 1",
                    scenes = listOf(Scene(id = "scene_1", text = "")),
                )
            ),
        )
        val folder = QuestStorage.createQuestFolder(
            context = context,
            baseName = QuestStorage.NEW_ADVENTURES_DIR,
            desiredName = cleanTitle,
            questJson = gson.toJson(template),
        ) ?: return@withContext null
        folder.toProjectRef()
    }

    override suspend fun duplicate(project: ProjectRef): ProjectRef? =
        withContext(Dispatchers.IO) {
            val source = QuestStorage.listQuestFolders(
                context,
                QuestStorage.NEW_ADVENTURES_DIR,
            ).firstOrNull { it.id == project.id } ?: return@withContext null
            val copy = QuestStorage.copyQuestFolder(
                context = context,
                source = source,
                baseName = QuestStorage.NEW_ADVENTURES_DIR,
                desiredName = "${source.name} (копия)",
            ) ?: return@withContext null

            val json = QuestStorage.readQuestJson(context, copy.questFileUri)
            val adventure = json?.let {
                runCatching { gson.fromJson(it, Adventure::class.java) }.getOrNull()
            }
            val title = adventure?.title?.takeIf { it.isNotBlank() }
                ?: project.title
            if (adventure != null) {
                QuestStorage.writeQuestJson(
                    context,
                    copy.questFileUri,
                    gson.toJson(adventure.copy(title = "$title (копия)")),
                )
            }
            copy.toProjectRef(title = "$title (копия)")
        }

    override suspend fun delete(project: ProjectRef): Boolean = withContext(Dispatchers.IO) {
        QuestStorage.deleteQuestFolder(context, project.fileUri.toUri())
    }

    private fun QuestStorage.QuestFolder.toProjectRef(
        title: String? = null,
    ): ProjectRef? {
        val json = QuestStorage.readQuestJson(context, questFileUri) ?: return null
        val adventure = runCatching {
            gson.fromJson(json, Adventure::class.java)
        }.getOrNull()
        return ProjectRef(
            id = id,
            fileUri = questFileUri.toString(),
            fileName = name,
            title = title
                ?: adventure?.title?.takeIf { it.isNotBlank() }
                ?: name,
            author = adventure?.author.orEmpty(),
            modifiedAt = System.currentTimeMillis(),
        )
    }
}
