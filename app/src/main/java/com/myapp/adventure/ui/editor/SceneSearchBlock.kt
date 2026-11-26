package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.utils.SearchService
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 300L
private const val MAX_RESULTS = 8

/**
 * Ф4.13: the old full-screen Search moved inline between the editor menu
 * and the single active scene. Search matches ids, titles, scene text,
 * choice text and tags (the old screen's default filters); a result click
 * switches the active scene.
 */
@Composable
fun SceneSearchBlock(
    scenes: List<EditorScene>,
    onOpenScene: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    val titleFallback = stringResource(R.string.label_without_title)
    val results = remember(debouncedQuery, scenes, titleFallback) {
        if (debouncedQuery.trim().length < 2) {
            emptyList()
        } else {
            SearchService.search(
                scenes = scenes,
                query = debouncedQuery,
                searchInTitles = true,
                searchInTexts = true,
                searchInChoices = true,
                searchInTags = true,
                titleFallback = titleFallback,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.editor_scene_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            debouncedQuery.trim().length < 2 -> Unit
            results.isEmpty() -> Text(
                text = stringResource(R.string.editor_scene_search_nothing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            else -> {
                Column {
                    results.take(MAX_RESULTS).forEach { result ->
                        SearchRow(
                            result = result,
                            onClick = {
                                onOpenScene(result.sceneUid)
                                query = ""
                            },
                        )
                    }
                    if (results.size > MAX_RESULTS) {
                        Text(
                            text = stringResource(
                                R.string.editor_scene_search_more_fmt,
                                results.size - MAX_RESULTS,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
