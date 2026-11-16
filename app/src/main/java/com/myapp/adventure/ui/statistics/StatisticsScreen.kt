package com.myapp.adventure.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.adventure.R
import com.myapp.adventure.data.StepBucket
import com.myapp.adventure.ui.theme.AdventureTheme
import com.myapp.adventure.utils.ruQuantityString
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** First rank threshold (visual only; reward logic is future work). */
private const val FIRST_RANK_THRESHOLD = 100_000L

@Composable
fun StatisticsScreen(
    onNavigateToMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNavigateToMenu) {
                Text(stringResource(R.string.stats_menu))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                Text(
                    text = stringResource(R.string.stats_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
                return@Column
            }

            if (!state.hasActivityPermission) {
                Card {
                    Text(
                        text = stringResource(R.string.stats_no_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AdventureTheme.extendedColors.statusWarning,
                    )
                }
            }

            if (!state.hasData) {
                Text(
                    text = stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
                return@Column
            }

            SummaryCard(total = state.total)
            RecentCard(
                today = state.today,
                last7 = state.last7,
                last30 = state.last30,
            )

            Text(
                text = stringResource(R.string.stats_last_14_days_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            state.days.forEach { bucket ->
                DayRow(bucket)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun SummaryCard(total: Long) {
    Card {
        Text(
            text = stringResource(R.string.stats_total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatSteps(total),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )

        Text(
            text = stringResource(R.string.stats_rank_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        val reached = total >= FIRST_RANK_THRESHOLD
        val progress = if (reached) {
            1f
        } else {
            ((total.toDouble() / FIRST_RANK_THRESHOLD).coerceIn(0.0, 1.0)).toFloat()
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        val rankName = stringResource(R.string.stats_rank_beginner)
        val progressText = if (reached) {
            stringResource(R.string.stats_rank_achieved, rankName)
        } else {
            stringResource(R.string.stats_rank_progress, (progress * 100).toInt(), rankName)
        }
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelSmall,
            color = if (reached) AdventureTheme.extendedColors.statusSuccess
            else AdventureTheme.extendedColors.statusWarning,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RecentCard(today: Int, last7: Int, last30: Int) {
    Card {
        StatLine(stringResource(R.string.stats_today), today)
        StatLine(stringResource(R.string.stats_last_7_days), last7)
        StatLine(stringResource(R.string.stats_last_30_days), last30)
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Text(
        text = "$label: ${formatSteps(value.toLong())}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------
// Day row
// ---------------------------------------------------------------------------

@Composable
private fun DayRow(bucket: StepBucket) {
    val date = try {
        LocalDate.parse(bucket.date)
    } catch (_: Exception) {
        return
    }
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val isToday = date == today

    val dateFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)

    val dateLabel = if (isToday) {
        val todayLabel = stringResource(R.string.stats_today).lowercase(locale)
        "${dateFormatter.format(date)} · $todayLabel"
    } else {
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        "$dayOfWeek, ${dateFormatter.format(date)}"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatSteps(bucket.steps.toLong()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (bucket.steps == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    AdventureTheme.extendedColors.statusWarning
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun formatSteps(value: Long): String {
    val context = LocalContext.current
    return remember(value) {
        val formattedNumber = NumberFormat
            .getIntegerInstance(Locale.getDefault())
            .format(value)
        // ruQuantityString forces the Russian plural forms even on non-ru
        // devices; the string is "N шаг/шага/шагов" — strip the leading "N".
        val stepsText = ruQuantityString(
            context, R.plurals.steps, value.toInt()
        )
        val suffix = stepsText.replace(value.toString(), "").trim()
        "$formattedNumber $suffix"
    }
}