package com.myapp.adventure.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.EditorScene
import com.myapp.adventure.data.SearchResult
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.SearchService
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Phase 3.1c-iv-d: "Поиск по истории" as a full screen (previously a
 * `dialog_search.xml` dialog).
 *
 * Reads `scenes` once (snapshot from the editor route). Search runs on
 * a 300 ms debounced query; results are recomputed via `remember` keyed
 * on `(debouncedQuery, filters)` — no explicit handler required.
 *
 * Tap on a result → `onResultClick(sceneUid)`; `SearchRoute` calls
 * `requestJumpToScene` + navigate back, so the editor route
 * scrolls + highlights the target scene.
 */
@Composable
fun SearchScreen(
    scenes: List<EditorScene>,
    onBack: () -> Unit,
    onResultClick: (sceneUid: String) -> Unit,
) {
    val titleFallback = stringResource(R.string.label_without_title)

    var query by rememberSaveable { mutableStateOf("") }
    var searchInTitles by rememberSaveable { mutableStateOf(true) }
    var searchInTexts by rememberSaveable { mutableStateOf(true) }
    var searchInChoices by rememberSaveable { mutableStateOf(true) }
    var searchInTags by rememberSaveable { mutableStateOf(true) }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    val results = remember(
        debouncedQuery,
        searchInTitles, searchInTexts, searchInChoices, searchInTags,
        scenes,
    ) {
        SearchService.search(
            scenes = scenes,
            query = debouncedQuery,
            searchInTitles = searchInTitles,
            searchInTexts = searchInTexts,
            searchInChoices = searchInChoices,
            searchInTags = searchInTags,
            titleFallback = titleFallback,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.editor_dialog_search_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.dialog_search_query_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                FilterCheck(
                    label = stringResource(R.string.dialog_search_filter_titles),
                    checked = searchInTitles,
                    onCheckedChange = { searchInTitles = it },
                    modifier = Modifier.weight(1f),
                )
                FilterCheck(
                    label = stringResource(R.string.dialog_search_filter_texts),
                    checked = searchInTexts,
                    onCheckedChange = { searchInTexts = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Row {
                FilterCheck(
                    label = stringResource(R.string.dialog_search_filter_choices),
                    checked = searchInChoices,
                    onCheckedChange = { searchInChoices = it },
                    modifier = Modifier.weight(1f),
                )
                FilterCheck(
                    label = stringResource(R.string.dialog_search_filter_tags),
                    checked = searchInTags,
                    onCheckedChange = { searchInTags = it },
                    modifier = Modifier.weight(1f),
                )
            }

            val summary = when {
                debouncedQuery.trim().length < 2 ->
                    stringResource(R.string.editor_search_enter_query)
                results.isEmpty() ->
                    stringResource(R.string.editor_search_nothing_found)
                else -> {
                    val totalMatches = results.sumOf { it.totalMatchesInField }
                    val scenesCount = results.map { it.sceneUid }.distinct().size
                    val matchesText = pluralStringResource(
                        R.plurals.word_matches, totalMatches, totalMatches,
                    )
                    val scenesText = if (scenesCount > 1) {
                        " " + pluralStringResource(
                            R.plurals.word_scenes_in, scenesCount, scenesCount,
                        )
                    } else ""
                    stringResource(R.string.editor_search_matches_fmt, matchesText + scenesText)
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            items(results) { result ->
                SearchRow(
                    result = result,
                    onClick = { onResultClick(result.sceneUid) },
                )
            }
        }
    }
}

@Composable
private fun FilterCheck(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SearchRow(result: SearchResult, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val detailText = if (result.detailArg != null) {
        stringResource(result.detailRes, result.detailArg)
    } else {
        stringResource(result.detailRes)
    }
    val moreText = if (result.totalMatchesInField > 1) {
        stringResource(R.string.editor_search_more_fmt, result.totalMatchesInField - 1)
    } else ""

    val titleLine = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
        ) {
            append(result.sceneTitle)
        }
        append("  ·  ")
        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
            append(detailText)
        }
        if (moreText.isNotEmpty()) {
            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                append(moreText)
            }
        }
    }

    val highlightBg = AdventureTheme.extendedColors.statusWarning
    val highlightFg = colorScheme.surface
    val contextLine = buildAnnotatedString {
        append("«…")
        append(result.contextBefore)
        withStyle(SpanStyle(background = highlightBg, color = highlightFg)) {
            append(result.match)
        }
        append(result.contextAfter)
        append("…»")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = result.fieldType.icon,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleLine,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                color = colorScheme.surface,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = contextLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}