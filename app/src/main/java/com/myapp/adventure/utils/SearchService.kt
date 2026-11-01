package com.myapp.adventure.utils

import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.SearchFieldType
import com.myapp.adventure.data.SearchResult

object SearchService {

    private const val CONTEXT_LEN = 30
    private const val MIN_QUERY_LENGTH = 2

    fun search(
        scenes: List<EditorScene>,
        query: String,
        searchInTitles: Boolean,
        searchInTexts: Boolean,
        searchInChoices: Boolean,
        searchInTags: Boolean
    ): List<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        val qLower = trimmed.lowercase()

        val results = mutableListOf<SearchResult>()

        for (scene in scenes) {
            val displayTitle = scene.title.ifBlank { "(без названия)" }

            if (searchInTitles && scene.title.isNotEmpty()) {
                val positions = findAllPositions(scene.title, qLower)
                if (positions.isNotEmpty()) {
                    results += makeResult(
                        sceneUid = scene.uid,
                        sceneTitle = displayTitle,
                        fieldType = SearchFieldType.SCENE_TITLE,
                        detail = "Заголовок",
                        sourceText = scene.title,
                        positions = positions,
                        matchLen = trimmed.length
                    )
                }
            }

            if (searchInTexts && scene.text.isNotEmpty()) {
                val positions = findAllPositions(scene.text, qLower)
                if (positions.isNotEmpty()) {
                    results += makeResult(
                        sceneUid = scene.uid,
                        sceneTitle = displayTitle,
                        fieldType = SearchFieldType.SCENE_TEXT,
                        detail = "Текст сцены",
                        sourceText = scene.text,
                        positions = positions,
                        matchLen = trimmed.length
                    )
                }
            }

            if (searchInChoices) {
                scene.choices.forEachIndexed { idx, choice ->
                    if (choice.text.isEmpty()) return@forEachIndexed
                    val positions = findAllPositions(choice.text, qLower)
                    if (positions.isNotEmpty()) {
                        results += makeResult(
                            sceneUid = scene.uid,
                            sceneTitle = displayTitle,
                            fieldType = SearchFieldType.CHOICE_TEXT,
                            detail = "Выбор #${idx + 1}",
                            sourceText = choice.text,
                            positions = positions,
                            matchLen = trimmed.length
                        )
                    }
                }
            }

            if (searchInTags) {
                scene.tags.forEach { tag ->
                    if (tag.name.isEmpty()) return@forEach
                    val positions = findAllPositions(tag.name, qLower)
                    if (positions.isNotEmpty()) {
                        results += makeResult(
                            sceneUid = scene.uid,
                            sceneTitle = displayTitle,
                            fieldType = SearchFieldType.TAG_NAME,
                            detail = "Метка",
                            sourceText = tag.name,
                            positions = positions,
                            matchLen = trimmed.length
                        )
                    }
                }
            }
        }

        return results
    }

    private fun findAllPositions(text: String, qLower: String): List<Int> {
        val positions = mutableListOf<Int>()
        val textLower = text.lowercase()
        var idx = textLower.indexOf(qLower)
        while (idx >= 0) {
            positions += idx
            idx = textLower.indexOf(qLower, idx + 1)
        }
        return positions
    }

    private fun makeResult(
        sceneUid: String,
        sceneTitle: String,
        fieldType: SearchFieldType,
        detail: String,
        sourceText: String,
        positions: List<Int>,
        matchLen: Int
    ): SearchResult {
        val firstPos = positions.first()

        val beforeStart = (firstPos - CONTEXT_LEN).coerceAtLeast(0)
        val before = sourceText.substring(beforeStart, firstPos).replace('\n', ' ')

        val match = sourceText.substring(firstPos, firstPos + matchLen)

        val afterEnd = (firstPos + matchLen + CONTEXT_LEN).coerceAtMost(sourceText.length)
        val after = sourceText.substring(firstPos + matchLen, afterEnd).replace('\n', ' ')

        return SearchResult(
            sceneUid = sceneUid,
            sceneTitle = sceneTitle,
            fieldType = fieldType,
            detail = detail,
            contextBefore = before,
            match = match,
            contextAfter = after,
            totalMatchesInField = positions.size,
            charOffset = firstPos
        )
    }
}