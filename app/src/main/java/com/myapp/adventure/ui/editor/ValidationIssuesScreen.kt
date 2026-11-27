package com.myapp.adventure.ui.editor

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.adventure.R
import com.myapp.adventure.ui.GlobalMenuButton
import com.myapp.adventure.data.IssueSeverity
import com.myapp.adventure.data.ValidationIssue
import com.myapp.adventure.ui.theme.AdventureTheme

/**
 * Phase 3.1c-iv-b: "Здоровье истории" as a full screen (previously a
 * `dialog_validation_issues.xml` dialog).
 *
 * Two visual states:
 *  - [issues] empty → "Проблем не найдено" + Back.
 *  - non-empty → "N ошибок • M предупреждений" + list. Tap on an issue
 *    with a non-empty `sceneUid` → `onIssueClick(uid)`; `ValidationIssuesRoute`
 *    then pops back and the editor's `pendingJumpToSceneUid` handles
 *    scroll/highlight.
 *
 * Issue ordering (errors before warnings) is decided by the host, not
 * this screen — the list is rendered in the order it arrives.
 */
@Composable
fun ValidationIssuesScreen(
    issues: List<ValidationIssue>,
    onBack: () -> Unit,
    onIssueClick: (sceneUid: String) -> Unit,
) {
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
                    text = stringResource(R.string.editor_dialog_health_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                GlobalMenuButton()
            }
        }

        if (issues.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_dialog_health_no_issues_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        val errorCount = issues.count { it.severity == IssueSeverity.ERROR }
        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }
        val summary = buildList {
            if (errorCount > 0) {
                add(stringResource(R.string.validation_summary_errors_fmt, errorCount))
            }
            if (warningCount > 0) {
                add(stringResource(R.string.validation_summary_warnings_fmt, warningCount))
            }
        }.joinToString(" • ")

        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            items(issues) { issue ->
                IssueRow(issue = issue, onClick = { onIssueClick(issue.sceneUid) })
            }
        }
    }
}

@Composable
private fun IssueRow(issue: ValidationIssue, onClick: () -> Unit) {
    val isError = issue.severity == IssueSeverity.ERROR
    val clickable = issue.sceneUid.isNotEmpty()
    val modifier = if (clickable) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    }
    Row(modifier = modifier) {
        Text(
            text = if (isError) "❌" else "⚠️",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column {
            Text(
                text = stringResource(issue.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.error
                else AdventureTheme.extendedColors.statusWarning,
            )
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
