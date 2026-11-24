package com.myapp.adventure.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.data.ChapterStats
import com.myapp.adventure.data.StoryStats
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-iv-c: "Статистика истории" as a full screen (previously a
 * `dialog_story_stats.xml` dialog).
 *
 * Read-only; no callbacks other than [onBack]. The stats are computed once
 * by `StoryStatsRoute` and rendered here.
 */
@Composable
fun StoryStatsScreen(
    stats: StoryStats,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
                    text = stringResource(R.string.editor_dialog_stats_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // ----- Summary -----
            item(key = "__summary__") {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.editor_stats_scenes_fmt,
                            formatInt(stats.totalScenes),
                            pluralWithNumber(context, R.plurals.word_scenes, stats.totalScenes),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.editor_stats_words_fmt,
                            formatInt(stats.totalWords),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.editor_stats_choices_fmt,
                            formatInt(stats.totalChoices),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.editor_stats_chapters_fmt,
                            formatInt(stats.totalChapters),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val readingText = if (stats.estimatedReadingMinutes < 1) {
                        stringResource(R.string.editor_stats_reading_less_than_minute)
                    } else {
                        pluralWithNumber(
                            context,
                            R.plurals.word_minutes,
                            stats.estimatedReadingMinutes,
                        )
                    }
                    Text(
                        text = stringResource(R.string.editor_stats_reading_fmt, readingText),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AdventureTheme.extendedColors.statusWarning,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // ----- Chapters section -----
            item(key = "__chapters_header__") {
                SectionHeader(stringResource(R.string.dialog_story_stats_chapters_section))
            }
            if (stats.chapters.isEmpty()) {
                item(key = "__chapters_empty__") {
                    Text(
                        text = stringResource(R.string.label_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            } else {
                items(stats.chapters, key = { it.index }) { chapter ->
                    ChapterRow(chapter = chapter)
                }
            }

            // ----- Scenes quality section -----
            item(key = "__lengths_header__") {
                SectionHeader(stringResource(R.string.dialog_story_stats_lengths_section))
            }
            if (stats.totalScenes > 0) {
                item(key = "__avg__") {
                    Text(
                        text = stringResource(
                            R.string.editor_stats_avg_fmt,
                            pluralWithNumber(context, R.plurals.word_words, stats.avgWordsPerScene),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                item(key = "__longest__") {
                    Text(
                        text = stringResource(
                            R.string.editor_stats_longest_fmt,
                            stats.longestSceneTitle,
                            pluralWithNumber(context, R.plurals.word_words, stats.longestSceneWords),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item(key = "__shortest__") {
                    Text(
                        text = stringResource(
                            R.string.editor_stats_shortest_fmt,
                            stats.shortestSceneTitle,
                            pluralWithNumber(context, R.plurals.word_words, stats.shortestSceneWords),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item(key = "__bottom_pad__") {
                Text(text = "", modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun ChapterRow(chapter: ChapterStats) {
    val context = LocalContext.current
    val stepsText: String
    val stepsColor = when {
        chapter.isLast -> {
            stepsText = stringResource(R.string.editor_chapter_last)
            AdventureTheme.extendedColors.statusSuccess
        }
        chapter.requiredSteps > 0 -> {
            stepsText = stringResource(
                R.string.editor_chapter_next_steps_fmt,
                pluralWithNumber(context, R.plurals.steps, chapter.requiredSteps),
            )
            AdventureTheme.extendedColors.statusWarning
        }
        else -> {
            stepsText = stringResource(R.string.editor_chapter_no_gate)
            AdventureTheme.extendedColors.statusWarning
        }
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.editor_chapter_title_fmt, chapter.index),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                R.string.editor_chapter_details_fmt,
                pluralWithNumber(context, R.plurals.word_scenes, chapter.sceneCount),
                pluralWithNumber(context, R.plurals.word_words, chapter.wordCount),
                pluralWithNumber(context, R.plurals.word_choices, chapter.choiceCount),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stepsText,
            style = MaterialTheme.typography.bodySmall,
            color = stepsColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}