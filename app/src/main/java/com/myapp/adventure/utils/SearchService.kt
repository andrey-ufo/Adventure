package com.myapp.adventure.utils

import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.SearchFieldType
import com.myapp.adventure.data.SearchResult
import com.myapp.adventure.R

object SearchService {

    private const val CONTEXT_LEN = 30
    private const val MIN_QUERY_LENGTH = 2

    /**
     * @param titleFallback displayed title for scenes whose `title` is
     *   blank. The caller (`SearchRoute`) passes
     *   `stringResource(R.string.label_without_title)`; `:utils` stays free
     *   of `Context` and free of user-facing strings.
     */
    fun search(
        scenes: List<EditorScene>,
        query: String,
        searchInTitles: Boolean,
        searchInTexts: Boolean,
        searchInChoices: Boolean,
        searchInTags: Boolean,
        titleFallback: String = "",
    ): List<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        val qLower = trimmed.lowercase()

        val results = mutableListOf<SearchResult>()

        for (scene in scenes) {
            val displayTitle = scene.title.ifBlank { titleFallback }

            if (searchInTitles && scene.title.isNotEmpty()) {
                val positions = findAllPositions(scene.title, qLower)
                if (positions.isNotEmpty()) {
                    results += makeResult(
                        sceneUid = scene.uid,
                        sceneTitle = displayTitle,
                        fieldType = SearchFieldType.SCENE_TITLE,
                        detailRes = R.string.search_detail_title,
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
                        detailRes = R.string.search_detail_text,
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
                            detailRes = R.string.search_detail_choice_fmt,
                            detailArg = idx + 1,
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
                            detailRes = R.string.search_detail_tag,
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
        detailRes: Int,
        sourceText: String,
        positions: List<Int>,
        matchLen: Int,
        detailArg: Int? = null,
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
            detailRes = detailRes,
            detailArg = detailArg,
            contextBefore = before,
            match = match,
            contextAfter = after,
            totalMatchesInField = positions.size,
            charOffset = firstPos
        )
    }
}